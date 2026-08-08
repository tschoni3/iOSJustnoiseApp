package com.justnoise.uiprototype

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class RestrictionCapabilityPreview(val contractValue: String) {
    Available("available"),
    PermissionRequired("permissionRequired"),
    PermissionDenied("permissionDenied"),
    TemporarilyUnavailable("temporarilyUnavailable"),
    Unsupported("unsupported"),
}

enum class RecoveryRequest {
    None,
    OpenSettings,
    Retry,
}

data class CapabilityRecoveryUiState(
    val capability: RestrictionCapabilityPreview,
    val request: RecoveryRequest = RecoveryRequest.None,
    val isDismissed: Boolean = false,
)

sealed interface CapabilityRecoveryAction {
    data object Primary : CapabilityRecoveryAction
    data object Dismiss : CapabilityRecoveryAction
    data object RequestHandled : CapabilityRecoveryAction
}

object CapabilityRecoveryReducer {
    fun reduce(
        state: CapabilityRecoveryUiState,
        action: CapabilityRecoveryAction,
    ): CapabilityRecoveryUiState = when (action) {
        CapabilityRecoveryAction.Primary -> when (state.capability) {
            RestrictionCapabilityPreview.PermissionRequired,
            RestrictionCapabilityPreview.PermissionDenied,
            -> state.copy(request = RecoveryRequest.OpenSettings)

            RestrictionCapabilityPreview.TemporarilyUnavailable ->
                state.copy(request = RecoveryRequest.Retry)

            RestrictionCapabilityPreview.Available,
            RestrictionCapabilityPreview.Unsupported,
            -> state.copy(isDismissed = true)
        }
        CapabilityRecoveryAction.Dismiss -> state.copy(isDismissed = true)
        CapabilityRecoveryAction.RequestHandled -> state.copy(request = RecoveryRequest.None)
    }
}

private val CapabilityRecoveryStateSaver =
    androidx.compose.runtime.saveable.mapSaver<CapabilityRecoveryUiState>(
        save = { state ->
            mapOf(
                "capability" to state.capability.name,
                "request" to state.request.name,
                "dismissed" to state.isDismissed,
            )
        },
        restore = { saved ->
            CapabilityRecoveryUiState(
                capability = RestrictionCapabilityPreview.valueOf(saved["capability"] as String),
                request = RecoveryRequest.valueOf(saved["request"] as String),
                isDismissed = saved["dismissed"] as Boolean,
            )
        },
    )

private data class CapabilityRecoveryPresentation(
    val title: String,
    val body: String,
    val primaryAction: String,
    val secondaryAction: String?,
)

private fun RestrictionCapabilityPreview.presentation(): CapabilityRecoveryPresentation? =
    when (this) {
        RestrictionCapabilityPreview.Available -> null
        RestrictionCapabilityPreview.PermissionRequired -> CapabilityRecoveryPresentation(
            title = "Permission required",
            body = "Android needs your permission before Justnoise can protect selected apps. Your phone is not Zapped.",
            primaryAction = "Review settings",
            secondaryAction = "Not now",
        )
        RestrictionCapabilityPreview.PermissionDenied -> CapabilityRecoveryPresentation(
            title = "Protection is off",
            body = "App protection permission was turned off. Your phone is not Zapped.",
            primaryAction = "Review settings",
            secondaryAction = "Back",
        )
        RestrictionCapabilityPreview.TemporarilyUnavailable -> CapabilityRecoveryPresentation(
            title = "Protection interrupted",
            body = "Justnoise cannot confirm app protection right now. Your phone is not Zapped.",
            primaryAction = "Try again",
            secondaryAction = "Back",
        )
        RestrictionCapabilityPreview.Unsupported -> CapabilityRecoveryPresentation(
            title = "App blocking unavailable",
            body = "This device cannot provide reliable app protection. No apps were blocked.",
            primaryAction = "Back",
            secondaryAction = null,
        )
    }

@Composable
fun CapabilityRecoveryScene(initialCapability: RestrictionCapabilityPreview) {
    var state by rememberSaveable(initialCapability, stateSaver = CapabilityRecoveryStateSaver) {
        mutableStateOf(CapabilityRecoveryUiState(initialCapability))
    }
    val dispatch: (CapabilityRecoveryAction) -> Unit = { action ->
        state = CapabilityRecoveryReducer.reduce(state, action)
    }

    BackHandler(enabled = !state.isDismissed) {
        dispatch(CapabilityRecoveryAction.Dismiss)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ZapScreen(
            state = ZapUiState(),
            onZapClick = {},
            onModeClick = {},
            onSchedulesClick = {},
            onHistoryClick = {},
        )

        if (!state.isDismissed) {
            state.capability.presentation()?.let { presentation ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.56f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CapabilityRecoveryCard(
                        presentation = presentation,
                        request = state.request,
                        onPrimary = { dispatch(CapabilityRecoveryAction.Primary) },
                        onDismiss = { dispatch(CapabilityRecoveryAction.Dismiss) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityRecoveryCard(
    presentation: CapabilityRecoveryPresentation,
    request: RecoveryRequest,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(30.dp, RoundedCornerShape(28.dp)),
        color = Color(0xFF242424),
        contentColor = Color.White,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = Color(0xFFFFD76A).copy(alpha = 0.14f),
                contentColor = Color(0xFFFFD76A),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = "NOT ZAPPED",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = presentation.title,
                fontSize = 27.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = presentation.body,
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 16.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(27.dp),
            ) {
                Text(
                    text = presentation.primaryAction,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            presentation.secondaryAction?.let { title ->
                TextButton(onClick = onDismiss) {
                    Text(title, color = Color.White.copy(alpha = 0.62f))
                }
            }
            Text(
                text = when (request) {
                    RecoveryRequest.None -> "UI preview only · no permission or service was requested"
                    RecoveryRequest.OpenSettings -> "Settings request previewed · capability remains unavailable"
                    RecoveryRequest.Retry -> "Retry previewed · capability remains unconfirmed"
                },
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun BlockedSurfacePrototype(
    displayName: String?,
    targetKind: ShieldTargetKind = ShieldTargetKind.Application,
) {
    val activity = LocalActivity.current
    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JustnoiseContract.color("surface.shield"))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.86f))
        Image(
            painter = painterResource(R.drawable.zap_button),
            contentDescription = "Justnoise Zap",
            modifier = Modifier.size(118.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = JustnoiseContract.text("shield.title"),
            color = JustnoiseContract.color("text.shield.primary"),
            fontSize = 29.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = renderShieldMessage(displayName, targetKind),
            color = JustnoiseContract.color("text.shield.secondary"),
            fontSize = 21.sp,
            lineHeight = 29.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.weight(1.15f))
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = JustnoiseContract.color("action.shield.background"),
                contentColor = JustnoiseContract.color("action.shield.label"),
            ),
            shape = RoundedCornerShape(30.dp),
        ) {
            Text(
                text = JustnoiseContract.text("shield.primaryAction"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview(name = "Blocked surface · app", widthDp = 412, heightDp = 915)
@Composable
private fun BlockedSurfacePreview() {
    JustnoiseTheme { BlockedSurfacePrototype(displayName = "Instagram") }
}

@Preview(name = "Blocked surface · long name", widthDp = 412, heightDp = 915)
@Composable
private fun BlockedSurfaceLongNamePreview() {
    JustnoiseTheme {
        BlockedSurfacePrototype(displayName = "A Very Long Social Media Application Name")
    }
}

@Preview(name = "Recovery · permission denied", widthDp = 412, heightDp = 915)
@Composable
private fun PermissionDeniedPreview() {
    JustnoiseTheme {
        CapabilityRecoveryScene(RestrictionCapabilityPreview.PermissionDenied)
    }
}

@Preview(name = "Recovery · unavailable", widthDp = 412, heightDp = 915)
@Composable
private fun TemporarilyUnavailablePreview() {
    JustnoiseTheme {
        CapabilityRecoveryScene(RestrictionCapabilityPreview.TemporarilyUnavailable)
    }
}
