package com.justnoise.gate0.platform.nfc

import com.justnoise.gate0.domain.AccidentalStopGuard
import java.util.concurrent.atomic.AtomicLong

/** Prevents two direct-dispatch Activities from mutating state for one physical tag bounce. */
class DirectNfcMutationGate(
    private val minimumIntervalMilliseconds: Long = DEFAULT_MINIMUM_INTERVAL_MILLISECONDS,
) {
    private val lastClaimElapsedMilliseconds = AtomicLong(NO_CLAIM)

    init {
        require(
            minimumIntervalMilliseconds >= AccidentalStopGuard.MINIMUM_ELAPSED_MILLISECONDS,
        )
    }

    fun tryClaim(nowElapsedMilliseconds: Long): Boolean {
        require(nowElapsedMilliseconds >= 0L)
        while (true) {
            val previous = lastClaimElapsedMilliseconds.get()
            if (
                previous != NO_CLAIM &&
                nowElapsedMilliseconds - previous < minimumIntervalMilliseconds
            ) {
                return false
            }
            if (lastClaimElapsedMilliseconds.compareAndSet(previous, nowElapsedMilliseconds)) {
                return true
            }
        }
    }

    companion object {
        const val DEFAULT_MINIMUM_INTERVAL_MILLISECONDS =
            AccidentalStopGuard.MINIMUM_ELAPSED_MILLISECONDS
        private const val NO_CLAIM = -1L
    }
}

object ProcessDirectNfcMutationGate {
    private val gate = DirectNfcMutationGate()

    fun tryClaim(nowElapsedMilliseconds: Long): Boolean =
        gate.tryClaim(nowElapsedMilliseconds)
}
