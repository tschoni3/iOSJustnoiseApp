package com.justnoise.gate0.feature

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.domain.PayloadClassification
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

data class ZapInteraction(
    val readDecision: ZapReadDecision,
    val sessionDecision: SessionDecision?,
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

    fun selectTarget(packageName: String): Boolean {
        val eligible = launchableApps.load().any { it.packageName == packageName }
        if (!eligible || ProtectedPackagePolicy.isProtected(packageName, protectedPackages.resolve())) {
            return false
        }
        val stored = preferences.setSelectedPackage(packageName)
        publishStateChange()
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
        val targetIsEligible = stored.selectedPackage?.let { packageName ->
            launchableApps.load().any { it.packageName == packageName } &&
                !ProtectedPackagePolicy.isProtected(packageName, protectedPackages.resolve())
        } ?: false
        val serviceReady = Gate0StateReconciler.isServiceReady(
            stored = stored,
            accessibilityEnabled = AccessibilityServiceStatus.isEnabled(applicationContext),
            nowElapsedMilliseconds = nowElapsedMilliseconds,
        )
        val capability = if (serviceReady) {
            RestrictionCapability.AVAILABLE
        } else {
            RestrictionCapability.TEMPORARILY_UNAVAILABLE
        }
        val elapsed = (nowEpochMilliseconds - stored.activeSinceEpochMilliseconds).coerceAtLeast(0L)
        val sessionDecision = SessionReducer.reduce(
            priorState = priorState,
            readOutcome = readDecision.outcome,
            selectionValid = targetIsEligible,
            capability = capability,
            elapsedMilliseconds = elapsed,
        )

        val persistedDecision = SessionDecisionPersistence.apply(
            decision = sessionDecision,
            epochMilliseconds = nowEpochMilliseconds,
            writer = preferences,
        )
        publishStateChange()
        return ZapInteraction(readDecision, persistedDecision)
    }

    fun truth(nowElapsedMilliseconds: Long = SystemClock.elapsedRealtime()): Gate0Truth =
        Gate0StateReconciler.reconcile(
            stored = preferences.snapshot(),
            accessibilityEnabled = AccessibilityServiceStatus.isEnabled(applicationContext),
            nowElapsedMilliseconds = nowElapsedMilliseconds,
        )

    fun selectedPackage(): String? = preferences.snapshot().selectedPackage

    fun hasAcceptedCurrentDisclosure(): Boolean =
        preferences.snapshot().let { stored ->
            stored.acceptedDisclosureVersion == Gate0Preferences.CURRENT_DISCLOSURE_VERSION &&
                stored.acceptedDisclosureEpochMilliseconds > 0L
        }

    fun withdrawAndClear(): Boolean {
        preferences.stopSession()
        publishStateChange()
        val keyCleared = try {
            zapStore.clear()
        } catch (_: Exception) {
            false
        }
        val preferencesCleared = preferences.clearForWithdrawal()
        applicationContext.sendBroadcast(
            Intent(Gate0AccessibilityService.ACTION_WITHDRAW)
                .setPackage(applicationContext.packageName),
        )
        return keyCleared && preferencesCleared
    }

    private fun publishStateChange() {
        applicationContext.sendBroadcast(
            Intent(Gate0AccessibilityService.ACTION_STATE_CHANGED)
                .setPackage(applicationContext.packageName),
        )
    }
}
