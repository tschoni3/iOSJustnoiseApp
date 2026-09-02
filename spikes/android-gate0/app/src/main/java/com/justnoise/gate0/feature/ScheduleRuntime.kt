package com.justnoise.gate0.feature

import android.content.Context
import android.os.SystemClock
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.ScheduleOutcomeCode
import com.justnoise.gate0.data.ScheduledOccurrence
import com.justnoise.gate0.platform.schedule.ScheduleAlarmScheduler
import com.justnoise.gate0.platform.schedule.ScheduleStartupGuard
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One native coordinator for callbacks, foreground recovery and future schedule UI changes. */
class ScheduleRuntime(context: Context) {
    private val context = context.applicationContext
    private val preferences = Gate0Preferences(this.context)
    private val alarms = ScheduleAlarmScheduler(this.context)

    fun onAlarm(deadlineElapsedMilliseconds: Long = deadline()) = synchronized(EXECUTION_LOCK) {
        runPass(deadlineElapsedMilliseconds)
    }

    fun onSystemChange(
        @Suppress("UNUSED_PARAMETER") action: String,
        deadlineElapsedMilliseconds: Long = deadline(),
    ) = synchronized(EXECUTION_LOCK) {
        // Startup guard distinguishes a real reboot from Android 15+'s BOOT_COMPLETED
        // after removal of Force Stop. A broadcast is never recovery authorization.
        runPass(deadlineElapsedMilliseconds)
    }

    /** Call only from a visible, explicit app entry, never Application/service/receiver startup. */
    fun onExplicitForeground() {
        if (!foregroundQueued.compareAndSet(false, true)) return
        foregroundWorker.execute {
            try {
                synchronized(EXECUTION_LOCK) {
                    val until = deadline()
                    ScheduleStartupGuard.initialize(context, preferences)
                    if (!Gate0Controller(context).hasAcceptedCurrentDisclosure()) return@synchronized
                    if (ScheduleStartupGuard.needsForegroundRecovery(preferences)) {
                        val recoveryCutoff = System.currentTimeMillis()
                        alarms.cancel()
                        // One explicit-entry cutoff owns both discard and acknowledgment.
                        // Work after it is future work, even if recovery processing takes time.
                        // A failure/timeout cannot open the gate.
                        val due = pending(recoveryCutoff) ?: return@synchronized
                        for (occurrence in due.filter { it.epochMilliseconds <= recoveryCutoff }) {
                            if (SystemClock.elapsedRealtime() >= until) return@synchronized
                            val result = preferences.dispatchSchedule(occurrence, recoveryCutoff) { _, _ ->
                                ScheduleOutcomeCode.RECOVERY_REQUIRED
                            }
                            if (result.persistenceFailed) return@synchronized
                        }
                        if (!ScheduleStartupGuard.acknowledgeForegroundRecovery(
                                preferences, recoveryCutoff,
                            )) return@synchronized
                    }
                    runPass(until)
                }
            } finally {
                foregroundQueued.set(false)
            }
        }
    }

    private fun runPass(until: Long) {
        ScheduleStartupGuard.initialize(context, preferences)
        val controller = Gate0Controller(context)
        if (!ScheduleStartupGuard.allowBackgroundScheduling(preferences) ||
            !controller.hasAcceptedCurrentDisclosure()
        ) {
            alarms.cancel()
            return
        }
        val due = pending() ?: return
        for (occurrence in due) {
            val now = System.currentTimeMillis()
            if (occurrence.epochMilliseconds > now || SystemClock.elapsedRealtime() >= until) break
            if (controller.startScheduled(occurrence).persistenceFailed) {
                alarms.cancel()
                return
            }
        }
        // Re-read definitions after dispatch so a concurrent edit, withdrawal or
        // zone change cannot leave an alarm representing an obsolete local plan.
        preferences.withScheduleLock {
            if (!ScheduleStartupGuard.allowBackgroundScheduling(preferences) ||
                !controller.hasAcceptedCurrentDisclosure()
            ) {
                alarms.cancel()
                return@withScheduleLock
            }
            val next = pending()?.firstOrNull() ?: run { alarms.cancel(); return@withScheduleLock }
            // A deadline-limited batch leaves unprocessed due work pending. Do not spin
            // an alarm at a past instant; Android may throttle this follow-up beyond grace.
            alarms.arm(maxOf(next.epochMilliseconds, System.currentTimeMillis() + 1_000L))
        }
    }

    private fun pending(now: Long = System.currentTimeMillis()): List<ScheduledOccurrence>? {
        val plan = preferences.reconcileSchedulePlan(now, ZoneId.systemDefault().id)
        if (plan.error) {
            alarms.cancel()
            return null
        }
        return plan.entries.mapNotNull { it.pending }
            .sortedWith(compareBy<ScheduledOccurrence> { it.epochMilliseconds }.thenBy { it.scheduleId })
    }

    companion object {
        private val EXECUTION_LOCK = Any()
        private val foregroundQueued = AtomicBoolean(false)
        private val foregroundWorker = Executors.newSingleThreadExecutor()
        private fun deadline(): Long = SystemClock.elapsedRealtime() + 8_000L
    }
}
