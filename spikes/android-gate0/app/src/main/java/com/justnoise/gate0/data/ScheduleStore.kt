package com.justnoise.gate0.data

import com.justnoise.gate0.domain.PortableScheduleTimingRules
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.domain.ScheduleTiming
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.ZoneId
import java.util.UUID

const val DEFAULT_SCHEDULE_MODE_REFERENCE = "local-default"

data class ScheduleDraft(
    val name: String,
    val enabled: Boolean,
    val timing: ScheduleTiming,
    val creationZoneId: String,
)

data class StoredSchedule(
    val id: String,
    val revision: Long,
    val name: String,
    val enabled: Boolean,
    val timing: ScheduleTiming,
    val creationZoneId: String,
    val modeReference: String = DEFAULT_SCHEDULE_MODE_REFERENCE,
)

data class ScheduleSnapshot(
    val schedules: List<StoredSchedule> = emptyList(),
    val error: Boolean = false,
)

/** Private configuration only; no preferences, occurrence receipts, alarms or sessions. */
object ScheduleStoreCodec {
    const val MAX_SCHEDULES = 100
    const val MAX_ENCODED_CHARACTERS = 262_144
    private const val SCHEMA_VERSION = 1L
    private val namedZones = ZoneId.getAvailableZoneIds() + "UTC"
    private val recordFields = setOf(
        "id", "revision", "name", "enabled", "timing", "creationZoneId", "modeReference",
    )
    private val timingFields = setOf("oneTimeEpochMilliseconds", "repeatWeekdays", "hour", "minute")

    fun encode(schedules: List<StoredSchedule>): String {
        validate(schedules)
        val records = JSONArray()
        schedules.forEach { schedule ->
            val weekdays = PortableWeekday.entries.filter { it in schedule.timing.repeatWeekdays }
            records.put(JSONObject().apply {
                put("id", schedule.id)
                put("revision", schedule.revision)
                put("name", schedule.name)
                put("enabled", schedule.enabled)
                put("timing", JSONObject().apply {
                    // Omitted and explicit-null input have this one canonical encoding.
                    put("oneTimeEpochMilliseconds", schedule.timing.oneTimeEpochMilliseconds ?: JSONObject.NULL)
                    put("repeatWeekdays", JSONArray(weekdays.map { it.contractValue }))
                    put("hour", schedule.timing.hour ?: JSONObject.NULL)
                    put("minute", schedule.timing.minute ?: JSONObject.NULL)
                })
                put("creationZoneId", schedule.creationZoneId)
                put("modeReference", schedule.modeReference)
            })
        }
        return JSONObject().put("schemaVersion", SCHEMA_VERSION).put("schedules", records)
            .toString().also {
                require(it.length <= MAX_ENCODED_CHARACTERS) { "Schedule configuration too large" }
            }
    }

    fun decode(encoded: String): List<StoredSchedule> {
        require(encoded.length <= MAX_ENCODED_CHARACTERS) { "Schedule configuration too large" }
        val tokens = JSONTokener(encoded)
        val root = tokens.nextValue() as? JSONObject ?: invalid()
        require(tokens.nextClean() == '\u0000') { "Trailing schedule data" }
        root.requireFields(setOf("schemaVersion", "schedules"))
        require(root.get("schemaVersion").longValue() == SCHEMA_VERSION) { "Unsupported schedule schema" }
        val records = root.get("schedules") as? JSONArray ?: invalid()
        require(records.length() <= MAX_SCHEDULES) { "Too many schedules" }
        return (0 until records.length()).map { index ->
            val record = records.get(index) as? JSONObject ?: invalid()
            record.requireFields(recordFields)
            val timing = record.get("timing") as? JSONObject ?: invalid()
            timing.requireFields(timingFields, required = setOf("repeatWeekdays"))
            val days = timing.get("repeatWeekdays") as? JSONArray ?: invalid()
            val weekdays = (0 until days.length()).map { dayIndex ->
                val name = days.get(dayIndex).stringValue()
                requireNotNull(PortableWeekday.entries.firstOrNull { it.contractValue == name }) {
                    "Invalid schedule weekday"
                }
            }
            require(weekdays.size == weekdays.toSet().size) { "Duplicate schedule weekday" }
            StoredSchedule(
                id = record.get("id").stringValue(),
                revision = record.get("revision").longValue(),
                name = record.get("name").stringValue(),
                enabled = record.get("enabled") as? Boolean ?: invalid(),
                timing = ScheduleTiming(
                    oneTimeEpochMilliseconds = timing.nullableValue("oneTimeEpochMilliseconds")?.longValue(),
                    repeatWeekdays = weekdays.toSet(),
                    hour = timing.nullableValue("hour")?.intValue(),
                    minute = timing.nullableValue("minute")?.intValue(),
                ),
                creationZoneId = record.get("creationZoneId").stringValue(),
                modeReference = record.get("modeReference").stringValue(),
            )
        }.also(::validate)
    }

    private fun validate(schedules: List<StoredSchedule>) {
        require(schedules.size <= MAX_SCHEDULES) { "Too many schedules" }
        val identifiers = mutableSetOf<String>()
        schedules.forEach { schedule ->
            require(schedule.id.length == 36 && UUID.fromString(schedule.id).toString() == schedule.id) {
                "Schedule ID must be a canonical UUID"
            }
            require(identifiers.add(schedule.id)) { "Duplicate schedule ID" }
            require(schedule.revision > 0L) { "Invalid schedule revision" }
            require(schedule.name.length in 1..80 && schedule.name == schedule.name.trim()) {
                "Invalid schedule name"
            }
            require(schedule.modeReference.isNotBlank() && schedule.modeReference.length <= 64) {
                "Invalid schedule mode reference"
            }
            require(schedule.creationZoneId in namedZones) { "Invalid schedule creation zone" }
            require(PortableScheduleTimingRules.isValid(schedule.timing)) { "Invalid schedule timing" }
        }
    }

    private fun JSONObject.requireFields(allowed: Set<String>, required: Set<String> = allowed) {
        val fields = keys().asSequence().toSet()
        require(fields.all { it in allowed } && fields.containsAll(required)) { "Invalid schedule fields" }
    }

    private fun JSONObject.nullableValue(key: String): Any? =
        if (!has(key) || get(key) === JSONObject.NULL) null else get(key)

    private fun Any.stringValue(): String = this as? String ?: invalid()

    // Do not use JSONObject.getLong/getInt: they coerce strings and floating-point values.
    // Representable JSON integers are Int/Long; all other numeric types are rejected.
    private fun Any.longValue(): Long = when (this) {
        is Int -> toLong()
        is Long -> this
        else -> invalid()
    }

    private fun Any.intValue(): Int = longValue().let {
        require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Schedule integer out of range" }
        it.toInt()
    }

    private fun invalid(): Nothing = throw IllegalArgumentException("Malformed schedule configuration")
}
