package com.justnoise.uiprototype

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class StartupPreviewPhase {
    AppHydration,
    AccountCleanup,
    AccountCleanupBlocked,
    AuthResolution,
}

enum class StartupPreviewRequest {
    None,
    RetryAccountCleanup,
}

data class StartupBoundaryUiState(
    val phase: StartupPreviewPhase,
    val request: StartupPreviewRequest = StartupPreviewRequest.None,
)

sealed interface StartupBoundaryAction {
    data object RetryAccountCleanup : StartupBoundaryAction
    data object RequestHandled : StartupBoundaryAction
}

object StartupBoundaryReducer {
    fun reduce(
        state: StartupBoundaryUiState,
        action: StartupBoundaryAction,
    ): StartupBoundaryUiState = when (action) {
        StartupBoundaryAction.RetryAccountCleanup -> if (
            state.phase == StartupPreviewPhase.AccountCleanupBlocked
        ) {
            state.copy(request = StartupPreviewRequest.RetryAccountCleanup)
        } else {
            state
        }
        StartupBoundaryAction.RequestHandled -> state.copy(
            request = StartupPreviewRequest.None,
        )
    }
}

private val StartupBoundaryStateSaver =
    androidx.compose.runtime.saveable.mapSaver<StartupBoundaryUiState>(
        save = { state ->
            mapOf(
                "phase" to state.phase.name,
                "request" to state.request.name,
            )
        },
        restore = { saved ->
            StartupBoundaryUiState(
                phase = StartupPreviewPhase.valueOf(saved["phase"] as String),
                request = StartupPreviewRequest.valueOf(saved["request"] as String),
            )
        },
    )

enum class PasswordUpdateVisualState {
    Editing,
    Submitting,
    Success,
    Failure,
}

enum class PasswordUpdatePreviewRequest {
    None,
    SubmitPassword,
    Dismiss,
}

data class PasswordUpdateUiState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val hasResetToken: Boolean = true,
    val visualState: PasswordUpdateVisualState = PasswordUpdateVisualState.Editing,
    val statusMessage: String? = null,
    val request: PasswordUpdatePreviewRequest = PasswordUpdatePreviewRequest.None,
)

sealed interface PasswordUpdateAction {
    data class ChangeNewPassword(val value: String) : PasswordUpdateAction
    data class ChangeConfirmation(val value: String) : PasswordUpdateAction
    data object Submit : PasswordUpdateAction
    data object ResolveSuccess : PasswordUpdateAction
    data class ResolveFailure(val message: String) : PasswordUpdateAction
    data object Dismiss : PasswordUpdateAction
    data object RequestHandled : PasswordUpdateAction
}

object PasswordUpdateReducer {
    fun reduce(
        state: PasswordUpdateUiState,
        action: PasswordUpdateAction,
    ): PasswordUpdateUiState = when (action) {
        is PasswordUpdateAction.ChangeNewPassword -> state.copy(
            newPassword = action.value,
            visualState = PasswordUpdateVisualState.Editing,
            statusMessage = null,
        )
        is PasswordUpdateAction.ChangeConfirmation -> state.copy(
            confirmPassword = action.value,
            visualState = PasswordUpdateVisualState.Editing,
            statusMessage = null,
        )
        PasswordUpdateAction.Submit -> validateSubmission(state)
        PasswordUpdateAction.ResolveSuccess -> if (
            state.request == PasswordUpdatePreviewRequest.SubmitPassword
        ) {
            state.copy(
                visualState = PasswordUpdateVisualState.Success,
                statusMessage = "Your password has been updated successfully.",
                request = PasswordUpdatePreviewRequest.None,
            )
        } else {
            state
        }
        is PasswordUpdateAction.ResolveFailure -> if (
            state.request == PasswordUpdatePreviewRequest.SubmitPassword
        ) {
            state.copy(
                visualState = PasswordUpdateVisualState.Failure,
                statusMessage = action.message,
                request = PasswordUpdatePreviewRequest.None,
            )
        } else {
            state
        }
        PasswordUpdateAction.Dismiss -> state.copy(
            request = PasswordUpdatePreviewRequest.Dismiss,
        )
        PasswordUpdateAction.RequestHandled -> state.copy(
            request = PasswordUpdatePreviewRequest.None,
        )
    }

    private fun validateSubmission(state: PasswordUpdateUiState): PasswordUpdateUiState = when {
        state.newPassword.isEmpty() || state.confirmPassword.isEmpty() -> state.copy(
            visualState = PasswordUpdateVisualState.Failure,
            statusMessage = "Please fill in all fields.",
            request = PasswordUpdatePreviewRequest.None,
        )
        state.newPassword != state.confirmPassword -> state.copy(
            visualState = PasswordUpdateVisualState.Failure,
            statusMessage = "Passwords do not match.",
            request = PasswordUpdatePreviewRequest.None,
        )
        state.newPassword.length < 6 -> state.copy(
            visualState = PasswordUpdateVisualState.Failure,
            statusMessage = "Password is too weak (min 6 chars).",
            request = PasswordUpdatePreviewRequest.None,
        )
        !state.hasResetToken -> state.copy(
            visualState = PasswordUpdateVisualState.Failure,
            statusMessage = "Invalid reset token.",
            request = PasswordUpdatePreviewRequest.None,
        )
        else -> state.copy(
            visualState = PasswordUpdateVisualState.Submitting,
            statusMessage = null,
            request = PasswordUpdatePreviewRequest.SubmitPassword,
        )
    }
}

private val PasswordUpdateStateSaver =
    androidx.compose.runtime.saveable.mapSaver<PasswordUpdateUiState>(
        save = { state ->
            mapOf(
                "new" to state.newPassword,
                "confirm" to state.confirmPassword,
                "hasToken" to state.hasResetToken,
                "visualState" to state.visualState.name,
                "status" to state.statusMessage,
                "request" to state.request.name,
            )
        },
        restore = { saved ->
            PasswordUpdateUiState(
                newPassword = saved["new"] as String,
                confirmPassword = saved["confirm"] as String,
                hasResetToken = saved["hasToken"] as Boolean,
                visualState = PasswordUpdateVisualState.valueOf(saved["visualState"] as String),
                statusMessage = saved["status"] as? String,
                request = PasswordUpdatePreviewRequest.valueOf(saved["request"] as String),
            )
        },
    )

@Composable
fun StartupBoundaryScene(initialPhase: StartupPreviewPhase) {
    var state by rememberSaveable(initialPhase, stateSaver = StartupBoundaryStateSaver) {
        mutableStateOf(StartupBoundaryUiState(initialPhase))
    }
    val dispatch: (StartupBoundaryAction) -> Unit = { action ->
        state = StartupBoundaryReducer.reduce(state, action)
    }

    UseDarkSystemBars()

    when (state.phase) {
        StartupPreviewPhase.AppHydration -> StartupProgressScreen("Loading Justnoise…")
        StartupPreviewPhase.AccountCleanup -> StartupProgressScreen(
            "Finishing account cleanup…",
        )
        StartupPreviewPhase.AuthResolution -> StartupProgressScreen("Loading Justnoise…")
        StartupPreviewPhase.AccountCleanupBlocked -> AccountCleanupBlockedScreen(
            request = state.request,
            onRetry = { dispatch(StartupBoundaryAction.RetryAccountCleanup) },
        )
    }
}

@Composable
private fun StartupProgressScreen(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun AccountCleanupBlockedScreen(
    request: StartupPreviewRequest,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Finishing account cleanup",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Account cleanup must finish before Justnoise can continue.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 16.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2176D2),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Retry", fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = when (request) {
                    StartupPreviewRequest.None ->
                        "UI preview only · account data is not loaded"
                    StartupPreviewRequest.RetryAccountCleanup ->
                        "Retry previewed · the cleanup gate remains closed"
                },
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
fun PasswordUpdateScene(initialState: PasswordUpdateUiState = PasswordUpdateUiState()) {
    var state by rememberSaveable(initialState, stateSaver = PasswordUpdateStateSaver) {
        mutableStateOf(initialState)
    }
    val dispatch: (PasswordUpdateAction) -> Unit = { action ->
        state = PasswordUpdateReducer.reduce(state, action)
    }

    UseDarkSystemBars()
    BackHandler { dispatch(PasswordUpdateAction.Dismiss) }

    if (state.request == PasswordUpdatePreviewRequest.Dismiss) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Password reset closed",
                color = Color.White.copy(alpha = 0.72f),
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF151515))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        PasswordUpdateTopBar(onCancel = { dispatch(PasswordUpdateAction.Dismiss) })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "Reset Your Password",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            PasswordUpdateField(
                value = state.newPassword,
                placeholder = "New Password",
                enabled = state.visualState != PasswordUpdateVisualState.Submitting,
                onValueChange = { dispatch(PasswordUpdateAction.ChangeNewPassword(it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            PasswordUpdateField(
                value = state.confirmPassword,
                placeholder = "Confirm New Password",
                enabled = state.visualState != PasswordUpdateVisualState.Submitting,
                onValueChange = { dispatch(PasswordUpdateAction.ChangeConfirmation(it)) },
            )
            state.statusMessage?.let { message ->
                Text(
                    text = message,
                    color = if (state.visualState == PasswordUpdateVisualState.Success) {
                        Color(0xFF72D58B)
                    } else {
                        Color(0xFFFF7B73)
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 18.dp),
                )
            } ?: Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (state.visualState == PasswordUpdateVisualState.Success) {
                        dispatch(PasswordUpdateAction.Dismiss)
                    } else {
                        dispatch(PasswordUpdateAction.Submit)
                    }
                },
                enabled = state.visualState != PasswordUpdateVisualState.Submitting,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2176D2),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF2176D2).copy(alpha = 0.72f),
                    disabledContentColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (state.visualState == PasswordUpdateVisualState.Submitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Text(
                        text = if (state.visualState == PasswordUpdateVisualState.Success) {
                            "Done"
                        } else {
                            "Update Password"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                text = when (state.request) {
                    PasswordUpdatePreviewRequest.SubmitPassword ->
                        "Update request previewed · no account change was sent"
                    else -> "UI preview only · no account change is sent"
                },
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun PasswordUpdateTopBar(onCancel: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) {
            Text("Cancel", color = Color(0xFF5EA6F4))
        }
        Text(
            text = "Reset Password",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun PasswordUpdateField(
    value: String,
    placeholder: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.46f)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default,
        visualTransformation = PasswordVisualTransformation(),
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color.White.copy(alpha = 0.72f),
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
            disabledContainerColor = Color.White.copy(alpha = 0.08f),
            cursorColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun UseDarkSystemBars() {
    val activity = LocalActivity.current
    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
}

@Preview(name = "Startup · loading", widthDp = 412, heightDp = 915)
@Composable
private fun StartupLoadingPreview() {
    JustnoiseTheme { StartupBoundaryScene(StartupPreviewPhase.AppHydration) }
}

@Preview(name = "Startup · cleanup blocked", widthDp = 412, heightDp = 915)
@Composable
private fun StartupCleanupBlockedPreview() {
    JustnoiseTheme { StartupBoundaryScene(StartupPreviewPhase.AccountCleanupBlocked) }
}

@Preview(name = "Password update · editing", widthDp = 412, heightDp = 915)
@Composable
private fun PasswordUpdateEditingPreview() {
    JustnoiseTheme { PasswordUpdateScene() }
}

@Preview(name = "Password update · success", widthDp = 412, heightDp = 915)
@Composable
private fun PasswordUpdateSuccessPreview() {
    JustnoiseTheme {
        PasswordUpdateScene(
            PasswordUpdateUiState(
                newPassword = "focus123",
                confirmPassword = "focus123",
                visualState = PasswordUpdateVisualState.Success,
                statusMessage = "Your password has been updated successfully.",
            ),
        )
    }
}
