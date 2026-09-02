package com.justnoise.integrated

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.justnoise.androidcontract.JustnoiseContract
import com.justnoise.gate0.R
import com.justnoise.gate0.feature.Gate0Truth
import kotlin.math.max

enum class IntegratedZapAction {
    START_SCAN,
    STOP_SCAN,
    OPEN_SETUP,
}

data class IntegratedZapState(
    val truth: Gate0Truth = Gate0Truth.CONSENT_REQUIRED,
    val selectedAppName: String? = null,
    val elapsedText: String = "00:00:00",
    val desiredActive: Boolean = false,
    val isScanning: Boolean = false,
    val transientStatus: String? = null,
) {
    val isActive: Boolean
        get() = truth == Gate0Truth.ACTIVE

    val primaryAction: IntegratedZapAction
        get() = when (truth) {
            Gate0Truth.IDLE -> IntegratedZapAction.START_SCAN
            Gate0Truth.ACTIVE -> IntegratedZapAction.STOP_SCAN
            Gate0Truth.RECOVERY_REQUIRED -> if (desiredActive) {
                IntegratedZapAction.STOP_SCAN
            } else {
                IntegratedZapAction.OPEN_SETUP
            }
            else -> IntegratedZapAction.OPEN_SETUP
        }

    val canScan: Boolean
        get() = primaryAction != IntegratedZapAction.OPEN_SETUP

    val actionLabel: String
        get() = if (isScanning) {
            "Hold Zap near phone"
        } else {
            when (primaryAction) {
                IntegratedZapAction.START_SCAN -> "Scan Zap to start"
                IntegratedZapAction.STOP_SCAN -> "Scan Zap to stop"
                IntegratedZapAction.OPEN_SETUP -> "Open setup"
            }
        }
}

@Composable
fun JustnoiseIntegratedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = JustnoiseContract.color("accent.noiseRewind"),
            surface = JustnoiseContract.color("surface.zap.idle"),
            onSurface = JustnoiseContract.color("text.zap.idle.primary"),
        ),
        content = content,
    )
}

@Composable
fun IntegratedZapSurface(
    state: IntegratedZapState,
    onZap: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = JustnoiseContract.color(
            if (state.isActive) "surface.zap.active" else "surface.zap.idle",
        ),
        label = "integrated-zap-background",
    )
    val foreground by animateColorAsState(
        targetValue = JustnoiseContract.color(
            if (state.isActive) "text.zap.active.primary" else "text.zap.idle.primary",
        ),
        label = "integrated-zap-foreground",
    )
    val activity = androidx.activity.compose.LocalActivity.current
    LaunchedEffect(state.isActive) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = state.isActive
                isAppearanceLightNavigationBars = state.isActive
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DevelopmentToolbar(
                foreground = foreground,
                onOpenSetup = onOpenSetup,
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val heroHeight = 126.dp
                val buttonSlot = 240.dp
                val stateSlot = 82.dp
                val bottomReserved = 176.dp
                val flexible = max(
                    0f,
                    (maxHeight - heroHeight - buttonSlot - stateSlot - bottomReserved).value,
                ).dp
                val buttonTop = heroHeight + (flexible * 0.5f).coerceIn(40.dp, 82.dp)
                val stateTop = buttonTop + buttonSlot + 22.dp

                HeroHeader(
                    state = state,
                    modifier = Modifier.align(Alignment.TopStart),
                )

                Image(
                    painter = painterResource(R.drawable.zap_button),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = buttonTop)
                        .size(buttonSlot)
                        .semantics {
                            contentDescription = state.actionLabel
                            role = Role.Button
                        }
                        .clickable(enabled = !state.isScanning, onClick = onZap)
                        .padding(10.dp)
                        .shadow(2.dp, CircleShape),
                )

                StateLabel(
                    state = state,
                    foreground = foreground,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = stateTop)
                        .fillMaxWidth(),
                )

                CapabilityCard(
                    state = state,
                    onZap = onZap,
                    onOpenSetup = onOpenSetup,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun DevelopmentToolbar(
    foreground: Color,
    onOpenSetup: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(modifier = Modifier.size(56.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = JustnoiseContract.text("brand.displayName").uppercase(),
                color = foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.2).sp,
            )
            Text(
                text = "ANDROID DEV",
                color = foreground.copy(alpha = 0.34f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Surface(
            onClick = onOpenSetup,
            modifier = Modifier.size(width = 56.dp, height = 36.dp),
            shape = RoundedCornerShape(18.dp),
            color = foreground.copy(alpha = 0.10f),
            contentColor = foreground,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "SETUP",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
private fun HeroHeader(
    state: IntegratedZapState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(126.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy((-8).dp),
    ) {
        Text(
            text = JustnoiseContract.text(
                if (state.isActive) "zap.active.title" else "zap.idle.title",
            ),
            color = JustnoiseContract.color(
                if (state.isActive) "text.zap.active.primary" else "text.zap.idle.primary",
            ),
            fontSize = 50.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 56.sp,
            maxLines = 1,
        )
        Text(
            text = if (state.isActive) {
                state.elapsedText
            } else {
                JustnoiseContract.text("zap.idle.subtitle")
            },
            color = JustnoiseContract.color(
                if (state.isActive) {
                    "text.zap.active.secondary"
                } else {
                    "text.zap.idle.secondary"
                },
            ),
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 54.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun StateLabel(
    state: IntegratedZapState,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = state.selectedAppName ?: "No apps selected",
            color = foreground,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = statusTitle(state.truth),
            color = foreground.copy(alpha = 0.48f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CapabilityCard(
    state: IntegratedZapState,
    onZap: () -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.isActive
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (active) Color(0xFFF2F2F2) else Color.White.copy(alpha = 0.07f),
        contentColor = if (active) Color.Black else Color.White,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.transientStatus ?: statusDetail(state.truth),
                color = if (active) Color.Black.copy(alpha = 0.62f)
                else Color.White.copy(alpha = 0.62f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Button(
                onClick = if (state.canScan) {
                    onZap
                } else {
                    onOpenSetup
                },
                enabled = !state.isScanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) {
                        JustnoiseContract.color("action.zap.stop.background")
                    } else {
                        Color.White
                    },
                    contentColor = JustnoiseContract.color("action.zap.stop.label"),
                ),
            ) {
                Text(
                    text = state.actionLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun statusTitle(truth: Gate0Truth): String = when (truth) {
    Gate0Truth.CONSENT_REQUIRED -> "Consent required"
    Gate0Truth.TARGET_REQUIRED -> "Choose apps"
    Gate0Truth.ZAP_REQUIRED -> "Pair your Zap"
    Gate0Truth.PERMISSION_REQUIRED -> "Enable Accessibility"
    Gate0Truth.IDLE -> "Ready to Zap"
    Gate0Truth.ACTIVE -> "Protection active"
    Gate0Truth.RECOVERY_REQUIRED -> "Reconnect Accessibility"
}

private fun statusDetail(truth: Gate0Truth): String = when (truth) {
    Gate0Truth.CONSENT_REQUIRED ->
        "Review the local Accessibility disclosure before enabling app blocking."

    Gate0Truth.TARGET_REQUIRED ->
        "Select the apps this development build should block."

    Gate0Truth.ZAP_REQUIRED ->
        "Pair your physical Zap once on this Android phone."

    Gate0Truth.PERMISSION_REQUIRED ->
        "Enable the Justnoise service manually in Android Accessibility settings."

    Gate0Truth.IDLE ->
        "The restriction service is ready. Open the scan control and tap your Zap."

    Gate0Truth.ACTIVE ->
        "The selected apps should be intercepted while this session is active."

    Gate0Truth.RECOVERY_REQUIRED ->
        "The app cannot prove enforcement is alive. Open setup to recover safely."
}

@Preview(name = "A16 Zap idle", widthDp = 384, heightDp = 832)
@Composable
private fun IntegratedZapIdlePreview() {
    IntegratedZapPreview(IntegratedZapState(truth = Gate0Truth.IDLE, selectedAppName = "Chrome"))
}

@Preview(name = "A16 Zap active", widthDp = 384, heightDp = 832)
@Composable
private fun IntegratedZapActivePreview() {
    IntegratedZapPreview(
        IntegratedZapState(
            truth = Gate0Truth.ACTIVE,
            selectedAppName = "Chrome",
            elapsedText = "00:25:00",
            desiredActive = true,
        ),
    )
}

@Preview(name = "A16 Zap recovery", widthDp = 384, heightDp = 832)
@Composable
private fun IntegratedZapRecoveryPreview() {
    IntegratedZapPreview(
        IntegratedZapState(
            truth = Gate0Truth.RECOVERY_REQUIRED,
            selectedAppName = "Chrome",
            desiredActive = true,
        ),
    )
}

@Composable
private fun IntegratedZapPreview(state: IntegratedZapState) {
    JustnoiseIntegratedTheme {
        IntegratedZapSurface(state = state, onZap = {}, onOpenSetup = {})
    }
}
