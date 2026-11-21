package com.justnoise.gate0.domain

enum class LongPressStartDecision(val contractValue: String) {
    START("start"), NO_CHANGE("noChange"), UNAVAILABLE("unavailable")
}

/** Explicit UI actions only. NFC entry points retain their separate physical-tag validation. */
object SessionShortcutRules {
    const val INITIAL_EMERGENCY_UNLOCKS = 5

    fun longPress(requested: Boolean, recovery: Boolean, account: Boolean, paired: Boolean,
        selection: Boolean, capability: Boolean): LongPressStartDecision = when {
        requested -> LongPressStartDecision.NO_CHANGE
        recovery || !account || !paired || !selection || !capability -> LongPressStartDecision.UNAVAILABLE
        else -> LongPressStartDecision.START
    }

    fun emergencyAllowed(requested: Boolean, sameSession: Boolean, remaining: Int): Boolean =
        requested && sameSession && remaining > 0
}
