package com.justnoise.gate0.feature

/** Optional production-only sink for completed product mutations, never Accessibility
 * observations. The offline capability/development applications do not implement it. */
interface FocusSessionEvents {
    fun focusEventGeneration(): Long
    fun recordFocusEvent(generation: Long, started: Boolean)
}
