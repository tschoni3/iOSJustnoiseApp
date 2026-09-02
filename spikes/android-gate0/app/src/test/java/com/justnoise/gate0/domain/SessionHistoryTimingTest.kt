package com.justnoise.gate0.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionHistoryTimingTest {
    @Test
    fun `continuous monotonic time wins over forward or backward wall clock changes`() {
        for (endEpoch in listOf(50L, 100L, 9_000_000L)) {
            assertEquals(
                3_000L,
                SessionHistoryTiming.durationMilliseconds(100L, endEpoch, 1_000L, 4_000L, true),
            )
        }
    }

    @Test
    fun `process gap uses nonnegative wall duration not unrelated elapsed clocks`() {
        assertEquals(
            60_000L,
            SessionHistoryTiming.durationMilliseconds(100_000L, 160_000L, 1_000L, 2_000L, false),
        )
        assertEquals(
            60_000L,
            SessionHistoryTiming.durationMilliseconds(100_000L, 160_000L, 9_000L, 1L, false),
        )
    }

    @Test
    fun `missing or reset elapsed clock falls back to valid wall duration`() {
        for ((startElapsed, endElapsed) in listOf(-1L to 5L, 5L to 4L)) {
            assertEquals(
                100L,
                SessionHistoryTiming.durationMilliseconds(100L, 200L, startElapsed, endElapsed, true),
            )
        }
    }

    @Test
    fun `invalid fallback timestamps produce zero not negative session time`() {
        for ((startEpoch, endEpoch) in listOf(-1L to 100L, 100L to 99L, 0L to -1L)) {
            assertEquals(
                0L,
                SessionHistoryTiming.durationMilliseconds(startEpoch, endEpoch, -1L, -1L, false),
            )
        }
    }

    @Test
    fun `zero duration and largest nonnegative differences are retained without overflow`() {
        assertEquals(0L, SessionHistoryTiming.durationMilliseconds(5L, 5L, 7L, 7L, true))
        assertEquals(Long.MAX_VALUE, SessionHistoryTiming.durationMilliseconds(0L, 0L, 0L, Long.MAX_VALUE, true))
        assertEquals(Long.MAX_VALUE, SessionHistoryTiming.durationMilliseconds(0L, Long.MAX_VALUE, -1L, -1L, false))
    }
}
