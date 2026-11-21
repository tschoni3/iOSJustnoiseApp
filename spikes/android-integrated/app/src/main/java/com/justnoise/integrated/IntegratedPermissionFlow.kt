package com.justnoise.integrated

internal enum class IntegratedPermissionStep { BLOCKING, SCHEDULES, COMPLETE }

/** Routing never grants access. Only current system facts advance a requested step. */
internal object IntegratedPermissionFlow {
    fun next(disclosureAccepted: Boolean, blockingEnabled: Boolean,
        offerSchedules: Boolean, alarmAccess: Boolean): IntegratedPermissionStep = when {
        !disclosureAccepted || !blockingEnabled -> IntegratedPermissionStep.BLOCKING
        offerSchedules && !alarmAccess -> IntegratedPermissionStep.SCHEDULES
        else -> IntegratedPermissionStep.COMPLETE
    }
}
