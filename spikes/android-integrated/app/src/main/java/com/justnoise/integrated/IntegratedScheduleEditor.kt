package com.justnoise.integrated

import com.justnoise.gate0.data.ScheduleDraft
import com.justnoise.gate0.data.StoredSchedule
import com.justnoise.gate0.data.StoredMode
import com.justnoise.gate0.domain.PortableScheduleTimingRules
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.domain.ScheduleTiming
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Editable native state only. Saving, consent, revisions and alarm access remain controller concerns. */
data class IntegratedScheduleEditor(
    val id: String? = null,
    val expectedRevision: Long? = null,
    val name: String,
    val isRepeating: Boolean,
    val localDate: String,
    val hour: Int,
    val minute: Int,
    val weekdays: Set<PortableWeekday>,
    val enabled: Boolean,
    val zoneId: String,
    val originalOneTimeEpochMilliseconds: Long? = null,
    val originalOneTimeZoneId: String? = null,
    val modeReference: String? = null,
) {
    fun modeProblem(modes: List<StoredMode>): IntegratedScheduleModeProblem? {
        if (modeReference.isNullOrBlank()) return IntegratedScheduleModeProblem.REQUIRED
        val mode = modes.find { it.id == modeReference } ?: return IntegratedScheduleModeProblem.UNAVAILABLE
        return if (mode.selectedPackages.isEmpty()) IntegratedScheduleModeProblem.EMPTY else null
    }

    fun toDraft(nowEpochMilliseconds: Long, deviceZoneId: String): IntegratedScheduleEditorResult {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return invalid("Enter a schedule name.")
        if (trimmedName.length > 80) return invalid("Use a name with 80 characters or fewer.")
        if (modeReference.isNullOrBlank()) return invalid("Choose a mode for this schedule.")
        if (nowEpochMilliseconds !in supportedEpochs) {
            return invalid("The current date is outside the supported range.")
        }
        if (hour !in 0..23 || minute !in 0..59) return invalid("Choose a valid time.")

        val savedZoneId = if (isRepeating) deviceZoneId else zoneId
        val zone = namedZone(savedZoneId) ?: return invalid("Choose a supported time zone.")
        val timing = if (isRepeating) {
            if (weekdays.isEmpty()) return invalid("Select at least one weekday.")
            ScheduleTiming(repeatWeekdays = weekdays.toSet(), hour = hour, minute = minute).also {
                if (PortableScheduleTimingRules.nextOccurrence(it, nowEpochMilliseconds, savedZoneId) == null) {
                    return invalid("No future occurrence is available within the supported date range.")
                }
            }
        } else {
            val original = originalOneTimeEpochMilliseconds?.takeIf { it in supportedEpochs }
            val originalLocal = original?.let { Instant.ofEpochMilli(it).atZone(zone) }
            val unchangedSavedTime = id != null && expectedRevision != null && expectedRevision > 0L &&
                originalOneTimeZoneId == zoneId && originalLocal != null && originalLocal.toLocalDate().toString() == localDate &&
                originalLocal.hour == hour && originalLocal.minute == minute

            // An old disabled occurrence may be renamed without inventing a future date. Re-enabling
            // it, changing its time/zone, or creating any new one-time input still requires a future instant.
            val epoch = if (unchangedSavedTime && original != null && (!enabled || original > nowEpochMilliseconds)) {
                original
            } else {
                PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds(
                    localDate, hour, minute, zoneId, nowEpochMilliseconds,
                ) ?: return invalid("Choose a valid future date and time in $zoneId. A daylight-saving gap cannot be used.")
            }
            ScheduleTiming(oneTimeEpochMilliseconds = epoch)
        }
        return IntegratedScheduleEditorResult.Valid(
            ScheduleDraft(trimmedName, enabled, timing, savedZoneId, modeReference),
        )
    }

    companion object {
        private const val HOUR_MILLISECONDS = 3_600_000L
        private val supportedEpochs = 0L..PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS
        private val namedZoneIds = ZoneId.getAvailableZoneIds() + "UTC"

        fun new(nowEpochMilliseconds: Long, zoneId: String, recentModeReference: String? = null): IntegratedScheduleEditor {
            val local = defaultLocalTime(nowEpochMilliseconds, zoneId)
            return IntegratedScheduleEditor(
                name = "",
                isRepeating = false,
                localDate = local?.toLocalDate()?.toString().orEmpty(),
                hour = local?.hour ?: 0,
                minute = local?.minute ?: 0,
                weekdays = emptySet(),
                enabled = true,
                zoneId = zoneId,
                modeReference = recentModeReference,
            )
        }

        fun fromStored(
            schedule: StoredSchedule,
            nowEpochMilliseconds: Long,
            deviceZoneId: String,
        ): IntegratedScheduleEditor {
            val repeating = schedule.timing.repeatWeekdays.isNotEmpty()
            val epoch = schedule.timing.oneTimeEpochMilliseconds
            val zoneId = if (repeating) deviceZoneId else schedule.creationZoneId
            val local = if (repeating) defaultLocalTime(nowEpochMilliseconds, deviceZoneId) else {
                epoch?.takeIf { it in supportedEpochs }?.let { instant ->
                    namedZone(zoneId)?.let { Instant.ofEpochMilli(instant).atZone(it) }
                }
            }
            return IntegratedScheduleEditor(
                id = schedule.id,
                expectedRevision = schedule.revision,
                name = schedule.name,
                isRepeating = repeating,
                localDate = local?.toLocalDate()?.toString().orEmpty(),
                hour = if (repeating) schedule.timing.hour ?: -1 else local?.hour ?: -1,
                minute = if (repeating) schedule.timing.minute ?: -1 else local?.minute ?: -1,
                weekdays = schedule.timing.repeatWeekdays.toSet(),
                enabled = schedule.enabled,
                zoneId = zoneId,
                originalOneTimeEpochMilliseconds = epoch,
                originalOneTimeZoneId = if (epoch != null) schedule.creationZoneId else null,
                modeReference = schedule.modeReference,
            )
        }

        private fun namedZone(value: String): ZoneId? =
            if (value in namedZoneIds) ZoneId.of(value) else null

        private fun defaultLocalTime(nowEpochMilliseconds: Long, zoneId: String): ZonedDateTime? {
            if (nowEpochMilliseconds !in supportedEpochs) return null
            val zone = namedZone(zoneId) ?: return null
            // Usually one hour from now. During a backward clock transition that can land in the
            // second overlap; try later defaults rather than offer a time the shared rules reject.
            for (hoursAhead in 1L..26L) {
                val delta = hoursAhead * HOUR_MILLISECONDS
                if (nowEpochMilliseconds > supportedEpochs.last - delta) return null
                val local = Instant.ofEpochMilli(nowEpochMilliseconds + delta).atZone(zone)
                if (PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds(
                        local.toLocalDate().toString(), local.hour, local.minute, zoneId, nowEpochMilliseconds,
                    ) != null
                ) return local
            }
            return null
        }

        private fun invalid(message: String) = IntegratedScheduleEditorResult.Invalid(message)
    }
}

enum class IntegratedScheduleModeProblem { REQUIRED, UNAVAILABLE, EMPTY }

sealed interface IntegratedScheduleEditorResult {
    data class Valid(val draft: ScheduleDraft) : IntegratedScheduleEditorResult
    data class Invalid(val message: String) : IntegratedScheduleEditorResult
}
