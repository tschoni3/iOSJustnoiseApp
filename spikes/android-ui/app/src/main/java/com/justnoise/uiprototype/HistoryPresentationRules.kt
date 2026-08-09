package com.justnoise.uiprototype

enum class PortableHistoryDensity(val contractValue: String) {
    EMPTY("empty"),
    LIGHT("light"),
    MEDIUM("medium"),
    DENSE("dense"),
}

enum class PortableHistoryDurationStyle(val contractValue: String) {
    SUMMARY("summary"),
    DAY_DETAIL("dayDetail"),
}

data class PortableHistorySummary(
    val zapCount: Int,
    val totalSeconds: Double,
    val protectedAttentionLabel: String,
)

object PortableHistoryRules {
    const val LIGHT_MINIMUM_SECONDS = 15.0 * 60.0
    const val MEDIUM_MINIMUM_SECONDS = 120.0 * 60.0
    const val DENSE_MINIMUM_SECONDS = 360.0 * 60.0

    fun density(totalSeconds: Double): PortableHistoryDensity {
        val seconds = normalizedDuration(totalSeconds)
        return when {
            seconds >= DENSE_MINIMUM_SECONDS -> PortableHistoryDensity.DENSE
            seconds >= MEDIUM_MINIMUM_SECONDS -> PortableHistoryDensity.MEDIUM
            seconds >= LIGHT_MINIMUM_SECONDS -> PortableHistoryDensity.LIGHT
            else -> PortableHistoryDensity.EMPTY
        }
    }

    fun formatDuration(
        totalSeconds: Double,
        style: PortableHistoryDurationStyle,
    ): String {
        val seconds = normalizedDuration(totalSeconds)
        val totalMinutes = seconds.toLong() / 60L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L

        if (hours > 0L) {
            return if (minutes > 0L) "${hours}h ${minutes}m" else "${hours}h"
        }
        if (
            style == PortableHistoryDurationStyle.DAY_DETAIL &&
            seconds > 0.0 &&
            totalMinutes == 0L
        ) {
            return "<1m"
        }
        return "${minutes}m"
    }

    fun summarize(durationsSeconds: Iterable<Double>): PortableHistorySummary {
        var zapCount = 0
        var totalSeconds = 0.0
        for (duration in durationsSeconds) {
            zapCount += 1
            totalSeconds += normalizedDuration(duration)
        }
        return PortableHistorySummary(
            zapCount = zapCount,
            totalSeconds = totalSeconds,
            protectedAttentionLabel = formatDuration(
                totalSeconds = totalSeconds,
                style = PortableHistoryDurationStyle.SUMMARY,
            ),
        )
    }

    private fun normalizedDuration(duration: Double): Double =
        if (duration.isFinite() && duration > 0.0) duration else 0.0
}
