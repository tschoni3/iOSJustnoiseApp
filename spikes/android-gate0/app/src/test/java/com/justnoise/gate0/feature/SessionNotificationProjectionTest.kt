package com.justnoise.gate0.feature

import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.StoredGate0State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionNotificationProjectionTest {
    private val active = StoredGate0State(
        acceptedDisclosureVersion = Gate0Preferences.CURRENT_DISCLOSURE_VERSION,
        acceptedDisclosureEpochMilliseconds = 1L,
        selectedPackages = setOf("synthetic.app"),
        activePackages = setOf("synthetic.app"),
        zapDigest = "synthetic-local-digest",
        desiredActive = true,
        activeSinceEpochMilliseconds = 1_000L,
        sessionRevision = 2L,
        recoveryRequired = false,
        serviceConnected = true,
        serviceHeartbeatElapsedMilliseconds = 100_000L,
        activeSessionId = "session-one",
        activeSinceElapsedMilliseconds = 50_000L,
        activeClockMatchesProcess = true,
    )

    @Test fun elapsedPresentationUsesThePersistedMonotonicStart() {
        assertEquals(SessionNotificationProjection.SessionTime("session-one", 50_000L), project(active))
    }

    @Test fun liveUpdateUsesOnlyBoundedActiveModeSnapshotNotCurrentSelection() {
        assertEquals(SessionNotificationProjection.SessionTime("session-one", 50_000L, "Sleep"),
            project(active.copy(activeModeName = "  Sleep  ")))
        assertEquals(project(active), project(active.copy(activeModeName = "  ")))
        assertEquals(60, (project(active.copy(activeModeName = "x".repeat(100))) as
            SessionNotificationProjection.SessionTime).modeName?.length)
    }

    @Test fun manualAndScheduledSessionsShareExactlyOneProjection() {
        assertEquals(project(active), project(active.copy(activeScheduleId = "schedule-one")))
    }

    @Test fun normalHeartbeatAndElapsedTicksDoNotChangeTheNotificationKey() {
        assertEquals(project(active), SessionNotificationRules.project(
            active.copy(serviceHeartbeatElapsedMilliseconds = 105_000L), true, 106_000L))
    }

    @Test fun idleMissingIdentityAndWithdrawalNeverLeaveASessionSurface() {
        for (stored in listOf(active.copy(desiredActive = false), active.copy(activeSessionId = null),
            active.copy(activeSessionId = " "), active.copy(acceptedDisclosureVersion = null),
            active.copy(acceptedDisclosureEpochMilliseconds = 0L))) {
            assertEquals(SessionNotificationProjection.Hidden, project(stored))
        }
    }

    @Test fun dismissalHidesOnlyTheMatchingSessionWithoutChangingItsState() {
        assertEquals(SessionNotificationProjection.Hidden,
            project(active.copy(dismissedNotificationSessionId = "session-one")))
        assertEquals(project(active), project(active.copy(dismissedNotificationSessionId = "older-session")))
        assertTrue(active.desiredActive)
        assertEquals(2L, active.sessionRevision)
    }

    @Test fun RecoveryOrDisconnectedServiceHasNoRunningChronometer() {
        for (stored in listOf(active.copy(recoveryRequired = true), active.copy(serviceConnected = false),
            active.copy(serviceHeartbeatElapsedMilliseconds = 90_000L),
            active.copy(serviceHeartbeatElapsedMilliseconds = 102_000L))) {
            assertEquals(SessionNotificationProjection.Attention("session-one"), project(stored))
        }
        assertEquals(SessionNotificationProjection.Attention("session-one"),
            SessionNotificationRules.project(active, false, 101_000L))
    }

    @Test fun MissingTargetsOrPairingNeverClaimsHealthyTiming() {
        assertEquals(SessionNotificationProjection.Attention("session-one"), project(active.copy(activePackages = emptySet())))
        assertEquals(SessionNotificationProjection.Attention("session-one"), project(active.copy(zapDigest = null)))
    }

    @Test fun RecreatedProcessAndInvalidElapsedStartGetAttentionInsteadOfAnInventedTimer() {
        for (stored in listOf(active.copy(activeClockMatchesProcess = false),
            active.copy(activeSinceElapsedMilliseconds = 0L),
            active.copy(activeSinceElapsedMilliseconds = 102_000L))) {
            assertEquals(SessionNotificationProjection.Attention("session-one"), project(stored))
        }
    }

    @Test fun wallClockAdjustmentKeepsElapsedDurationOnTheNextPost() {
        assertEquals(999_949_000L, SessionNotificationRules.chronometerWhen(50_000L, 101_000L, 1_000_000_000L))
        assertEquals(1_003_549_000L, SessionNotificationRules.chronometerWhen(50_000L, 101_000L, 1_003_600_000L))
        assertEquals(996_349_000L, SessionNotificationRules.chronometerWhen(50_000L, 101_000L, 996_400_000L))
    }

    @Test fun invalidClockMappingCannotCreateANegativeTimestampOrOverflow() {
        assertNull(SessionNotificationRules.chronometerWhen(0L, 1L, 100L))
        assertNull(SessionNotificationRules.chronometerWhen(2L, 1L, 100L))
        assertNull(SessionNotificationRules.chronometerWhen(1L, 200L, 100L))
        assertNull(SessionNotificationRules.chronometerWhen(1L, Long.MAX_VALUE, -1L))
    }

    @Test fun clockJitterDoesNotCauseHeartbeatRepostingButRealAdjustmentsDo() {
        assertTrue(SessionNotificationRules.clockOffsetChanged(null, 100_000L))
        assertFalse(SessionNotificationRules.clockOffsetChanged(100_000L, 100_001L))
        assertFalse(SessionNotificationRules.clockOffsetChanged(100_000L, 101_000L))
        assertTrue(SessionNotificationRules.clockOffsetChanged(100_000L, 101_001L))
        assertTrue(SessionNotificationRules.clockOffsetChanged(100_000L, 98_999L))
        assertTrue(SessionNotificationRules.clockOffsetChanged(Long.MIN_VALUE, Long.MAX_VALUE))
    }

    private fun project(stored: StoredGate0State) = SessionNotificationRules.project(stored, true, 101_000L)
}
