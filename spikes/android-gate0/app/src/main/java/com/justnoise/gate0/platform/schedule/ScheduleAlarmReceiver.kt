package com.justnoise.gate0.platform.schedule

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import com.justnoise.gate0.feature.ScheduleRuntime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Must remain non-exported in each app manifest; extras never authorize a start. */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val dispatch = classifyScheduleAlarmAction(action)
        if (dispatch == ScheduleAlarmDispatch.IGNORE) return
        val deadline = SystemClock.elapsedRealtime() + PROCESSING_BUDGET_MILLISECONDS
        val appContext = context.applicationContext
        val pendingResult = goAsync() ?: return
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = appContext.getSystemService(PowerManager::class.java)
                ?: error("Power service unavailable")
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Justnoise:ScheduleDelivery")
            wakeLock.setReferenceCounted(false)
            // Acquire before onReceive returns; the alarm's own lock does not cover this worker.
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLISECONDS)
        } catch (_: RuntimeException) {
            finishDelivery(wakeLock, pendingResult)
            return
        }

        val acquiredLock = wakeLock ?: run {
            pendingResult.finish()
            return
        }
        try {
            executor.execute {
                try {
                    // Queue wait is part of the same eight-second budget, not a new lease.
                    if (!acquiredLock.isHeld || SystemClock.elapsedRealtime() >= deadline) return@execute
                    val runtime = ScheduleRuntime(appContext)
                    when (dispatch) {
                        ScheduleAlarmDispatch.ALARM -> runtime.onAlarm(deadlineElapsedMilliseconds = deadline)
                        ScheduleAlarmDispatch.SYSTEM_CHANGE -> runtime.onSystemChange(
                            requireNotNull(action), deadlineElapsedMilliseconds = deadline,
                        )
                        ScheduleAlarmDispatch.IGNORE -> Unit
                    }
                } finally {
                    finishDelivery(acquiredLock, pendingResult)
                }
            }
        } catch (_: RuntimeException) {
            // Rejected submissions never call the runtime or mutate persisted state.
            finishDelivery(acquiredLock, pendingResult)
        }
    }

    companion object {
        const val ACTION_ALARM = "com.justnoise.gate0.action.SCHEDULE_ALARM"
        private const val PROCESSING_BUDGET_MILLISECONDS = 8_000L
        private const val WAKE_LOCK_TIMEOUT_MILLISECONDS = 9_000L

        // One running and at most one waiting delivery. Further bursts are rejected;
        // the running runtime rechecks current persisted plans before its final rearm.
        private val executor = ThreadPoolExecutor(
            1, 1, 30L, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(1),
            { work -> Thread(work, "JustnoiseSchedule") },
            ThreadPoolExecutor.AbortPolicy(),
        ).apply { allowCoreThreadTimeOut(true) }

        private fun finishDelivery(
            wakeLock: PowerManager.WakeLock?,
            pendingResult: BroadcastReceiver.PendingResult,
        ) {
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (_: RuntimeException) {
                // The timed lease may already have expired; finishing the broadcast is mandatory.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal enum class ScheduleAlarmDispatch { ALARM, SYSTEM_CHANGE, IGNORE }

internal fun classifyScheduleAlarmAction(action: String?): ScheduleAlarmDispatch = when (action) {
    ScheduleAlarmReceiver.ACTION_ALARM -> ScheduleAlarmDispatch.ALARM
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
    -> ScheduleAlarmDispatch.SYSTEM_CHANGE
    else -> ScheduleAlarmDispatch.IGNORE
}
