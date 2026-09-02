package com.justnoise.androidcontract

import androidx.compose.ui.graphics.Color

/**
 * Reviewed native Android mapping of the small versioned product contracts.
 *
 * The JSON files at the repository root remain authoritative. Android tests compare
 * every mapped key/value with those files so application code never parses repository
 * assets at runtime and the disposable projects cannot silently diverge.
 */
object JustnoiseContract {
    val copy: Map<String, String> = mapOf(
        "brand.displayName" to "Justnoise",
        "navigation.zap" to "Zap",
        "navigation.capture" to "Capture",
        "zap.idle.title" to "Zap to Block",
        "zap.idle.subtitle" to "Distractions",
        "zap.active.title" to "Phone Zapped",
        "zap.active.action.stop" to "Unzap Phone",
        "mode.selector.label" to "Select a mode",
        "mode.selector.empty" to "Select a Mode",
        "mode.list.title" to "Select Mode",
        "mode.list.prompt" to "What do you want to make space for?",
        "mode.list.emptyAction" to "Select apps to block",
        "schedule.list.title" to "Schedules",
        "schedule.list.empty" to "No schedules yet. Tap + to create one.",
        "shield.title" to "THIS IS JUSTNOISE",
        "shield.item.message" to "{appName} is currently Zapped.\nTap your Zap to access it.",
        "shield.application.fallbackName" to "This app",
        "shield.website.fallbackName" to "This website",
        "shield.primaryAction" to "Keep going",
        "session.complete.title" to "Session Complete",
        "session.complete.body" to "Your focus was protected.",
        "coach.basic.zap.title" to "Tap to Start/Stop",
        "coach.basic.zap.message" to "This is your Zap button. Tap to start focus, tap again to end.",
        "coach.basic.mode.title" to "Choose a Mode",
        "coach.basic.mode.message" to "Pick which apps/sites you want blocked before you start.",
        "coach.basic.schedule.title" to "Schedules",
        "coach.basic.schedule.message" to "Plan automatic focus sessions. Set start times and modes, and we’ll block for you.",
    )

    val colors: Map<String, String> = mapOf(
        "surface.zap.idle" to "#151515",
        "surface.zap.active" to "#FFFFFF",
        "surface.shield" to "#18181A",
        "text.zap.idle.primary" to "#FFFFFF",
        "text.zap.idle.secondary" to "#FFFFFF1F",
        "text.zap.active.primary" to "#000000",
        "text.zap.active.secondary" to "#00000029",
        "text.shield.primary" to "#FFFFFF",
        "text.shield.secondary" to "#C7C7C7",
        "action.zap.stop.background" to "#FFFF00E6",
        "action.zap.stop.label" to "#000000",
        "accent.noiseRewind" to "#DDFF00",
        "history.dot.empty" to "#EBEBEB",
        "history.dot.light" to "#C0C0C0",
        "history.dot.medium" to "#777777",
        "history.dot.dense" to "#191919",
        "action.shield.background" to "#FFFFFF",
        "action.shield.label" to "#0D0D0D",
    )

    private val nativeColors = colors.mapValues { (_, value) ->
        Color(parseContractColor(value).toInt())
    }

    fun text(key: String): String = copy.getValue(key)

    fun color(key: String): Color = nativeColors.getValue(key)
}

enum class ShieldTargetKind {
    Application,
    Website,
}

fun renderShieldMessage(
    displayName: String?,
    targetKind: ShieldTargetKind = ShieldTargetKind.Application,
): String {
    val fallbackKey = when (targetKind) {
        ShieldTargetKind.Application -> "shield.application.fallbackName"
        ShieldTargetKind.Website -> "shield.website.fallbackName"
    }
    val resolvedName = displayName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: JustnoiseContract.text(fallbackKey)

    return JustnoiseContract.text("shield.item.message")
        .replace("{appName}", resolvedName)
}

/** Converts contract #RRGGBB or trailing-alpha #RRGGBBAA into Compose ARGB. */
fun parseContractColor(raw: String): Long {
    require(raw.startsWith('#')) { "Contract colors must begin with #" }
    val hex = raw.drop(1)
    require(hex.length == 6 || hex.length == 8) {
        "Contract colors must use #RRGGBB or #RRGGBBAA"
    }
    require(hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
        "Contract colors must contain hexadecimal digits"
    }

    val red = hex.substring(0, 2).toLong(16)
    val green = hex.substring(2, 4).toLong(16)
    val blue = hex.substring(4, 6).toLong(16)
    val alpha = if (hex.length == 8) hex.substring(6, 8).toLong(16) else 0xFF
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}
