package com.justnoise.gate0.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTimelineTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `ninety day window is sixty past days today and twenty nine future days`() {
        val today = LocalDate.parse("2026-09-06")
        val range = HistoryDateRange.forWindow(HistoryWindow.NINETY_DAYS, today, utc)
        assertEquals(today.minusDays(60), range.startInclusive)
        assertEquals(today.plusDays(30), range.endExclusive)
        assertEquals(90, range.dayCount)
        assertEquals(90, HistoryDayAccumulator(range).summaries().size)
    }

    @Test
    fun `year includes leap day and ranges reject excessive or inverted work`() {
        val range = HistoryDateRange.forWindow(HistoryWindow.YEAR, LocalDate.parse("2028-07-04"), utc)
        assertEquals(366, range.dayCount)
        assertTrue(HistoryDayAccumulator(range).summaries().any { it.date == LocalDate.parse("2028-02-29") })
        val start = LocalDate.parse("2026-01-01")
        for (days in listOf(-1L, 0L, 367L)) {
            assertThrows(IllegalArgumentException::class.java) { HistoryDateRange(start, start.plusDays(days), utc) }
        }
    }

    @Test
    fun `midnight split retains every subminute millisecond and exact end boundary`() {
        val days = summarize("2026-09-05", 3, interval("2026-09-05T23:59:30Z", "2026-09-06T00:00:30Z"))
        assertEquals(listOf(30_000L, 30_000L, 0L), days.map { it.durationMilliseconds })
        assertEquals(listOf(1, 1, 0), days.map { it.sessionCount })
        val midnightEnd = summarize("2026-09-05", 2, interval("2026-09-05T23:59:30Z", "2026-09-06T00:00:00Z"))
        assertEquals(listOf(30_000L, 0L), midnightEnd.map { it.durationMilliseconds })
        assertEquals(listOf(1, 0), midnightEnd.map { it.sessionCount })
    }

    @Test
    fun `proportional elapsed attribution uses deterministic cumulative rounding across ranges`() {
        val row = interval("2026-09-05T00:00:00Z", "2026-09-08T00:00:00Z", duration = 10L)
        val complete = summarize("2026-09-05", 3, row)
        assertEquals(listOf(3L, 3L, 4L), complete.map { it.durationMilliseconds })
        assertEquals(10L, complete.sumOf { it.durationMilliseconds })
        for (day in complete) {
            assertEquals(day, summarize(day.date.toString(), 1, row).single())
            assertEquals(1, day.timingUncertainCount)
        }
    }

    @Test
    fun `spring DST day has twenty three hours and autumn day twenty five`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val spring = interval("2026-03-27T23:00:00Z", "2026-03-30T22:00:00Z")
        assertEquals(listOf(24L, 23L, 24L).map { it * 3_600_000 },
            summarize("2026-03-28", 3, spring, zone = berlin).map { it.durationMilliseconds })
        val autumn = interval("2026-10-23T22:00:00Z", "2026-10-26T23:00:00Z")
        assertEquals(listOf(24L, 25L, 24L).map { it * 3_600_000 },
            summarize("2026-10-24", 3, autumn, zone = berlin).map { it.durationMilliseconds })
    }

    @Test
    fun `changing device zone rebuckets same rows without changing totals or rows`() {
        val row = interval("2026-09-05T22:30:00Z", "2026-09-05T23:30:00Z")
        val original = row.copy()
        val utcDays = summarize("2026-09-05", 2, row)
        val berlinDays = summarize("2026-09-05", 2, row, zone = ZoneId.of("Europe/Berlin"))
        assertEquals(listOf(3_600_000L, 0L), utcDays.map { it.durationMilliseconds })
        assertEquals(listOf(0L, 3_600_000L), berlinDays.map { it.durationMilliseconds })
        assertEquals(row, original)
    }

    @Test
    fun `positive subminute sessions are summed before display truncation`() {
        val rows = listOf(
            interval("2026-09-05T09:00:00Z", "2026-09-05T09:00:30Z"),
            interval("2026-09-05T10:00:00Z", "2026-09-05T10:00:30Z"),
        )
        val range = range("2026-09-05", 1)
        val accumulator = HistoryDayAccumulator(range)
        rows.forEach(accumulator::add)
        assertEquals(60_000L, accumulator.summaries().single().durationMilliseconds)
        assertEquals(2, accumulator.summaries().single().sessionCount)
    }

    @Test
    fun `overlapping sessions remain separate elapsed records not a union of wall coverage`() {
        val accumulator = HistoryDayAccumulator(range("2026-09-05", 1))
        accumulator.add(interval("2026-09-05T09:00:00Z", "2026-09-05T10:00:00Z"))
        accumulator.add(interval("2026-09-05T09:30:00Z", "2026-09-05T10:30:00Z", interrupted = true))
        val day = accumulator.summaries().single()
        assertEquals(7_200_000L, day.durationMilliseconds)
        assertEquals(2, day.sessionCount)
        assertEquals(1, day.interruptedCount)
        assertEquals(0, day.timingUncertainCount)
    }

    @Test
    fun `clock sampling tolerance includes one second on either side and never clears interruption`() {
        val row = interval("2026-09-05T09:00:00Z", "2026-09-05T10:00:00Z")
        for (difference in listOf(-1_001L, -1_000L, 0L, 1_000L, 1_001L)) {
            val changed = row.copy(durationMilliseconds = row.durationMilliseconds + difference, interrupted = true)
            val day = summarize("2026-09-05", 1, changed).single()
            assertEquals(if (difference in -1_000L..1_000L) 0 else 1, day.timingUncertainCount)
            assertEquals(1, day.interruptedCount)
            assertEquals(changed.durationMilliseconds, day.durationMilliseconds)
        }
    }

    @Test
    fun `zero and backwards wall intervals attribute elapsed duration only to recorded end date`() {
        for (start in listOf("2026-09-06T12:00:00Z", "2026-09-09T12:00:00Z")) {
            val row = interval(start, "2026-09-06T12:00:00Z", duration = 42_001L)
            val days = summarize("2026-09-05", 3, row)
            assertEquals(listOf(0L, 42_001L, 0L), days.map { it.durationMilliseconds })
            assertEquals(listOf(0, 1, 0), days.map { it.timingUncertainCount })
        }
        val outside = interval("2026-09-06T12:00:00Z", "2026-09-04T12:00:00Z", duration = 123L)
        assertEquals(0L, summarize("2026-09-05", 3, outside).sumOf { it.durationMilliseconds })
    }

    @Test
    fun `zero elapsed rows are retained as sessions without fabricated time`() {
        val row = interval("2026-09-05T09:00:00Z", "2026-09-05T09:00:00Z", duration = 0L)
        val day = summarize("2026-09-05", 1, row).single()
        assertEquals(0L, day.durationMilliseconds)
        assertEquals(1, day.sessionCount)
        assertEquals(1, day.timingUncertainCount)
    }

    @Test(timeout = 2_000L)
    fun `huge intervals require only requested day count and proportional multiplication cannot overflow`() {
        val row = HistoryInterval(0L, Long.MAX_VALUE, Long.MAX_VALUE, false)
        val days = summarize("2026-09-05", 3, row)
        assertEquals(listOf(86_400_000L, 86_400_000L, 86_400_000L), days.map { it.durationMilliseconds })
        assertTrue(days.all { it.timingUncertainCount == 0 })
        val shortWall = interval("2026-09-05T00:00:00Z", "2026-09-08T00:00:00Z", duration = Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, summarize("2026-09-05", 3, shortWall).sumOf { it.durationMilliseconds })
    }

    @Test
    fun `unrepresentable aggregate fails explicitly instead of wrapping to negative`() {
        val accumulator = HistoryDayAccumulator(range("2026-09-05", 1))
        val row = interval("2026-09-05T09:00:00Z", "2026-09-05T10:00:00Z", duration = Long.MAX_VALUE)
        accumulator.add(row)
        assertThrows(ArithmeticException::class.java) { accumulator.add(row) }
    }

    @Test
    fun `out of range rows and empty skipped civil date do not invent time`() {
        val outside = interval("2026-09-02T00:00:00Z", "2026-09-03T00:00:00Z")
        assertTrue(summarize("2026-09-05", 2, outside).all { it.sessionCount == 0 })
        val samoa = ZoneId.of("Pacific/Apia")
        val row = interval("2011-12-29T10:00:00Z", "2011-12-31T10:00:00Z")
        val days = summarize("2011-12-29", 3, row, zone = samoa)
        assertEquals(0L, days[1].durationMilliseconds)
        assertEquals(0, days[1].sessionCount)
        assertEquals(row.durationMilliseconds, days.sumOf { it.durationMilliseconds })
        assertFalse(row.timingUncertain)
    }

    @Test(timeout = 2_000L)
    fun `many same day sessions do not perform year sized work per row`() {
        val range = HistoryDateRange.forWindow(HistoryWindow.YEAR, LocalDate.parse("2028-09-05"), utc)
        val accumulator = HistoryDayAccumulator(range)
        val row = interval("2028-09-05T09:00:00Z", "2028-09-05T09:00:30Z")
        repeat(100_000) { accumulator.add(row) }
        val days = accumulator.summaries()
        assertEquals(3_000_000_000L, days.sumOf { it.durationMilliseconds })
        assertEquals(100_000, days.single { it.sessionCount > 0 }.sessionCount)
    }

    private fun range(start: String, days: Int, zone: ZoneId = utc): HistoryDateRange {
        val date = LocalDate.parse(start)
        return HistoryDateRange(date, date.plusDays(days.toLong()), zone)
    }

    private fun summarize(start: String, days: Int, interval: HistoryInterval, zone: ZoneId = utc): List<HistoryDaySummary> =
        HistoryDayAccumulator(range(start, days, zone)).apply { add(interval) }.summaries()

    private fun interval(start: String, end: String, duration: Long? = null, interrupted: Boolean = false): HistoryInterval {
        val startEpoch = Instant.parse(start).toEpochMilli()
        val endEpoch = Instant.parse(end).toEpochMilli()
        return HistoryInterval(startEpoch, endEpoch, duration ?: (endEpoch - startEpoch), interrupted)
    }
}
