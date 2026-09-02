package com.justnoise.uiprototype

import com.justnoise.androidcontract.JustnoiseContract

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

data class NoiseRewindUiState(
    val page: Int = 0,
    val reflectionDraft: String = "",
)

sealed interface NoiseRewindAction {
    data class ShowPage(val page: Int) : NoiseRewindAction
    data object Begin : NoiseRewindAction
    data object Next : NoiseRewindAction
    data object Back : NoiseRewindAction
    data class ChangeReflection(val value: String) : NoiseRewindAction
    data object Reset : NoiseRewindAction
}

object NoiseRewindReducer {
    const val PageCount = 7

    fun reduce(state: NoiseRewindUiState, action: NoiseRewindAction): NoiseRewindUiState =
        when (action) {
            is NoiseRewindAction.ShowPage -> state.copy(
                page = action.page.coerceIn(0, PageCount - 1),
            )
            NoiseRewindAction.Begin -> state.copy(page = 1)
            NoiseRewindAction.Next -> state.copy(
                page = (state.page + 1).coerceAtMost(PageCount - 1),
            )
            NoiseRewindAction.Back -> state.copy(
                page = (state.page - 1).coerceAtLeast(0),
            )
            is NoiseRewindAction.ChangeReflection -> state.copy(
                reflectionDraft = action.value,
            )
            NoiseRewindAction.Reset -> NoiseRewindUiState()
        }
}

private val NoiseRewindStateSaver =
    androidx.compose.runtime.saveable.mapSaver<NoiseRewindUiState>(
        save = { state ->
            mapOf(
                "page" to state.page,
                "reflectionDraft" to state.reflectionDraft,
            )
        },
        restore = { saved ->
            NoiseRewindUiState(
                page = saved["page"] as Int,
                reflectionDraft = saved["reflectionDraft"] as String,
            )
        },
    )

@Composable
fun NoiseRewindSceneHost(startWithFlow: Boolean) {
    var scene by rememberSaveable {
        mutableStateOf(if (startWithFlow) "flow" else "ready")
    }
    when (scene) {
        "ready" -> NoiseRewindReadyPrototype(
            onView = { scene = "flow" },
            onDismiss = { scene = "zap" },
        )
        "flow" -> NoiseRewindFlowPrototype(onClose = { scene = "zap" })
        else -> ZapScreen(
            state = ZapUiState(),
            onZapClick = {},
            onModeClick = {},
            onSchedulesClick = {},
            onHistoryClick = {},
        )
    }
}

@Composable
private fun NoiseRewindReadyPrototype(
    onView: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ZapScreen(
            state = ZapUiState(),
            onZapClick = {},
            onModeClick = {},
            onSchedulesClick = {},
            onHistoryClick = {},
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .shadow(30.dp, RoundedCornerShape(28.dp)),
            color = Color(0xFF232323),
            contentColor = Color.White,
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "Your Noise Rewind is ready",
                        color = Color.White,
                        fontSize = 24.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", color = Color.White.copy(alpha = 0.78f), fontSize = 22.sp)
                    }
                }
                Text(
                    text = "Review your focus rhythm from the past week. A quiet weekly ritual, not another interruption.",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                )
                Button(
                    onClick = onView,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JustnoiseContract.color("accent.noiseRewind"),
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Text("View Rewind  ↗", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "UI preview only · eligibility was not evaluated",
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NoiseRewindFlowPrototype(onClose: () -> Unit) {
    var state by rememberSaveable(stateSaver = NoiseRewindStateSaver) {
        mutableStateOf(NoiseRewindUiState())
    }
    val dispatch: (NoiseRewindAction) -> Unit = { action ->
        state = NoiseRewindReducer.reduce(state, action)
    }
    val pagerState = rememberPagerState(initialPage = state.page) {
        NoiseRewindReducer.PageCount
    }
    val activity = LocalActivity.current

    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    LaunchedEffect(state.page) {
        if (pagerState.currentPage != state.page) {
            pagerState.animateScrollToPage(state.page)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != state.page) {
            dispatch(NoiseRewindAction.ShowPage(pagerState.settledPage))
        }
    }

    BackHandler {
        if (state.page == 0) onClose() else dispatch(NoiseRewindAction.Back)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.page in 1..5) {
                RewindProgress(page = state.page)
            } else {
                Spacer(modifier = Modifier.height(15.dp))
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                RewindPage(
                    page = page,
                    reflectionDraft = state.reflectionDraft,
                    onBegin = { dispatch(NoiseRewindAction.Begin) },
                    onReflectionChange = {
                        dispatch(NoiseRewindAction.ChangeReflection(it))
                    },
                    onClose = onClose,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 18.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.06f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.Black.copy(alpha = 0.65f), fontSize = 22.sp)
        }
    }
}

@Composable
private fun RewindProgress(page: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index <= page - 1) Color.Black else Color.Black.copy(alpha = 0.14f),
                    ),
            )
        }
    }
}

@Composable
private fun RewindPage(
    page: Int,
    reflectionDraft: String,
    onBegin: () -> Unit,
    onReflectionChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    when (page) {
        0 -> RewindIntro(onBegin)
        1 -> WeeklySignalPage()
        2 -> ProtectedMomentsPage()
        3 -> MostFocusedDayPage()
        4 -> PatternInsightPage()
        5 -> ArchetypePage()
        else -> ReflectionPage(
            reflectionDraft = reflectionDraft,
            onReflectionChange = onReflectionChange,
            onClose = onClose,
        )
    }
}

@Composable
private fun RewindSlide(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 48.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun RewindIntro(onBegin: () -> Unit) {
    RewindSlide {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Your Week in Signal",
            color = Color.Black,
            fontSize = 42.sp,
            lineHeight = 47.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "A reflection of your attention patterns.",
            color = Color.Black.copy(alpha = 0.55f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        RewindPrimaryButton("Begin", onBegin)
        Text(
            text = "UI preview only · autoplay and weekly analytics are not running",
            color = Color.Black.copy(alpha = 0.3f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun WeeklySignalPage() {
    RewindSlide {
        Text("WEEKLY SIGNAL", color = Color.Black.copy(alpha = 0.45f), fontSize = 13.sp, letterSpacing = 1.4.sp)
        Text(
            text = "Your signal is building",
            color = Color.Black,
            fontSize = 34.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 13.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 0.18f },
                modifier = Modifier.size(210.dp),
                color = Color.Black,
                trackColor = Color.Black.copy(alpha = 0.08f),
                strokeWidth = 18.dp,
            )
            Text("+18%", color = Color.Black, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ProtectedMomentsPage() {
    RewindSlide {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("This Week", color = Color.Black.copy(alpha = 0.45f), fontSize = 28.sp)
            Text(
                text = "4h 35m of protected attention",
                color = Color.Black,
                fontSize = 47.sp,
                lineHeight = 52.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        RewindDotGrid(filled = 12)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "12 protected moments made space for what mattered.",
            color = Color.Black.copy(alpha = 0.58f),
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RewindDotGrid(filled: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(5) { column ->
                    val index = row * 5 + column
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < filled) Color.Black else Color.Black.copy(alpha = 0.12f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MostFocusedDayPage() {
    RewindSlide {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Most focused day", color = Color.Black.copy(alpha = 0.45f), fontSize = 28.sp)
            Text("Tuesday", color = Color.Black, fontSize = 60.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("1h 25m", color = Color.Black, fontSize = 70.sp, fontWeight = FontWeight.Bold)
            Text("of protected focus", color = Color.Black.copy(alpha = 0.45f), fontSize = 34.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
        WeekDots(activeIndex = 1)
    }
}

@Composable
private fun WeekDots(activeIndex: Int) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { index, label ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (index == activeIndex) Color.Black else Color.Black.copy(alpha = 0.12f)),
                )
                Text(label, color = Color.Black.copy(alpha = 0.65f), fontSize = 15.sp, modifier = Modifier.padding(top = 9.dp))
            }
        }
    }
}

@Composable
private fun PatternInsightPage() {
    RewindSlide {
        Text("PATTERN INSIGHT", color = Color.Black.copy(alpha = 0.45f), fontSize = 13.sp, letterSpacing = 1.4.sp)
        Text(
            text = "Your strongest window is morning",
            color = Color.Black,
            fontSize = 34.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(modifier = Modifier.height(28.dp))
        SignalBars(modifier = Modifier.fillMaxWidth().weight(1f))
        Text(
            text = "You protected the most attention before midday.",
            color = Color.Black.copy(alpha = 0.55f),
            fontSize = 17.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
private fun SignalBars(modifier: Modifier = Modifier) {
    val values = remember { listOf(0.18f, 0.22f, 0.3f, 0.55f, 0.88f, 1f, 0.72f, 0.5f, 0.36f, 0.28f, 0.2f, 0.16f) }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(value)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (index == 5) Color.Black else Color.Black.copy(alpha = 0.13f)),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach {
                Text(it, color = Color.Black.copy(alpha = 0.35f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ArchetypePage() {
    RewindSlide {
        Spacer(modifier = Modifier.weight(1f))
        Text("CURRENT SIGNAL PATTERN", color = Color.Black.copy(alpha = 0.45f), fontSize = 13.sp, letterSpacing = 1.4.sp)
        Text(
            text = "THE BUILDER",
            color = Color.Black,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = "You are building a steadier rhythm, one protected block at a time.",
            color = Color.Black.copy(alpha = 0.56f),
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReflectionPage(
    reflectionDraft: String,
    onReflectionChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    RewindSlide {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            Text(
                text = "What helped you protect your attention most this week?",
                color = Color.Black,
                fontSize = 34.sp,
                lineHeight = 39.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            TextField(
                value = reflectionDraft,
                onValueChange = onReflectionChange,
                modifier = Modifier.fillMaxWidth().height(130.dp).padding(top = 22.dp),
                placeholder = { Text("Write your reflection...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(alpha = 0.045f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.045f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(22.dp),
            )
            Spacer(modifier = Modifier.height(60.dp))
            RewindPrimaryButton("Done", onClose)
            TextButton(onClick = onClose) {
                Text("Skip", color = Color.Black.copy(alpha = 0.45f), fontWeight = FontWeight.SemiBold)
            }
            Text("See you next week.", color = Color.Black.copy(alpha = 0.38f), fontSize = 14.sp)
            Text(
                text = "UI preview only · reflection is not persisted",
                color = Color.Black.copy(alpha = 0.28f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun RewindPrimaryButton(title: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
        shape = RoundedCornerShape(22.dp),
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(name = "Noise Rewind ready", widthDp = 412, heightDp = 915)
@Composable
private fun NoiseRewindReadyPreview() {
    JustnoiseTheme { NoiseRewindReadyPrototype({}, {}) }
}

@Preview(name = "Noise Rewind intro", widthDp = 412, heightDp = 915)
@Composable
private fun NoiseRewindIntroPreview() {
    JustnoiseTheme { RewindIntro({}) }
}

@Preview(name = "Noise Rewind reflection", widthDp = 412, heightDp = 915)
@Composable
private fun NoiseRewindReflectionPreview() {
    JustnoiseTheme { ReflectionPage("", {}, {}) }
}
