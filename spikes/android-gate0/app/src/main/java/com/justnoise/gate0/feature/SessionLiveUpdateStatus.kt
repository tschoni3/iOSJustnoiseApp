package com.justnoise.gate0.feature

/** Capability/presentation only. ALLOWED never means the OEM displayed a Now Bar. */
enum class SessionLiveUpdateStatus { UNSUPPORTED, NOTIFICATIONS_OFF, PROMOTION_OFF, NOT_PROMOTED, ALLOWED, PROMOTED }

object SessionLiveUpdateRules {
    const val MIN_REQUEST_SDK_FULL = 3_600_001

    fun supported(sdk: Int, sdkFull: Int, permissionDeclared: Boolean): Boolean =
        sdk >= 36 && sdkFull >= MIN_REQUEST_SDK_FULL && permissionDeclared

    fun status(
        supported: Boolean,
        notificationsEnabled: Boolean,
        promotionAllowed: Boolean,
        currentSessionPromoted: Boolean,
        rejected: Boolean = false,
    ): SessionLiveUpdateStatus = when {
        !supported -> SessionLiveUpdateStatus.UNSUPPORTED
        !notificationsEnabled -> SessionLiveUpdateStatus.NOTIFICATIONS_OFF
        !promotionAllowed -> SessionLiveUpdateStatus.PROMOTION_OFF
        currentSessionPromoted -> SessionLiveUpdateStatus.PROMOTED
        rejected -> SessionLiveUpdateStatus.NOT_PROMOTED
        else -> SessionLiveUpdateStatus.ALLOWED
    }

    fun shouldPost(projection: SessionNotificationProjection, allowed: Boolean,
        requested: Boolean, promotable: Boolean): Boolean =
        projection is SessionNotificationProjection.SessionTime && allowed && requested && promotable
}
