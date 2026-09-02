package com.justnoise.gate0.data

import com.justnoise.gate0.domain.PortableScheduleTimingRules
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ScheduledOccurrence(
    val scheduleId: String,
    val revision: Long,
    val key: String,
    val epochMilliseconds: Long,
    val localDate: String?,
    val zoneId: String,
)

enum class ScheduleOutcomeCode {
    STARTED, MISSED, ACTIVE_SESSION, MODE_UNAVAILABLE, EMPTY_SELECTION,
    TARGET_UNAVAILABLE, CONSENT_REQUIRED, ZAP_REQUIRED, CAPABILITY_UNAVAILABLE,
    EXACT_ALARM_REQUIRED, RECOVERY_REQUIRED,
}

data class ScheduleOutcome(
    val code: ScheduleOutcomeCode,
    val occurrence: ScheduledOccurrence,
    val handledAtEpochMilliseconds: Long,
    val sessionId: String? = null,
)

data class ScheduleExecution(
    val scheduleId: String,
    val pending: ScheduledOccurrence? = null,
    val lastResolvedLocalDate: String? = null,
    val lastResolvedOneTimeEpochMilliseconds: Long? = null,
    val lastOutcome: ScheduleOutcome? = null,
)

data class ScheduleExecutionSnapshot(
    val entries: List<ScheduleExecution> = emptyList(),
    val error: Boolean = false,
)

/** Bounded persistence values only; current-schedule matching and atomic transitions live in the store. */
object ScheduleExecutionCodec {
    const val MAX_ENTRIES = 100
    const val MAX_ENCODED_CHARACTERS = 262_144
    private const val SCHEMA_VERSION = 1L
    private val namedZones = ZoneId.getAvailableZoneIds() + "UTC"
    private val dateShape = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val repeatingKey = Regex("repeating:([0-9]{4}-[0-9]{2}-[0-9]{2}):([0-9]{2}):([0-9]{2})")
    private val entryFields = setOf(
        "scheduleId", "pending", "lastResolvedLocalDate", "lastResolvedOneTimeEpochMilliseconds", "lastOutcome",
    )
    private val occurrenceFields = setOf("scheduleId", "revision", "key", "epochMilliseconds", "localDate", "zoneId")
    private val outcomeFields = setOf("code", "occurrence", "handledAtEpochMilliseconds", "sessionId")

    fun encode(entries: List<ScheduleExecution>): String {
        validate(entries)
        val records = JSONArray()
        entries.forEach { entry ->
            records.put(JSONObject().apply {
                put("scheduleId", entry.scheduleId)
                put("pending", entry.pending?.toJson() ?: JSONObject.NULL)
                put("lastResolvedLocalDate", entry.lastResolvedLocalDate ?: JSONObject.NULL)
                put("lastResolvedOneTimeEpochMilliseconds", entry.lastResolvedOneTimeEpochMilliseconds ?: JSONObject.NULL)
                put("lastOutcome", entry.lastOutcome?.toJson() ?: JSONObject.NULL)
            })
        }
        return JSONObject().put("schemaVersion", SCHEMA_VERSION).put("entries", records).toString().also {
            require(it.length <= MAX_ENCODED_CHARACTERS) { "Schedule execution data too large" }
        }
    }

    fun decode(encoded: String): List<ScheduleExecution> {
        require(encoded.length <= MAX_ENCODED_CHARACTERS) { "Schedule execution data too large" }
        val tokens = JSONTokener(encoded)
        val root = tokens.nextValue().objectValue()
        require(tokens.nextClean() == '\u0000') { "Trailing schedule execution data" }
        root.requireFields(setOf("schemaVersion", "entries"))
        require(root.get("schemaVersion").longValue() == SCHEMA_VERSION) { "Unsupported schedule execution schema" }
        val records = root.get("entries") as? JSONArray ?: invalid()
        require(records.length() <= MAX_ENTRIES) { "Too many schedule execution entries" }
        return (0 until records.length()).map { index ->
            val record = records.get(index).objectValue()
            record.requireFields(entryFields, required = setOf("scheduleId"))
            ScheduleExecution(
                scheduleId = record.get("scheduleId").stringValue(),
                pending = record.nullableValue("pending")?.objectValue()?.toOccurrence(),
                lastResolvedLocalDate = record.nullableValue("lastResolvedLocalDate")?.stringValue(),
                lastResolvedOneTimeEpochMilliseconds = record.nullableValue("lastResolvedOneTimeEpochMilliseconds")?.longValue(),
                lastOutcome = record.nullableValue("lastOutcome")?.objectValue()?.toOutcome(),
            )
        }.also(::validate)
    }

    private fun ScheduledOccurrence.toJson(): JSONObject = JSONObject().apply {
        put("scheduleId", scheduleId)
        put("revision", revision)
        put("key", key)
        put("epochMilliseconds", epochMilliseconds)
        put("localDate", localDate ?: JSONObject.NULL)
        put("zoneId", zoneId)
    }

    private fun ScheduleOutcome.toJson(): JSONObject = JSONObject().apply {
        put("code", code.name)
        put("occurrence", occurrence.toJson())
        put("handledAtEpochMilliseconds", handledAtEpochMilliseconds)
        put("sessionId", sessionId ?: JSONObject.NULL)
    }

    private fun JSONObject.toOccurrence(): ScheduledOccurrence {
        requireFields(occurrenceFields, required = occurrenceFields - "localDate")
        return ScheduledOccurrence(
            scheduleId = get("scheduleId").stringValue(),
            revision = get("revision").longValue(),
            key = get("key").stringValue(),
            epochMilliseconds = get("epochMilliseconds").longValue(),
            localDate = nullableValue("localDate")?.stringValue(),
            zoneId = get("zoneId").stringValue(),
        )
    }

    private fun JSONObject.toOutcome(): ScheduleOutcome {
        requireFields(outcomeFields, required = outcomeFields - "sessionId")
        return ScheduleOutcome(
            code = ScheduleOutcomeCode.valueOf(get("code").stringValue()),
            occurrence = get("occurrence").objectValue().toOccurrence(),
            handledAtEpochMilliseconds = get("handledAtEpochMilliseconds").longValue(),
            sessionId = nullableValue("sessionId")?.stringValue(),
        )
    }

    private fun validate(entries: List<ScheduleExecution>) {
        require(entries.size <= MAX_ENTRIES) { "Too many schedule execution entries" }
        val identifiers = mutableSetOf<String>()
        entries.forEach { entry ->
            requireUuid(entry.scheduleId)
            require(identifiers.add(entry.scheduleId)) { "Duplicate schedule execution ID" }
            entry.pending?.let { validateOccurrence(it, entry.scheduleId) }
            // Both markers may survive a one-time/repeating edit; neither is inferred from the latest outcome.
            entry.lastResolvedLocalDate?.let(::requireDate)
            entry.lastResolvedOneTimeEpochMilliseconds?.let(::requireEpoch)
            entry.lastOutcome?.let { outcome ->
                validateOccurrence(outcome.occurrence, entry.scheduleId)
                requireEpoch(outcome.handledAtEpochMilliseconds)
                if (outcome.code == ScheduleOutcomeCode.STARTED) {
                    requireUuid(requireNotNull(outcome.sessionId) { "Started outcome requires a session ID" })
                } else {
                    require(outcome.sessionId == null) { "Unstarted outcome cannot claim a session" }
                }
            }
        }
    }

    private fun validateOccurrence(occurrence: ScheduledOccurrence, parentId: String) {
        require(occurrence.scheduleId == parentId) { "Occurrence belongs to another schedule" }
        require(occurrence.revision > 0L) { "Invalid occurrence revision" }
        requireEpoch(occurrence.epochMilliseconds)
        require(occurrence.zoneId in namedZones) { "Invalid occurrence zone" }
        if (occurrence.localDate == null) {
            require(occurrence.key == "oneTime:${occurrence.epochMilliseconds}") { "Invalid one-time occurrence key" }
        } else {
            requireDate(occurrence.localDate)
            val match = requireNotNull(repeatingKey.matchEntire(occurrence.key)) { "Invalid repeating occurrence key" }
            require(match.groupValues[1] == occurrence.localDate &&
                match.groupValues[2].toInt() in 0..23 && match.groupValues[3].toInt() in 0..59) {
                "Invalid repeating occurrence date or time"
            }
        }
    }

    private fun requireUuid(value: String) {
        require(value.length == 36 && UUID.fromString(value).toString() == value) { "Invalid canonical UUID" }
    }

    private fun requireDate(value: String) {
        require(dateShape.matches(value) && LocalDate.parse(value).year in 1..9999) { "Invalid civil date" }
    }

    private fun requireEpoch(value: Long) {
        require(value in 0L..PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS) { "Invalid schedule epoch" }
    }

    private fun JSONObject.requireFields(allowed: Set<String>, required: Set<String> = allowed) {
        val fields = keys().asSequence().toSet()
        require(fields.all { it in allowed } && fields.containsAll(required)) { "Invalid schedule execution fields" }
    }

    private fun JSONObject.nullableValue(key: String): Any? =
        if (!has(key) || get(key) === JSONObject.NULL) null else get(key)

    private fun Any.objectValue(): JSONObject = this as? JSONObject ?: invalid()
    private fun Any.stringValue(): String = this as? String ?: invalid()

    // Avoid JSONObject coercion of strings/floating point; preserve exact Long revisions.
    private fun Any.longValue(): Long = when (this) {
        is Int -> toLong()
        is Long -> this
        else -> invalid()
    }

    private fun invalid(): Nothing = throw IllegalArgumentException("Malformed schedule execution data")
}
