package com.justnoise.gate0.feature

import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.StoredGate0State
import org.junit.Assert.assertEquals
import org.junit.Test

class Gate0StateReconcilerTest {
    @Test
    fun `stale heartbeat never produces active truth`() {
        val stored = readyState().copy(
            desiredActive = true,
            serviceHeartbeatElapsedMilliseconds = 1_000L,
        )

        assertEquals(
            Gate0Truth.RECOVERY_REQUIRED,
            Gate0StateReconciler.reconcile(
                stored,
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 10_000L,
            ),
        )
    }

    @Test
    fun `fresh connected heartbeat permits active truth`() {
        assertEquals(
            Gate0Truth.ACTIVE,
            Gate0StateReconciler.reconcile(
                readyState().copy(desiredActive = true),
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
    }

    @Test
    fun `revocation is permission required while idle and recovery required while desired active`() {
        assertEquals(
            Gate0Truth.PERMISSION_REQUIRED,
            Gate0StateReconciler.reconcile(
                readyState(),
                accessibilityEnabled = false,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
        assertEquals(
            Gate0Truth.RECOVERY_REQUIRED,
            Gate0StateReconciler.reconcile(
                readyState().copy(desiredActive = true),
                accessibilityEnabled = false,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
    }

    @Test
    fun `current disclosure target and Zap are explicit prerequisites`() {
        assertEquals(
            Gate0Truth.CONSENT_REQUIRED,
            Gate0StateReconciler.reconcile(
                readyState().copy(acceptedDisclosureVersion = null),
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
        assertEquals(
            Gate0Truth.TARGET_REQUIRED,
            Gate0StateReconciler.reconcile(
                readyState().copy(selectedPackage = null),
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
        assertEquals(
            Gate0Truth.ZAP_REQUIRED,
            Gate0StateReconciler.reconcile(
                readyState().copy(zapDigest = null),
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
        assertEquals(
            Gate0Truth.RECOVERY_REQUIRED,
            Gate0StateReconciler.reconcile(
                readyState().copy(recoveryRequired = true),
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
    }

    private fun readyState() = StoredGate0State(
        acceptedDisclosureVersion = Gate0Preferences.CURRENT_DISCLOSURE_VERSION,
        acceptedDisclosureEpochMilliseconds = 1_000L,
        selectedPackage = "fixture.target",
        zapDigest = "digest",
        desiredActive = false,
        activeSinceEpochMilliseconds = 0L,
        sessionRevision = 1L,
        recoveryRequired = false,
        serviceConnected = true,
        serviceHeartbeatElapsedMilliseconds = 4_000L,
    )
}
