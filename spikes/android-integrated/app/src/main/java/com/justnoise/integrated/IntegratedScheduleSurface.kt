package com.justnoise.integrated

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.justnoise.androidcontract.JustnoiseContract
import com.justnoise.gate0.data.DEFAULT_SCHEDULE_MODE_REFERENCE
import com.justnoise.gate0.data.ScheduleExecution
import com.justnoise.gate0.data.ScheduleOutcome
import com.justnoise.gate0.data.ScheduleOutcomeCode
import com.justnoise.gate0.data.ScheduledOccurrence
import com.justnoise.gate0.data.StoredSchedule
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

private val ScheduleBackground = Color(0xFFF4F4F6)
private val ScheduleSecondary = Color(0xFF55555D)

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
    var deleting by remember { mutableStateOf<StoredSchedule?>(null) }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color.Black, onPrimary = Color.White, secondary = Color.Black,
        surface = Color.White, onSurface = Color.Black, background = ScheduleBackground,
    )) {
        Surface(color = ScheduleBackground, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = if (state.editor == null) onBack else onCancelEditor,
                        enabled = state.editor == null || !state.saving,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (state.editor == null) "Back" else "Cancel")
                    }
                    Text(
                        if (state.editor == null) JustnoiseContract.text("schedule.list.title") else "Schedule",
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
                        fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    )
                    if (state.editor == null) TextButton(
                        onClick = onNew, enabled = canMutate, modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("+ Add") }
                }
                if (state.editor != null && snapshot == null) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator()
                        Text("Loading current setup…")
                    }
                } else if (state.editor != null) {
                    ScheduleEditorForm(state, onEditorChange, onSave, onReloadEditor, onRefresh, onOpenSetup, onAlarmAccess)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item { ScheduleExplanation() }
                        item {
                            TextButton(onClick = onRefresh, enabled = !state.loading && !state.saving,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh") }
                        }
                        state.message?.let { message -> item { ScheduleNotice(message) } }
                        if (state.mutationUncertain) item { ScheduleNotice(
                            "A save may have finished. Refresh the saved schedules before trying again.",
                        ) }
                        when {
                            snapshot == null || (state.loading && snapshot.error) -> item { CircularProgressIndicator() }
                            snapshot.error -> item { ScheduleNotice("Schedules couldn’t load. Check free storage, then refresh.", isError = true) }
                            else -> {
                                item { ScheduleSetupCards(snapshot, onOpenSetup, onAlarmAccess) }
                                if (snapshot.schedules.isEmpty()) item {
                                    ScheduleCard { Text(JustnoiseContract.text("schedule.list.empty"), color = ScheduleSecondary) }
                                }
                                items(snapshot.schedules, key = { it.id }) { schedule ->
                                    ScheduleListCard(
                                        schedule, snapshot.executions.find { it.scheduleId == schedule.id }, snapshot,
                                        canMutate, { onEdit(schedule) }, { onToggle(schedule, it) }, { deleting = schedule },
                                    )
                                }
                                if (state.loading || state.saving) item { CircularProgressIndicator() }
                            }
                        }
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
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete schedule") }
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
    onSave: () -> Unit,
    onReload: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSetup: () -> Unit,
    onAlarmAccess: () -> Unit,
) {
    val editor = state.editor ?: return
    val snapshot = state.snapshot
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val editable = !state.saving && !state.mutationUncertain
    val canSave = editable && !state.loading && snapshot?.consentAccepted == true && !snapshot.error
    var dateDialog by remember { mutableStateOf<DatePickerDialog?>(null) }
    var timeDialog by remember { mutableStateOf<TimePickerDialog?>(null) }
    var confirmingReload by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { dateDialog?.dismiss(); timeDialog?.dismiss() }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScheduleExplanation()
        state.message?.let { ScheduleNotice(it) }
        state.editorError?.let { ScheduleNotice(it, isError = true) }
        if (state.mutationUncertain) ScheduleCard {
            Text("A save may have finished. Return to the list and refresh before creating another schedule, or reload this saved schedule.")
            TextButton(onClick = onRefresh, enabled = !state.loading && !state.saving,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh saved schedules") }
        }
        ScheduleCard {
            OutlinedTextField(
                value = editor.name, onValueChange = { onChange(editor.copy(name = it)) },
                label = { Text("Schedule name") }, enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("When", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !editor.isRepeating,
                    onClick = { onChange(editor.copy(isRepeating = false)) }, enabled = editable,
                    label = { Text("One time") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = editor.isRepeating,
                    onClick = { onChange(editor.copy(isRepeating = true)) }, enabled = editable,
                    label = { Text("Repeat weekly") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            if (editor.isRepeating) {
                Text("Repeat on", fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortableWeekday.entries.forEach { day ->
                        val weekday = DayOfWeek.valueOf(day.name)
                        FilterChip(
                            selected = day in editor.weekdays,
                            onClick = { onChange(editor.copy(weekdays = if (day in editor.weekdays) editor.weekdays - day else editor.weekdays + day)) },
                            enabled = editable,
                            label = { Text(weekday.getDisplayName(TextStyle.SHORT, locale)) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                contentDescription = weekday.getDisplayName(TextStyle.FULL, locale)
                            },
                        )
                    }
                }
                Text("Follows your device time zone: ${ZoneId.systemDefault().id}", color = ScheduleSecondary, fontSize = 14.sp)
            } else {
                val dateLabel = runCatching {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(LocalDate.parse(editor.localDate))
                }.getOrDefault("Choose date")
                OutlinedButton(onClick = {
                    val date = runCatching { LocalDate.parse(editor.localDate) }.getOrElse { LocalDate.now() }
                    dateDialog?.dismiss()
                    dateDialog = DatePickerDialog(context, { _, year, month, day ->
                        onChange(editor.copy(localDate = LocalDate.of(year, month + 1, day).toString()))
                    }, date.year, date.monthValue - 1, date.dayOfMonth).also { it.show() }
                }, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Date: $dateLabel")
                }
                Text("Creation time zone: ${editor.zoneId}. This one-time start keeps its instant if you travel.",
                    color = ScheduleSecondary, fontSize = 14.sp)
            }
            val timeLabel = runCatching {
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(LocalTime.of(editor.hour, editor.minute))
            }.getOrDefault("Choose time")
            OutlinedButton(onClick = {
                timeDialog?.dismiss()
                timeDialog = TimePickerDialog(context, { _, hour, minute ->
                    onChange(editor.copy(hour = hour, minute = minute))
                }, editor.hour.coerceIn(0, 23), editor.minute.coerceIn(0, 59), DateFormat.is24HourFormat(context))
                    .also { it.show() }
            }, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Start time: $timeLabel")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Schedule enabled", fontWeight = FontWeight.Medium)
                    Text("Requests future starts; setup is checked again when due.", color = ScheduleSecondary, fontSize = 14.sp)
                }
                Switch(checked = editor.enabled, onCheckedChange = { onChange(editor.copy(enabled = it)) }, enabled = editable,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Schedule enabled" })
            }
        }
        ScheduleCard {
            Text(when (val count = snapshot?.selectedAppCount) {
                null -> "Loading saved app selection…"
                0 -> "No apps selected"
                1 -> "Uses your saved selection: 1 app"
                else -> "Uses your saved selection: $count apps"
            }, fontWeight = FontWeight.Medium)
            Text("Each scheduled start uses the selection saved at that time. An already-started session keeps its own app selection.",
                color = ScheduleSecondary, fontSize = 14.sp)
        }
        if (snapshot != null && !snapshot.error) ScheduleSetupCards(
            snapshot, onOpenSetup, onAlarmAccess,
            alarmRequested = editor.enabled || snapshot.schedules.any { it.enabled },
        )
        if (snapshot?.error == true) ScheduleCard {
            Text("Schedules couldn’t load. Your draft is kept; check free storage, then refresh.")
            TextButton(onClick = onRefresh, enabled = !state.loading && !state.saving,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh") }
        }
        if (state.loading || state.saving) CircularProgressIndicator()
        Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (state.saving) "Saving…" else "Save schedule")
        }
        if (editor.id != null) TextButton(
            onClick = { confirmingReload = true }, enabled = !state.loading && !state.saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Reload saved version") }
    }
    if (confirmingReload) AlertDialog(
        onDismissRequest = { confirmingReload = false },
        title = { Text("Reload saved version?") },
        text = { Text("This discards your unsaved edits and loads the latest saved version. It does not stop an active session.") },
        confirmButton = {
            TextButton(onClick = { confirmingReload = false; onReload() }, enabled = !state.loading && !state.saving,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Reload") }
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
    onDelete: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    ScheduleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(schedule.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(checked = schedule.enabled, onCheckedChange = onToggle, enabled = canMutate,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Enable ${schedule.name}" })
        }
        Text(scheduleTimingLabel(schedule, locale), color = ScheduleSecondary)
        Text(when {
            !schedule.enabled -> "Disabled"
            schedule.modeReference != DEFAULT_SCHEDULE_MODE_REFERENCE || snapshot.hasSetupGap() -> "Enabled · setup required"
            execution?.pending == null -> "Enabled · no upcoming occurrence"
            else -> "Enabled · checked again when due"
        }, fontWeight = FontWeight.Medium)
        if (schedule.enabled) execution?.pending?.let { pending ->
            Text("Next planned: ${formatScheduleInstant(pending.epochMilliseconds, pending.zoneId, locale)} · ${pending.zoneId}",
                color = ScheduleSecondary, fontSize = 14.sp)
        }
        execution?.lastOutcome?.let { outcome ->
            Text("Last result: ${outcomeLabel(outcome.code)}\n${formatScheduleInstant(outcome.handledAtEpochMilliseconds, ZoneId.systemDefault().id, locale)}",
                color = ScheduleSecondary, fontSize = 13.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit, enabled = canMutate, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
            TextButton(onClick = onDelete, enabled = canMutate, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete") }
        }
    }
}

@Composable
private fun ScheduleSetupCards(
    snapshot: ScheduleManagementSnapshot,
    onSetup: () -> Unit,
    onAlarmAccess: () -> Unit,
    alarmRequested: Boolean = snapshot.schedules.any { it.enabled },
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!snapshot.consentAccepted) ScheduleCard {
            Text("Review setup first", fontWeight = FontWeight.SemiBold)
            Text("Review and accept the current disclosure before creating or changing schedules.")
            Button(onClick = onSetup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open setup") }
        } else {
            if (alarmRequested && !snapshot.exactAlarmAccess) ScheduleCard {
                Text("Alarm access required", fontWeight = FontWeight.SemiBold)
                Text("Allow alarms and reminders for requested start times. Saving a schedule alone does not grant this access.")
                Button(onClick = onAlarmAccess, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow alarms & reminders") }
            }
            val gaps = buildList {
                if (!snapshot.zapPaired) add("Pair your Zap so you can stop sessions.")
                if (snapshot.selectedAppCount == 0) add("Select apps to block.")
                if (!snapshot.accessibilityEnabled) add("Enable Accessibility in setup.")
                if (snapshot.recoveryRequired || snapshot.suspended) add("Recovery is required before scheduled starts can resume.")
            }
            if (gaps.isNotEmpty()) ScheduleCard {
                Text("Setup needed", fontWeight = FontWeight.SemiBold)
                gaps.forEach { Text(it) }
                Button(onClick = onSetup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open setup") }
            }
        }
    }
}

@Composable
private fun ScheduleExplanation() {
    Text("Start automatically at your chosen time. Tap your paired Zap to stop. Each start uses your saved app selection. " +
        "Android may delay delivery; a planned time is not a guarantee of blocking.", color = ScheduleSecondary, fontSize = 14.sp)
}

@Composable
private fun ScheduleNotice(message: String, isError: Boolean = false) = ScheduleCard {
    Text(message, color = if (isError) MaterialTheme.colorScheme.error else ScheduleSecondary)
}

@Composable
private fun ScheduleCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

private fun ScheduleManagementSnapshot.hasSetupGap(): Boolean = !consentAccepted || !exactAlarmAccess ||
    !zapPaired || selectedAppCount == 0 || !accessibilityEnabled || recoveryRequired || suspended

private fun formatScheduleInstant(epoch: Long, zone: String, locale: Locale): String = try {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withZone(ZoneId.of(zone)).format(Instant.ofEpochMilli(epoch))
} catch (_: Exception) { "Time unavailable" }

private fun scheduleTimingLabel(schedule: StoredSchedule, locale: Locale): String {
    schedule.timing.oneTimeEpochMilliseconds?.let {
        return "Once · ${formatScheduleInstant(it, schedule.creationZoneId, locale)} · ${schedule.creationZoneId}"
    }
    val days = PortableWeekday.entries.filter { it in schedule.timing.repeatWeekdays }
        .joinToString(", ") { DayOfWeek.valueOf(it.name).getDisplayName(TextStyle.SHORT, locale) }
    val time = runCatching {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(
            LocalTime.of(requireNotNull(schedule.timing.hour), requireNotNull(schedule.timing.minute)),
        )
    }.getOrDefault("Time unavailable")
    return "$days · $time · device time zone"
}

private fun outcomeLabel(code: ScheduleOutcomeCode): String = when (code) {
    ScheduleOutcomeCode.STARTED -> "A session started (not current session status)"
    ScheduleOutcomeCode.MISSED -> "Missed the start window"
    ScheduleOutcomeCode.ACTIVE_SESSION -> "Skipped; another session was already requested"
    ScheduleOutcomeCode.MODE_UNAVAILABLE -> "Saved selection unavailable"
    ScheduleOutcomeCode.EMPTY_SELECTION -> "No apps selected"
    ScheduleOutcomeCode.TARGET_UNAVAILABLE -> "Selected apps unavailable"
    ScheduleOutcomeCode.CONSENT_REQUIRED -> "Disclosure acceptance required"
    ScheduleOutcomeCode.ZAP_REQUIRED -> "Zap pairing required"
    ScheduleOutcomeCode.CAPABILITY_UNAVAILABLE -> "Blocking capability unavailable"
    ScheduleOutcomeCode.EXACT_ALARM_REQUIRED -> "Alarm access required"
    ScheduleOutcomeCode.RECOVERY_REQUIRED -> "Skipped for recovery"
}

private fun previewScheduleSnapshot(schedules: List<StoredSchedule> = emptyList(), executions: List<ScheduleExecution> = emptyList()) =
    ScheduleManagementSnapshot(schedules = schedules, executions = executions, exactAlarmAccess = true,
        consentAccepted = true, zapPaired = true, selectedAppCount = 3, accessibilityEnabled = true)

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
        weekdays = setOf(PortableWeekday.MONDAY, PortableWeekday.WEDNESDAY, PortableWeekday.FRIDAY), enabled = true, zoneId = "Europe/Berlin"),
))
