package com.justnoise.gate0.feature

import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.StoredGate0State

/** A read-only system surface, never a second session or enforcement state machine. */
sealed interface SessionNotificationProjection {
    data object Hidden : SessionNotificationProjection
    data class SessionTime(val sessionId: String, val startElapsedMilliseconds: Long,
        val modeName: String? = null) : SessionNotificationProjection
    data class Attention(val sessionId: String) : SessionNotificationProjection
}

object SessionNotificationRules {
    fun clockOffsetChanged(previous: Long?, current: Long): Boolean = previous == null ||
        try {
            val difference = Math.subtractExact(current, previous)
            difference < -1_000L || difference > 1_000L
        } catch (_: ArithmeticException) {
            true
        }

    fun project(
        stored: StoredGate0State,
        accessibilityEnabled: Boolean,
        nowElapsedMilliseconds: Long,
    ): SessionNotificationProjection {
        val sessionId = stored.activeSessionId?.takeIf(String::isNotBlank)
        if (!stored.desiredActive || sessionId == null ||
            stored.acceptedDisclosureVersion != Gate0Preferences.CURRENT_DISCLOSURE_VERSION ||
            stored.acceptedDisclosureEpochMilliseconds <= 0L ||
            stored.dismissedNotificationSessionId == sessionId
        ) return SessionNotificationProjection.Hidden

        val truth = Gate0StateReconciler.reconcile(stored, accessibilityEnabled, nowElapsedMilliseconds)
        val continuousClock = stored.activeClockMatchesProcess &&
            stored.activeSinceElapsedMilliseconds > 0L &&
            nowElapsedMilliseconds >= stored.activeSinceElapsedMilliseconds
        return if (truth == Gate0Truth.ACTIVE && continuousClock) {
            SessionNotificationProjection.SessionTime(sessionId, stored.activeSinceElapsedMilliseconds,
                stored.activeModeName?.trim()?.take(60)?.takeIf(String::isNotEmpty))
        } else {
            SessionNotificationProjection.Attention(sessionId)
        }
    }

    /** Convert only when posting; the OS chronometer keeps the stable elapsed base afterward. */
    fun chronometerWhen(
        startElapsedMilliseconds: Long,
        nowElapsedMilliseconds: Long,
        nowEpochMilliseconds: Long,
    ): Long? {
        if (startElapsedMilliseconds <= 0L || nowElapsedMilliseconds < startElapsedMilliseconds) return null
        val elapsed = nowElapsedMilliseconds - startElapsedMilliseconds
        if (nowEpochMilliseconds < elapsed) return null
        return nowEpochMilliseconds - elapsed
    }
}
