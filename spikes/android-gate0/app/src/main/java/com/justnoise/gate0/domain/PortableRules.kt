package com.justnoise.gate0.domain

object TargetSelectionRules {
    fun isValid(
        applicationCount: Int,
        categoryCount: Int,
        websiteCount: Int,
    ): Boolean = applicationCount + categoryCount + websiteCount > 0
}

object AccidentalStopGuard {
    const val MINIMUM_ELAPSED_MILLISECONDS: Long = 3_000L

    fun isStopAllowed(elapsedMilliseconds: Long): Boolean =
        elapsedMilliseconds >= MINIMUM_ELAPSED_MILLISECONDS
}

enum class PayloadClassification(val contractValue: String) {
    AUTHORIZED("authorized"),
    UNAUTHORIZED("unauthorized"),
    MALFORMED("malformed"),
    CANCELED("canceled"),
}

enum class ZapReadOutcome(val contractValue: String) {
    ACCEPTED("accepted"),
    DUPLICATE_IGNORED("duplicateIgnored"),
    UNAUTHORIZED("unauthorized"),
    INVALID("invalid"),
    CANCELED("canceled"),
}

data class ZapReadDecision(
    val outcome: ZapReadOutcome,
    val mutationEligible: Boolean,
)

object ZapReadRules {
    fun decide(
        payloadClassification: PayloadClassification,
        acceptedReadAlreadyHandled: Boolean,
    ): ZapReadDecision = when {
        payloadClassification == PayloadClassification.AUTHORIZED && acceptedReadAlreadyHandled ->
            ZapReadDecision(ZapReadOutcome.DUPLICATE_IGNORED, mutationEligible = false)

        payloadClassification == PayloadClassification.AUTHORIZED ->
            ZapReadDecision(ZapReadOutcome.ACCEPTED, mutationEligible = true)

        payloadClassification == PayloadClassification.UNAUTHORIZED ->
            ZapReadDecision(ZapReadOutcome.UNAUTHORIZED, mutationEligible = false)

        payloadClassification == PayloadClassification.MALFORMED ->
            ZapReadDecision(ZapReadOutcome.INVALID, mutationEligible = false)

        else -> ZapReadDecision(ZapReadOutcome.CANCELED, mutationEligible = false)
    }
}

enum class SessionState(val contractValue: String) {
    IDLE("idle"),
    STARTING("starting"),
    ACTIVE("active"),
    STOPPING("stopping"),
    FAILED("failed"),
}

enum class RestrictionCapability(val contractValue: String) {
    AVAILABLE("available"),
    PERMISSION_REQUIRED("permissionRequired"),
    PERMISSION_DENIED("permissionDenied"),
    TEMPORARILY_UNAVAILABLE("temporarilyUnavailable"),
    UNSUPPORTED("unsupported"),
}

enum class SessionTransition(val contractValue: String) {
    START("start"),
    START_FAILED("startFailed"),
    STOP("stop"),
    STOP_REJECTED("stopRejected"),
    NO_CHANGE("noChange"),
}

enum class RestrictionAction(val contractValue: String) {
    APPLY("apply"),
    CLEAR("clear"),
    NONE("none"),
}

data class SessionDecision(
    val transition: SessionTransition,
    val state: SessionState,
    val restrictionAction: RestrictionAction,
)

object SessionReducer {
    fun reduce(
        priorState: SessionState,
        readOutcome: ZapReadOutcome,
        selectionValid: Boolean,
        capability: RestrictionCapability,
        elapsedMilliseconds: Long,
    ): SessionDecision {
        if (readOutcome != ZapReadOutcome.ACCEPTED) {
            return SessionDecision(
                transition = SessionTransition.NO_CHANGE,
                state = priorState,
                restrictionAction = RestrictionAction.NONE,
            )
        }

        return when (priorState) {
            SessionState.IDLE -> {
                if (!selectionValid || capability != RestrictionCapability.AVAILABLE) {
                    SessionDecision(
                        transition = SessionTransition.START_FAILED,
                        state = SessionState.FAILED,
                        restrictionAction = RestrictionAction.NONE,
                    )
                } else {
                    SessionDecision(
                        transition = SessionTransition.START,
                        state = SessionState.ACTIVE,
                        restrictionAction = RestrictionAction.APPLY,
                    )
                }
            }

            SessionState.ACTIVE -> {
                if (AccidentalStopGuard.isStopAllowed(elapsedMilliseconds)) {
                    SessionDecision(
                        transition = SessionTransition.STOP,
                        state = SessionState.IDLE,
                        restrictionAction = RestrictionAction.CLEAR,
                    )
                } else {
                    SessionDecision(
                        transition = SessionTransition.STOP_REJECTED,
                        state = SessionState.ACTIVE,
                        restrictionAction = RestrictionAction.NONE,
                    )
                }
            }

            SessionState.STARTING,
            SessionState.STOPPING,
            SessionState.FAILED,
            -> SessionDecision(
                transition = SessionTransition.NO_CHANGE,
                state = priorState,
                restrictionAction = RestrictionAction.NONE,
            )
        }
    }
}
