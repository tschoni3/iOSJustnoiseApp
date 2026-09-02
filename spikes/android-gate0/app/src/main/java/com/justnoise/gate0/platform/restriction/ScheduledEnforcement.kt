package com.justnoise.gate0.platform.restriction

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A reference to the currently system-bound service, never reconstructed from persisted liveness. */
interface ScheduledEnforcementEndpoint {
    fun prepareScheduleStart(): Long?
    fun isScheduleReadinessCurrent(generation: Long): Boolean
    fun onScheduledStateChanged()
}

object ScheduledEnforcement {
    @Volatile private var endpoint: ScheduledEnforcementEndpoint? = null

    fun connected(value: ScheduledEnforcementEndpoint) { endpoint = value }
    fun disconnected(value: ScheduledEnforcementEndpoint) {
        if (endpoint === value) endpoint = null
    }

    class PreparedStart internal constructor(
        private val endpoint: ScheduledEnforcementEndpoint,
        private val generation: Long,
        private val expiresAt: Long,
    ) {
        fun isStillReady(): Boolean = ScheduledEnforcement.endpoint === endpoint &&
            SystemClock.elapsedRealtime() <= expiresAt && endpoint.isScheduleReadinessCurrent(generation)
    }

    /** Must be called outside the preference transaction: the main-thread acknowledgment reads state. */
    fun prepareStart(): PreparedStart? {
        val current = endpoint ?: return null
        var prepared: PreparedStart? = null
        val done = CountDownLatch(1)
        val check = Runnable {
            try {
                if (endpoint === current) {
                    current.prepareScheduleStart()?.let {
                        prepared = PreparedStart(current, it, SystemClock.elapsedRealtime() + 1_000L)
                    }
                }
            } catch (_: Exception) {
                prepared = null
            } finally {
                done.countDown()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) check.run() else {
            val handler = Handler(Looper.getMainLooper())
            if (!handler.post(check)) return null
            try {
                if (!done.await(750L, TimeUnit.MILLISECONDS)) {
                    handler.removeCallbacks(check)
                    return null
                }
            } catch (_: InterruptedException) {
                handler.removeCallbacks(check)
                Thread.currentThread().interrupt()
                return null
            }
        }
        return prepared
    }

    fun stateChanged() { endpoint?.onScheduledStateChanged() }
}
