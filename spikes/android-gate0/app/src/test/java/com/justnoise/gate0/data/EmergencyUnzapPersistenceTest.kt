package com.justnoise.gate0.data

import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

/** Exercises the actual stop/outbox transaction; no owner phone or installation data. */
class EmergencyUnzapPersistenceTest {
    private val raw = MemoryPreferences()
    private val history = MemoryHistory()
    private var elapsed = 10_000L
    private val store = Gate0Preferences(raw, history) { elapsed }

    @Before fun prepare() { assertTrue(store.clearForWithdrawal()); assertTrue(store.acceptDisclosure(
        Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L)); assertTrue(store.setSelectedPackages(setOf("example.focus"))) }
    @After fun finish() { assertTrue(store.clearForWithdrawal()) }

    private fun start(): String {
        assertTrue(store.startSession(100_000L))
        store.updateServiceHeartbeat(elapsed)
        elapsed += 5_000L
        return requireNotNull(store.snapshot().activeSessionId)
    }

    @Test fun emergencyStopsAndConsumesOneWithExactCompletionOnce() {
        assertEquals(5, store.emergencyUnlocksRemaining())
        val id = start()
        val stopped = store.emergencyUnzap(105_000L, id)
        assertTrue(stopped.succeeded)
        assertEquals(id, stopped.completion?.id)
        assertFalse(store.snapshot().desiredActive)
        assertTrue(store.snapshot().activePackages.isEmpty())
        assertEquals(4, store.emergencyUnlocksRemaining())
        assertFalse(store.emergencyUnzap(106_000L, id).succeeded)
        assertEquals(4, store.emergencyUnlocksRemaining())
        assertEquals(4, Gate0Preferences(raw, history) { elapsed }.emergencyUnlocksRemaining())
    }

    @Test fun idleStaleAndExhaustedDoNotConsumeOrStop() {
        assertFalse(store.emergencyUnzap(105_000L, "missing").succeeded)
        val id = start()
        assertFalse(store.emergencyUnzap(105_000L, "different-session").succeeded)
        assertEquals(5, store.emergencyUnlocksRemaining())
        raw.edit().putInt("emergency_unlocks_remaining_v1", 0).commit()
        assertFalse(store.emergencyUnzap(105_000L, id).succeeded)
        assertTrue(store.snapshot().desiredActive)
    }

    @Test fun failedCommitRestoresUnlockAndSessionWithoutCompletion() {
        val id = start()
        raw.failNextCommit = true
        val result = store.emergencyUnzap(105_000L, id)
        assertFalse(result.succeeded)
        assertNull(result.completion)
        assertEquals(5, store.emergencyUnlocksRemaining())
        assertEquals(id, store.snapshot().activeSessionId)
        assertTrue(store.snapshot().desiredActive)
        assertTrue(store.snapshot().recoveryRequired)
    }

    @Test fun recoveryAndClosedAccountCanStillEscapeWithoutService() {
        val id = start()
        store.markServiceDisconnected()
        store.markRecoveryRequired()
        store.denyAccountSessionStarts()
        assertTrue(store.emergencyUnzap(105_000L, id).succeeded)
        assertEquals(4, store.emergencyUnlocksRemaining())
        assertFalse(store.snapshot().recoveryRequired)
    }

    @Test fun normalZapStopDoesNotConsumeAndWithdrawalDoesNotReplenish() {
        val first = start()
        assertTrue(store.stopSession(105_000L, first))
        assertEquals(5, store.emergencyUnlocksRemaining())
        val second = start()
        assertTrue(store.emergencyUnzap(110_000L, second).succeeded)
        assertTrue(store.clearForWithdrawal())
        assertEquals(4, store.emergencyUnlocksRemaining())
    }
}
