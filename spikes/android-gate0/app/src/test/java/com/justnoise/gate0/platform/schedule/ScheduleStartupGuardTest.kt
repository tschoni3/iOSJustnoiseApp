package com.justnoise.gate0.platform.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleStartupGuardTest {
    @Test
    fun `missing current process evidence never permits background scheduling`() {
        val unrelated = listOf(
            ScheduleProcessStart(2, "app", false),
            ScheduleProcessStart(1, "app:other", false),
        )
        assertEquals(ScheduleStartupEvidence.UNKNOWN, currentScheduleStartupEvidence(emptyList(), 1, "app"))
        assertEquals(ScheduleStartupEvidence.UNKNOWN, currentScheduleStartupEvidence(unrelated, 1, "app"))
    }

    @Test
    fun `current process stopped start is retained despite later warm starts`() {
        val starts = listOf(
            ScheduleProcessStart(1, "app", false),
            ScheduleProcessStart(1, "app", true),
        )
        assertEquals(ScheduleStartupEvidence.RECOVERY_REQUIRED, currentScheduleStartupEvidence(starts, 1, "app"))
        assertEquals(
            ScheduleStartupEvidence.CLEAR,
            currentScheduleStartupEvidence(listOf(ScheduleProcessStart(1, "app", false)), 1, "app"),
        )
    }

    @Test
    fun `older platform user-requested exit requires recovery only after acknowledgment`() {
        val exits = listOf(ScheduleProcessExit(20, false), ScheduleProcessExit(10, true))
        assertEquals(ScheduleStartupEvidence.RECOVERY_REQUIRED, legacyScheduleStartupEvidence(exits, 9, 30))
        assertEquals(ScheduleStartupEvidence.CLEAR, legacyScheduleStartupEvidence(exits, 10, 30))
        assertEquals(ScheduleStartupEvidence.CLEAR, legacyScheduleStartupEvidence(exits, 11, 30))
    }

    @Test
    fun `empty invalid or clock-rollback legacy evidence stays unknown`() {
        assertEquals(ScheduleStartupEvidence.UNKNOWN, legacyScheduleStartupEvidence(emptyList(), 0, 30))
        assertEquals(ScheduleStartupEvidence.UNKNOWN, legacyScheduleStartupEvidence(listOf(ScheduleProcessExit(20, false)), 21, 19))
        assertEquals(ScheduleStartupEvidence.UNKNOWN, legacyScheduleStartupEvidence(listOf(ScheduleProcessExit(-1, false)), 0, 30))
        assertEquals(ScheduleStartupEvidence.UNKNOWN, legacyScheduleStartupEvidence(listOf(ScheduleProcessExit(40, false)), 0, 30))
    }

    @Test
    fun `uninitialized unknown and persisted suspension each block background work`() {
        val state = ScheduleStartupState()
        assertFalse(state.allowsBackground(false))
        assertFalse(state.acknowledge(true))
        state.initialize(ScheduleStartupEvidence.UNKNOWN)
        assertFalse(state.allowsBackground(false))
        assertTrue(state.acknowledge(true))
        assertTrue(state.allowsBackground(false))
        assertFalse(state.allowsBackground(true))
    }

    @Test
    fun `failed persistence and later component initialization cannot clear recovery`() {
        val state = ScheduleStartupState()
        state.initialize(ScheduleStartupEvidence.RECOVERY_REQUIRED)
        state.initialize(ScheduleStartupEvidence.CLEAR)
        assertFalse(state.allowsBackground(false))
        assertFalse(state.acknowledge(false))
        assertFalse(state.allowsBackground(false))
        assertTrue(state.acknowledge(true))
        assertTrue(state.allowsBackground(false))
        state.initialize(ScheduleStartupEvidence.RECOVERY_REQUIRED)
        assertTrue(state.allowsBackground(false))
    }

    @Test
    fun `failed acknowledgment blocks even when the earlier startup was clear`() {
        val state = ScheduleStartupState()
        state.initialize(ScheduleStartupEvidence.CLEAR)
        assertTrue(state.allowsBackground(false))
        assertFalse(state.acknowledge(false))
        assertFalse(state.allowsBackground(false))
    }
}
