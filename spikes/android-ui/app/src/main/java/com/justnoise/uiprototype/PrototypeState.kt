package com.justnoise.uiprototype

data class ModeOption(
    val id: String,
    val name: String,
)

data class ZapUiState(
    val isActive: Boolean = false,
    val selectedMode: ModeOption = PreviewModes.first(),
    val elapsedText: String = "00:25:00",
    val isCompletionVisible: Boolean = false,
)

sealed interface ZapPreviewAction {
    data object ToggleSession : ZapPreviewAction
    data class SelectMode(val mode: ModeOption) : ZapPreviewAction
    data object DismissCompletion : ZapPreviewAction
}

object ZapPreviewReducer {
    fun reduce(state: ZapUiState, action: ZapPreviewAction): ZapUiState = when (action) {
        ZapPreviewAction.ToggleSession -> if (state.isActive) {
            state.copy(isActive = false, isCompletionVisible = true)
        } else {
            state.copy(isActive = true, isCompletionVisible = false)
        }
        is ZapPreviewAction.SelectMode -> state.copy(selectedMode = action.mode)
        ZapPreviewAction.DismissCompletion -> state.copy(isCompletionVisible = false)
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
    Modes,
    History,
    Schedules,
    Settings,
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
    data object OpenModes : PrototypeFlowAction
    data object OpenHistory : PrototypeFlowAction
    data object OpenSchedules : PrototypeFlowAction
    data object OpenSettings : PrototypeFlowAction
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
        PrototypeFlowAction.OpenModes -> state.copy(route = PrototypeRoute.Modes)
        PrototypeFlowAction.OpenHistory -> state.copy(route = PrototypeRoute.History)
        PrototypeFlowAction.OpenSchedules -> state.copy(route = PrototypeRoute.Schedules)
        PrototypeFlowAction.OpenSettings -> state.copy(route = PrototypeRoute.Settings)
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
            PrototypeRoute.Modes,
            PrototypeRoute.History,
            PrototypeRoute.Schedules,
            -> PrototypeFlowState(route = PrototypeRoute.Zap)
            PrototypeRoute.Settings -> PrototypeFlowState(route = PrototypeRoute.History)
        }
        PrototypeFlowAction.Reset -> PrototypeFlowState()
    }
}
