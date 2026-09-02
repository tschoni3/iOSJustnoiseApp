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
    fun `fresh heartbeat cannot clear interruption recovery while service stays connected`() {
        for (desiredActive in listOf(false, true)) {
            val interrupted = readyState().copy(
                desiredActive = desiredActive,
                recoveryRequired = true,
            )
            for (heartbeat in listOf(4_000L, 6_000L)) {
                assertEquals(
                    "desiredActive=$desiredActive, heartbeat=$heartbeat",
                    Gate0Truth.RECOVERY_REQUIRED,
                    Gate0StateReconciler.reconcile(
                        interrupted.copy(serviceHeartbeatElapsedMilliseconds = heartbeat),
                        accessibilityEnabled = true,
                        nowElapsedMilliseconds = heartbeat,
                    ),
                )
            }
        }
    }

    @Test
    fun `temporary disconnect recovers idle and active truth from a fresh heartbeat`() {
        for (desiredActive in listOf(false, true)) {
            val disconnected = readyState().copy(
                desiredActive = desiredActive,
                serviceConnected = false,
                serviceHeartbeatElapsedMilliseconds = 0L,
            )
            assertEquals(
                Gate0Truth.RECOVERY_REQUIRED,
                Gate0StateReconciler.reconcile(disconnected, true, 5_000L),
            )

            val reconnected = disconnected.copy(
                serviceConnected = true,
                serviceHeartbeatElapsedMilliseconds = 5_000L,
            )
            assertEquals(
                if (desiredActive) Gate0Truth.ACTIVE else Gate0Truth.IDLE,
                Gate0StateReconciler.reconcile(reconnected, true, 5_000L),
            )
        }
    }

    @Test
    fun `heartbeat freshness includes zero and the timeout but rejects future and expired values`() {
        val timeout = Gate0StateReconciler.HEARTBEAT_STALE_AFTER_MILLISECONDS
        for (desiredActive in listOf(false, true)) {
            val stored = readyState().copy(desiredActive = desiredActive)
            for (age in listOf(-1L, 0L, timeout, timeout + 1L)) {
                val expected = when {
                    age !in 0L..timeout -> Gate0Truth.RECOVERY_REQUIRED
                    desiredActive -> Gate0Truth.ACTIVE
                    else -> Gate0Truth.IDLE
                }
                assertEquals(
                    "desiredActive=$desiredActive, heartbeatAge=$age",
                    expected,
                    Gate0StateReconciler.reconcile(
                        stored,
                        accessibilityEnabled = true,
                        nowElapsedMilliseconds = stored.serviceHeartbeatElapsedMilliseconds + age,
                    ),
                )
            }
        }
    }

    @Test
    fun `connected flag without a recorded heartbeat is not ready`() {
        assertEquals(
            Gate0Truth.RECOVERY_REQUIRED,
            Gate0StateReconciler.reconcile(
                readyState().copy(serviceHeartbeatElapsedMilliseconds = 0L),
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 0L,
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
