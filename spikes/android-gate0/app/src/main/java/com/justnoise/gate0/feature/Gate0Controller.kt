package com.justnoise.gate0.feature

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.data.ScheduledOccurrence
import com.justnoise.gate0.data.ScheduleDispatchResult
import com.justnoise.gate0.data.ScheduleOutcomeCode
import com.justnoise.gate0.domain.PayloadClassification
import com.justnoise.gate0.domain.RestrictionAction
import com.justnoise.gate0.domain.RestrictionCapability
import com.justnoise.gate0.domain.SessionDecision
import com.justnoise.gate0.domain.SessionDecisionPersistence
import com.justnoise.gate0.domain.SessionReducer
import com.justnoise.gate0.domain.SessionState
import com.justnoise.gate0.domain.ZapReadDecision
import com.justnoise.gate0.domain.ZapReadRules
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import com.justnoise.gate0.platform.discovery.ProtectedPackagePolicy
import com.justnoise.gate0.platform.discovery.ProtectedPackageResolver
import com.justnoise.gate0.platform.restriction.AccessibilityServiceStatus
import com.justnoise.gate0.platform.restriction.Gate0AccessibilityService
import com.justnoise.gate0.platform.restriction.ScheduledEnforcement
import com.justnoise.gate0.platform.schedule.ScheduleAlarmScheduler

data class ZapInteraction(
    val readDecision: ZapReadDecision,
    val sessionDecision: SessionDecision?,
    val persistenceFailed: Boolean = false,
)

class Gate0Controller(
    context: Context,
    private val preferences: Gate0Preferences = Gate0Preferences(context),
    private val zapStore: HmacZapStore = HmacZapStore(preferences),
) {
    private val applicationContext = context.applicationContext
    private val protectedPackages = ProtectedPackageResolver(applicationContext)
    private val launchableApps = LauncherAppRepository(applicationContext)

    fun acceptDisclosure(): Boolean = preferences.acceptDisclosure(
        Gate0Preferences.CURRENT_DISCLOSURE_VERSION,
    )

    fun selectTargets(packageNames: Set<String>): Boolean {
        val state = preferences.snapshot()
        // Restoring the same selection is not a session change or a broadcast event.
        if (state.selectedPackages == packageNames) return true
        if (state.desiredActive || !targetsAreEligible(packageNames)) return false

        val stored = preferences.setSelectedPackages(packageNames)
        if (stored) publishStateChange()
        return stored
    }

    fun pairZap(transientCanonicalRecord: ByteArray): Boolean =
        zapStore.pair(transientCanonicalRecord)

    fun handleZap(
        transientCanonicalRecord: ByteArray,
        acceptedReadAlreadyHandled: Boolean,
        nowEpochMilliseconds: Long = System.currentTimeMillis(),
        nowElapsedMilliseconds: Long = SystemClock.elapsedRealtime(),
    ): ZapInteraction {
        val classification = if (zapStore.isAuthorized(transientCanonicalRecord)) {
            PayloadClassification.AUTHORIZED
        } else {
            PayloadClassification.UNAUTHORIZED
        }
        val readDecision = ZapReadRules.decide(classification, acceptedReadAlreadyHandled)
        if (!readDecision.mutationEligible) return ZapInteraction(readDecision, null)

        val stored = preferences.snapshot()
        val priorState = if (stored.desiredActive) SessionState.ACTIVE else SessionState.IDLE
        val targetsAreEligible = stored.selectedPackages.isNotEmpty() &&
            targetsAreEligible(stored.selectedPackages)
        val currentConsent = stored.acceptedDisclosureVersion ==
            Gate0Preferences.CURRENT_DISCLOSURE_VERSION &&
            stored.acceptedDisclosureEpochMilliseconds > 0L
        val serviceReady = currentConsent && Gate0StateReconciler.isServiceReady(
            stored = stored,
            accessibilityEnabled = AccessibilityServiceStatus.isEnabled(applicationContext),
            nowElapsedMilliseconds = nowElapsedMilliseconds,
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

        val persistedDecision = SessionDecisionPersistence.apply(
            decision = sessionDecision,
            epochMilliseconds = nowEpochMilliseconds,
            writer = preferences,
            expectedSessionId = stored.activeSessionId,
        )
        // The service dismisses its overlay on this signal. Rejected/no-op reads
        // must leave it in place; failed writes still notify for safe recovery.
        if (persistedDecision.restrictionAction != RestrictionAction.NONE ||
            persistedDecision != sessionDecision
        ) {
            publishStateChange()
        }
        return ZapInteraction(readDecision, persistedDecision, persistedDecision != sessionDecision)
    }

    fun truth(nowElapsedMilliseconds: Long = SystemClock.elapsedRealtime()): Gate0Truth {
        val stored = preferences.snapshot()
        val enabled = AccessibilityServiceStatus.isEnabled(applicationContext)
        if (stored.desiredActive && !Gate0StateReconciler.isServiceReady(stored, enabled, nowElapsedMilliseconds)) {
            preferences.markHistoryInterrupted()
        }
        return Gate0StateReconciler.reconcile(
            stored = stored,
            accessibilityEnabled = enabled,
            nowElapsedMilliseconds = nowElapsedMilliseconds,
        )
    }

    fun startScheduled(occurrence: ScheduledOccurrence): ScheduleDispatchResult {
        val storedBefore = preferences.snapshot()
        val pending = preferences.loadScheduleExecutions().entries
            .find { it.scheduleId == occurrence.scheduleId }?.pending
        val prepared = if (!storedBefore.desiredActive && pending == occurrence &&
            hasAcceptedCurrentDisclosure() && !storedBefore.recoveryRequired &&
            !preferences.areSchedulesSuspended()
        ) ScheduledEnforcement.prepareStart() else null
        val result = preferences.dispatchSchedule(occurrence) { stored, _ ->
            when {
                !hasAcceptedCurrentDisclosure() -> ScheduleOutcomeCode.CONSENT_REQUIRED
                preferences.areSchedulesSuspended() || stored.recoveryRequired -> ScheduleOutcomeCode.RECOVERY_REQUIRED
                stored.zapDigest.isNullOrBlank() -> ScheduleOutcomeCode.ZAP_REQUIRED
                !targetsAreEligible(stored.selectedPackages) -> ScheduleOutcomeCode.TARGET_UNAVAILABLE
                !ScheduleAlarmScheduler(applicationContext).hasExactAlarmAccess() -> ScheduleOutcomeCode.EXACT_ALARM_REQUIRED
                !AccessibilityServiceStatus.isEnabled(applicationContext) ||
                    prepared?.isStillReady() != true -> ScheduleOutcomeCode.CAPABILITY_UNAVAILABLE
                else -> null
            }
        }
        // Skips and duplicates must leave an existing overlay untouched. A start can
        // now intercept the last observed foreground target without waiting for navigation.
        if (result.started || result.persistenceFailed) ScheduledEnforcement.stateChanged()
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
    }

    private fun targetsAreEligible(packageNames: Set<String>): Boolean {
        val launchablePackages = launchableApps.load().mapTo(mutableSetOf()) { it.packageName }
        val protected = protectedPackages.resolve()
        return packageNames.all { packageName ->
            packageName in launchablePackages &&
                !ProtectedPackagePolicy.isProtected(packageName, protected)
        }
    }
}
