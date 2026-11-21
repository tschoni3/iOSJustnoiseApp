package com.justnoise.integrated

import android.app.DatePickerDialog
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import com.justnoise.androidcontract.JustnoiseContract
import com.justnoise.gate0.R
import com.justnoise.gate0.data.SessionHistoryTimelineSnapshot
import com.justnoise.gate0.domain.HistoryDaySummary
import com.justnoise.gate0.domain.HistoryWindow
import com.justnoise.uiprototype.PortableHistoryDurationStyle
import com.justnoise.uiprototype.PortableHistoryRules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val HistoryDark = Color(0xFF0E0E0D)
private val HistoryOrange = Color(0xFFFF9500)
private val HistoryBody = Color(0xFF555555)

/** Real read-only calendar state. This surface never creates records or calls an adapter. */
@Composable
fun IntegratedHistorySurface(
    state: IntegratedHistoryState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectWindow: (HistoryWindow) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onDismissDay: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val activity = LocalActivity.current
    LaunchedEffect(activity) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    Surface(Modifier.fillMaxSize().testTag("history-main"), color = Color.White, contentColor = Color.Black) {
        Box(Modifier.background(HistoryDark).windowInsetsPadding(WindowInsets.safeDrawing)) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
                val scrollingHeader = state.window == HistoryWindow.NINETY_DAYS &&
                    (maxHeight < 680.dp || LocalDensity.current.fontScale >= 1.5f)
                Column(Modifier.fillMaxSize()) {
                    HistoryToolbar(state.window, onBack, onSelectWindow, onOpenSettings)
                    if (!scrollingHeader) HistorySummary(state)
                    if (state.window == HistoryWindow.YEAR) {
                        HistoryYearCalendar(state, onRetry, onSelectDay, Modifier.fillMaxWidth().weight(1f))
                    } else {
                        HistoryCalendar(state, scrollingHeader, onRetry, onSelectDay,
                            Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
        state.selectedDay?.let { HistoryDayDialog(it, state.today, onDismissDay) }
    }
}

/** A year is an overview, never a scrollable list. A full-size date picker is the
 * accessible alternative to tapping the deliberately small overview dots. */
@Composable
private fun HistoryYearCalendar(state: IntegratedHistoryState, onRetry: () -> Unit,
                                onSelectDay: (LocalDate) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val snapshot = state.snapshot
    Column(modifier.padding(horizontal = 16.dp).testTag("history-calendar")) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(state.today.year.toString(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = {
                val initial = state.selectedDate ?: state.today
                DatePickerDialog(context, { _, year, month, day ->
                    onSelectDay(LocalDate.of(year, month + 1, day))
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                    datePicker.minDate = state.range.startInclusive.atStartOfDay(state.zoneId).toInstant().toEpochMilli()
                    datePicker.maxDate = state.range.endExclusive.minusDays(1).atStartOfDay(state.zoneId).toInstant().toEpochMilli()
                }.show()
            }, enabled = !state.loading && snapshot?.error == false,
                modifier = Modifier.heightIn(min = 48.dp).testTag("history-year-choose-day"),
                colors = ButtonDefaults.textButtonColors(contentColor = HistoryDark)) {
                Text(stringResource(R.string.history_choose_day))
            }
        }
        when {
            state.loading || snapshot == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val description = stringResource(R.string.history_loading)
                CircularProgressIndicator(Modifier.size(32.dp).semantics { contentDescription = description }, color = HistoryDark)
            }
            snapshot.error -> Box(Modifier.fillMaxWidth().weight(1f)) {
                HistoryNotice(stringResource(R.string.history_error_title), stringResource(R.string.history_error_body), onRetry)
            }
            else -> {
                HistoryYearDots(snapshot.days, state.today, state.selectedDate, onSelectDay,
                    Modifier.fillMaxWidth().weight(1f))
                val (left, percent) = IntegratedHistoryGridRules.yearProgress(state.today)
                Text(stringResource(R.string.history_year_progress, left, percent), color = HistoryBody,
                    fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun HistoryYearDots(days: List<HistoryDaySummary>, today: LocalDate, selected: LocalDate?,
                            onSelectDay: (LocalDate) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.testTag("history-year-dots"), contentAlignment = Alignment.Center) {
        val layout = remember(days.size, maxWidth, maxHeight) {
            IntegratedHistoryGridRules.yearLayout(days.size, maxWidth.value, maxHeight.value)
        }
        val cell = with(LocalDensity.current) { floor(layout.cellSizeDp.dp.toPx()).toInt().toDp() }
        val configuration = LocalViewConfiguration.current
        val preciseDotTargets = remember(configuration) {
            object : ViewConfiguration by configuration {
                override val minimumTouchTargetSize = DpSize.Zero
            }
        }
        // Avoid overlapping expanded touch areas. Choose day above stays >=48dp.
        CompositionLocalProvider(LocalViewConfiguration provides preciseDotTargets) {
            Column(Modifier.width(cell * layout.columns).height(cell * layout.rows)) {
                repeat(layout.rows) { row ->
                    Row {
                        val start = row * layout.columns
                        days.subList(start, minOf(start + layout.columns, days.size)).forEach { day ->
                            HistoryDay(day, today, selected == day.date, onSelectDay,
                                compactSize = cell, dotPadding = cell * .1f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryToolbar(window: HistoryWindow, onBack: () -> Unit,
                           onSelectWindow: (HistoryWindow) -> Unit, onOpenSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentWindow = windowLabel(window)
    Row(Modifier.fillMaxWidth().background(HistoryDark).heightIn(min = 52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        HistoryIconButton(stringResource(R.string.history_back), onBack) { color ->
            val middle = size.height / 2
            drawLine(color, Offset(size.width * .85f, middle), Offset(size.width * .15f, middle), 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(size.width * .15f, middle), Offset(size.width * .45f, size.height * .2f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(size.width * .15f, middle), Offset(size.width * .45f, size.height * .8f), 2.dp.toPx(), StrokeCap.Round)
        }
        Box(Modifier.weight(1f)) {
            Row(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 8.dp).testTag("history-window-menu")
                .semantics { stateDescription = currentWindow },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.history_title), color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text("⌄", color = Color.White.copy(alpha = .8f), fontSize = 16.sp)
            }
            DropdownMenu(expanded, { expanded = false }) {
                HistoryWindow.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(windowLabel(choice)) },
                        modifier = Modifier.semantics { selected = choice == window }.testTag("history-window-${choice.name}"),
                        onClick = { expanded = false; onSelectWindow(choice) })
                }
            }
        }
        HistoryIconButton(stringResource(R.string.history_settings), onOpenSettings) { color ->
            val radius = size.minDimension * .29f
            drawCircle(color, radius, style = Stroke(2.dp.toPx()))
            drawCircle(color, size.minDimension * .10f, style = Stroke(2.dp.toPx()))
            repeat(8) { tooth ->
                val angle = Math.PI * tooth / 4
                drawLine(color,
                    center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat()),
                    center + Offset((cos(angle) * radius * 1.5).toFloat(), (sin(angle) * radius * 1.5).toFloat()),
                    3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun HistoryIconButton(description: String, onClick: () -> Unit,
                              draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) { draw(Color.White) }
    }
}

@Composable
private fun HistorySummary(state: IntegratedHistoryState) {
    val snapshot = state.snapshot?.takeUnless { it.error }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
        .background(HistoryDark).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HistoryTotal(stringResource(R.string.history_zaps), snapshot?.totalCount?.toString() ?: "—", Modifier.weight(1f))
            HistoryTotal(stringResource(R.string.history_session_time), snapshot?.let {
                duration(it.totalDurationMilliseconds, PortableHistoryDurationStyle.SUMMARY)
            } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HistoryTotal(label: String, value: String, modifier: Modifier) {
    Surface(modifier.heightIn(min = 128.dp), shape = RoundedCornerShape(10.dp),
        color = Color(0xFF131312), shadowElevation = 4.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = HistoryOrange.copy(alpha = .92f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(24.dp))
            Text(value, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoryCalendar(state: IntegratedHistoryState, scrollingHeader: Boolean, onRetry: () -> Unit,
                            onSelectDay: (LocalDate) -> Unit, modifier: Modifier) {
    val locale = LocalLocale.current.platformLocale
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    BoxWithConstraints(modifier) {
        val columns = IntegratedHistoryGridRules.columns(state.window, (maxWidth - 32.dp).value)
        val scroll = rememberLazyGridState()
        val snapshot = state.snapshot
        val valid = snapshot != null && !snapshot.error && !state.loading
        // Standard keeps the summary fixed. Compact/enlarged-text scrolls the header as well.
        LaunchedEffect(state.range, columns, scrollingHeader, valid) {
            if (valid && state.window == HistoryWindow.NINETY_DAYS && !scrollingHeader) {
                scroll.scrollToItem(1 + IntegratedHistoryGridRules.todayContextIndex(state.range, state.today, columns))
            } else if (valid) scroll.scrollToItem(0)
        }
        LazyVerticalGrid(columns = GridCells.Fixed(columns), state = scroll,
            modifier = Modifier.fillMaxSize().testTag("history-calendar"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (scrollingHeader) item(span = { GridItemSpan(maxLineSpan) }, key = "summary") { HistorySummary(state) }
            item(span = { GridItemSpan(maxLineSpan) }, key = "range") {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(windowLabel(state.window), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${dateFormatter.format(state.range.startInclusive)} – ${dateFormatter.format(state.range.endExclusive.minusDays(1))}",
                            color = HistoryBody, fontSize = 12.sp)
                    }
                    TextButton(onRetry, enabled = !state.loading, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = HistoryDark,
                            disabledContentColor = HistoryBody.copy(alpha = .5f))) {
                        Text(stringResource(R.string.history_refresh))
                    }
                }
            }
            when {
                state.loading || snapshot == null -> item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    val description = stringResource(R.string.history_loading)
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp).semantics { contentDescription = description }, color = HistoryDark)
                    }
                }
                snapshot.error -> item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                    HistoryNotice(stringResource(R.string.history_error_title), stringResource(R.string.history_error_body), onRetry)
                }
                else -> {
                    items(snapshot.days, key = { it.date.toEpochDay() }) { day ->
                        HistoryDay(day, state.today, state.selectedDate == day.date, onSelectDay)
                    }
                    if (snapshot.totalCount == 0) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        HistoryNotice(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDay(day: HistoryDaySummary, today: LocalDate, isSelected: Boolean,
                       onSelect: (LocalDate) -> Unit,
                       modifier: Modifier = Modifier, compactSize: Dp? = null,
                       dotPadding: Dp = 0.dp) {
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val label = listOfNotNull(formatter.format(day.date),
        if (day.date == today) stringResource(R.string.history_today) else null,
        if (day.date > today) stringResource(R.string.history_future) else null,
        stringResource(R.string.history_day_duration, duration(day.durationMilliseconds, PortableHistoryDurationStyle.DAY_DETAIL)),
        pluralStringResource(R.plurals.history_day_sessions, day.sessionCount, day.sessionCount)).joinToString(". ")
    val density = PortableHistoryRules.density(day.durationMilliseconds / 1_000.0)
    val fill = if (day.date == today) HistoryOrange else JustnoiseContract.color("history.dot.${density.contractValue}")
    val target = if (compactSize == null) modifier.heightIn(min = 48.dp).aspectRatio(1f)
        else modifier.size(compactSize)
    Box(target.testTag("history-day-${day.date}")
        .clickable(role = Role.Button) { onSelect(day.date) }
        .semantics { contentDescription = label; selected = isSelected; role = Role.Button },
        contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().padding(dotPadding).clip(CircleShape).background(fill)
            .then(if (isSelected) Modifier.border(minOf(3.dp, dotPadding.takeIf { it > 0.dp } ?: 3.dp), HistoryOrange, CircleShape) else Modifier))
    }
}

@Composable
private fun HistoryDayDialog(day: HistoryDaySummary, today: LocalDate, onDismiss: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.uuuu") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.widthIn(max = 340.dp).testTag("history-day-dialog"),
            shape = RoundedCornerShape(24.dp), color = Color(0xFF141414), contentColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(formatter.format(day.date), fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.history_day_duration,
                    duration(day.durationMilliseconds, PortableHistoryDurationStyle.DAY_DETAIL)),
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(pluralStringResource(R.plurals.history_day_sessions, day.sessionCount, day.sessionCount),
                    color = Color.White.copy(alpha = .72f), fontSize = 17.sp)
                if (day.date > today) Text(stringResource(R.string.history_future), color = Color.White.copy(alpha = .72f))
                TextButton(onDismiss, Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.history_close), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun HistoryNotice(title: String, body: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(body, color = HistoryBody, fontSize = 14.sp, lineHeight = 20.sp)
        if (onRetry != null) Button(onRetry, Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HistoryDark, contentColor = Color.White)) {
            Text(stringResource(R.string.history_retry))
        }
    }
}

@Composable private fun windowLabel(window: HistoryWindow): String = stringResource(
    if (window == HistoryWindow.NINETY_DAYS) R.string.history_ninety_days else R.string.history_year)

private fun duration(milliseconds: Long, style: PortableHistoryDurationStyle): String =
    PortableHistoryRules.formatDuration(milliseconds / 1_000.0, style)

@Preview(name = "History empty", widthDp = 384, heightDp = 832)
@Composable
private fun HistoryEmptyPreview() {
    val state = IntegratedHistoryState()
    val empty = SessionHistoryTimelineSnapshot(state.range,
        List(state.range.dayCount) { HistoryDaySummary(state.range.startInclusive.plusDays(it.toLong())) })
    JustnoiseIntegratedTheme { IntegratedHistorySurface(state.copy(snapshot = empty), {}, {}, {}, {}, {}) }
}

@Preview(name = "History error", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun HistoryErrorPreview() {
    val state = IntegratedHistoryState()
    JustnoiseIntegratedTheme { IntegratedHistorySurface(
        state.copy(snapshot = SessionHistoryTimelineSnapshot(state.range, error = true)), {}, {}, {}, {}, {}) }
}

@Preview(name = "History loading", widthDp = 384, heightDp = 832)
@Composable
private fun HistoryLoadingPreview() {
    JustnoiseIntegratedTheme { IntegratedHistorySurface(IntegratedHistoryState(loading = true), {}, {}, {}, {}, {}) }
}

@Preview(name = "Whole year — 366 dots", widthDp = 384, heightDp = 832)
@Preview(name = "Whole year — compact large text", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun HistoryYearPreview() {
    val state = IntegratedHistoryState(window = HistoryWindow.YEAR, today = LocalDate.of(2028, 10, 11))
    val snapshot = SessionHistoryTimelineSnapshot(state.range,
        List(state.range.dayCount) { HistoryDaySummary(state.range.startInclusive.plusDays(it.toLong())) })
    JustnoiseIntegratedTheme { IntegratedHistorySurface(state.copy(snapshot = snapshot), {}, {}, {}, {}, {}) }
}
