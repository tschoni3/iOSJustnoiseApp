package com.justnoise.gate0.domain

object SessionHistoryTiming {
    /** A process gap has no continuous monotonic-clock proof; callers label it interrupted. */
    fun durationMilliseconds(
        startEpoch: Long,
        endEpoch: Long,
        startElapsed: Long,
        endElapsed: Long,
        sameProcess: Boolean,
    ): Long = if (sameProcess && startElapsed >= 0L && endElapsed >= startElapsed) {
        endElapsed - startElapsed
    } else if (startEpoch >= 0L && endEpoch >= startEpoch) {
        endEpoch - startEpoch
    } else {
        0L
    }
}
