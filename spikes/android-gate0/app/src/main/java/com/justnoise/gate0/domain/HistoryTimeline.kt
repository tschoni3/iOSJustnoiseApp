package com.justnoise.gate0.domain

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class HistoryWindow { NINETY_DAYS, YEAR }

/** A read-time device-zone interpretation, never persisted back into a session. */
data class HistoryDateRange(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
    val zoneId: ZoneId,
) {
    val dayCount: Int = ChronoUnit.DAYS.between(startInclusive, endExclusive).also {
        require(it in 1L..366L) { "History ranges must contain 1 to 366 days" }
    }.toInt()
    val startEpochMilliseconds: Long = startInclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val endEpochMilliseconds: Long = endExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()

    companion object {
        fun forWindow(window: HistoryWindow, today: LocalDate, zoneId: ZoneId): HistoryDateRange = when (window) {
            HistoryWindow.NINETY_DAYS -> HistoryDateRange(today.minusDays(60), today.plusDays(30), zoneId)
            HistoryWindow.YEAR -> HistoryDateRange(today.withDayOfYear(1), today.withDayOfYear(1).plusYears(1), zoneId)
        }
    }
}

data class HistoryDaySummary(
    val date: LocalDate,
    val durationMilliseconds: Long = 0L,
    val sessionCount: Int = 0,
    val interruptedCount: Int = 0,
    val timingUncertainCount: Int = 0,
)

/** Completed interval values only: no package names, tag values, or foreground observations. */
data class HistoryInterval(
    val startedAtEpochMilliseconds: Long,
    val endedAtEpochMilliseconds: Long,
    val durationMilliseconds: Long,
    val interrupted: Boolean,
) {
    init {
        require(startedAtEpochMilliseconds >= 0 && endedAtEpochMilliseconds >= 0 && durationMilliseconds >= 0)
    }

    val timingUncertain: Boolean
        get() {
            if (endedAtEpochMilliseconds <= startedAtEpochMilliseconds) return true
            val wallDuration = endedAtEpochMilliseconds - startedAtEpochMilliseconds
            // Both durations are non-negative, so subtracting smaller from larger cannot overflow.
            return maxOf(wallDuration, durationMilliseconds) - minOf(wallDuration, durationMilliseconds) >
                WALL_CLOCK_TOLERANCE_MILLISECONDS
        }

    companion object {
        /** Sampling tolerance, not evidence that blocking was continuous. */
        const val WALL_CLOCK_TOLERANCE_MILLISECONDS = 1_000L
    }
}

/**
 * Read-only attribution of stored elapsed duration. Work is bounded by the requested days per row,
 * even if a corrupted wall interval spans millions of years. Each bucket receives the difference
 * between cumulative floor ratios; all buckets together preserve every millisecond, independently
 * of range/pagination boundaries. Fractions are deterministically assigned to later buckets.
 */
class HistoryDayAccumulator(private val range: HistoryDateRange) {
    private val dates = List(range.dayCount) { range.startInclusive.plusDays(it.toLong()) }
    private val boundaries = List(range.dayCount + 1) {
        range.startInclusive.plusDays(it.toLong()).atStartOfDay(range.zoneId).toInstant().toEpochMilli()
    }
    private val durations = LongArray(range.dayCount)
    private val sessions = IntArray(range.dayCount)
    private val interruptions = IntArray(range.dayCount)
    private val uncertainties = IntArray(range.dayCount)

    fun add(interval: HistoryInterval) {
        val start = interval.startedAtEpochMilliseconds
        val end = interval.endedAtEpochMilliseconds
        if (end <= start) {
            // A backwards/zero wall interval cannot be split meaningfully. Keep its elapsed total
            // on the recorded end date and explain uncertainty; never repair the persisted row.
            val date = Instant.ofEpochMilli(end).atZone(range.zoneId).toLocalDate()
            val index = ChronoUnit.DAYS.between(range.startInclusive, date)
            if (index in 0L until range.dayCount.toLong()) addBucket(index.toInt(), interval.durationMilliseconds, interval)
            return
        }
        if (end <= range.startEpochMilliseconds || start >= range.endEpochMilliseconds) return
        val firstDate = Instant.ofEpochMilli(maxOf(start, range.startEpochMilliseconds)).atZone(range.zoneId).toLocalDate()
        val lastDate = Instant.ofEpochMilli(minOf(end, range.endEpochMilliseconds) - 1L).atZone(range.zoneId).toLocalDate()
        val firstIndex = ChronoUnit.DAYS.between(range.startInclusive, firstDate).toInt().coerceAtLeast(0)
        val lastIndex = ChronoUnit.DAYS.between(range.startInclusive, lastDate).toInt().coerceAtMost(range.dayCount - 1)
        val wallDuration = end - start
        val proportional = interval.durationMilliseconds != wallDuration
        val duration = if (proportional) BigInteger.valueOf(interval.durationMilliseconds) else BigInteger.ZERO
        for (index in firstIndex..lastIndex) {
            val overlapStart = maxOf(start, boundaries[index])
            val overlapEnd = minOf(end, boundaries[index + 1])
            if (overlapEnd <= overlapStart) continue
            fun allocatedThrough(epoch: Long): BigInteger =
                duration.multiply(BigInteger.valueOf(epoch - start)).divide(BigInteger.valueOf(wallDuration))
            val allocated = if (proportional) {
                allocatedThrough(overlapEnd).subtract(allocatedThrough(overlapStart)).longValueExact()
            } else {
                overlapEnd - overlapStart
            }
            addBucket(index, allocated, interval)
        }
    }

    fun summaries(): List<HistoryDaySummary> = dates.mapIndexed { index, date ->
        HistoryDaySummary(date, durations[index], sessions[index], interruptions[index], uncertainties[index])
    }

    private fun addBucket(index: Int, duration: Long, interval: HistoryInterval) {
        // An unrepresentable aggregate is an explicit read failure, never a wrapped/negative total.
        durations[index] = Math.addExact(durations[index], duration)
        sessions[index] = Math.incrementExact(sessions[index])
        if (interval.interrupted) interruptions[index] = Math.incrementExact(interruptions[index])
        if (interval.timingUncertain) uncertainties[index] = Math.incrementExact(uncertainties[index])
    }
}
