package com.justnoise.gate0.feature

import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.StoredGate0State
import org.junit.Assert.assertEquals
import org.junit.Test

class Gate0StateReconcilerTest {
    @Test
    fun `observation samples clock after a heartbeat committed during snapshot read`() {
        val calls = mutableListOf<String>()
        var elapsed = 3_999L
        val observed = Gate0StateReconciler.observe(
            readStored = {
                calls += "snapshot"
                // Models snapshot() waiting for the service's concurrent preference
                // commit. A clock sampled at method entry would now be too early.
                elapsed = 4_000L
                readyState(desiredActive = true).copy(
                    serviceHeartbeatElapsedMilliseconds = elapsed,
                )
            },
            readAccessibilityEnabled = {
                calls += "accessibility"
                true
            },
            readElapsedRealtime = {
                calls += "clock"
                elapsed
            },
        )

        assertEquals(listOf("snapshot", "accessibility", "clock"), calls)
        assertEquals(4_000L, observed.elapsedMilliseconds)
        assertEquals(
            Gate0Truth.ACTIVE,
            Gate0StateReconciler.reconcile(
                observed.stored, observed.accessibilityEnabled, observed.elapsedMilliseconds,
            ),
        )
    }

    @Test
    fun `observation preserves explicit timestamp and unchanged heartbeat boundaries`() {
        // Explicit timestamps are deterministic observations, not values to clamp
        // to the heartbeat. In particular, future heartbeats must still fail closed.
        for ((observedAt, expected) in listOf(
            3_999L to Gate0Truth.RECOVERY_REQUIRED,
            4_000L to Gate0Truth.ACTIVE,
            12_000L to Gate0Truth.ACTIVE,
            12_001L to Gate0Truth.RECOVERY_REQUIRED,
        )) {
            val calls = mutableListOf<String>()
            val observed = Gate0StateReconciler.observe(
                readStored = {
                    calls += "snapshot"
                    readyState(desiredActive = true)
                },
                readAccessibilityEnabled = {
                    calls += "accessibility"
                    true
                },
                readElapsedRealtime = { error("Explicit observation must not sample the clock") },
                nowElapsedMilliseconds = observedAt,
            )

            assertEquals(listOf("snapshot", "accessibility"), calls)
            assertEquals(observedAt, observed.elapsedMilliseconds)
            assertEquals(
                "observedAt=$observedAt",
                expected,
                Gate0StateReconciler.reconcile(
                    observed.stored, observed.accessibilityEnabled, observed.elapsedMilliseconds,
                ),
            )
        }
    }

    @Test
    fun `stale heartbeat never produces active truth`() {
        val stored = readyState(desiredActive = true).copy(
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
                readyState(desiredActive = true),
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
    }

    @Test
    fun `fresh heartbeat cannot clear interruption recovery while service stays connected`() {
        for (desiredActive in listOf(false, true)) {
            val interrupted = readyState(desiredActive).copy(
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
            val disconnected = readyState(desiredActive).copy(
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
            val stored = readyState(desiredActive)
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
                readyState(desiredActive = true),
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
                readyState().copy(selectedPackages = emptySet()),
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

    @Test
    fun `active truth uses captured targets even when saved selection is empty`() {
        assertEquals(
            Gate0Truth.ACTIVE,
            Gate0StateReconciler.reconcile(
                readyState(desiredActive = true).copy(selectedPackages = emptySet()),
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
    }

    @Test
    fun `active truth cannot borrow saved targets when active snapshot is empty`() {
        assertEquals(
            Gate0Truth.TARGET_REQUIRED,
            Gate0StateReconciler.reconcile(
                readyState(desiredActive = true).copy(activePackages = emptySet()),
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
    }

    @Test
    fun `idle truth does not use a stale active target snapshot`() {
        assertEquals(
            Gate0Truth.TARGET_REQUIRED,
            Gate0StateReconciler.reconcile(
                readyState().copy(
                    selectedPackages = emptySet(),
                    activePackages = setOf("fixture.old.active.target"),
                ),
                accessibilityEnabled = true,
                nowElapsedMilliseconds = 5_000L,
            ),
        )
    }

    @Test
    fun `legacy disclosure is retained but cannot authorize multi app behavior`() {
        for (desiredActive in listOf(false, true)) {
            assertEquals(
                Gate0Truth.CONSENT_REQUIRED,
                Gate0StateReconciler.reconcile(
                    readyState(desiredActive).copy(acceptedDisclosureVersion = "gate0-accessibility-v1"),
                    accessibilityEnabled = true,
                    nowElapsedMilliseconds = 5_000L,
                ),
            )
        }
    }

    private fun readyState(desiredActive: Boolean = false) = StoredGate0State(
        acceptedDisclosureVersion = Gate0Preferences.CURRENT_DISCLOSURE_VERSION,
        acceptedDisclosureEpochMilliseconds = 1_000L,
        selectedPackages = setOf("fixture.target", "fixture.second.target"),
        activePackages = if (desiredActive) setOf("fixture.target", "fixture.second.target") else emptySet(),
        zapDigest = "digest",
        desiredActive = desiredActive,
        activeSinceEpochMilliseconds = 0L,
        sessionRevision = 1L,
        recoveryRequired = false,
        serviceConnected = true,
        serviceHeartbeatElapsedMilliseconds = 4_000L,
    )
}
