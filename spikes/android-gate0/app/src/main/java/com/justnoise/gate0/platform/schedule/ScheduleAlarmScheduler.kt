package com.justnoise.gate0.platform.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.justnoise.gate0.domain.PortableScheduleTimingRules

/** One wakeup for persisted schedule revalidation; it never carries session authority. */
class ScheduleAlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun hasExactAlarmAccess(): Boolean = try {
        alarmManager?.canScheduleExactAlarms() == true
    } catch (_: SecurityException) {
        false
    }

    /** Missing access or a permission race is a failure, never an inexact fallback. */
    fun arm(epochMilliseconds: Long?): Boolean {
        if (epochMilliseconds == null) return cancel()
        if (epochMilliseconds !in 0L..PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS) return false
        val manager = alarmManager ?: return false
        return try {
            if (!manager.canScheduleExactAlarms()) return false
            val operation = pendingIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMilliseconds, operation)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(): Boolean {
        return try {
            val operation = pendingIntent(PendingIntent.FLAG_NO_CREATE) ?: return true
            val manager = alarmManager ?: return false
            manager.cancel(operation)
            operation.cancel()
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun permissionIntent(): Intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.fromParts("package", appContext.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun pendingIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        appContext,
        ALARM_REQUEST_CODE,
        Intent(appContext, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_ALARM)
            .setPackage(appContext.packageName),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val ALARM_REQUEST_CODE = 0
    }
}
