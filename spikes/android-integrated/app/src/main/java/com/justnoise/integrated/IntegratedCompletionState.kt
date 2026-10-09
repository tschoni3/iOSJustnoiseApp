package com.justnoise.integrated

import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.domain.HistoryInterval

/**
 * Transient foreground-STOP presentation only. Never serialize this holder or populate it by
 * polling active/idle state or the latest History row. The host owns lifecycle callback authority.
 */
data class IntegratedCompletionState(
    val visibleEntry: SessionHistoryEntry? = null,
    private val consumedSessionIds: Set<String> = emptySet(),
) {
    /** A receipt comes from a newly committed stop, not from a repeated/idempotent stop call. */
    fun receive(entry: SessionHistoryEntry): IntegratedCompletionState {
        if (entry.id in consumedSessionIds) return this
        return copy(
            visibleEntry = entry.takeIf { it.durationMilliseconds >= MINIMUM_DURATION_MILLISECONDS },
            consumedSessionIds = consumedSessionIds + entry.id,
        )
    }

    fun dismiss(): IntegratedCompletionState = copy(visibleEntry = null)
    fun onBackground(): IntegratedCompletionState = dismiss()
    // Local-data removal also drops the transient UUID suppression set. The host invalidates
    // foreground scan authority on pause/withdrawal; it never replays History into this holder.
    fun onWithdrawal(): IntegratedCompletionState = IntegratedCompletionState()
    fun onNewSession(): IntegratedCompletionState = dismiss()

    // Avoid accidentally logging the receipt's UUID and session metadata through a state dump.
    override fun toString(): String = "IntegratedCompletionState(visible=${visibleEntry != null})"

    companion object {
        /** Existing iOS source threshold, not a new session-start or stop restriction. */
        const val MINIMUM_DURATION_MILLISECONDS = 1_200_000L
    }
}

internal val SessionHistoryEntry.completionTimingUncertain: Boolean
    get() = HistoryInterval(startedAtEpochMilliseconds, endedAtEpochMilliseconds,
        durationMilliseconds, interrupted).timingUncertain
