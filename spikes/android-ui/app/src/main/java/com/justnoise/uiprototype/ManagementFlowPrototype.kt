package com.justnoise.uiprototype

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val ManagementBackground = Color(0xFFF4F4F6)
private val ManagementDark = Color(0xFF0E0E0D)
private val ManagementBlue = Color(0xFF1467C8)
private val ManagementOrange = Color(0xFFE58B22)

@Composable
fun ManagementFlowScreen(
    route: PrototypeRoute,
    selectedMode: ModeOption,
    onAction: (PrototypeFlowAction) -> Unit,
    onModeSelected: (ModeOption) -> Unit,
) {
    val activity = LocalActivity.current
    LaunchedEffect(route) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = route != PrototypeRoute.History
                isAppearanceLightNavigationBars = true
            }
        }
    }

    when (route) {
        PrototypeRoute.Modes -> ModesManagementScreen(
            selectedMode = selectedMode,
            onSelect = onModeSelected,
            onBack = { onAction(PrototypeFlowAction.Back) },
        )
        PrototypeRoute.History -> HistoryScreen(
            onBack = { onAction(PrototypeFlowAction.Back) },
            onSettings = { onAction(PrototypeFlowAction.OpenSettings) },
        )
        PrototypeRoute.Schedules -> SchedulesScreen(
            onBack = { onAction(PrototypeFlowAction.Back) },
        )
        PrototypeRoute.Settings -> SettingsScreen(
            onBack = { onAction(PrototypeFlowAction.Back) },
        )
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModesManagementScreen(
    selectedMode: ModeOption,
    onSelect: (ModeOption) -> Unit,
    onBack: () -> Unit,
) {
    var showNewMode by rememberSaveable { mutableStateOf(false) }
    var editingModeId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAppPickerBoundary by rememberSaveable { mutableStateOf(false) }

    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            PrototypeTopBar(title = "Select Mode", onBack = onBack, action = "Done")
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "What do you want to make space for?",
                    color = Color.Black,
                    fontSize = 31.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(28.dp))
                PreviewModes.forEach { mode ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        color = if (mode.id == selectedMode.id) Color.Black else ManagementBackground,
                        contentColor = if (mode.id == selectedMode.id) Color.White else Color.Black,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = mode.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelect(mode) }
                                    .padding(vertical = 10.dp),
                            )
                            if (mode.id == selectedMode.id) {
                                Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { editingModeId = mode.id }) {
                                Text(
                                    text = "Edit",
                                    color = if (mode.id == selectedMode.id) Color.White else ManagementBlue,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { showNewMode = true }) {
                    Text("+ New Mode", color = ManagementBlue)
                }
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Blocked in this mode", color = Color.Gray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CountLabel(value = "3", label = "Apps")
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(Color.Black.copy(alpha = 0.18f)),
                            )
                            CountLabel(value = "1", label = "Category")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = { showAppPickerBoundary = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(27.dp),
                ) {
                    Text("Select apps to block", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                PrototypeNotice("UI preview only · Android app selection is not running")
            }
        }
    }

    if (showNewMode) {
        PrototypeEditorSheet(
            title = "New Mode",
            initialName = "",
            onChooseApps = { showAppPickerBoundary = true },
            onDismiss = { showNewMode = false },
        )
    }
    PreviewModes.firstOrNull { it.id == editingModeId }?.let { mode ->
        PrototypeEditorSheet(
            title = "Edit Mode",
            initialName = mode.name,
            onChooseApps = { showAppPickerBoundary = true },
            onDelete = { editingModeId = null },
            onDismiss = { editingModeId = null },
        )
    }
    if (showAppPickerBoundary) {
        BoundarySheet(
            title = "Select apps to block",
            message = "The Android system picker and restriction permission are intentionally not connected before physical Gate 0 passes.",
            onDismiss = { showAppPickerBoundary = false },
        )
    }
}

@Composable
private fun CountLabel(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun HistoryScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    var timeline by rememberSaveable { mutableStateOf("90 Days") }
    val previewSummary = PortableHistoryRules.summarize(PreviewHistoryDurationsSeconds)
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.TopCenter)
                .background(ManagementDark),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(ManagementDark)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                color = Color.White,
                fontSize = 38.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp),
            )
            TextButton(onClick = { timeline = if (timeline == "90 Days") "Year" else "90 Days" }) {
                Text("Timeline · $timeline  ⌄", color = Color.White, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "⚙",
                color = Color.White,
                fontSize = 25.sp,
                modifier = Modifier.clickable(onClick = onSettings).padding(10.dp),
            )
        }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ManagementDark, RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HistorySummaryCard(
                        "Zaps",
                        previewSummary.zapCount.toString(),
                        Modifier.weight(1f),
                    )
                    HistorySummaryCard(
                        "Protected Attention",
                        previewSummary.protectedAttentionLabel,
                        Modifier.weight(1f),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(timeline, color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Protected days", color = Color.Gray, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
                HistoryDotGrid(PreviewHistoryDayDurationsSeconds)
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = "Darker dots represent more protected attention.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                PrototypeNotice("Representative preview data · no session history was read")
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(128.dp).shadow(4.dp, RoundedCornerShape(10.dp)),
        color = Color(0xFF131312),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                title,
                color = ManagementOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(value, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoryDotGrid(dayDurationsSeconds: List<Double>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(12) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(7) { column ->
                    val index = row * 7 + column
                    val density = PortableHistoryRules.density(
                        dayDurationsSeconds.getOrElse(index) { 0.0 },
                    )
                    val color = when (density) {
                        PortableHistoryDensity.EMPTY -> JustnoiseContract.color("history.dot.empty")
                        PortableHistoryDensity.LIGHT -> JustnoiseContract.color("history.dot.light")
                        PortableHistoryDensity.MEDIUM -> JustnoiseContract.color("history.dot.medium")
                        PortableHistoryDensity.DENSE -> JustnoiseContract.color("history.dot.dense")
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
    }
}

private val PreviewHistoryDurationsSeconds = listOf(
    900.0,
    1_200.0,
    1_500.0,
    1_800.0,
    900.0,
    2_100.0,
    600.0,
    2_400.0,
    1_200.0,
    1_500.0,
    900.0,
    1_500.0,
)

private val PreviewHistoryDayDurationsSeconds = List(84) { index ->
    when {
        index % 17 == 0 -> PortableHistoryRules.DENSE_MINIMUM_SECONDS
        index % 11 == 0 -> PortableHistoryRules.MEDIUM_MINIMUM_SECONDS
        index % 5 == 0 -> PortableHistoryRules.LIGHT_MINIMUM_SECONDS
        else -> 0.0
    }
}

private data class PreviewSchedule(
    val id: String,
    val name: String,
    val detail: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulesScreen(
    onBack: () -> Unit,
    startsEmpty: Boolean = false,
) {
    val schedules = if (startsEmpty) {
        emptyList()
    } else {
        listOf(
            PreviewSchedule("morning", "Morning Focus", "Focus · 08:30 · M T W T F"),
            PreviewSchedule("study", "Study Block", "Study · 19:00 · T T"),
        )
    }
    var enabled by rememberSaveable { mutableStateOf(setOf("morning")) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingScheduleId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ManagementBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PrototypeTopBar(
            title = "Schedules",
            onBack = onBack,
            action = "+",
            onAction = {
                editingScheduleId = null
                showEditor = true
            },
        )
        Text(
            text = "Enable Screen Time access to run schedules automatically.",
            color = Color(0xFFC93232),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        if (schedules.isEmpty()) {
            Text(
                text = JustnoiseContract.text("schedule.list.empty"),
                color = Color.Gray,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
            )
        }
        schedules.forEach { schedule ->
            Surface(
                onClick = {
                    editingScheduleId = schedule.id
                    showEditor = true
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(schedule.name, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(schedule.detail, color = Color.Gray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(5.dp))
                        Text("Last used: Preview", color = Color.Gray.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    Switch(
                        checked = schedule.id in enabled,
                        onCheckedChange = { isChecked ->
                            enabled = if (isChecked) enabled + schedule.id else enabled - schedule.id
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFDFFF00),
                            checkedBorderColor = Color(0xFFDFFF00),
                            uncheckedThumbColor = Color(0xFF8B8991),
                            uncheckedTrackColor = Color(0xFF3E3C45),
                            uncheckedBorderColor = Color(0xFF8B8991),
                        ),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        PrototypeNotice("UI preview only · no background schedule is registered")
    }

    if (showEditor) {
        ScheduleEditorSheet(
            schedule = schedules.firstOrNull { it.id == editingScheduleId },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
fun EmptySchedulesScene() {
    JustnoiseTheme { SchedulesScreen(onBack = {}, startsEmpty = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorSheet(
    schedule: PreviewSchedule?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(schedule?.id) { mutableStateOf(schedule?.name.orEmpty()) }
    var repeatDays by rememberSaveable { mutableStateOf(setOf<String>()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        contentColor = Color.Black,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                if (schedule == null) "New Schedule" else "Edit Schedule",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(18.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Schedule Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(14.dp))
            PreviewFormRow("Date", "Today")
            PreviewFormRow("Time", "08:30")
            PreviewFormRow("Mode", "Focus")
            Text("REPEAT", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "T", "W", "T2", "F", "S", "S2").forEach { day ->
                    val selected = day in repeatDays
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.Black else ManagementBackground)
                            .clickable {
                                repeatDays = if (selected) repeatDays - day else repeatDays + day
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(day.take(1), color = if (selected) Color.White else Color.Black)
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            ) {
                Text("Save Preview", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            PrototypeNotice("Saving closes this preview; it does not register a schedule")
        }
    }
}

@Composable
private fun PreviewFormRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.Black, fontSize = 16.sp)
        Text(value, color = ManagementBlue, fontSize = 16.sp)
    }
    HorizontalDivider(color = Color.Black.copy(alpha = 0.08f))
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ManagementBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PrototypeTopBar(title = "Settings", onBack = onBack, action = "Done")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SettingsSection("ACCOUNT") {
                SettingsRow("Name", "Justnoise User")
                SettingsRow("Email", "preview@example.com")
                SettingsRow("Subscription", "Free")
                SettingsRow("Language", "Auto")
            }
            SettingsSection("ONBOARDING") {
                SettingsAction("Re-run Onboarding Tour") {
                    notice = "Preview only—the onboarding preference was not changed."
                }
            }
            SettingsSection("SUPPORT") {
                SettingsAction("Write a Review") { notice = "Preview only—no store page was opened." }
                SettingsAction("Community") { notice = "Preview only—no external link was opened." }
            }
            SettingsSection("ABOUT JUSTNOISE") {
                SettingsAction("About us") { notice = "Preview only—no external link was opened." }
                SettingsAction("Privacy Policy") { notice = "Preview only—no external link was opened." }
                SettingsAction("Terms of Use") { notice = "Preview only—no external link was opened." }
            }
            SettingsSection("EMERGENCY") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Emergency Unzap", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Use this if you lost your Zap. Each use consumes a token. 3 left.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                    Button(
                        onClick = { confirmation = "emergency" },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD43C35)),
                    ) {
                        Text("Use Emergency Unzap", color = Color.White)
                    }
                }
            }
            SettingsSection("DANGER ZONE") {
                SettingsAction("Delete Account", destructive = true) {
                    confirmation = "delete-account"
                }
            }
            SettingsSection("") {
                SettingsAction("Sign out", destructive = true) {
                    notice = "Preview only—the session remains signed in."
                }
            }
            if (notice != null) {
                Text(
                    text = checkNotNull(notice),
                    color = Color(0xFF6A4D00),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFE8A3), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
            PrototypeNotice("Representative account data · no backend request is sent")
        }
    }

    when (confirmation) {
        "emergency" -> PreviewConfirmationDialog(
            title = "Confirm Emergency Unzap",
            message = "This will use one of your emergency tokens (3 left). Do you want to continue?",
            confirmLabel = "Yes, Unzap",
            onConfirm = {
                confirmation = null
                notice = "Preview only—no emergency token was consumed."
            },
            onDismiss = { confirmation = null },
        )
        "delete-account" -> PreviewConfirmationDialog(
            title = "Delete Account?",
            message = "This action is permanent and cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmation = null
                notice = "Preview only—no deletion request was sent."
            },
            onDismiss = { confirmation = null },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (title.isNotBlank()) {
        Text(
            text = title,
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 14.dp, top = 22.dp, bottom = 7.dp),
        )
    } else {
        Spacer(modifier = Modifier.height(18.dp))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.Black, fontSize = 15.sp)
        Text(value, color = ManagementBlue, fontSize = 15.sp)
    }
    HorizontalDivider(color = Color.Black.copy(alpha = 0.06f))
}

@Composable
private fun SettingsAction(
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (destructive) Color(0xFFD43C35) else ManagementBlue,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrototypeEditorSheet(
    title: String,
    initialName: String,
    onChooseApps: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        contentColor = Color.Black,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(18.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Mode name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "Please enter a name for your mode.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "SELECT APPS TO BLOCK",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
            )
            Surface(
                onClick = onChooseApps,
                modifier = Modifier.fillMaxWidth(),
                color = ManagementBackground,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Choose Apps", color = Color.Black)
                    Text("3 Apps, 1 Category Selected", color = Color.Gray, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onDismiss,
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            ) {
                Text("Save Preview", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            if (onDelete != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Delete Mode", color = Color(0xFFD43C35))
                }
            }
            PrototypeNotice("Preview only · no mode is stored")
        }
    }

    if (confirmDelete && onDelete != null) {
        PreviewConfirmationDialog(
            title = "Delete Mode",
            message = "Are you sure you want to delete this mode? This action cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PreviewConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.Black, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = Color.DarkGray) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = Color(0xFFD43C35), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ManagementBlue)
            }
        },
        containerColor = Color.White,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoundarySheet(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        contentColor = Color.Black,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                message,
                color = Color.Gray,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 18.dp),
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            ) {
                Text("Done", color = Color.White)
            }
        }
    }
}

@Composable
private fun PrototypeTopBar(
    title: String,
    onBack: () -> Unit,
    action: String? = null,
    onAction: () -> Unit = onBack,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            color = ManagementBlue,
            fontSize = 38.sp,
            lineHeight = 38.sp,
            modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(title, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                text = action,
                color = ManagementBlue,
                fontSize = if (action == "+") 28.sp else 16.sp,
                modifier = Modifier.clickable(onClick = onAction).padding(8.dp),
            )
        } else {
            Spacer(modifier = Modifier.width(44.dp))
        }
    }
}

@Composable
private fun PrototypeNotice(text: String) {
    Text(
        text = text,
        color = Color.Gray.copy(alpha = 0.72f),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
    )
}

@Preview(name = "Modes", widthDp = 412, heightDp = 915)
@Composable
private fun ModesPreview() {
    JustnoiseTheme {
        ModesManagementScreen(PreviewModes.first(), {}, {})
    }
}

@Preview(name = "History", widthDp = 412, heightDp = 915)
@Composable
private fun HistoryPreview() {
    JustnoiseTheme { HistoryScreen({}, {}) }
}

@Preview(name = "Schedules", widthDp = 412, heightDp = 915)
@Composable
private fun SchedulesPreview() {
    JustnoiseTheme { SchedulesScreen({}) }
}

@Preview(name = "Schedules · Empty", widthDp = 412, heightDp = 915)
@Composable
private fun EmptySchedulesPreview() {
    EmptySchedulesScene()
}

@Preview(name = "Settings", widthDp = 412, heightDp = 915)
@Composable
private fun SettingsPreview() {
    JustnoiseTheme { SettingsScreen({}) }
}
