package com.justnoise.integrated

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.justnoise.androidcontract.JustnoiseContract
import com.justnoise.gate0.R
import com.justnoise.gate0.data.ScheduleExecution
import com.justnoise.gate0.data.ScheduleOutcome
import com.justnoise.gate0.data.ScheduleOutcomeCode
import com.justnoise.gate0.data.ScheduledOccurrence
import com.justnoise.gate0.data.StoredSchedule
import com.justnoise.gate0.data.StoredMode
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.domain.ScheduleTiming
import com.justnoise.gate0.feature.ScheduleManagementSnapshot
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val ScheduleBackground = Color(0xFFF2F2F7)
private val ScheduleSecondary = Color(0xFF55555D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegratedScheduleSurface(
    state: IntegratedScheduleState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onEdit: (StoredSchedule) -> Unit,
    onEditorChange: (IntegratedScheduleEditor) -> Unit,
    onSave: () -> Unit,
    onCancelEditor: () -> Unit,
    onReloadEditor: () -> Unit,
    onDelete: (StoredSchedule) -> Unit,
    onToggle: (StoredSchedule, Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    onAlarmAccess: () -> Unit,
) {
    val activity = LocalActivity.current
    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    val snapshot = state.snapshot
    val canMutate = snapshot?.consentAccepted == true && !snapshot.error &&
        !state.loading && !state.saving && !state.mutationUncertain
    val editingStored = snapshot?.schedules?.find {
        it.id == state.editor?.id && it.revision == state.editor?.expectedRevision
    }
    var deleting by remember { mutableStateOf<StoredSchedule?>(null) }
    val mayDismissEditor by rememberUpdatedState(!state.saving)
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color.Black, onPrimary = Color.White, secondary = Color.Black,
        surface = Color.White, onSurface = Color.Black, background = ScheduleBackground,
    )) {
        Surface(color = ScheduleBackground, modifier = Modifier.fillMaxSize().testTag("schedules-main")) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("schedules-back")) {
                        Text(stringResource(R.string.integrated_schedule_back))
                    }
                    Spacer(Modifier.weight(1f))
                    val addDescription = stringResource(R.string.integrated_schedule_add)
                    TextButton(
                        onClick = onNew, enabled = canMutate,
                        modifier = Modifier.size(48.dp).testTag("schedules-add")
                            .semantics { contentDescription = addDescription },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        val color = if (canMutate) Color.Black else Color(0xFF8E8E93)
                        Canvas(Modifier.size(20.dp)) {
                            drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
                            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("schedules-list"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            JustnoiseContract.text("schedule.list.title"),
                            fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp).semantics { heading() },
                        )
                    }
                    state.message?.let { message -> item { ScheduleNotice(message) } }
                    if (state.mutationUncertain) item { ScheduleNotice(
                        "A save may have finished. Refresh the saved schedules before trying again.",
                    ) }
                    when {
                        snapshot == null || (state.loading && snapshot.error) -> item { CircularProgressIndicator() }
                        snapshot.error -> item { ScheduleNotice("Schedules couldn’t load. Check free storage, then refresh.", isError = true) }
                        else -> {
                            if (snapshot.schedules.isEmpty()) item {
                                ScheduleCard {
                                    Text(JustnoiseContract.text("schedule.list.empty"), color = ScheduleSecondary, fontStyle = FontStyle.Italic)
                                }
                            }
                            items(snapshot.schedules, key = { it.id }) { schedule ->
                                ScheduleListCard(
                                    schedule, snapshot.executions.find { it.scheduleId == schedule.id }, snapshot,
                                    canMutate, { onEdit(schedule) }, { onToggle(schedule, it) },
                                )
                            }
                            if (state.loading || state.saving) item { CircularProgressIndicator() }
                        }
                    }
                    if (snapshot?.error == true || state.mutationUncertain) item {
                        TextButton(onClick = onRefresh, enabled = !state.loading && !state.saving,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("schedules-refresh")) { Text("Refresh") }
                    }
                }
            }
        }
        if (state.editor != null) {
            IntegratedStableSheet(
                mayDismiss = mayDismissEditor, onDismiss = onCancelEditor, tag = "schedule-editor-sheet",
                containerColor = ScheduleBackground,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onCancelEditor, enabled = !state.saving,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-cancel")) {
                            Text(stringResource(R.string.integrated_schedule_cancel))
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = onSave,
                            enabled = state.canEditDraft && snapshot?.consentAccepted == true && !snapshot.error,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-save"),
                        ) {
                            Text(stringResource(if (state.saving) R.string.integrated_schedule_saving else R.string.integrated_schedule_save),
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (snapshot == null) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            CircularProgressIndicator()
                            Text("Loading current setup…")
                        }
                    } else {
                        ScheduleEditorForm(
                            state, onEditorChange, onReloadEditor, onRefresh, onOpenSetup, onAlarmAccess,
                            onDelete = { editingStored?.let { deleting = it } },
                        )
                    }
                }
            }
        }
        deleting?.let { schedule ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Delete ${schedule.name}?") },
                text = { Text("This removes future starts for this schedule. It does not stop an active session; tap your Zap to stop it.") },
                confirmButton = {
                    TextButton(onClick = { deleting = null; onDelete(schedule) }, enabled = canMutate,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-delete-confirm")) { Text("Delete schedule") }
                },
                dismissButton = { TextButton(onClick = { deleting = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun ScheduleEditorForm(
    state: IntegratedScheduleState,
    onChange: (IntegratedScheduleEditor) -> Unit,
    onReload: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSetup: () -> Unit,
    onAlarmAccess: () -> Unit,
    onDelete: () -> Unit,
) {
    val editor = state.editor ?: return
    val snapshot = state.snapshot
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val editable = state.canEditDraft
    var dateDialog by remember { mutableStateOf<DatePickerDialog?>(null) }
    var timeDialog by remember { mutableStateOf<TimePickerDialog?>(null) }
    var confirmingReload by remember { mutableStateOf(false) }
    var choosingMode by remember(editor.id) { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { dateDialog?.dismiss(); timeDialog?.dismiss() }
    }
    Column(
        modifier = Modifier.fillMaxSize().testTag("schedule-editor-form")
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(if (editor.id == null) R.string.integrated_schedule_new_title else R.string.integrated_schedule_edit_title),
            fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        state.message?.let { ScheduleNotice(it) }
        state.editorError?.let { ScheduleNotice(it, isError = true) }
        if (state.mutationUncertain) ScheduleCard {
            Text("A save may have finished. Return to the list and refresh before creating another schedule, or reload this saved schedule.")
            TextButton(onClick = onRefresh, enabled = !state.loading && !state.saving,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh saved schedules") }
        }
        ScheduleSectionLabel(stringResource(R.string.integrated_schedule_details))
        ScheduleCard {
            OutlinedTextField(
                value = editor.name, onValueChange = { onChange(editor.copy(name = it)) },
                label = { Text("Schedule name") }, enabled = editable,
                modifier = Modifier.fillMaxWidth().testTag("schedule-name"),
            )
            if (!editor.isRepeating) {
                val dateLabel = runCatching {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(LocalDate.parse(editor.localDate))
                }.getOrDefault("Choose date")
                SchedulePickerRow("Date", dateLabel, editable, "schedule-date") {
                    val date = runCatching { LocalDate.parse(editor.localDate) }.getOrElse { LocalDate.now() }
                    dateDialog?.dismiss()
                    dateDialog = DatePickerDialog(context, { _, year, month, day ->
                        onChange(editor.copy(localDate = LocalDate.of(year, month + 1, day).toString()))
                    }, date.year, date.monthValue - 1, date.dayOfMonth).also { it.show() }
                }
                HorizontalDivider(color = ScheduleBackground)
            }
            val timeLabel = runCatching {
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(LocalTime.of(editor.hour, editor.minute))
            }.getOrDefault("Choose time")
            SchedulePickerRow("Time", timeLabel, editable, "schedule-time") {
                timeDialog?.dismiss()
                timeDialog = TimePickerDialog(context, { _, hour, minute ->
                    onChange(editor.copy(hour = hour, minute = minute))
                }, editor.hour.coerceIn(0, 23), editor.minute.coerceIn(0, 59), DateFormat.is24HourFormat(context))
                    .also { it.show() }
            }
            HorizontalDivider(color = ScheduleBackground)
            val mode = snapshot?.modeForReference(editor.modeReference)
            SchedulePickerRow(
                stringResource(R.string.integrated_schedule_mode_label),
                mode?.name ?: stringResource(if (editor.modeReference.isNullOrBlank())
                    R.string.integrated_schedule_mode_choose else R.string.integrated_schedule_mode_missing_label),
                editable, "schedule-mode",
            ) { choosingMode = true }
            mode?.let { ScheduleModeTargets(it) }
            if (snapshot != null && !snapshot.error) editor.modeProblem(snapshot.modes)?.let { problem ->
                Text(stringResource(when (problem) {
                    IntegratedScheduleModeProblem.REQUIRED -> R.string.integrated_schedule_mode_required
                    IntegratedScheduleModeProblem.UNAVAILABLE -> R.string.integrated_schedule_mode_unavailable
                    IntegratedScheduleModeProblem.EMPTY -> R.string.integrated_schedule_mode_empty
                }), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
        }
        ScheduleSectionLabel(stringResource(R.string.integrated_schedule_repeat))
        ScheduleCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.Black, selectedLabelColor = Color.White,
                )
                FilterChip(selected = !editor.isRepeating,
                    onClick = { onChange(editor.copy(isRepeating = false)) }, enabled = editable,
                    colors = chipColors, label = { Text("One time") },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-one-time"))
                FilterChip(selected = editor.isRepeating,
                    onClick = { onChange(editor.copy(isRepeating = true)) }, enabled = editable,
                    colors = chipColors, label = { Text("Repeat weekly") },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-repeating"))
            }
            if (editor.isRepeating) {
                ScheduleWeekdays(editor.weekdays, editable) { day, selected ->
                    onChange(editor.copy(weekdays = if (selected) editor.weekdays + day else editor.weekdays - day))
                }
            }
        }
        ScheduleCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Schedule enabled", fontWeight = FontWeight.Medium)
                }
                Switch(checked = editor.enabled, onCheckedChange = { onChange(editor.copy(enabled = it)) }, enabled = editable,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-editor-enabled")
                        .semantics { contentDescription = "Schedule enabled" })
            }
        }
        if (snapshot?.error == true) ScheduleCard {
            Text("Schedules couldn’t load. Your draft is kept; check free storage, then refresh.")
            TextButton(onClick = onRefresh, enabled = !state.loading && !state.saving,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh") }
        }
        if (state.loading || state.saving) CircularProgressIndicator()
        if (editor.id != null) TextButton(
            onClick = { confirmingReload = true }, enabled = !state.loading && !state.saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("schedule-reload"),
        ) { Text("Reload saved version") }
        if (editor.id != null && snapshot?.schedules?.any {
            it.id == editor.id && it.revision == editor.expectedRevision
        } == true) {
            TextButton(
                onClick = onDelete,
                enabled = editable && snapshot.consentAccepted && !snapshot.error,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("schedule-delete"),
            ) { Text("Delete schedule", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (choosingMode) AlertDialog(
        onDismissRequest = { choosingMode = false },
        title = { Text(stringResource(R.string.integrated_schedule_mode_choose)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (snapshot?.modes.isNullOrEmpty()) Text(stringResource(R.string.integrated_schedule_modes_empty))
                snapshot?.modes.orEmpty().forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("schedule-mode-choice:${mode.id}")
                            .selectable(selected = mode.id == editor.modeReference, enabled = editable, role = Role.RadioButton) {
                                onChange(editor.copy(modeReference = mode.id))
                                choosingMode = false
                            }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode.id == editor.modeReference, onClick = null, enabled = editable)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(mode.name, fontWeight = FontWeight.Medium)
                            ScheduleModeTargets(mode)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { choosingMode = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.integrated_schedule_cancel))
            }
        },
    )
    if (confirmingReload) AlertDialog(
        onDismissRequest = { confirmingReload = false },
        title = { Text("Reload saved version?") },
        text = { Text("This discards your unsaved edits and loads the latest saved version. It does not stop an active session.") },
        confirmButton = {
            TextButton(onClick = { confirmingReload = false; onReload() }, enabled = !state.loading && !state.saving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-reload-confirm")) { Text("Reload") }
        },
        dismissButton = { TextButton(onClick = { confirmingReload = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Keep editing") } },
    )
}

@Composable
private fun ScheduleListCard(
    schedule: StoredSchedule,
    execution: ScheduleExecution?,
    snapshot: ScheduleManagementSnapshot,
    canMutate: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).testTag("schedule-row:${schedule.id}")
                    .clickable(enabled = canMutate, role = Role.Button, onClickLabel = "Edit ${schedule.name}", onClick = onEdit)
                    .padding(top = 14.dp, bottom = 14.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(schedule.name, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
                Text(scheduleTimingLabel(schedule, locale), fontSize = 15.sp, lineHeight = 20.sp, color = ScheduleSecondary)
                val mode = snapshot.modeForReference(schedule.modeReference)
                Text(mode?.name ?: stringResource(R.string.integrated_schedule_mode_missing_label),
                    fontSize = 14.sp, lineHeight = 19.sp, color = ScheduleSecondary)
                mode?.let { ScheduleModeTargets(it) }
                if (schedule.timing.repeatWeekdays.isNotEmpty()) ScheduleWeekdayDots(schedule.timing.repeatWeekdays)
                val status = when {
                    !schedule.enabled -> null
                    snapshot.modeForReference(schedule.modeReference)?.selectedPackages.isNullOrEmpty() -> "Choose a mode"
                    execution?.pending == null -> "No upcoming start"
                    else -> null
                }
                status?.let { Text(it, color = ScheduleSecondary, fontSize = 12.sp, lineHeight = 17.sp) }
                if (schedule.enabled) execution?.pending?.let { pending ->
                    Text(scheduleNextStartLabel(pending.epochMilliseconds, pending.zoneId, locale),
                        color = ScheduleSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Switch(checked = schedule.enabled, onCheckedChange = onToggle, enabled = canMutate,
                modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-enabled:${schedule.id}")
                    .semantics { contentDescription = "Enable ${schedule.name}" })
        }
    }
}

@Composable
private fun ScheduleSectionLabel(label: String) {
    Text(label, fontSize = 13.sp, color = ScheduleSecondary,
        modifier = Modifier.padding(start = 16.dp).semantics { heading() })
}

@Composable
private fun SchedulePickerRow(label: String, value: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(vertical = 10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)) {
        if (LocalDensity.current.fontScale > 1.3f) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, fontSize = 17.sp)
                Text(value, fontSize = 17.sp, color = ScheduleSecondary)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 17.sp, modifier = Modifier.weight(0.3f))
                Text(value, fontSize = 17.sp, color = ScheduleSecondary, textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.7f))
            }
        }
    }
}

@Composable
private fun ScheduleWeekdays(selected: Set<PortableWeekday>, enabled: Boolean, onToggle: (PortableWeekday, Boolean) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val circleSize = (34f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    val touchSize = circleSize.coerceAtLeast(48.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= touchSize * 7) 7 else 4
        FlowRow(maxItemsInEachRow = perRow, horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PortableWeekday.entries.forEach { day ->
                val weekday = DayOfWeek.valueOf(day.name)
                val checked = day in selected
                Box(
                    Modifier.size(touchSize).testTag("schedule-day:${day.name}")
                        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(day, it) })
                        .semantics { contentDescription = weekday.getDisplayName(TextStyle.FULL, locale) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(circleSize).background(
                        if (checked) Color.Black else Color(0xFFE5E5EA), CircleShape,
                    ), contentAlignment = Alignment.Center) {
                        Text(weekday.getDisplayName(TextStyle.NARROW, locale), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (checked) Color.White else if (enabled) Color.Black else ScheduleSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleWeekdayDots(selected: Set<PortableWeekday>) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // The subtitle already names the selected days; these dots are decorative.
        PortableWeekday.entries.forEach { day ->
            Box(Modifier.size(8.dp).background(if (day in selected) Color.Black else Color(0xFFD1D1D6), CircleShape))
        }
    }
}

@Composable
private fun ScheduleModeTargets(mode: StoredMode) {
    val count = mode.selectedPackages.size
    Text(if (count == 0) stringResource(R.string.integrated_schedule_mode_no_apps)
        else pluralStringResource(R.plurals.integrated_schedule_mode_app_count, count, count),
        color = ScheduleSecondary, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
private fun ScheduleNotice(message: String, isError: Boolean = false) = ScheduleCard {
    Text(message, color = if (isError) MaterialTheme.colorScheme.error else ScheduleSecondary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun ScheduleCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

internal fun formatScheduleInstant(epoch: Long, zone: String, locale: Locale): String = try {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withZone(ZoneId.of(zone)).format(Instant.ofEpochMilli(epoch))
} catch (_: Exception) { "Time unavailable" }

internal fun scheduleNextStartLabel(epoch: Long, zone: String, locale: Locale): String =
    "Next: ${formatScheduleInstant(epoch, zone, locale)}"

internal fun scheduleTimingLabel(schedule: StoredSchedule, locale: Locale): String {
    schedule.timing.oneTimeEpochMilliseconds?.let {
        return "Once · ${formatScheduleInstant(it, schedule.creationZoneId, locale)}"
    }
    val days = PortableWeekday.entries.filter { it in schedule.timing.repeatWeekdays }
        .joinToString(", ") { DayOfWeek.valueOf(it.name).getDisplayName(TextStyle.SHORT, locale) }
    val time = runCatching {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(
            LocalTime.of(requireNotNull(schedule.timing.hour), requireNotNull(schedule.timing.minute)),
        )
    }.getOrDefault("Time unavailable")
    return "$days · $time"
}

private fun previewScheduleSnapshot(schedules: List<StoredSchedule> = emptyList(), executions: List<ScheduleExecution> = emptyList()) =
    ScheduleManagementSnapshot(schedules = schedules, executions = executions, exactAlarmAccess = true,
        consentAccepted = true, zapPaired = true, accessibilityEnabled = true,
        modes = listOf(StoredMode("local-default", 1L, "Focus", setOf("preview.one", "preview.two", "preview.three"))),
        recentModeId = "local-default")

@Composable
private fun SchedulePreview(state: IntegratedScheduleState) {
    JustnoiseIntegratedTheme {
        IntegratedScheduleSurface(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
    }
}

@Preview(name = "Schedules empty", widthDp = 384, heightDp = 832)
@Composable
private fun ScheduleEmptyPreview() = SchedulePreview(IntegratedScheduleState(snapshot = previewScheduleSnapshot()))

@Preview(name = "Schedules saved", widthDp = 384, heightDp = 832)
@Composable
private fun ScheduleListPreview() {
    val schedule = StoredSchedule("00000000-0000-0000-0000-000000000001", 1L, "Morning focus", true,
        ScheduleTiming(repeatWeekdays = setOf(PortableWeekday.MONDAY, PortableWeekday.WEDNESDAY, PortableWeekday.FRIDAY), hour = 9, minute = 0),
        "Europe/Berlin")
    val previous = ScheduledOccurrence(schedule.id, 1L, "repeating:2026-09-02:09:00",
        1_788_332_400_000L, "2026-09-02", "Europe/Berlin")
    val next = ScheduledOccurrence(schedule.id, 1L, "repeating:2026-09-04:09:00",
        1_788_505_200_000L, "2026-09-04", "Europe/Berlin")
    val execution = ScheduleExecution(schedule.id, pending = next, lastOutcome =
        ScheduleOutcome(ScheduleOutcomeCode.STARTED, previous, previous.epochMilliseconds, "preview-session"))
    SchedulePreview(IntegratedScheduleState(snapshot = previewScheduleSnapshot(listOf(schedule), listOf(execution))))
}

@Preview(name = "Schedule editor", widthDp = 384, heightDp = 832)
@Composable
private fun ScheduleEditorPreview() = SchedulePreview(IntegratedScheduleState(
    snapshot = previewScheduleSnapshot(),
    editor = IntegratedScheduleEditor(name = "Morning focus", isRepeating = true, localDate = "2026-09-04", hour = 9, minute = 0,
        weekdays = setOf(PortableWeekday.MONDAY, PortableWeekday.WEDNESDAY, PortableWeekday.FRIDAY), enabled = true,
        zoneId = "Europe/Berlin", modeReference = "local-default"),
))
