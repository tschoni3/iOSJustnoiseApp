package com.justnoise.gate0.platform.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleAlarmDispatchTest {
    @Test
    fun `only the private alarm action dispatches due schedule revalidation`() {
        assertEquals(
            ScheduleAlarmDispatch.ALARM,
            classifyScheduleAlarmAction("com.justnoise.gate0.action.SCHEDULE_ALARM"),
        )
    }

    @Test
    fun `only the five approved system changes request revalidation`() {
        for (action in listOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )) {
            assertEquals(action, ScheduleAlarmDispatch.SYSTEM_CHANGE, classifyScheduleAlarmAction(action))
        }
    }

    @Test
    fun `missing unrelated and lookalike actions never invoke runtime`() {
        for (action in listOf(
            null,
            "",
            "com.justnoise.gate0.action.STATE_CHANGED",
            "android.intent.action.PACKAGE_REPLACED",
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "com.justnoise.gate0.action.SCHEDULE_ALARM.extra",
            " com.justnoise.gate0.action.SCHEDULE_ALARM",
            "android.intent.action.TIMEZONE_CHANGED.extra",
        )) {
            assertEquals(action, ScheduleAlarmDispatch.IGNORE, classifyScheduleAlarmAction(action))
        }
    }
}
