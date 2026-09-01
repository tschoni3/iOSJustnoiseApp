package com.justnoise.gate0.platform.nfc

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the lifecycle of one NFC interaction without retaining any tag data.
 *
 * A callback must claim the token captured when its reader was enabled before doing tag I/O.
 * Cancellation or replacement invalidates both an armed callback and a callback already reading,
 * so an Activity lifecycle change or queued callback cannot apply a late mutation.
 */
class OneShotScanCoordinator<P> {
    private val nextIdentifier = AtomicLong(0)
    private val state = AtomicReference<State<P>?>(null)

    fun begin(purpose: P): Token<P> {
        val token = Token(nextIdentifier.incrementAndGet(), purpose)
        state.set(State(token = token, claimed = false))
        return token
    }

    fun claim(token: Token<P>): Boolean {
        while (true) {
            val current = state.get() ?: return false
            if (current.token.identifier != token.identifier || current.claimed) return false
            if (state.compareAndSet(current, current.copy(claimed = true))) {
                return true
            }
        }
    }

    fun isClaimActive(token: Token<P>): Boolean {
        val current = state.get()
        return current?.claimed == true && current.token.identifier == token.identifier
    }

    fun finish(token: Token<P>) {
        while (true) {
            val current = state.get() ?: return
            if (current.token.identifier != token.identifier) return
            if (state.compareAndSet(current, null)) return
        }
    }

    fun cancel() {
        state.set(null)
    }

    fun cancel(token: Token<P>) {
        while (true) {
            val current = state.get() ?: return
            if (current.token.identifier != token.identifier) return
            if (state.compareAndSet(current, null)) return
        }
    }

    fun isActive(): Boolean = state.get() != null

    class Token<P> internal constructor(
        internal val identifier: Long,
        val purpose: P,
    )

    private data class State<P>(
        val token: Token<P>,
        val claimed: Boolean,
    )
}
