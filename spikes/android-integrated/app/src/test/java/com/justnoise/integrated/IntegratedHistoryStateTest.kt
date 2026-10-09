package com.justnoise.integrated

import com.justnoise.gate0.data.SessionHistoryTimelineSnapshot
import com.justnoise.gate0.domain.HistoryDaySummary
import com.justnoise.gate0.domain.HistoryWindow
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedHistoryStateTest {
    private val today = LocalDate.of(2028, 2, 29)
    private val base = IntegratedHistoryState(today = today, zoneId = ZoneId.of("Europe/Berlin"))

    @Test fun ninetyDayRangeHasSixtyPastAndTwentyNineFutureDates() {
        assertEquals(today.minusDays(60), base.range.startInclusive)
        assertEquals(today.plusDays(30), base.range.endExclusive)
        assertEquals(90, base.range.dayCount)
    }

    @Test fun yearKeepsLeapDayAndTheCurrentDeviceZone() {
        val year = base.copy(window = HistoryWindow.YEAR)
        assertEquals(LocalDate.of(2028, 1, 1), year.range.startInclusive)
        assertEquals(LocalDate.of(2029, 1, 1), year.range.endExclusive)
        assertEquals(366, year.range.dayCount)
        assertEquals(ZoneId.of("Europe/Berlin"), year.range.zoneId)
    }

    @Test fun selectionUsesActualSummaryIncludingSubminuteAndIndependentWarnings() {
        val day = HistoryDaySummary(today, durationMilliseconds = 42, sessionCount = 3,
            interruptedCount = 1, timingUncertainCount = 2)
        val state = loaded().let { loaded ->
            loaded.copy(snapshot = loaded.snapshot!!.copy(days = loaded.snapshot.days.map { if (it.date == today) day else it }))
        }.selecting(today)
        assertEquals(day, state.selectedDay)
        assertEquals(42L, state.selectedDay!!.durationMilliseconds)
        assertNull(state.selecting(null).selectedDay)
    }

    @Test fun loadingFailedOrAbsentDaysCannotOpenAStaleDetail() {
        val loaded = loaded()
        assertNull(loaded.copy(loading = true).selecting(today).selectedDate)
        assertNull(loaded.copy(snapshot = loaded.snapshot!!.copy(error = true)).selecting(today).selectedDate)
        assertNull(base.selecting(today).selectedDate)
        assertNull(loaded.selecting(base.range.endExclusive).selectedDate)
    }

    @Test fun futureDayRemainsSelectableWithoutInventingCompletedSessions() {
        val future = loaded().selecting(today.plusDays(29)).selectedDay!!
        assertEquals(0, future.sessionCount)
        assertEquals(0L, future.durationMilliseconds)
    }

    @Test fun allTimeTotalsAreNotDerivedFromWindowDaysOrPaginatedEntries() {
        val state = loaded()
        assertEquals(126, state.snapshot!!.totalCount)
        assertEquals(9_999_999L, state.snapshot.totalDurationMilliseconds)
        assertTrue(state.snapshot.days.all { it.durationMilliseconds == 0L })
        assertTrue(IntegratedHistoryGridRules.validSnapshot(state.snapshot, state.range))
    }

    @Test fun gridKeepsFortyEightDpTargetsAndBoundsNinetyDaysToFourColumns() {
        listOf(120f, 240f, 328f, 352f, 768f).forEach { width ->
            HistoryWindow.entries.forEach { window ->
                val columns = IntegratedHistoryGridRules.columns(window, width)
                assertTrue((width - 6 * (columns - 1)) / columns >= 48)
                if (window == HistoryWindow.NINETY_DAYS) assertTrue(columns <= 4)
            }
        }
        assertEquals(1, IntegratedHistoryGridRules.columns(HistoryWindow.YEAR, 0f))
        assertEquals(4, IntegratedHistoryGridRules.columns(HistoryWindow.NINETY_DAYS, 352f))
        assertEquals(6, IntegratedHistoryGridRules.columns(HistoryWindow.YEAR, 352f))
    }

    @Test fun todayContextUsesRowStartAndYearProgressIncludesLeapDays() {
        assertEquals(44, IntegratedHistoryGridRules.todayContextIndex(base.range, today, 4))
        assertEquals(306 to 16, IntegratedHistoryGridRules.yearProgress(today))
        assertEquals(0 to 100, IntegratedHistoryGridRules.yearProgress(LocalDate.of(2028, 12, 31)))
    }

    @Test fun incompleteDuplicateOrMismatchedReadIsAnErrorNotAnEmptyCalendar() {
        val state = loaded()
        val snapshot = state.snapshot!!
        assertFalse(IntegratedHistoryGridRules.validSnapshot(snapshot.copy(days = snapshot.days.drop(1)), state.range))
        assertFalse(IntegratedHistoryGridRules.validSnapshot(snapshot.copy(days = List(90) { snapshot.days.first() }), state.range))
        assertFalse(IntegratedHistoryGridRules.validSnapshot(snapshot.copy(range = state.range.copy(zoneId = ZoneId.of("UTC"))), state.range))
        assertFalse(IntegratedHistoryGridRules.validSnapshot(snapshot.copy(interruptedCount = 127), state.range))
    }

    private fun loaded(): IntegratedHistoryState = base.copy(snapshot = SessionHistoryTimelineSnapshot(
        range = base.range,
        days = List(base.range.dayCount) { HistoryDaySummary(base.range.startInclusive.plusDays(it.toLong())) },
        totalCount = 126, totalDurationMilliseconds = 9_999_999L,
    ))
}
