package com.justnoise.gate0.data

/** Receipt for a newly committed stop, not proof of SQLite delivery or continuous blocking. */
data class SessionStopResult(
    val succeeded: Boolean,
    val completion: SessionHistoryEntry? = null,
)
