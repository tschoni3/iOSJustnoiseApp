package com.justnoise.gate0.domain

interface SessionStateWriter {
    fun startSession(epochMilliseconds: Long): Boolean
    fun stopSession(): Boolean
    fun markRecoveryRequired()
}

object SessionDecisionPersistence {
    fun apply(
        decision: SessionDecision,
        epochMilliseconds: Long,
        writer: SessionStateWriter,
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

        RestrictionAction.CLEAR -> if (writer.stopSession()) {
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
