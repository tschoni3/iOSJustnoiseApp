package com.justnoise.gate0.data

import com.justnoise.gate0.domain.PortableScheduleTimingRules
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.domain.ScheduleTiming
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ScheduleStoreCodecTest {
    private val oneTime = StoredSchedule(
        id = "aa001122-3344-5566-7788-990011223344",
        revision = 1L,
        name = "Morning focus",
        enabled = true,
        timing = ScheduleTiming(oneTimeEpochMilliseconds = 1_788_418_800_000L),
        creationZoneId = "Europe/Berlin",
    )
    private val repeating = oneTime.copy(
        id = "bb001122-3344-5566-7788-990011223344",
        revision = 2L,
        enabled = false,
        timing = ScheduleTiming(
            repeatWeekdays = setOf(PortableWeekday.MONDAY, PortableWeekday.FRIDAY),
            hour = 9,
            minute = 30,
        ),
        creationZoneId = "UTC",
    )

    @Test
    fun `one-time and repeating enabled states round trip without adding occurrences`() {
        val schedules = listOf(oneTime, repeating)
        assertEquals(schedules, ScheduleStoreCodec.decode(ScheduleStoreCodec.encode(schedules)))
        val root = JSONObject(ScheduleStoreCodec.encode(schedules))
        assertEquals(setOf("schemaVersion", "schedules"), root.keys().asSequence().toSet())
        assertEquals(1, root.getInt("schemaVersion"))
        assertFalse(root.toString().contains("lastFired"))
    }

    @Test
    fun `empty set and default models are explicit and do not imply an error`() {
        assertEquals(emptyList<StoredSchedule>(), ScheduleStoreCodec.decode(ScheduleStoreCodec.encode(emptyList())))
        assertEquals(ScheduleSnapshot(emptyList(), false), ScheduleSnapshot())
        assertEquals("local-default", oneTime.modeReference)
        assertEquals(oneTime.timing, ScheduleDraft("Draft", true, oneTime.timing, "UTC").timing)
    }

    @Test
    fun `null and absent nullable timing fields encode to the same representation`() {
        for (schedule in listOf(oneTime, repeating)) {
            val explicit = ScheduleStoreCodec.encode(listOf(schedule))
            val omitted = JSONObject(explicit)
            val timing = omitted.getJSONArray("schedules").getJSONObject(0).getJSONObject("timing")
            for (field in listOf("oneTimeEpochMilliseconds", "hour", "minute")) {
                if (timing.isNull(field)) timing.remove(field)
            }
            val decoded = ScheduleStoreCodec.decode(omitted.toString())
            assertEquals(listOf(schedule), decoded)
            assertEquals(explicit, ScheduleStoreCodec.encode(decoded))
            val canonical = JSONObject(ScheduleStoreCodec.encode(decoded))
                .getJSONArray("schedules").getJSONObject(0).getJSONObject("timing")
            assertTrue(canonical.has("oneTimeEpochMilliseconds"))
            assertTrue(canonical.has("hour"))
            assertTrue(canonical.has("minute"))
        }
    }

    @Test
    fun `weekday set order is canonical and unknown mode references remain unchanged`() {
        val reversed = repeating.copy(
            timing = repeating.timing.copy(
                repeatWeekdays = linkedSetOf(PortableWeekday.FRIDAY, PortableWeekday.MONDAY),
            ),
        )
        assertEquals(ScheduleStoreCodec.encode(listOf(repeating)), ScheduleStoreCodec.encode(listOf(reversed)))
        val unknown = oneTime.copy(modeReference = " unavailable-mode ")
        assertEquals(unknown, ScheduleStoreCodec.decode(ScheduleStoreCodec.encode(listOf(unknown))).single())
    }

    @Test
    fun `text escapes unicode and exact integer boundary values round trip`() {
        val schedules = listOf(
            oneTime.copy(name = "Focus \"quiet\" \\ 🌱\nnow", revision = 9_007_199_254_740_993L),
            repeating.copy(name = "x".repeat(80), modeReference = "m".repeat(64), revision = Long.MAX_VALUE),
            oneTime.copy(id = UUID(0L, 3L).toString(), timing = ScheduleTiming(oneTimeEpochMilliseconds = 0L)),
            oneTime.copy(
                id = UUID(0L, 4L).toString(),
                timing = ScheduleTiming(oneTimeEpochMilliseconds = PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS),
            ),
        )
        assertEquals(schedules, ScheduleStoreCodec.decode(ScheduleStoreCodec.encode(schedules)))
    }

    @Test
    fun `malformed roots unsupported schema and trailing documents fail`() {
        for (encoded in listOf(
            "", "{", "null", "[]", "true", "{}", "{\"schemaVersion\":1}",
            "{\"schemaVersion\":2,\"schedules\":[]}",
            "{\"schemaVersion\":\"1\",\"schedules\":[]}",
            "{\"schemaVersion\":1.0,\"schedules\":[]}",
            "{\"schemaVersion\":1,\"schedules\":{}}",
            "{\"schemaVersion\":1,\"schedules\":[],\"extra\":false}",
            ScheduleStoreCodec.encode(emptyList()) + "{}",
        )) rejectDecode(encoded)
    }

    @Test
    fun `unknown missing and wrong-type record fields fail the whole set`() {
        for (mutate in listOf<(JSONObject) -> Unit>(
            { it.put("unknown", true) }, { it.remove("id") }, { it.remove("modeReference") },
            { it.put("id", 42) }, { it.put("name", true) }, { it.put("enabled", "true") },
            { it.put("enabled", 1) }, { it.put("creationZoneId", JSONObject.NULL) },
            { it.put("modeReference", 9) }, { it.put("timing", JSONArray()) },
        )) {
            val root = JSONObject(ScheduleStoreCodec.encode(listOf(oneTime, repeating)))
            mutate(root.getJSONArray("schedules").getJSONObject(1))
            rejectDecode(root.toString())
        }
    }

    @Test
    fun `duplicate schedule identifiers fail encoding and decoding`() {
        assertThrows(Exception::class.java) { ScheduleStoreCodec.encode(listOf(oneTime, oneTime)) }
        val root = JSONObject(ScheduleStoreCodec.encode(listOf(oneTime)))
        root.getJSONArray("schedules").put(root.getJSONArray("schedules").getJSONObject(0))
        rejectDecode(root.toString())
    }

    @Test
    fun `identifiers must use canonical lowercase UUID shape`() {
        for (id in listOf("", "not-a-uuid", oneTime.id.uppercase(), "0-0-0-0-0", oneTime.id.replace("-", ""))) {
            rejectBoth(oneTime.copy(id = id)) { it.put("id", id) }
        }
    }

    @Test
    fun `names must be trimmed nonempty and within eighty UTF-16 characters`() {
        for (name in listOf("", " ", "\n", " leading", "trailing ", "x".repeat(81))) {
            rejectBoth(oneTime.copy(name = name)) { it.put("name", name) }
        }
    }

    @Test
    fun `blank and oversized mode references are rejected without replacing unknown ones`() {
        for (reference in listOf("", " \n", "x".repeat(65))) {
            rejectBoth(oneTime.copy(modeReference = reference)) { it.put("modeReference", reference) }
        }
    }

    @Test
    fun `zone must be a named IANA identifier or UTC not a raw offset`() {
        for (zone in listOf("", "Not/AZone", "+01:00", "UTC+01:00", "Z", " UTC ", "Europe/BERLIN")) {
            rejectBoth(oneTime.copy(creationZoneId = zone)) { it.put("creationZoneId", zone) }
        }
    }

    @Test
    fun `revision and time numbers are never coerced from strings fractions or overflow`() {
        val encoded = ScheduleStoreCodec.encode(listOf(oneTime))
        for (raw in listOf("\"1\"", "1.0", "1.25", "1e3", "9223372036854775808", "-9223372036854775809", "1e309", "null")) {
            rejectDecode(encoded.replace("\"revision\":1", "\"revision\":$raw"))
        }
        for (revision in listOf(0L, -1L, Long.MIN_VALUE)) {
            rejectBoth(oneTime.copy(revision = revision)) { it.put("revision", revision) }
        }
        for ((field, raw) in listOf("hour" to "2147483648", "minute" to "1.5", "hour" to "\"9\"")) {
            val source = ScheduleStoreCodec.encode(listOf(repeating))
            val current = if (field == "hour") 9 else 30
            rejectDecode(source.replace("\"$field\":$current", "\"$field\":$raw"))
        }
        rejectDecode(encoded.replace("1788418800000", "1788418800000.0"))
    }

    @Test
    fun `invalid and mixed timing shapes are not normalized into valid schedules`() {
        for (timing in listOf(
            ScheduleTiming(),
            oneTime.timing.copy(hour = 9),
            oneTime.timing.copy(minute = 0),
            oneTime.timing.copy(repeatWeekdays = setOf(PortableWeekday.MONDAY)),
            oneTime.timing.copy(oneTimeEpochMilliseconds = -1L),
            oneTime.timing.copy(oneTimeEpochMilliseconds = PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS + 1L),
            repeating.timing.copy(hour = -1), repeating.timing.copy(hour = 24),
            repeating.timing.copy(minute = -1), repeating.timing.copy(minute = 60),
            repeating.timing.copy(hour = null), repeating.timing.copy(minute = null),
            repeating.timing.copy(oneTimeEpochMilliseconds = 1L),
        )) {
            assertThrows(Exception::class.java) { ScheduleStoreCodec.encode(listOf(oneTime.copy(timing = timing))) }
            val root = JSONObject(ScheduleStoreCodec.encode(listOf(oneTime)))
            root.getJSONArray("schedules").getJSONObject(0).put("timing", JSONObject().apply {
                put("oneTimeEpochMilliseconds", timing.oneTimeEpochMilliseconds ?: JSONObject.NULL)
                put("repeatWeekdays", JSONArray(timing.repeatWeekdays.map { it.contractValue }))
                put("hour", timing.hour ?: JSONObject.NULL)
                put("minute", timing.minute ?: JSONObject.NULL)
            })
            rejectDecode(root.toString())
        }
    }

    @Test
    fun `missing extra duplicate unknown and wrong-type weekdays fail`() {
        for (mutate in listOf<(JSONObject) -> Unit>(
            { it.remove("repeatWeekdays") }, { it.put("repeatWeekdays", JSONObject.NULL) },
            { it.put("repeatWeekdays", "monday") },
            { it.put("repeatWeekdays", JSONArray(listOf("monday", "monday"))) },
            { it.put("repeatWeekdays", JSONArray(listOf("Monday"))) },
            { it.put("repeatWeekdays", JSONArray(listOf("holiday"))) },
            { it.put("repeatWeekdays", JSONArray(listOf(1))) }, { it.put("extra", true) },
        )) {
            val root = JSONObject(ScheduleStoreCodec.encode(listOf(repeating)))
            mutate(root.getJSONArray("schedules").getJSONObject(0).getJSONObject("timing"))
            rejectDecode(root.toString())
        }
    }

    @Test
    fun `one hundred schedules is the inclusive record cap`() {
        val schedules = (1L..100L).map { oneTime.copy(id = UUID(0L, it).toString()) }
        assertEquals(schedules, ScheduleStoreCodec.decode(ScheduleStoreCodec.encode(schedules)))
        val excess = schedules + oneTime
        assertThrows(Exception::class.java) { ScheduleStoreCodec.encode(excess) }
        val root = JSONObject(ScheduleStoreCodec.encode(schedules))
        root.getJSONArray("schedules").put(JSONObject(ScheduleStoreCodec.encode(listOf(oneTime)))
            .getJSONArray("schedules").getJSONObject(0))
        rejectDecode(root.toString())
    }

    @Test
    fun `encoded UTF-16 character cap is checked before parsing including whitespace`() {
        val valid = ScheduleStoreCodec.encode(emptyList())
        val maximum = valid.padEnd(ScheduleStoreCodec.MAX_ENCODED_CHARACTERS, ' ')
        assertEquals(emptyList<StoredSchedule>(), ScheduleStoreCodec.decode(maximum))
        rejectDecode(maximum + " ")
        rejectDecode("x".repeat(ScheduleStoreCodec.MAX_ENCODED_CHARACTERS + 1))
    }

    private fun rejectBoth(invalid: StoredSchedule, mutate: (JSONObject) -> Unit) {
        assertThrows(Exception::class.java) { ScheduleStoreCodec.encode(listOf(invalid)) }
        val root = JSONObject(ScheduleStoreCodec.encode(listOf(oneTime)))
        mutate(root.getJSONArray("schedules").getJSONObject(0))
        rejectDecode(root.toString())
    }

    private fun rejectDecode(encoded: String) {
        assertThrows(Exception::class.java) { ScheduleStoreCodec.decode(encoded) }
    }
}
