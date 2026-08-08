package com.justnoise.uiprototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

enum class CoachTarget {
    Zap,
    Mode,
    Schedule,
}

data class CoachMarkUi(
    val target: CoachTarget,
    val title: String,
    val message: String,
)

val BasicCoachMarks = listOf(
    CoachMarkUi(
        target = CoachTarget.Zap,
        title = JustnoiseContract.text("coach.basic.zap.title"),
        message = JustnoiseContract.text("coach.basic.zap.message"),
    ),
    CoachMarkUi(
        target = CoachTarget.Mode,
        title = JustnoiseContract.text("coach.basic.mode.title"),
        message = JustnoiseContract.text("coach.basic.mode.message"),
    ),
    CoachMarkUi(
        target = CoachTarget.Schedule,
        title = JustnoiseContract.text("coach.basic.schedule.title"),
        message = JustnoiseContract.text("coach.basic.schedule.message"),
    ),
)

data class CoachTourUiState(
    val step: Int = 0,
    val isVisible: Boolean = true,
)

sealed interface CoachTourAction {
    data object Next : CoachTourAction
    data object Skip : CoachTourAction
}

object CoachTourReducer {
    fun reduce(state: CoachTourUiState, action: CoachTourAction): CoachTourUiState {
        if (!state.isVisible) return state
        return when (action) {
            CoachTourAction.Skip -> state.copy(isVisible = false)
            CoachTourAction.Next -> if (state.step >= BasicCoachMarks.lastIndex) {
                state.copy(isVisible = false)
            } else {
                state.copy(step = state.step + 1)
            }
        }
    }
}

private data class CoachHole(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp,
    val cornerRadius: Dp,
)

@Composable
fun CoachTourOverlay(
    state: CoachTourUiState,
    onAction: (CoachTourAction) -> Unit,
) {
    if (!state.isVisible) return
    val mark = BasicCoachMarks[state.step.coerceIn(BasicCoachMarks.indices)]

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val contentHeight = maxHeight - 52.dp
        val flexibleHeight = max(
            0f,
            (contentHeight - 126.dp - 240.dp - 84.dp - 26.dp - 132.dp).value,
        ).dp
        val heroToButton = (flexibleHeight * 0.5f).coerceIn(48.dp, 88.dp)
        val zapTop = 52.dp + 126.dp + heroToButton
        val stateTop = zapTop + 240.dp + 26.dp
        val hole = when (mark.target) {
            CoachTarget.Zap -> CoachHole(
                left = (maxWidth - 256.dp) / 2,
                top = zapTop + 12.dp,
                width = 256.dp,
                height = 256.dp,
                cornerRadius = 128.dp,
            )
            CoachTarget.Mode -> CoachHole(
                left = (maxWidth - 236.dp) / 2,
                top = stateTop - 8.dp,
                width = 236.dp,
                height = 78.dp,
                cornerRadius = 20.dp,
            )
            CoachTarget.Schedule -> CoachHole(
                left = 8.dp,
                top = 0.dp,
                width = 56.dp,
                height = 56.dp,
                cornerRadius = 14.dp,
            )
        }
        val cardHeight = 166.dp
        val cardTop = if (hole.top + hole.height + cardHeight + 20.dp < maxHeight) {
            hole.top + hole.height + 14.dp
        } else {
            (hole.top - cardHeight - 14.dp).coerceAtLeast(12.dp)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(mark.target) { detectTapGestures { } },
        ) {
            val topLeft = Offset(hole.left.toPx(), hole.top.toPx())
            val size = Size(hole.width.toPx(), hole.height.toPx())
            val radius = CornerRadius(hole.cornerRadius.toPx())
            drawRect(Color.Black.copy(alpha = 0.60f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = topLeft,
                size = size,
                cornerRadius = radius,
                blendMode = BlendMode.Clear,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.95f),
                topLeft = topLeft,
                size = size,
                cornerRadius = radius,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = cardTop)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = Color(0xEE282828),
            contentColor = Color.White,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mark.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        color = Color.White.copy(alpha = 0.90f),
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            "${state.step + 1}/${BasicCoachMarks.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(mark.message, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onAction(CoachTourAction.Skip) }) {
                        Text("Skip", color = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        onClick = { onAction(CoachTourAction.Next) },
                        color = Color.White,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            if (state.step == BasicCoachMarks.lastIndex) "Finish" else "Next",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CoachTourScene() {
    var step by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }
    Box(modifier = Modifier.fillMaxSize()) {
        ZapScreen(
            state = ZapUiState(),
            onZapClick = {},
            onModeClick = {},
            onSchedulesClick = {},
            onHistoryClick = {},
        )
        CoachTourOverlay(CoachTourUiState(step = step, isVisible = visible)) { action ->
            val next = CoachTourReducer.reduce(
                CoachTourUiState(step = step, isVisible = visible),
                action,
            )
            step = next.step
            visible = next.isVisible
        }
    }
}

@Preview(name = "Coach tour · Zap", widthDp = 412, heightDp = 915)
@Composable
private fun CoachTourPreview() {
    JustnoiseTheme { CoachTourScene() }
}
