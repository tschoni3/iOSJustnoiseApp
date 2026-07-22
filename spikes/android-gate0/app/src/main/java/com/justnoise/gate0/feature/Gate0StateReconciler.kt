package com.justnoise.gate0.feature

import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.StoredGate0State

enum class Gate0Truth {
    CONSENT_REQUIRED,
    TARGET_REQUIRED,
    ZAP_REQUIRED,
    PERMISSION_REQUIRED,
    IDLE,
    ACTIVE,
    RECOVERY_REQUIRED,
}

object Gate0StateReconciler {
    const val HEARTBEAT_STALE_AFTER_MILLISECONDS: Long = 8_000L

    fun reconcile(
        stored: StoredGate0State,
        accessibilityEnabled: Boolean,
        nowElapsedMilliseconds: Long,
    ): Gate0Truth {
        if (
            stored.acceptedDisclosureVersion != Gate0Preferences.CURRENT_DISCLOSURE_VERSION ||
            stored.acceptedDisclosureEpochMilliseconds <= 0L
        ) {
            return Gate0Truth.CONSENT_REQUIRED
        }
        if (stored.selectedPackage.isNullOrBlank()) return Gate0Truth.TARGET_REQUIRED
        if (stored.zapDigest.isNullOrBlank()) return Gate0Truth.ZAP_REQUIRED
        if (stored.recoveryRequired) return Gate0Truth.RECOVERY_REQUIRED

        val serviceReady = isServiceReady(
            stored = stored,
            accessibilityEnabled = accessibilityEnabled,
            nowElapsedMilliseconds = nowElapsedMilliseconds,
        )
        if (stored.desiredActive) {
            return if (serviceReady) Gate0Truth.ACTIVE else Gate0Truth.RECOVERY_REQUIRED
        }
        if (!accessibilityEnabled) return Gate0Truth.PERMISSION_REQUIRED
        return if (serviceReady) Gate0Truth.IDLE else Gate0Truth.RECOVERY_REQUIRED
    }

    fun isServiceReady(
        stored: StoredGate0State,
        accessibilityEnabled: Boolean,
        nowElapsedMilliseconds: Long,
    ): Boolean {
        val heartbeatAge = nowElapsedMilliseconds - stored.serviceHeartbeatElapsedMilliseconds
        return accessibilityEnabled &&
            stored.serviceConnected &&
            stored.serviceHeartbeatElapsedMilliseconds > 0L &&
            heartbeatAge in 0..HEARTBEAT_STALE_AFTER_MILLISECONDS
    }
}
