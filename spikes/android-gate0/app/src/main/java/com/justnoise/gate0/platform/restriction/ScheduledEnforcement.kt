package com.justnoise.gate0.platform.restriction

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A reference to the currently system-bound service, never reconstructed from persisted liveness. */
interface ScheduledEnforcementEndpoint {
    fun prepareScheduleStart(): ScheduledEnforcement.Preparation
    fun isScheduleReadinessCurrent(generation: Long): Boolean
    fun onScheduledStateChanged()
}

object ScheduledEnforcement {
    @Volatile private var endpoint: ScheduledEnforcementEndpoint? = null

    // One process-local health code, not an event log. It has no identifiers,
    // foreground metadata, timestamps or user content and never authorizes work.
    enum class ReadinessStatus {
        NOT_ATTEMPTED, PREPARING, PREPARED, READY, NO_BOUND_SERVICE,
        SERVICE_REPLACED, SERVICE_DISABLED, CONSENT_REQUIRED, RECOVERY_REQUIRED,
        FOREGROUND_UNKNOWN, HEARTBEAT_WRITE_FAILED, SERVICE_REJECTED,
        PREPARATION_ERROR, MAIN_THREAD_UNAVAILABLE, MAIN_THREAD_TIMEOUT,
        WAIT_INTERRUPTED, LEASE_EXPIRED, FOREGROUND_CHANGED,
    }
    @Volatile internal var lastReadinessStatus = ReadinessStatus.NOT_ATTEMPTED
        private set

    internal fun recordReadiness(status: ReadinessStatus) { lastReadinessStatus = status }

    sealed interface Preparation {
        data class Ready(val generation: Long) : Preparation
        data class Rejected(val reason: ReadinessStatus) : Preparation
    }

    fun connected(value: ScheduledEnforcementEndpoint) { endpoint = value }
    fun disconnected(value: ScheduledEnforcementEndpoint) {
        if (endpoint === value) endpoint = null
    }

    class PreparedStart internal constructor(
        private val endpoint: ScheduledEnforcementEndpoint,
        private val generation: Long,
        private val expiresAt: Long,
    ) {
        fun isStillReady(): Boolean {
            val status = when {
                ScheduledEnforcement.endpoint !== endpoint -> ReadinessStatus.SERVICE_REPLACED
                SystemClock.elapsedRealtime() > expiresAt -> ReadinessStatus.LEASE_EXPIRED
                !endpoint.isScheduleReadinessCurrent(generation) -> ReadinessStatus.FOREGROUND_CHANGED
                else -> ReadinessStatus.READY
            }
            recordReadiness(status)
            return status == ReadinessStatus.READY
        }
    }

    /** Must be called outside the preference transaction: the main-thread acknowledgment reads state. */
    fun prepareStart(): PreparedStart? {
        recordReadiness(ReadinessStatus.PREPARING)
        val current = endpoint ?: run {
            recordReadiness(ReadinessStatus.NO_BOUND_SERVICE)
            return null
        }
        var prepared: PreparedStart? = null
        var status = ReadinessStatus.SERVICE_REJECTED
        val done = CountDownLatch(1)
        val check = Runnable {
            try {
                if (endpoint === current) {
                    when (val result = current.prepareScheduleStart()) {
                        is Preparation.Ready -> {
                            prepared = PreparedStart(current, result.generation, SystemClock.elapsedRealtime() + 1_000L)
                            status = ReadinessStatus.PREPARED
                        }
                        is Preparation.Rejected -> status = result.reason
                    }
                } else {
                    status = ReadinessStatus.SERVICE_REPLACED
                }
            } catch (_: Exception) {
                status = ReadinessStatus.PREPARATION_ERROR
                prepared = null
            } finally {
                done.countDown()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) check.run() else {
            val handler = Handler(Looper.getMainLooper())
            if (!handler.post(check)) {
                recordReadiness(ReadinessStatus.MAIN_THREAD_UNAVAILABLE)
                return null
            }
            try {
                if (!done.await(750L, TimeUnit.MILLISECONDS)) {
                    handler.removeCallbacks(check)
                    recordReadiness(ReadinessStatus.MAIN_THREAD_TIMEOUT)
                    return null
                }
            } catch (_: InterruptedException) {
                handler.removeCallbacks(check)
                recordReadiness(ReadinessStatus.WAIT_INTERRUPTED)
                Thread.currentThread().interrupt()
                return null
            }
        }
        // Only the awaiting caller publishes the result. A late main-thread
        // callback cannot overwrite TIMEOUT/INTERRUPTED or a later attempt.
        recordReadiness(status)
        return prepared
    }

    fun stateChanged() { endpoint?.onScheduledStateChanged() }
}
