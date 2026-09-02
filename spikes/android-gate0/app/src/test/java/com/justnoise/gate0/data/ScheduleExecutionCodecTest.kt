package com.justnoise.gate0.data

import com.justnoise.gate0.domain.PortableScheduleTimingRules
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ScheduleExecutionCodecTest {
    private val scheduleId = "aa001122-3344-5566-7788-990011223344"
    private val sessionId = "cc001122-3344-5566-7788-990011223344"
    private val oneTime = ScheduledOccurrence(
        scheduleId, 1L, "oneTime:1788418800000", 1_788_418_800_000L, null, "Europe/Berlin",
    )
    private val repeating = oneTime.copy(
        revision = 2L, key = "repeating:2026-09-03:09:00", localDate = "2026-09-03",
    )
    private val outcome = ScheduleOutcome(
        ScheduleOutcomeCode.STARTED, oneTime, oneTime.epochMilliseconds + 500L, sessionId,
    )
    private val entry = ScheduleExecution(
        scheduleId, repeating, "2026-09-02", oneTime.epochMilliseconds - 86_400_000L, outcome,
    )

    @Test
    fun `empty execution set and default snapshot round trip without error`() {
        assertEquals(emptyList<ScheduleExecution>(), ScheduleExecutionCodec.decode(ScheduleExecutionCodec.encode(emptyList())))
        assertEquals(ScheduleExecutionSnapshot(emptyList(), false), ScheduleExecutionSnapshot())
        assertRoundTrip(ScheduleExecution(scheduleId))
    }

    @Test
    fun `pending and latest outcome preserve independent high water markers across edits`() {
        assertRoundTrip(entry)
        val root = JSONObject(ScheduleExecutionCodec.encode(listOf(entry)))
        assertEquals(setOf("schemaVersion", "entries"), root.keys().asSequence().toSet())
        val stored = root.getJSONArray("entries").getJSONObject(0)
        assertEquals(setOf("scheduleId", "pending", "lastResolvedLocalDate", "lastResolvedOneTimeEpochMilliseconds", "lastOutcome"),
            stored.keys().asSequence().toSet())
        assertTrue(stored.get("lastOutcome") is JSONObject)
        assertFalse(stored.has("history"))
        assertEquals(2L, stored.getJSONObject("pending").getLong("revision"))
        assertEquals(1L, stored.getJSONObject("lastOutcome").getJSONObject("occurrence").getLong("revision"))
    }

    @Test
    fun `all outcome codes round trip and only started links a session`() {
        for (code in ScheduleOutcomeCode.entries) {
            val updated = outcome.copy(code = code, sessionId = sessionId.takeIf { code == ScheduleOutcomeCode.STARTED })
            assertRoundTrip(entry.copy(pending = null, lastOutcome = updated))
        }
    }

    @Test
    fun `omitted and explicit null optional fields have a canonical encoding`() {
        val empty = ScheduleExecution(scheduleId)
        val canonical = ScheduleExecutionCodec.encode(listOf(empty))
        val root = JSONObject(canonical)
        val record = root.getJSONArray("entries").getJSONObject(0)
        for (key in listOf("pending", "lastResolvedLocalDate", "lastResolvedOneTimeEpochMilliseconds", "lastOutcome")) {
            assertTrue(record.isNull(key))
            record.remove(key)
        }
        assertEquals(canonical, ScheduleExecutionCodec.encode(ScheduleExecutionCodec.decode(root.toString())))

        val withOptionals = entry.copy(pending = oneTime, lastOutcome = outcome.copy(code = ScheduleOutcomeCode.MISSED, sessionId = null))
        val nested = JSONObject(ScheduleExecutionCodec.encode(listOf(withOptionals)))
        val nestedEntry = nested.getJSONArray("entries").getJSONObject(0)
        nestedEntry.getJSONObject("pending").remove("localDate")
        nestedEntry.getJSONObject("lastOutcome").remove("sessionId")
        nestedEntry.getJSONObject("lastOutcome").getJSONObject("occurrence").remove("localDate")
        assertEquals(listOf(withOptionals), ScheduleExecutionCodec.decode(nested.toString()))
    }

    @Test
    fun `exact long revisions epoch endpoints and civil date endpoints survive`() {
        for (epoch in listOf(0L, PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS)) {
            val occurrence = oneTime.copy(key = "oneTime:$epoch", epochMilliseconds = epoch, revision = Long.MAX_VALUE)
            assertRoundTrip(entry.copy(
                pending = occurrence, lastResolvedOneTimeEpochMilliseconds = epoch,
                lastOutcome = outcome.copy(occurrence = occurrence, handledAtEpochMilliseconds = epoch),
            ))
        }
        assertRoundTrip(entry.copy(pending = oneTime.copy(revision = 9_007_199_254_740_993L)))
        for (date in listOf("0001-01-01", "2028-02-29", "9999-12-31")) {
            assertRoundTrip(entry.copy(lastResolvedLocalDate = date))
        }
    }

    @Test
    fun `malformed roots schemas and trailing documents fail`() {
        for (encoded in listOf(
            "", "{", "null", "[]", "false", "{}", "{\"schemaVersion\":1}",
            "{\"schemaVersion\":2,\"entries\":[]}", "{\"schemaVersion\":\"1\",\"entries\":[]}",
            "{\"schemaVersion\":1.0,\"entries\":[]}", "{\"schemaVersion\":true,\"entries\":[]}",
            "{\"schemaVersion\":1,\"entries\":null}", "{\"schemaVersion\":1,\"entries\":[false]}",
            "{\"schemaVersion\":1,\"entries\":[],\"extra\":1}",
            ScheduleExecutionCodec.encode(emptyList()) + "{}",
        )) rejectDecode(encoded)
    }

    @Test
    fun `unknown missing and wrong type fields never become an empty execution`() {
        for (mutate in listOf<(JSONObject) -> Unit>(
            { it.remove("scheduleId") }, { it.put("extra", true) }, { it.put("scheduleId", 1) },
            { it.put("pending", false) }, { it.put("pending", JSONArray()) },
            { it.put("lastResolvedLocalDate", true) }, { it.put("lastOutcome", "STARTED") },
            { it.getJSONObject("pending").remove("zoneId") },
            { it.getJSONObject("pending").put("extra", true) },
            { it.getJSONObject("pending").put("key", 123) },
            { it.getJSONObject("pending").put("localDate", false) },
            { it.getJSONObject("lastOutcome").remove("occurrence") },
            { it.getJSONObject("lastOutcome").put("occurrence", JSONObject.NULL) },
            { it.getJSONObject("lastOutcome").put("sessionId", true) },
            { it.getJSONObject("lastOutcome").put("extra", true) },
        )) rejectAfter(mutate)
    }

    @Test
    fun `duplicate entries or one corrupt entry reject the entire list`() {
        rejectEncode(listOf(entry, entry))
        val root = JSONObject(ScheduleExecutionCodec.encode(listOf(entry)))
        root.getJSONArray("entries").put(root.getJSONArray("entries").getJSONObject(0))
        rejectDecode(root.toString())
        val mixed = JSONObject(ScheduleExecutionCodec.encode(listOf(entry)))
        mixed.getJSONArray("entries").put(JSONObject().put("scheduleId", "invalid"))
        rejectDecode(mixed.toString())
    }

    @Test
    fun `schedule and session ids require canonical UUIDs and nested ids match their parent`() {
        for (id in listOf("", "invalid", scheduleId.uppercase(), "0-0-0-0-0", scheduleId.replace("-", ""))) {
            rejectEncode(listOf(ScheduleExecution(id)))
            rejectAfter { it.put("scheduleId", id) }
            rejectEncode(listOf(entry.copy(lastOutcome = outcome.copy(sessionId = id))))
            rejectAfter { it.getJSONObject("lastOutcome").put("sessionId", id) }
        }
        rejectOccurrence(oneTime.copy(scheduleId = sessionId))
        rejectAfter { it.getJSONObject("pending").put("scheduleId", sessionId) }
        rejectAfter { it.getJSONObject("lastOutcome").getJSONObject("occurrence").put("scheduleId", sessionId) }
    }

    @Test
    fun `started requires a session id and other outcomes cannot claim one`() {
        rejectEncode(listOf(entry.copy(lastOutcome = outcome.copy(sessionId = null))))
        rejectAfter { it.getJSONObject("lastOutcome").put("sessionId", JSONObject.NULL) }
        for (code in ScheduleOutcomeCode.entries.filter { it != ScheduleOutcomeCode.STARTED }) {
            rejectEncode(listOf(entry.copy(lastOutcome = outcome.copy(code = code))))
            rejectAfter { it.getJSONObject("lastOutcome").put("code", code.name) }
        }
        for (code in listOf("started", "UNKNOWN", "", " STARTED")) {
            rejectAfter { it.getJSONObject("lastOutcome").put("code", code) }
        }
        rejectAfter { it.getJSONObject("lastOutcome").put("code", true) }
    }

    @Test
    fun `integers never coerce fractions exponent overflow strings booleans or null`() {
        val encoded = ScheduleExecutionCodec.encode(listOf(entry.copy(pending = oneTime)))
        for (raw in listOf("0", "-1", "1.0", "1.5", "1e3", "9223372036854775808", "-9223372036854775809", "\"1\"", "true", "null")) {
            rejectDecode(encoded.replace("\"revision\":1", "\"revision\":$raw"))
        }
        for (key in listOf("epochMilliseconds", "handledAtEpochMilliseconds", "lastResolvedOneTimeEpochMilliseconds")) {
            for (raw in listOf("1.5", "1e3", "\"123\"", "true", "9223372036854775808")) {
                rejectDecode(encoded.replace(Regex("\"$key\":[0-9]+"), "\"$key\":$raw"))
            }
        }
        rejectOccurrence(oneTime.copy(revision = 0L))
        rejectOccurrence(oneTime.copy(revision = Long.MIN_VALUE))
    }

    @Test
    fun `epochs must stay inside supported range throughout the record`() {
        for (epoch in listOf(-1L, PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS + 1L, Long.MAX_VALUE)) {
            rejectOccurrence(oneTime.copy(key = "oneTime:$epoch", epochMilliseconds = epoch))
            rejectEncode(listOf(entry.copy(lastResolvedOneTimeEpochMilliseconds = epoch)))
            rejectEncode(listOf(entry.copy(lastOutcome = outcome.copy(handledAtEpochMilliseconds = epoch))))
            rejectAfter { it.getJSONObject("pending").put("epochMilliseconds", epoch) }
            rejectAfter { it.put("lastResolvedOneTimeEpochMilliseconds", epoch) }
            rejectAfter { it.getJSONObject("lastOutcome").put("handledAtEpochMilliseconds", epoch) }
        }
    }

    @Test
    fun `one time keys must exactly encode their instant with no local date`() {
        for (key in listOf("", "oneTime:1", "oneTime:01788418800000", "oneTime:+1788418800000", "onetime:1788418800000", repeating.key)) {
            rejectOccurrence(oneTime.copy(key = key))
            rejectAfter { it.getJSONObject("lastOutcome").getJSONObject("occurrence").put("key", key) }
        }
        rejectOccurrence(oneTime.copy(localDate = "2026-09-03"))
    }

    @Test
    fun `repeating keys require matching civil date and zero padded valid hour minute`() {
        for (key in listOf(
            "repeating:2026-09-04:09:00", "repeating:2026-09-03:9:00", "repeating:2026-09-03:09:0",
            "repeating:2026-09-03:24:00", "repeating:2026-09-03:09:60", "repeating:2026-09-03:09:00:00",
            "repeating:2026-9-03:09:00", "repeating:2026-09-03:-1:00", "Repeating:2026-09-03:09:00",
        )) {
            rejectOccurrence(repeating.copy(key = key))
            rejectAfter { it.getJSONObject("pending").put("key", key) }
        }
        rejectOccurrence(repeating.copy(localDate = null))
        rejectAfter { it.getJSONObject("pending").remove("localDate") }
        assertRoundTrip(entry.copy(pending = repeating.copy(key = "repeating:2026-09-03:00:00")))
        assertRoundTrip(entry.copy(pending = repeating.copy(key = "repeating:2026-09-03:23:59")))
    }

    @Test
    fun `invalid civil dates and raw offset zones fail without normalization`() {
        for (date in listOf("", "2026-02-30", "1500-02-29", "2026-13-01", "2026-9-03", "0000-01-01", "+10000-01-01", " 2026-09-03")) {
            rejectOccurrence(repeating.copy(localDate = date, key = "repeating:$date:09:00"))
            rejectEncode(listOf(entry.copy(lastResolvedLocalDate = date)))
            rejectAfter { it.put("lastResolvedLocalDate", date) }
        }
        for (zone in listOf("", "Not/AZone", "+01:00", "UTC+01:00", "Z", " UTC ")) {
            rejectOccurrence(oneTime.copy(zoneId = zone))
            rejectAfter { it.getJSONObject("pending").put("zoneId", zone) }
        }
        assertRoundTrip(entry.copy(pending = oneTime.copy(zoneId = "UTC")))
        assertRoundTrip(entry.copy(pending = oneTime.copy(zoneId = "US/Pacific")))
    }

    @Test
    fun `entry and encoded character caps are inclusive and checked before parsing`() {
        val entries = (1L..100L).map { ScheduleExecution(UUID(0L, it).toString()) }
        assertEquals(entries, ScheduleExecutionCodec.decode(ScheduleExecutionCodec.encode(entries)))
        rejectEncode(entries + entry)
        val root = JSONObject(ScheduleExecutionCodec.encode(entries))
        root.getJSONArray("entries").put(JSONObject().put("scheduleId", scheduleId))
        rejectDecode(root.toString())
        val maximum = ScheduleExecutionCodec.encode(emptyList()).padEnd(ScheduleExecutionCodec.MAX_ENCODED_CHARACTERS, ' ')
        assertEquals(emptyList<ScheduleExecution>(), ScheduleExecutionCodec.decode(maximum))
        rejectDecode(maximum + " ")
        rejectDecode("x".repeat(ScheduleExecutionCodec.MAX_ENCODED_CHARACTERS + 1))
    }

    private fun assertRoundTrip(value: ScheduleExecution) {
        assertEquals(listOf(value), ScheduleExecutionCodec.decode(ScheduleExecutionCodec.encode(listOf(value))))
    }

    private fun rejectOccurrence(value: ScheduledOccurrence) {
        rejectEncode(listOf(entry.copy(pending = value)))
        rejectEncode(listOf(entry.copy(lastOutcome = outcome.copy(occurrence = value))))
    }

    private fun rejectAfter(mutate: (JSONObject) -> Unit) {
        val root = JSONObject(ScheduleExecutionCodec.encode(listOf(entry)))
        mutate(root.getJSONArray("entries").getJSONObject(0))
        rejectDecode(root.toString())
    }

    private fun rejectEncode(values: List<ScheduleExecution>) {
        assertThrows(Exception::class.java) { ScheduleExecutionCodec.encode(values) }
    }

    private fun rejectDecode(encoded: String) {
        assertThrows(Exception::class.java) { ScheduleExecutionCodec.decode(encoded) }
    }
}
