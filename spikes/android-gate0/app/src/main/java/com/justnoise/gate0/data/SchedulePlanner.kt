package com.justnoise.gate0.data

import com.justnoise.gate0.domain.PortableScheduleTimingRules

/** Native execution bookkeeping; the time calculation itself stays in the portable rules. */
object SchedulePlanner {
    fun reconcile(
        schedules: List<StoredSchedule>,
        executions: List<ScheduleExecution>,
        nowEpochMilliseconds: Long,
        zoneId: String,
    ): List<ScheduleExecution> {
        require(nowEpochMilliseconds in 0L..PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS)
        val previous = executions.associateBy { it.scheduleId }
        return schedules.map { schedule ->
            val execution = previous[schedule.id] ?: ScheduleExecution(schedule.id)
            val pending = execution.pending?.takeIf {
                matches(schedule, execution, it) &&
                    (it.localDate == null || it.zoneId == zoneId)
            }
            execution.copy(pending = if (!schedule.enabled) null else pending ?: next(
                schedule, execution, nowEpochMilliseconds, zoneId,
            ))
        }
    }

    fun matches(
        schedule: StoredSchedule,
        execution: ScheduleExecution,
        occurrence: ScheduledOccurrence,
    ): Boolean {
        if (!schedule.enabled || occurrence.scheduleId != schedule.id ||
            occurrence.revision != schedule.revision
        ) return false
        val once = schedule.timing.oneTimeEpochMilliseconds
        if (once != null) {
            return occurrence.localDate == null && occurrence.epochMilliseconds == once &&
                occurrence.key == "oneTime:$once" &&
                (execution.lastResolvedOneTimeEpochMilliseconds == null ||
                    once > execution.lastResolvedOneTimeEpochMilliseconds)
        }
        if (occurrence.epochMilliseconds <= 0L) return false
        val expected = PortableScheduleTimingRules.nextOccurrence(
            schedule.timing, occurrence.epochMilliseconds - 1L, occurrence.zoneId,
            execution.lastResolvedLocalDate,
        ) ?: return false
        return expected.key == occurrence.key &&
            expected.epochMilliseconds == occurrence.epochMilliseconds &&
            expected.localDate == occurrence.localDate
    }

    private fun next(
        schedule: StoredSchedule,
        execution: ScheduleExecution,
        now: Long,
        zone: String,
    ): ScheduledOccurrence? {
        val once = schedule.timing.oneTimeEpochMilliseconds
        if (once != null) {
            if (execution.lastResolvedOneTimeEpochMilliseconds?.let { once <= it } == true) return null
            // A saved occurrence which elapsed while the process was absent must be
            // classified as due/missed, never silently rolled to a future date.
            return ScheduledOccurrence(
                schedule.id, schedule.revision, "oneTime:$once", once, null, schedule.creationZoneId,
            )
        }
        val next = PortableScheduleTimingRules.nextOccurrence(
            schedule.timing, now, zone, execution.lastResolvedLocalDate,
        ) ?: return null
        return ScheduledOccurrence(
            schedule.id, schedule.revision, next.key, next.epochMilliseconds, next.localDate, zone,
        )
    }
}
