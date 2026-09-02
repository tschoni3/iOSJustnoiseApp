package com.justnoise.gate0.domain

interface SessionStateWriter {
    fun startSession(epochMilliseconds: Long): Boolean
    fun stopSession(
        epochMilliseconds: Long = System.currentTimeMillis(),
        expectedSessionId: String? = null,
    ): Boolean
    fun markRecoveryRequired()
}

object SessionDecisionPersistence {
    fun apply(
        decision: SessionDecision,
        epochMilliseconds: Long,
        writer: SessionStateWriter,
        expectedSessionId: String? = null,
    ): SessionDecision = when (decision.restrictionAction) {
        RestrictionAction.APPLY -> if (writer.startSession(epochMilliseconds)) {
            decision
        } else {
            writer.markRecoveryRequired()
            SessionDecision(
                transition = SessionTransition.START_FAILED,
                state = SessionState.FAILED,
                restrictionAction = RestrictionAction.NONE,
            )
        }

        RestrictionAction.CLEAR -> if (writer.stopSession(epochMilliseconds, expectedSessionId)) {
            decision
        } else {
            writer.markRecoveryRequired()
            SessionDecision(
                transition = SessionTransition.NO_CHANGE,
                state = SessionState.FAILED,
                restrictionAction = RestrictionAction.NONE,
            )
        }

        RestrictionAction.NONE -> decision
    }
}
