package com.justnoise.uiprototype

import androidx.compose.ui.graphics.Color

/**
 * Native mapping of the small shared product contracts used by this prototype.
 * The JSON remains the source of truth; tests reject mapping drift.
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
        "action.shield.background" to "#FFFFFF",
        "action.shield.label" to "#0D0D0D",
    )

    fun text(key: String): String = copy.getValue(key)

    fun color(key: String): Color = Color(parseContractColor(colors.getValue(key)).toInt())
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

data class ModeOption(
    val id: String,
    val name: String,
)

data class ZapUiState(
    val isActive: Boolean = false,
    val selectedMode: ModeOption = PreviewModes.first(),
    val elapsedText: String = "00:25:00",
)

sealed interface ZapPreviewAction {
    data object ToggleSession : ZapPreviewAction
    data class SelectMode(val mode: ModeOption) : ZapPreviewAction
}

object ZapPreviewReducer {
    fun reduce(state: ZapUiState, action: ZapPreviewAction): ZapUiState = when (action) {
        ZapPreviewAction.ToggleSession -> state.copy(isActive = !state.isActive)
        is ZapPreviewAction.SelectMode -> state.copy(selectedMode = action.mode)
    }
}

val PreviewModes = listOf(
    ModeOption(id = "deep-work", name = "Deep Work"),
    ModeOption(id = "study", name = "Study"),
    ModeOption(id = "offline", name = "Offline"),
)

enum class PrototypeRoute {
    Onboarding,
    Activation,
    SignIn,
    SignUp,
    Zap,
}

data class PrototypeFlowState(
    val route: PrototypeRoute = PrototypeRoute.Onboarding,
    val onboardingPage: Int = 0,
)

sealed interface PrototypeFlowAction {
    data class ShowOnboardingPage(val page: Int) : PrototypeFlowAction
    data object AdvanceOnboarding : PrototypeFlowAction
    data object CompleteActivationPreview : PrototypeFlowAction
    data object OpenSignUp : PrototypeFlowAction
    data object OpenSignIn : PrototypeFlowAction
    data object PreviewAuthenticated : PrototypeFlowAction
    data object Back : PrototypeFlowAction
    data object Reset : PrototypeFlowAction
}

object PrototypeFlowReducer {
    fun reduce(
        state: PrototypeFlowState,
        action: PrototypeFlowAction,
    ): PrototypeFlowState = when (action) {
        is PrototypeFlowAction.ShowOnboardingPage -> state.copy(
            onboardingPage = action.page.coerceIn(0, 1),
        )
        PrototypeFlowAction.AdvanceOnboarding -> when (state.onboardingPage) {
            0 -> state.copy(onboardingPage = 1)
            else -> PrototypeFlowState(route = PrototypeRoute.Activation)
        }
        PrototypeFlowAction.CompleteActivationPreview -> PrototypeFlowState(
            route = PrototypeRoute.SignIn,
        )
        PrototypeFlowAction.OpenSignUp -> state.copy(route = PrototypeRoute.SignUp)
        PrototypeFlowAction.OpenSignIn -> state.copy(route = PrototypeRoute.SignIn)
        PrototypeFlowAction.PreviewAuthenticated -> PrototypeFlowState(
            route = PrototypeRoute.Zap,
        )
        PrototypeFlowAction.Back -> when (state.route) {
            PrototypeRoute.Onboarding -> state.copy(
                onboardingPage = (state.onboardingPage - 1).coerceAtLeast(0),
            )
            PrototypeRoute.Activation -> PrototypeFlowState(
                route = PrototypeRoute.Onboarding,
                onboardingPage = 1,
            )
            PrototypeRoute.SignIn -> PrototypeFlowState(route = PrototypeRoute.Activation)
            PrototypeRoute.SignUp -> PrototypeFlowState(route = PrototypeRoute.SignIn)
            PrototypeRoute.Zap -> state
        }
        PrototypeFlowAction.Reset -> PrototypeFlowState()
    }
}
