package com.justnoise.gate0.feature

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.data.ScheduledOccurrence
import com.justnoise.gate0.data.ScheduleDispatchResult
import com.justnoise.gate0.data.ScheduleOutcomeCode
import com.justnoise.gate0.data.SelectionEditResult
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.data.ZapPairingToken
import com.justnoise.gate0.domain.PayloadClassification
import com.justnoise.gate0.domain.RestrictionAction
import com.justnoise.gate0.domain.RestrictionCapability
import com.justnoise.gate0.domain.SessionDecision
import com.justnoise.gate0.domain.SessionDecisionPersistence
import com.justnoise.gate0.domain.SessionReducer
import com.justnoise.gate0.domain.SessionState
import com.justnoise.gate0.domain.SessionShortcutRules
import com.justnoise.gate0.domain.LongPressStartDecision
import com.justnoise.gate0.domain.ZapReadOutcome
import com.justnoise.gate0.domain.ZapReadDecision
import com.justnoise.gate0.domain.ZapReadRules
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import com.justnoise.gate0.platform.discovery.ProtectedPackagePolicy
import com.justnoise.gate0.platform.discovery.ProtectedPackageResolver
import com.justnoise.gate0.platform.notification.SessionNotifications
import com.justnoise.gate0.platform.nfc.ZapCompatibilityLabels
import com.justnoise.gate0.platform.restriction.AccessibilityServiceStatus
import com.justnoise.gate0.platform.restriction.Gate0AccessibilityService
import com.justnoise.gate0.platform.restriction.ScheduledEnforcement
import com.justnoise.gate0.platform.schedule.ScheduleAlarmScheduler

data class ZapInteraction(
    val readDecision: ZapReadDecision,
    val sessionDecision: SessionDecision?,
    val persistenceFailed: Boolean = false,
    val completedSession: SessionHistoryEntry? = null,
    val accountRequired: Boolean = false,
)

data class SessionShortcutResult(val succeeded: Boolean, val noChange: Boolean = false,
    val completedSession: SessionHistoryEntry? = null)

class Gate0Controller(
    context: Context,
    private val preferences: Gate0Preferences = Gate0Preferences(context),
    private val zapStore: HmacZapStore = HmacZapStore(preferences),
    private val loadLaunchablePackages: () -> Set<String> =
        LauncherAppRepository(context.applicationContext)::loadPackageNames,
) {
    private val applicationContext = context.applicationContext
    private val protectedPackages = ProtectedPackageResolver(applicationContext)

    fun acceptDisclosure(): Boolean = preferences.acceptDisclosure(
        Gate0Preferences.CURRENT_DISCLOSURE_VERSION,
    )

    fun selectTargets(packageNames: Set<String>): Boolean {
        val state = preferences.snapshot()
        // Restoring the same selection is not a session change or a broadcast event.
        if (state.selectedPackages == packageNames) return true
        val token = preferences.readSelectionEdit().token ?: return false
        return commitTargetSelection(token, packageNames) in
            setOf(SelectionEditResult.SAVED, SelectionEditResult.UNCHANGED)
    }

    /** Discovery/validation belongs on the caller's worker, never under the storage lock. */
    fun commitTargetSelection(token: SelectionEditToken, packageNames: Set<String>): SelectionEditResult {
        val draft = packageNames.toSet()
        val current = preferences.readSelectionEdit()
        if (current.error) return SelectionEditResult.STORAGE_FAILED
        if (current.sessionRequested) return SelectionEditResult.ACTIVE_SESSION
        if (current.recoveryRequired) return SelectionEditResult.RECOVERY_REQUIRED
        if (current.token != token) return SelectionEditResult.CONFLICT
        // An unchanged set may retain a missing target until the user explicitly removes it.
        // Changed sets must still be eligible at Save, not merely at picker-open time.
        if (current.selectedPackages != draft && !targetsAreEligible(draft)) {
            return SelectionEditResult.INVALID_SELECTION
        }
        val result = preferences.commitSelectionEdit(token, draft)
        if (result == SelectionEditResult.SAVED || result == SelectionEditResult.STORAGE_FAILED) {
            publishStateChange()
        }
        return result
    }

    /** Legacy callers may only create the initial pairing. */
    fun pairZap(transientCanonicalRecord: ByteArray): Boolean = pairInitialZap(transientCanonicalRecord)

    fun prepareZapPairing(): ZapPairingToken? = preferences.prepareZapPairing()

    /** The setup UI freezes this intent before explicit replacement confirmation and scanning. */
    fun pairZap(transientCanonicalRecord: ByteArray, expectedToken: ZapPairingToken): Boolean = try {
        ZapCompatibilityLabels.isRecognized(transientCanonicalRecord) &&
            zapStore.pair(transientCanonicalRecord, expectedToken)
    } catch (_: Exception) {
        false
    }

    /** Initial foreground pairing only; existing setup keeps its separate replacement action. */
    fun pairInitialZap(transientCanonicalRecord: ByteArray): Boolean = try {
        ZapCompatibilityLabels.isRecognized(transientCanonicalRecord) &&
            zapStore.pairInitial(transientCanonicalRecord)
    } catch (_: Exception) {
        false
    }

    fun handleZap(
        transientCanonicalRecord: ByteArray,
        acceptedReadAlreadyHandled: Boolean,
        nowEpochMilliseconds: Long = System.currentTimeMillis(),
        nowElapsedMilliseconds: Long? = null,
    ): ZapInteraction {
        val events = applicationContext as? FocusSessionEvents
        val eventGeneration = runCatching { events?.focusEventGeneration() }.getOrNull()
        val (stored, startToken) = preferences.withScheduleLock {
            preferences.snapshot() to preferences.readSelectionEdit().token
        }
        val classification = if (ZapCompatibilityLabels.allowsSessionRead(
                recognized = ZapCompatibilityLabels.isRecognized(transientCanonicalRecord),
                paired = zapStore.isAuthorized(transientCanonicalRecord),
                sessionRequested = stored.desiredActive,
            )) {
            PayloadClassification.AUTHORIZED
        } else {
            PayloadClassification.UNAUTHORIZED
        }
        val readDecision = ZapReadRules.decide(classification, acceptedReadAlreadyHandled)
        if (!readDecision.mutationEligible) return ZapInteraction(readDecision, null)

        if (!stored.desiredActive && !preferences.accountAllowsSessionStart())
            return ZapInteraction(readDecision, null, accountRequired = true)

        val priorState = if (stored.desiredActive) SessionState.ACTIVE else SessionState.IDLE
        val targetsAreEligible = stored.selectedPackages.isNotEmpty() &&
            targetsAreEligible(stored.selectedPackages)
        val currentConsent = stored.acceptedDisclosureVersion ==
            Gate0Preferences.CURRENT_DISCLOSURE_VERSION &&
            stored.acceptedDisclosureEpochMilliseconds > 0L
        val serviceReady = currentConsent && Gate0StateReconciler.isServiceReady(
            stored = stored,
            accessibilityEnabled = AccessibilityServiceStatus.isEnabled(applicationContext),
            nowElapsedMilliseconds = nowElapsedMilliseconds ?: SystemClock.elapsedRealtime(),
        )
        if (stored.desiredActive && !serviceReady) preferences.markHistoryInterrupted()
        val capability = if (serviceReady) {
            RestrictionCapability.AVAILABLE
        } else {
            RestrictionCapability.TEMPORARILY_UNAVAILABLE
        }
        val elapsed = (nowEpochMilliseconds - stored.activeSinceEpochMilliseconds).coerceAtLeast(0L)
        val sessionDecision = SessionReducer.reduce(
            priorState = priorState,
            readOutcome = readDecision.outcome,
            selectionValid = targetsAreEligible,
            capability = capability,
            elapsedMilliseconds = elapsed,
        )

        var completedSession: SessionHistoryEntry? = null
        val writer = ValidatedSessionWriter(preferences, stored, startToken,
            capabilityReady = { current -> Gate0StateReconciler.isServiceReady(
                current, AccessibilityServiceStatus.isEnabled(applicationContext), SystemClock.elapsedRealtime(),
            ) }, completed = { completedSession = it })
        val persistedDecision = SessionDecisionPersistence.apply(
            decision = sessionDecision,
            epochMilliseconds = nowEpochMilliseconds,
            writer = writer,
            expectedSessionId = stored.activeSessionId,
        )
        // The service dismisses its overlay on this signal. Rejected/no-op reads
        // must leave it in place; failed writes still notify for safe recovery.
        if (persistedDecision.restrictionAction != RestrictionAction.NONE ||
            writer.persistenceFailed
        ) {
            publishStateChange()
        }
        if (!writer.persistenceFailed && persistedDecision == sessionDecision && eventGeneration != null) {
            when (persistedDecision.restrictionAction) {
                RestrictionAction.APPLY -> runCatching { events?.recordFocusEvent(eventGeneration, true) }
                RestrictionAction.CLEAR -> runCatching { events?.recordFocusEvent(eventGeneration, false) }
                else -> Unit
            }
        }
        return ZapInteraction(
            readDecision, persistedDecision, writer.persistenceFailed,
            accountRequired = !stored.desiredActive && !preferences.accountAllowsSessionStart() &&
                persistedDecision.restrictionAction != RestrictionAction.APPLY,
            completedSession = completedSession.takeIf {
                persistedDecision.restrictionAction == RestrictionAction.CLEAR &&
                    persistedDecision == sessionDecision
            },
        )
    }

    fun truth(nowElapsedMilliseconds: Long? = null): Gate0Truth {
        val (stored, enabled, observedAt) = Gate0StateReconciler.observe(
            readStored = preferences::snapshot,
            readAccessibilityEnabled = { AccessibilityServiceStatus.isEnabled(applicationContext) },
            readElapsedRealtime = SystemClock::elapsedRealtime,
            nowElapsedMilliseconds = nowElapsedMilliseconds,
        )
        if (stored.desiredActive && !Gate0StateReconciler.isServiceReady(stored, enabled, observedAt)) {
            preferences.markHistoryInterrupted()
        }
        return Gate0StateReconciler.reconcile(
            stored = stored,
            accessibilityEnabled = enabled,
            nowElapsedMilliseconds = observedAt,
        )
    }

    /** Start-only UI shortcut. Never treats fabricated tag bytes as an authorized scan. */
    fun startWithoutScan(): SessionShortcutResult {
        val events = applicationContext as? FocusSessionEvents
        val generation = runCatching { events?.focusEventGeneration() }.getOrNull()
        val (stored, token) = preferences.withScheduleLock {
            preferences.snapshot() to preferences.readSelectionEdit().token
        }
        if (stored.desiredActive) return SessionShortcutResult(false, noChange = true)
        val ready = hasAcceptedCurrentDisclosure() && Gate0StateReconciler.isServiceReady(stored,
            AccessibilityServiceStatus.isEnabled(applicationContext), SystemClock.elapsedRealtime())
        val decision = SessionShortcutRules.longPress(stored.desiredActive, stored.recoveryRequired,
            preferences.accountAllowsSessionStart(), !stored.zapDigest.isNullOrBlank(),
            !stored.modeStorageError && stored.selectedPackages.isNotEmpty() && targetsAreEligible(stored.selectedPackages), ready)
        if (decision != LongPressStartDecision.START)
            return SessionShortcutResult(false, noChange = decision == LongPressStartDecision.NO_CHANGE)
        val writer = ValidatedSessionWriter(preferences, stored, token,
            capabilityReady = { current -> hasAcceptedCurrentDisclosure() && Gate0StateReconciler.isServiceReady(
                current, AccessibilityServiceStatus.isEnabled(applicationContext), SystemClock.elapsedRealtime()) },
            completed = {})
        val start = SessionReducer.reduce(SessionState.IDLE, ZapReadOutcome.ACCEPTED, true,
            RestrictionCapability.AVAILABLE, 0L)
        val result = SessionDecisionPersistence.apply(start, System.currentTimeMillis(), writer, stored.activeSessionId)
        if (result.restrictionAction == RestrictionAction.APPLY || writer.persistenceFailed) publishStateChange()
        val succeeded = !writer.persistenceFailed && result.restrictionAction == RestrictionAction.APPLY
        if (succeeded && generation != null) runCatching { events?.recordFocusEvent(generation, true) }
        return SessionShortcutResult(succeeded)
    }

    fun emergencyUnzap(expectedSessionId: String): SessionShortcutResult {
        val events = applicationContext as? FocusSessionEvents
        val generation = runCatching { events?.focusEventGeneration() }.getOrNull()
        val result = preferences.emergencyUnzap(System.currentTimeMillis(), expectedSessionId)
        // A stale/exhausted confirmation must not dismiss a different session's overlay.
        if (result.succeeded || preferences.snapshot().recoveryRequired) publishStateChange()
        if (result.succeeded && generation != null) runCatching { events?.recordFocusEvent(generation, false) }
        return SessionShortcutResult(result.succeeded, completedSession = result.completion)
    }

    fun startScheduled(occurrence: ScheduledOccurrence): ScheduleDispatchResult {
        ScheduledEnforcement.recordReadiness(ScheduledEnforcement.ReadinessStatus.NOT_ATTEMPTED)
        val events = applicationContext as? FocusSessionEvents
        val eventGeneration = runCatching { events?.focusEventGeneration() }.getOrNull()
        val storedBefore = preferences.snapshot()
        val pending = preferences.loadScheduleExecutions().entries
            .find { it.scheduleId == occurrence.scheduleId }?.pending
        val mayPrepare = !storedBefore.desiredActive && pending == occurrence &&
            hasAcceptedCurrentDisclosure() && !storedBefore.recoveryRequired &&
            !preferences.areSchedulesSuspended()
        // Package-manager discovery can take longer than the short foreground lease.
        // Finish it before requesting that lease and outside the preference lock.
        // This inventory is per attempt, not a persisted eligibility/cache decision.
        val launchablePackages = if (mayPrepare) runCatching {
            loadLaunchablePackages()
        }.getOrNull() else null
        val prepared = if (launchablePackages != null) ScheduledEnforcement.prepareStart() else null
        val result = preferences.dispatchSchedule(occurrence) { stored, _ ->
            when {
                !hasAcceptedCurrentDisclosure() -> ScheduleOutcomeCode.CONSENT_REQUIRED
                preferences.areSchedulesSuspended() || stored.recoveryRequired -> ScheduleOutcomeCode.RECOVERY_REQUIRED
                stored.zapDigest.isNullOrBlank() -> ScheduleOutcomeCode.ZAP_REQUIRED
                launchablePackages == null ||
                    !targetsAreEligible(stored.selectedPackages, launchablePackages) -> ScheduleOutcomeCode.TARGET_UNAVAILABLE
                !ScheduleAlarmScheduler(applicationContext).hasExactAlarmAccess() -> ScheduleOutcomeCode.EXACT_ALARM_REQUIRED
                !AccessibilityServiceStatus.isEnabled(applicationContext) -> {
                    ScheduledEnforcement.recordReadiness(ScheduledEnforcement.ReadinessStatus.SERVICE_DISABLED)
                    ScheduleOutcomeCode.CAPABILITY_UNAVAILABLE
                }
                prepared?.isStillReady() != true -> ScheduleOutcomeCode.CAPABILITY_UNAVAILABLE
                else -> null
            }
        }
        // Skips and duplicates must leave an existing overlay untouched. A start can
        // now intercept the last observed foreground target without waiting for navigation.
        if (result.started || result.persistenceFailed) {
            ScheduledEnforcement.stateChanged()
            SessionNotifications.refresh(applicationContext)
        }
        if (result.started && !result.persistenceFailed && eventGeneration != null)
            runCatching { events?.recordFocusEvent(eventGeneration, true) }
        return result
    }

    fun selectedPackages(): Set<String> = preferences.snapshot().selectedPackages

    fun sessionPackages(): Set<String> = preferences.snapshot().let { stored ->
        if (stored.desiredActive) stored.activePackages else stored.selectedPackages
    }

    fun isSessionRequested(): Boolean = preferences.snapshot().desiredActive

    fun hasAcceptedCurrentDisclosure(): Boolean =
        preferences.snapshot().let { stored ->
            stored.acceptedDisclosureVersion == Gate0Preferences.CURRENT_DISCLOSURE_VERSION &&
                stored.acceptedDisclosureEpochMilliseconds > 0L
        }

    fun withdrawAndClear(): Boolean {
        SessionNotifications.cancel(applicationContext)
        val localStateCleared = preferences.withScheduleLock {
            val preferencesCleared = preferences.clearForWithdrawal()
            val alarmsCleared = ScheduleAlarmScheduler(applicationContext).cancel()
            preferencesCleared && alarmsCleared
        }
        publishStateChange()
        val keyCleared = try {
            zapStore.clear()
        } catch (_: Exception) {
            false
        }
        applicationContext.sendBroadcast(
            Intent(Gate0AccessibilityService.ACTION_WITHDRAW)
                .setPackage(applicationContext.packageName),
        )
        return keyCleared && localStateCleared
    }

    private fun publishStateChange() {
        applicationContext.sendBroadcast(
            Intent(Gate0AccessibilityService.ACTION_STATE_CHANGED)
                .setPackage(applicationContext.packageName),
        )
        SessionNotifications.refresh(applicationContext)
    }

    private fun targetsAreEligible(
        packageNames: Set<String>,
        launchablePackages: Set<String> = loadLaunchablePackages(),
    ): Boolean {
        // Re-resolve protected system roles at validation, including scheduled commits.
        val protected = protectedPackages.resolve()
        return packageNames.all { packageName ->
            packageName in launchablePackages &&
                !ProtectedPackagePolicy.isProtected(packageName, protected)
        }
    }
}
