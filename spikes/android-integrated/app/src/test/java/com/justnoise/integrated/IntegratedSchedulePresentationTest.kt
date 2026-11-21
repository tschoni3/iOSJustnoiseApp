package com.justnoise.integrated

import com.justnoise.gate0.data.StoredSchedule
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.domain.ScheduleTiming
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Copy/formatting only; no alarms, persistence or physical delivery claims. */
class IntegratedSchedulePresentationTest {
    private val epoch = Instant.parse("2026-10-10T08:00:00Z").toEpochMilli()
    private fun once(zone: String) = StoredSchedule("synthetic", 1L, "Focus", true,
        ScheduleTiming(oneTimeEpochMilliseconds = epoch), zone)

    @Test fun oneTimeLabelKeepsTheSavedZoneConversionWithoutShowingItsIdentifier() {
        val berlin = scheduleTimingLabel(once("Europe/Berlin"), Locale.GERMANY)
        val newYork = scheduleTimingLabel(once("America/New_York"), Locale.GERMANY)
        assertTrue(berlin.contains("10:00"))
        assertTrue(newYork.contains("04:00"))
        assertFalse(berlin.contains("Europe/Berlin"))
        assertFalse(newYork.contains("America/New_York"))
        assertEquals("Once · ${formatScheduleInstant(epoch, "Europe/Berlin", Locale.GERMANY)}", berlin)
    }

    @Test fun nextStartOmitsTheZoneSuffixButKeepsTheRealInstant() {
        val label = scheduleNextStartLabel(epoch, "Europe/Berlin", Locale.GERMANY)
        assertEquals("Next: ${formatScheduleInstant(epoch, "Europe/Berlin", Locale.GERMANY)}", label)
        assertFalse(label.contains("Europe/Berlin"))
    }

    @Test fun repeatingLabelKeepsWeekdaysAndTimeWithoutTechnicalHelp() {
        val schedule = once("Europe/Berlin").copy(timing = ScheduleTiming(
            repeatWeekdays = setOf(PortableWeekday.MONDAY), hour = 12, minute = 30))
        val label = scheduleTimingLabel(schedule, Locale.GERMANY)
        assertTrue(label.contains("12:30"))
        assertFalse(label.contains("device time zone"))
        assertFalse(label.contains("Europe/Berlin"))
    }
}
