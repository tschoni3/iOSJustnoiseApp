package com.justnoise.gate0.domain

import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableScheduleTimingTest {
    private val fixture: JSONObject by lazy {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("portable-fixtures.v1.json"))
        stream.bufferedReader().use { JSONObject(it.readText()).getJSONObject("scheduleTemporal") }
    }

    @Test
    fun `consumes every one-time local resolution fixture`() {
        val cases = fixture.getJSONArray("oneTimeResolutionCases")
        assertEquals(9, cases.length())
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            assertEquals(
                case.getString("id"),
                case.optionalInstant("expectedInstant"),
                PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds(
                    localDate = case.getString("localDate"),
                    hour = case.getInt("hour"),
                    minute = case.getInt("minute"),
                    zoneId = case.getString("zoneId"),
                    nowEpochMilliseconds = instant(case.getString("now")),
                ),
            )
        }
    }

    @Test
    fun `consumes every occurrence identity and high-water fixture`() {
        val cases = fixture.getJSONArray("nextOccurrenceCases")
        assertEquals(16, cases.length())
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val value = case.getJSONObject("timing")
            val weekdays = value.getJSONArray("repeatWeekdays")
            val timing = ScheduleTiming(
                oneTimeEpochMilliseconds = value.optionalInstant("oneTimeInstant"),
                repeatWeekdays = (0 until weekdays.length()).mapTo(mutableSetOf()) { dayIndex ->
                    PortableWeekday.entries.single { it.contractValue == weekdays.getString(dayIndex) }
                },
                hour = value.optionalInt("hour"),
                minute = value.optionalInt("minute"),
            )
            val result = PortableScheduleTimingRules.nextOccurrence(
                timing,
                afterExclusiveEpochMilliseconds = instant(case.getString("after")),
                zoneId = case.getString("zoneId"),
                lastResolvedLocalDate = case.optionalString("lastResolvedLocalDate"),
            )
            assertEquals(case.getString("id"), case.optionalInstant("expectedInstant"), result?.epochMilliseconds)
            assertEquals(case.getString("id"), case.optionalString("expectedKey"), result?.key)
            assertEquals(
                case.getString("id"),
                case.optionalString("expectedKey")?.takeIf { it.startsWith("repeating:") }
                    ?.substringAfter("repeating:")?.substringBefore(":"),
                result?.localDate,
            )
        }
    }

    @Test
    fun `consumes every delivery fixture without treating candidate as a session start`() {
        assertEquals(fixture.getLong("graceMilliseconds"), ScheduleDeliveryRules.GRACE_MILLISECONDS)
        assertEquals(fixture.getLong("maximumEpochMilliseconds"), PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS)
        val cases = fixture.getJSONArray("deliveryCases")
        assertEquals(13, cases.length())
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            assertEquals(
                case.getString("id"),
                case.getString("expected"),
                ScheduleDeliveryRules.evaluate(
                    currentRevisionEnabled = case.getBoolean("currentRevisionEnabled"),
                    alreadyHandled = case.getBoolean("alreadyHandled"),
                    sessionRequested = case.getBoolean("sessionRequested"),
                    scheduledAtEpochMilliseconds = case.getLong("scheduledAtEpochMilliseconds"),
                    nowEpochMilliseconds = case.getLong("nowEpochMilliseconds"),
                ).contractValue,
            )
        }
    }

    @Test
    fun `one-time and repeating shapes are distinct and reject invalid fields`() {
        val repeating = ScheduleTiming(repeatWeekdays = setOf(PortableWeekday.MONDAY), hour = 9, minute = 0)
        val oneTime = ScheduleTiming(oneTimeEpochMilliseconds = 1_000L)
        for (valid in listOf(
            oneTime,
            oneTime.copy(oneTimeEpochMilliseconds = 0L),
            oneTime.copy(oneTimeEpochMilliseconds = PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS),
            repeating,
            repeating.copy(repeatWeekdays = PortableWeekday.entries.toSet(), hour = 23, minute = 59),
        )) assertTrue(valid.toString(), PortableScheduleTimingRules.isValid(valid))

        for (invalid in listOf(
            ScheduleTiming(),
            oneTime.copy(hour = 9),
            oneTime.copy(minute = 0),
            oneTime.copy(repeatWeekdays = setOf(PortableWeekday.MONDAY)),
            repeating.copy(oneTimeEpochMilliseconds = 1_000L),
            repeating.copy(repeatWeekdays = emptySet()),
            repeating.copy(hour = null),
            repeating.copy(minute = null),
            repeating.copy(hour = -1),
            repeating.copy(hour = 24),
            repeating.copy(minute = -1),
            repeating.copy(minute = 60),
        )) {
            assertFalse(invalid.toString(), PortableScheduleTimingRules.isValid(invalid))
            assertNull(PortableScheduleTimingRules.nextOccurrence(invalid, 0L, "UTC"))
        }
    }

    @Test
    fun `unsupported epochs fail before date conversion or subtraction`() {
        val maximum = PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS
        val timing = ScheduleTiming(oneTimeEpochMilliseconds = maximum)
        for (invalid in listOf(-1L, maximum + 1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            assertFalse(PortableScheduleTimingRules.isValid(timing.copy(oneTimeEpochMilliseconds = invalid)))
            assertNull(PortableScheduleTimingRules.nextOccurrence(timing, invalid, "UTC"))
            assertNull(PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds("2026-09-03", 9, 0, "UTC", invalid))
            assertEquals(ScheduleDeliveryDecision.INVALID, ScheduleDeliveryRules.evaluate(true, false, false, invalid, 0L))
            assertEquals(ScheduleDeliveryDecision.INVALID, ScheduleDeliveryRules.evaluate(true, false, false, 0L, invalid))
        }
        assertEquals(maximum, PortableScheduleTimingRules.nextOccurrence(timing, maximum - 1L, "UTC")?.epochMilliseconds)
        assertNull(PortableScheduleTimingRules.nextOccurrence(timing, maximum, "UTC"))
        assertEquals(ScheduleDeliveryDecision.START_CANDIDATE, ScheduleDeliveryRules.evaluate(true, false, false, maximum, maximum))
    }

    @Test
    fun `dates zones and high-water marks must be explicit and strict`() {
        val now = instant("2026-01-01T00:00:00Z")
        val repeating = ScheduleTiming(repeatWeekdays = PortableWeekday.entries.toSet(), hour = 9, minute = 0)
        for (date in listOf("2026-2-03", "2026-02-30", "2026-09-03 ", "0000-01-01", "+10000-01-01")) {
            assertNull(date, PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds(date, 9, 0, "UTC", now))
            assertNull(date, PortableScheduleTimingRules.nextOccurrence(repeating, now, "UTC", date))
        }
        for (zone in listOf("", "Not/AZone", "+02:00", "UTC+02:00", " Europe/Berlin")) {
            assertNull(zone, PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds("2026-09-03", 9, 0, zone, now))
            assertNull(zone, PortableScheduleTimingRules.nextOccurrence(repeating, now, zone))
        }
        assertNull(PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds("2026-09-03", 9, 60, "UTC", now))
        assertNull(PortableScheduleTimingRules.nextOccurrence(repeating, now, "UTC", "9999-12-31"))
    }

    @Test
    fun `one-time overlap is not silently moved to its second occurrence after first passes`() {
        assertNull(
            PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds(
                "2026-10-25", 2, 30, "Europe/Berlin", instant("2026-10-25T00:45:00Z"),
            ),
        )
    }

    private fun instant(value: String): Long = Instant.parse(value).toEpochMilli()
    private fun JSONObject.optionalInstant(key: String): Long? = optionalString(key)?.let(::instant)
    private fun JSONObject.optionalString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.optionalInt(key: String): Int? = if (isNull(key)) null else getInt(key)
}
