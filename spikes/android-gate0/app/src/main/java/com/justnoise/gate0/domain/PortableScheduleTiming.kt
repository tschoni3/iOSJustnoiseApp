package com.justnoise.gate0.domain

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class ScheduleTiming(
    val oneTimeEpochMilliseconds: Long? = null,
    val repeatWeekdays: Set<PortableWeekday> = emptySet(),
    val hour: Int? = null,
    val minute: Int? = null,
)

data class ScheduleOccurrence(
    val key: String,
    val epochMilliseconds: Long,
    val localDate: String? = null,
)

object PortableScheduleTimingRules {
    const val MAXIMUM_EPOCH_MILLISECONDS = 253_402_300_799_999L

    private val dateShape = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val namedZoneIds: Set<String> = ZoneId.getAvailableZoneIds() + "UTC"

    fun isValid(timing: ScheduleTiming): Boolean = if (timing.repeatWeekdays.isEmpty()) {
        timing.oneTimeEpochMilliseconds != null &&
            validEpoch(timing.oneTimeEpochMilliseconds) &&
            timing.hour == null && timing.minute == null
    } else {
        timing.oneTimeEpochMilliseconds == null &&
            timing.hour != null && timing.hour in 0..23 &&
            timing.minute != null && timing.minute in 0..59
    }

    fun resolveOneTimeEpochMilliseconds(
        localDate: String,
        hour: Int,
        minute: Int,
        zoneId: String,
        nowEpochMilliseconds: Long,
    ): Long? {
        if (!validEpoch(nowEpochMilliseconds) || hour !in 0..23 || minute !in 0..59) return null
        val date = parseDate(localDate) ?: return null
        val zone = namedZone(zoneId) ?: return null
        val epoch = firstValidInstant(date.atTime(hour, minute), zone) ?: return null
        return epoch.takeIf { validEpoch(it) && it > nowEpochMilliseconds }
    }

    fun nextOccurrence(
        timing: ScheduleTiming,
        afterExclusiveEpochMilliseconds: Long,
        zoneId: String,
        lastResolvedLocalDate: String? = null,
    ): ScheduleOccurrence? {
        if (!isValid(timing) || !validEpoch(afterExclusiveEpochMilliseconds)) return null
        val zone = namedZone(zoneId) ?: return null
        timing.oneTimeEpochMilliseconds?.let { epoch ->
            // The zone never shifts a saved one-time instant.
            return if (epoch > afterExclusiveEpochMilliseconds) {
                ScheduleOccurrence("oneTime:$epoch", epoch)
            } else null
        }

        val highWaterDate = lastResolvedLocalDate?.let { parseDate(it) ?: return null }
        var date = Instant.ofEpochMilli(afterExclusiveEpochMilliseconds).atZone(zone).toLocalDate()
        if (highWaterDate != null && !date.isAfter(highWaterDate)) {
            date = highWaterDate.plusDays(1L)
        }
        val hour = requireNotNull(timing.hour)
        val minute = requireNotNull(timing.minute)
        val weekdays = timing.repeatWeekdays.mapTo(mutableSetOf()) { it.name }
        val clockKey = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

        while (date.year <= 9999) {
            if (date.dayOfWeek.name in weekdays) {
                // Empty offsets mean a gap. First offset only means no second overlap fire.
                val epoch = firstValidInstant(date.atTime(hour, minute), zone)
                if (epoch != null && epoch > afterExclusiveEpochMilliseconds) {
                    if (!validEpoch(epoch)) return null
                    return ScheduleOccurrence("repeating:$date:$clockKey", epoch, date.toString())
                }
            }
            date = date.plusDays(1L)
        }
        return null
    }

    private fun validEpoch(epoch: Long): Boolean = epoch in 0L..MAXIMUM_EPOCH_MILLISECONDS

    private fun parseDate(value: String): LocalDate? {
        if (!dateShape.matches(value)) return null
        return try {
            LocalDate.parse(value).takeIf { it.year in 1..9999 }
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun namedZone(value: String): ZoneId? {
        if (value !in namedZoneIds) return null
        return try {
            ZoneId.of(value)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun firstValidInstant(local: LocalDateTime, zone: ZoneId): Long? {
        val offset = zone.rules.getValidOffsets(local).firstOrNull() ?: return null
        return local.toInstant(offset).toEpochMilli()
    }
}

enum class ScheduleDeliveryDecision(val contractValue: String) {
    IGNORE_STALE("ignoreStale"),
    IGNORE_DUPLICATE("ignoreDuplicate"),
    INVALID("invalid"),
    TOO_EARLY("tooEarly"),
    MISSED("missed"),
    SKIP_ACTIVE("skipActive"),
    START_CANDIDATE("startCandidate"),
}

object ScheduleDeliveryRules {
    const val GRACE_MILLISECONDS = 300_000L

    /** A candidate still requires live capability, consent, targets and an atomic start. */
    fun evaluate(
        currentRevisionEnabled: Boolean,
        alreadyHandled: Boolean,
        sessionRequested: Boolean,
        scheduledAtEpochMilliseconds: Long,
        nowEpochMilliseconds: Long,
    ): ScheduleDeliveryDecision {
        if (!currentRevisionEnabled) return ScheduleDeliveryDecision.IGNORE_STALE
        if (alreadyHandled) return ScheduleDeliveryDecision.IGNORE_DUPLICATE
        val supported = 0L..PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS
        if (scheduledAtEpochMilliseconds !in supported || nowEpochMilliseconds !in supported) {
            return ScheduleDeliveryDecision.INVALID
        }
        if (nowEpochMilliseconds < scheduledAtEpochMilliseconds) return ScheduleDeliveryDecision.TOO_EARLY
        if (nowEpochMilliseconds - scheduledAtEpochMilliseconds > GRACE_MILLISECONDS) {
            return ScheduleDeliveryDecision.MISSED
        }
        return if (sessionRequested) ScheduleDeliveryDecision.SKIP_ACTIVE
        else ScheduleDeliveryDecision.START_CANDIDATE
    }
}
