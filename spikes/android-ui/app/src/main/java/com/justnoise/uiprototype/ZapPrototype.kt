package com.justnoise.uiprototype

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.max

@Composable
fun JustnoisePrototypeApp() {
    var flowState by rememberSaveable(stateSaver = PrototypeFlowStateSaver) {
        mutableStateOf(PrototypeFlowState())
    }
    var zapState by rememberSaveable(stateSaver = ZapStateSaver) {
        mutableStateOf(ZapUiState())
    }
    var showModes by rememberSaveable { mutableStateOf(false) }

    val dispatch: (PrototypeFlowAction) -> Unit = { action ->
        flowState = PrototypeFlowReducer.reduce(flowState, action)
    }

    BackHandler(
        enabled = flowState.route != PrototypeRoute.Onboarding || flowState.onboardingPage > 0,
    ) {
        dispatch(PrototypeFlowAction.Back)
    }

    JustnoiseTheme {
        if (flowState.route == PrototypeRoute.Zap) {
            ZapScreen(
                state = zapState,
                onZapClick = {
                    zapState = ZapPreviewReducer.reduce(
                        zapState,
                        ZapPreviewAction.ToggleSession,
                    )
                },
                onModeClick = { showModes = true },
            )

            if (showModes) {
                ModeSheet(
                    selectedMode = zapState.selectedMode,
                    onSelect = { mode ->
                        zapState = ZapPreviewReducer.reduce(
                            zapState,
                            ZapPreviewAction.SelectMode(mode),
                        )
                        showModes = false
                    },
                    onDismiss = { showModes = false },
                )
            }
        } else {
            LaunchFlowScreen(
                state = flowState,
                onAction = dispatch,
            )
        }
    }
}

private val PrototypeFlowStateSaver = androidx.compose.runtime.saveable.mapSaver<PrototypeFlowState>(
    save = { state ->
        mapOf(
            "route" to state.route.name,
            "onboardingPage" to state.onboardingPage,
        )
    },
    restore = { saved ->
        PrototypeFlowState(
            route = PrototypeRoute.valueOf(saved["route"] as String),
            onboardingPage = saved["onboardingPage"] as Int,
        )
    },
)

private val ZapStateSaver = androidx.compose.runtime.saveable.mapSaver<ZapUiState>(
    save = { state ->
        mapOf(
            "active" to state.isActive,
            "modeId" to state.selectedMode.id,
            "modeName" to state.selectedMode.name,
            "elapsed" to state.elapsedText,
        )
    },
    restore = { saved ->
        ZapUiState(
            isActive = saved["active"] as Boolean,
            selectedMode = ModeOption(
                id = saved["modeId"] as String,
                name = saved["modeName"] as String,
            ),
            elapsedText = saved["elapsed"] as String,
        )
    },
)

@Composable
fun JustnoiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = JustnoiseContract.color("accent.noiseRewind"),
            surface = JustnoiseContract.color("surface.zap.idle"),
            onSurface = Color.White,
        ),
        content = content,
    )
}

@Composable
fun ZapScreen(
    state: ZapUiState,
    onZapClick: () -> Unit,
    onModeClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = JustnoiseContract.color(
            if (state.isActive) "surface.zap.active" else "surface.zap.idle",
        ),
        label = "zap-background",
    )
    val foreground by animateColorAsState(
        targetValue = JustnoiseContract.color(
            if (state.isActive) "text.zap.active.primary" else "text.zap.idle.primary",
        ),
        label = "zap-foreground",
    )
    val activity = LocalActivity.current
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
            ZapToolbar(
                foreground = foreground,
                showActions = !state.isActive,
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val heroHeight = 126.dp
                val buttonSlot = 240.dp
                val stateSlot = 84.dp
                val stateSpacing = 26.dp
                val bottomReserved = 132.dp
                val flexible = max(
                    0f,
                    (maxHeight - heroHeight - buttonSlot - stateSlot - stateSpacing - bottomReserved).value,
                ).dp
                val heroToButton = (flexible * 0.5f).coerceIn(48.dp, 88.dp)
                val buttonTop = heroHeight + heroToButton
                val stateTop = buttonTop + buttonSlot + stateSpacing

                HeroHeader(
                    state = state,
                    modifier = Modifier.align(Alignment.TopStart),
                )

                ZapButton(
                    onClick = onZapClick,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = buttonTop)
                        .size(buttonSlot),
                )

                ZapStateSlot(
                    state = state,
                    onModeClick = onModeClick,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = stateTop)
                        .fillMaxWidth()
                        .height(stateSlot),
                )

                if (state.isActive) {
                    UnzapButton(
                        onClick = onZapClick,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                }
            }
        }

    }
}

@Composable
private fun ZapToolbar(
    foreground: Color,
    showActions: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (showActions) CalendarGlyph(color = foreground)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = JustnoiseContract.text("brand.displayName").uppercase(),
                color = foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.2).sp,
                lineHeight = 15.sp,
            )
            Text(
                text = "UI PREVIEW",
                color = foreground.copy(alpha = 0.28f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                lineHeight = 9.sp,
            )
        }

        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (showActions) HistoryGlyph(color = foreground)
        }
    }
}

@Composable
private fun HeroHeader(
    state: ZapUiState,
    modifier: Modifier = Modifier,
) {
    val titleColor = JustnoiseContract.color(
        if (state.isActive) "text.zap.active.primary" else "text.zap.idle.primary",
    )
    val subtitleColor = JustnoiseContract.color(
        if (state.isActive) "text.zap.active.secondary" else "text.zap.idle.secondary",
    )
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
            color = titleColor,
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
            color = subtitleColor,
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 54.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ZapButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressShadow by animateDpAsState(targetValue = 1.dp, label = "zap-shadow")
    Box(
        modifier = modifier
            .semantics {
                contentDescription = "Zap visual state preview"
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.zap_button),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(220.dp)
                .shadow(pressShadow, CircleShape),
        )
    }
}

@Composable
private fun ZapStateSlot(
    state: ZapUiState,
    onModeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        if (state.isActive) {
            Text(
                text = state.selectedMode.name,
                color = JustnoiseContract.color("text.zap.active.primary"),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = JustnoiseContract.text("mode.selector.label"),
                    color = Color(0xFF747474),
                    fontSize = 14.sp,
                )
                Surface(
                    onClick = onModeClick,
                    modifier = Modifier
                        .size(width = 220.dp, height = 38.dp)
                        .shadow(10.dp, RoundedCornerShape(50)),
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    contentColor = Color.Black.copy(alpha = 0.92f),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.selectedMode.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "⌄",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnzapButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = JustnoiseContract.color("action.zap.stop.background"),
            contentColor = JustnoiseContract.color("action.zap.stop.label"),
        ),
    ) {
        Text(
            text = JustnoiseContract.text("zap.active.action.stop"),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSheet(
    selectedMode: ModeOption,
    onSelect: (ModeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF7F7F7),
        contentColor = Color.Black,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.Black.copy(alpha = 0.22f))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = JustnoiseContract.text("mode.list.title"),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = JustnoiseContract.text("mode.list.prompt"),
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(30.dp))
            PreviewModes.forEach { mode ->
                ModeRow(
                    mode = mode,
                    selected = mode.id == selectedMode.id,
                    onClick = { onSelect(mode) },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Mode editing and app selection arrive after Gate 0.",
                color = Color.Black.copy(alpha = 0.46f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ModeRow(
    mode: ModeOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        color = if (selected) Color.Black else Color.White,
        contentColor = if (selected) Color.White else Color.Black,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = if (selected) 0.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = mode.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (selected) {
                Text(text = "✓", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CalendarGlyph(color: Color) {
    Canvas(modifier = Modifier.size(22.dp).semantics { contentDescription = "Schedules" }) {
        val stroke = 1.8.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(2.dp.toPx(), 4.dp.toPx()),
            size = Size(18.dp.toPx(), 16.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawLine(
            color = color,
            start = Offset(2.dp.toPx(), 9.dp.toPx()),
            end = Offset(20.dp.toPx(), 9.dp.toPx()),
            strokeWidth = stroke,
        )
        listOf(7.dp, 15.dp).forEach { x ->
            drawLine(
                color = color,
                start = Offset(x.toPx(), 2.dp.toPx()),
                end = Offset(x.toPx(), 6.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun HistoryGlyph(color: Color) {
    Canvas(modifier = Modifier.size(21.dp).semantics { contentDescription = "History" }) {
        val radius = 4.dp.toPx()
        listOf(
            Offset(6.dp.toPx(), 6.dp.toPx()),
            Offset(15.dp.toPx(), 6.dp.toPx()),
            Offset(6.dp.toPx(), 15.dp.toPx()),
            Offset(15.dp.toPx(), 15.dp.toPx()),
        ).forEach { center -> drawCircle(color = color, radius = radius, center = center) }
    }
}


@Preview(name = "Zap idle", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun ZapIdlePreview() {
    JustnoiseTheme {
        ZapScreen(
            state = ZapUiState(isActive = false),
            onZapClick = {},
            onModeClick = {},
        )
    }
}

@Preview(name = "Zap active", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun ZapActivePreview() {
    JustnoiseTheme {
        ZapScreen(
            state = ZapUiState(isActive = true),
            onZapClick = {},
            onModeClick = {},
        )
    }
}
