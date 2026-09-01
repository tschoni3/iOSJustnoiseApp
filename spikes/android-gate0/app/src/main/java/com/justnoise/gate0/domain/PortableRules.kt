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

enum class PortableWeekday(val contractValue: String) {
    MONDAY("monday"),
    TUESDAY("tuesday"),
    WEDNESDAY("wednesday"),
    THURSDAY("thursday"),
    FRIDAY("friday"),
    SATURDAY("saturday"),
    SUNDAY("sunday"),
}

enum class PortableScheduleKind(val contractValue: String) {
    ONE_TIME("oneTime"),
    REPEATING("repeating"),
}

enum class PortableScheduleEligibilityReason(val contractValue: String) {
    ELIGIBLE("eligible"),
    DISABLED("disabled"),
    MODE_UNAVAILABLE("modeUnavailable"),
    TARGET_SELECTION_EMPTY("targetSelectionEmpty"),
}

data class PortableScheduleEligibilityDecision(
    val kind: PortableScheduleKind,
    val isEligible: Boolean,
    val reason: PortableScheduleEligibilityReason,
)

object PortableScheduleRules {
    fun evaluate(
        isEnabled: Boolean,
        modeResolvable: Boolean,
        targetSelectionValid: Boolean,
        kind: PortableScheduleKind,
    ): PortableScheduleEligibilityDecision {
        val reason = when {
            !isEnabled -> PortableScheduleEligibilityReason.DISABLED
            !modeResolvable -> PortableScheduleEligibilityReason.MODE_UNAVAILABLE
            !targetSelectionValid -> PortableScheduleEligibilityReason.TARGET_SELECTION_EMPTY
            else -> PortableScheduleEligibilityReason.ELIGIBLE
        }
        return PortableScheduleEligibilityDecision(
            kind = kind,
            isEligible = reason == PortableScheduleEligibilityReason.ELIGIBLE,
            reason = reason,
        )
    }
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

enum class ZapEntryModeAction(val contractValue: String) {
    STOP_ACTIVE_SESSION("stopActiveSession"),
    START_RECENT_MODE("startRecentMode"),
    MODE_UNAVAILABLE("modeUnavailable"),
}

object ZapEntryModeRules {
    fun resolve(
        sessionActive: Boolean,
        recentModeExists: Boolean,
        recentModeSelectionValid: Boolean,
    ): ZapEntryModeAction = when {
        sessionActive -> ZapEntryModeAction.STOP_ACTIVE_SESSION
        recentModeExists && recentModeSelectionValid -> ZapEntryModeAction.START_RECENT_MODE
        else -> ZapEntryModeAction.MODE_UNAVAILABLE
    }
}

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
