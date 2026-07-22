package com.justnoise.gate0.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDecisionPersistenceTest {
    @Test
    fun `failed start persistence cannot report active`() {
        val writer = FakeWriter(startSucceeds = false, stopSucceeds = true)
        val result = SessionDecisionPersistence.apply(
            decision = SessionDecision(
                SessionTransition.START,
                SessionState.ACTIVE,
                RestrictionAction.APPLY,
            ),
            epochMilliseconds = 123L,
            writer = writer,
        )

        assertEquals(SessionTransition.START_FAILED, result.transition)
        assertEquals(SessionState.FAILED, result.state)
        assertEquals(RestrictionAction.NONE, result.restrictionAction)
        assertEquals(true, writer.recoveryRequired)
    }

    @Test
    fun `failed stop persistence cannot report stopped`() {
        val writer = FakeWriter(startSucceeds = true, stopSucceeds = false)
        val result = SessionDecisionPersistence.apply(
            decision = SessionDecision(
                SessionTransition.STOP,
                SessionState.IDLE,
                RestrictionAction.CLEAR,
            ),
            epochMilliseconds = 123L,
            writer = writer,
        )

        assertEquals(SessionTransition.NO_CHANGE, result.transition)
        assertEquals(SessionState.FAILED, result.state)
        assertEquals(RestrictionAction.NONE, result.restrictionAction)
        assertEquals(true, writer.recoveryRequired)
    }

    @Test
    fun `successful writes preserve reducer decision`() {
        val start = SessionDecision(
            SessionTransition.START,
            SessionState.ACTIVE,
            RestrictionAction.APPLY,
        )
        val stop = SessionDecision(
            SessionTransition.STOP,
            SessionState.IDLE,
            RestrictionAction.CLEAR,
        )
        val writer = FakeWriter(startSucceeds = true, stopSucceeds = true)

        assertEquals(start, SessionDecisionPersistence.apply(start, 123L, writer))
        assertEquals(stop, SessionDecisionPersistence.apply(stop, 123L, writer))
    }

    private class FakeWriter(
        private val startSucceeds: Boolean,
        private val stopSucceeds: Boolean,
    ) : SessionStateWriter {
        var recoveryRequired = false

        override fun startSession(epochMilliseconds: Long): Boolean = startSucceeds
        override fun stopSession(): Boolean = stopSucceeds
        override fun markRecoveryRequired() {
            recoveryRequired = true
        }
    }
}
