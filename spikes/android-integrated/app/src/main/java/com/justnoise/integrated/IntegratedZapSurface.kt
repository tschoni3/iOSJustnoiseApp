package com.justnoise.integrated

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.justnoise.androidcontract.JustnoiseContract
import com.justnoise.gate0.R
import com.justnoise.gate0.feature.Gate0Truth

enum class IntegratedZapAction { START_SCAN, STOP_SCAN, OPEN_SETUP }

data class IntegratedZapState(
    val truth: Gate0Truth = Gate0Truth.CONSENT_REQUIRED,
    val selectedAppName: String? = null,
    val elapsedText: String = "00:00:00",
    val desiredActive: Boolean = false,
    val isScanning: Boolean = false,
    val transientStatus: String? = null,
    val modeName: String? = null,
    val shortcutBusy: Boolean = false,
) {
    val isActive: Boolean get() = truth == Gate0Truth.ACTIVE
    val primaryAction: IntegratedZapAction
        get() = when (truth) {
            Gate0Truth.IDLE -> IntegratedZapAction.START_SCAN
            Gate0Truth.ACTIVE -> IntegratedZapAction.STOP_SCAN
            Gate0Truth.RECOVERY_REQUIRED -> if (desiredActive) IntegratedZapAction.STOP_SCAN else IntegratedZapAction.OPEN_SETUP
            else -> IntegratedZapAction.OPEN_SETUP
        }
    val canScan: Boolean get() = primaryAction != IntegratedZapAction.OPEN_SETUP
    val actionLabel: String
        get() = if (isScanning) "Hold Zap near phone" else when (primaryAction) {
            IntegratedZapAction.START_SCAN -> "Scan Zap to start"
            IntegratedZapAction.STOP_SCAN -> "Scan Zap to stop"
            IntegratedZapAction.OPEN_SETUP -> "Open setup"
        }
    val needsAttention: Boolean get() = truth != Gate0Truth.IDLE && truth != Gate0Truth.ACTIVE
}

/** Source-characterized iOS geometry; compact/enlarged-text layouts use a scrollable flow. */
internal data class IntegratedZapGeometry(val artworkTop: Float, val selectionTop: Float)
internal fun integratedZapGeometry(availableHeightDp: Float): IntegratedZapGeometry {
    val flexible = (availableHeightDp - 126f - 240f - 84f - 26f - 132f).coerceAtLeast(0f)
    val artworkTop = 126f + (flexible * 0.5f).coerceIn(48f, 88f)
    return IntegratedZapGeometry(artworkTop, artworkTop + 240f + 26f)
}

/** Android readability adaptation; the shared iOS characterization stays unchanged. */
internal fun integratedZapSubtitleColor(state: IntegratedZapState): Color = when {
    state.isActive -> Color(0xFF595959)
    state.truth == Gate0Truth.RECOVERY_REQUIRED && state.desiredActive -> Color.White
    else -> JustnoiseContract.color("text.zap.idle.secondary")
}

@Composable
fun JustnoiseIntegratedTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = JustnoiseContract.color("accent.noiseRewind"),
        surface = JustnoiseContract.color("surface.zap.idle"),
        onSurface = JustnoiseContract.color("text.zap.idle.primary"),
    ), content = content)
}

@Composable
fun IntegratedZapSurface(
    state: IntegratedZapState,
    onZap: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenSelection: () -> Unit,
    onCancelScan: () -> Unit,
    onLongPressZap: () -> Unit = {},
) {
    // Compose respects the system animator duration scale; no independent animation loop.
    val background by animateColorAsState(JustnoiseContract.color(
        if (state.isActive) "surface.zap.active" else "surface.zap.idle"), label = "zap-background")
    val foreground by animateColorAsState(JustnoiseContract.color(
        if (state.isActive) "text.zap.active.primary" else "text.zap.idle.primary"), label = "zap-foreground")
    val activity = LocalActivity.current
    LaunchedEffect(state.isActive) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = state.isActive
                isAppearanceLightNavigationBars = state.isActive
            }
        }
    }
    Column(Modifier.fillMaxSize().background(background)
        .windowInsetsPadding(WindowInsets.safeDrawing).testTag("zap-main")) {
        ZapToolbar(foreground, showActions = !state.isActive, onOpenSchedules, onOpenHistory)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val flowLayout = maxHeight < 660.dp || LocalDensity.current.fontScale > 1.15f ||
                state.needsAttention || (state.transientStatus != null && !state.isScanning)
            if (flowLayout) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    HeroHeader(state, scalable = true)
                    Spacer(Modifier.height(48.dp))
                    ZapArtwork(state, onZap, onLongPressZap = onLongPressZap)
                    Spacer(Modifier.height(26.dp))
                    SelectionSlot(state, onOpenSelection)
                    Spacer(Modifier.height(32.dp))
                    ZapActions(state, foreground, onZap, onOpenSetup)
                }
            } else {
                val geometry = integratedZapGeometry(maxHeight.value)
                HeroHeader(state, scalable = false)
                ZapArtwork(state, onZap, Modifier.align(Alignment.TopCenter).offset(y = geometry.artworkTop.dp), onLongPressZap)
                SelectionSlot(state, onOpenSelection,
                    Modifier.align(Alignment.TopCenter).offset(y = geometry.selectionTop.dp))
                ZapActions(state, foreground, onZap, onOpenSetup,
                    Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    if (state.isScanning) IntegratedNfcScanDialog(onCancelScan)
}

@Composable
private fun ZapToolbar(foreground: Color, showActions: Boolean, onSchedules: () -> Unit, onHistory: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp)) {
        Image(painterResource(R.drawable.justnoise_logo_nav), JustnoiseContract.text("brand.displayName"),
            Modifier.align(Alignment.Center).height(15.dp).widthIn(max = 156.dp),
            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(foreground))
        if (showActions) {
            ToolbarAction(stringResource(R.string.zap_schedules_accessibility), foreground, false, onSchedules,
                Modifier.align(Alignment.CenterStart))
        }
        // History -> Settings -> Emergency Unzap remains reachable during a session.
        ToolbarAction(stringResource(R.string.zap_history_accessibility), foreground, true, onHistory,
            Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun ToolbarAction(label: String, color: Color, history: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(23.dp)) {
            if (history) {
                val radius = size.width * 0.18f
                for (x in listOf(0.25f, 0.75f)) for (y in listOf(0.25f, 0.75f)) {
                    drawCircle(color, radius, Offset(size.width * x, size.height * y))
                }
            } else {
                val stroke = 1.7.dp.toPx()
                drawRoundRect(color, Offset(size.width * 0.08f, size.height * 0.18f),
                    Size(size.width * 0.84f, size.height * 0.76f), CornerRadius(2.dp.toPx()), style = Stroke(stroke))
                drawLine(color, Offset(size.width * 0.08f, size.height * 0.39f),
                    Offset(size.width * 0.92f, size.height * 0.39f), stroke)
                for (x in listOf(0.3f, 0.7f)) drawLine(color,
                    Offset(size.width * x, 0f), Offset(size.width * x, size.height * 0.26f), stroke, StrokeCap.Round)
                for (x in listOf(0.3f, 0.5f, 0.7f)) for (y in listOf(0.57f, 0.77f)) {
                    drawCircle(color, 1.dp.toPx(), Offset(size.width * x, size.height * y))
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(state: IntegratedZapState, scalable: Boolean) {
    val title = JustnoiseContract.text(
        if (state.isActive) "zap.active.title" else "zap.idle.title")
    val subtitle = if (state.isActive) state.elapsedText else JustnoiseContract.text("zap.idle.subtitle")
    val foreground = JustnoiseContract.color(if (state.isActive) "text.zap.active.primary" else "text.zap.idle.primary")
    val secondary = integratedZapSubtitleColor(state)
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (scalable) Modifier else Modifier.height(126.dp))
        .padding(horizontal = 24.dp).padding(top = 8.dp)) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        val measurer = rememberTextMeasurer()
        fun fittedSize(text: String, base: Float, weight: FontWeight, minimum: Float): Float {
            if (scalable) return base
            val measured = measurer.measure(text, TextStyle(fontSize = base.sp, fontWeight = weight)).size.width
            return base * (width / measured.coerceAtLeast(1)).coerceIn(minimum, 1f)
        }
        Column(verticalArrangement = Arrangement.spacedBy((-8).dp)) {
            Text(title, color = foreground, fontWeight = FontWeight.ExtraBold,
                fontSize = fittedSize(title, 50f, FontWeight.ExtraBold, 0.7f).sp,
                lineHeight = 58.sp, modifier = Modifier.semantics { heading() })
            Text(subtitle, color = secondary, fontWeight = FontWeight.Black,
                fontSize = fittedSize(subtitle, 48f, FontWeight.Black, 0.72f).sp, lineHeight = 56.sp)
        }
    }
}

@Composable
private fun ZapArtwork(state: IntegratedZapState, onZap: () -> Unit, modifier: Modifier = Modifier,
    onLongPressZap: () -> Unit = {}) {
    Box(modifier.size(240.dp).testTag("zap-artwork")
        .combinedClickable(enabled = !state.isScanning && !state.shortcutBusy, role = Role.Button,
            onClickLabel = state.actionLabel, onClick = onZap,
            onLongClickLabel = if (state.truth == Gate0Truth.IDLE) "Start session" else null,
            onLongClick = { if (!state.desiredActive && !state.isActive) onLongPressZap() })
        .semantics { contentDescription = state.actionLabel }, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.zap_button), null, Modifier.size(220.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun SelectionSlot(state: IntegratedZapState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.desiredActive) {
            Text(state.modeName ?: stringResource(R.string.zap_selected_apps),
                color = if (state.isActive) Color.Black else Color.White, fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        } else {
            Text(state.selectedAppName ?: stringResource(R.string.zap_no_apps),
                color = Color(0xFF9A9A9A), fontSize = 14.sp, textAlign = TextAlign.Center)
            Surface(onClick = onOpen, shape = RoundedCornerShape(50), color = Color.White,
                contentColor = Color.Black.copy(alpha = 0.92f), shadowElevation = 6.dp,
                modifier = Modifier.widthIn(min = 220.dp, max = 300.dp).heightIn(min = 48.dp).testTag("zap-selection")) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(state.modeName ?: JustnoiseContract.text("mode.selector.empty"),
                        modifier = Modifier.weight(1f, fill = false), fontSize = 16.sp,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Text("⌄", Modifier.padding(start = 6.dp), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ZapActions(state: IntegratedZapState, foreground: Color, onZap: () -> Unit, onSetup: () -> Unit,
    modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val notice = if (state.isScanning) null else state.transientStatus
        notice?.let {
            Text(it, color = foreground.copy(alpha = 0.74f), fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        when {
            state.isScanning -> Unit // The modal owns the only cancel action.
            state.isActive -> Button(onClick = onZap, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = JustnoiseContract.color("action.zap.stop.background"), contentColor = Color.Black)) {
                Text(JustnoiseContract.text("zap.active.action.stop"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(name = "Real Zap idle", widthDp = 384, heightDp = 832)
@Composable
private fun IntegratedZapIdlePreview() = IntegratedZapPreview(IntegratedZapState(truth = Gate0Truth.IDLE,
    selectedAppName = "2 apps", modeName = "Sleep"))
@Preview(name = "Real Zap active", widthDp = 384, heightDp = 832)
@Composable
private fun IntegratedZapActivePreview() = IntegratedZapPreview(IntegratedZapState(truth = Gate0Truth.ACTIVE,
    selectedAppName = "2 apps", elapsedText = "00:25:00", desiredActive = true, modeName = "Sleep"))
@Preview(name = "Recovery, enlarged text", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun IntegratedZapRecoveryPreview() = JustnoiseIntegratedTheme {
    IntegratedRecoverySurface(true, false, false, false, null, false, {}, {}, {}, {})
}
@Composable
private fun IntegratedZapPreview(state: IntegratedZapState) = JustnoiseIntegratedTheme {
    IntegratedZapSurface(state, {}, {}, {}, {}, {}, {})
}
