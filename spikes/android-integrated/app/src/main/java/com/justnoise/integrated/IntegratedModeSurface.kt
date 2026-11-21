package com.justnoise.integrated

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justnoise.gate0.R
import com.justnoise.gate0.data.ModeRules
import com.justnoise.gate0.data.ModeSnapshot
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.data.StoredMode
import com.justnoise.gate0.feature.AppSelectionChoice

private val ModeSecondary = Color(0xFF636366)
private val ModeBlue = Color(0xFF1467C8)
private val ModeBackground = Color(0xFFF4F4F6)

/** Real mode catalog and editor supplied by ModeManagement; no local preview reducer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegratedModeSheet(
    state: IntegratedModeState,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onName: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancelEditor: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return
    val mayDismiss by rememberUpdatedState(!state.saving)
    var confirmingDelete by remember(state.editor?.id) { mutableStateOf(false) }
    var confirmingReload by remember { mutableStateOf(false) }
    IntegratedStableSheet(
        mayDismiss = mayDismiss, onDismiss = onDismiss, tag = "modes-sheet",
    ) {
        ModeSheetContent(state, onSelect, onNew, onEdit, onName, onToggle, onSave,
            onDelete = { confirmingDelete = true }, onCancelEditor = onCancelEditor,
            onReload = { if (state.editor?.hasChanges == true) confirmingReload = true else onReload() },
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxSize())
    }
    if (confirmingDelete && state.editor?.id != null) {
        ModeConfirmation(
            title = stringResource(R.string.integrated_mode_delete_title, state.editor.name),
            body = stringResource(R.string.integrated_mode_delete_body),
            confirm = stringResource(R.string.integrated_mode_delete),
            busy = state.saving,
            onConfirm = { confirmingDelete = false; onDelete() },
            onDismiss = { confirmingDelete = false },
        )
    }
    if (confirmingReload) {
        ModeConfirmation(
            title = stringResource(R.string.integrated_mode_reload_title),
            body = stringResource(R.string.integrated_mode_reload_body),
            confirm = stringResource(R.string.integrated_mode_reload),
            busy = state.saving,
            onConfirm = { confirmingReload = false; onReload() },
            onDismiss = { confirmingReload = false },
        )
    }
}

@Composable
internal fun ModeSheetContent(
    state: IntegratedModeState,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onName: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancelEditor: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editor = state.editor
    Column(modifier.background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editor != null) TextButton(onClick = onCancelEditor, enabled = !state.saving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("mode-cancel")) {
                Text(stringResource(R.string.integrated_mode_cancel), color = ModeBlue)
            }
            Text(if (editor == null) stringResource(R.string.integrated_mode_list_title) else stringResource(
                if (editor.id == null) R.string.integrated_mode_new else R.string.integrated_mode_edit_title),
                color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f).semantics { heading() })
            if (editor == null) TextButton(onClick = onDismiss, enabled = !state.saving,
                modifier = Modifier.heightIn(min = 48.dp).testTag("modes-done")) {
                Text(stringResource(R.string.integrated_mode_done), color = ModeBlue)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(if (editor == null) "mode-list" else "mode-app-list"),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 2.dp)
                    Text(stringResource(R.string.integrated_mode_loading), color = ModeSecondary)
                }
            }
            state.problem?.let { problem -> item {
                Text(modeProblemText(problem), color = Color(0xFF8A241B), fontSize = 15.sp, lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            } }
            if (editor == null && !state.loading) {
                item {
                    Text(stringResource(R.string.integrated_mode_list_prompt), color = Color.Black, fontSize = 31.sp,
                        lineHeight = 37.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp))
                }
                items(state.catalog?.modes.orEmpty(), key = StoredMode::id) { mode ->
                    ModeRow(mode, state.catalog?.recentModeId == mode.id, state.canMutate, onSelect, onEdit)
                }
                if (state.catalog?.modes?.isEmpty() == true) item {
                    Text(stringResource(R.string.integrated_mode_empty), color = ModeSecondary,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                }
                item {
                    TextButton(onClick = onNew, enabled = state.canMutate,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("mode-new")) {
                        Text(stringResource(R.string.integrated_mode_add), color = if (state.canMutate) ModeBlue else ModeSecondary)
                    }
                }
            } else if (editor != null) {
                item {
                    OutlinedTextField(value = editor.name, onValueChange = onName, enabled = state.canMutate,
                        label = { Text(stringResource(R.string.integrated_mode_name)) }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.integrated_mode_name_hint)) },
                        isError = editor.name.isNotEmpty() && !ModeRules.validName(editor.name),
                        supportingText = if (editor.name.isNotEmpty() && !ModeRules.validName(editor.name)) {
                            { Text(stringResource(R.string.integrated_mode_invalid_name, ModeRules.MAX_NAME_LENGTH)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth().testTag("mode-name"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black, disabledTextColor = ModeSecondary,
                            focusedBorderColor = Color.Black, unfocusedBorderColor = ModeSecondary,
                            focusedLabelColor = Color.Black, unfocusedLabelColor = ModeSecondary, cursorColor = Color.Black))
                    Text(stringResource(R.string.integrated_mode_apps_heading), color = Color.Black, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                    Text(pluralStringResource(R.plurals.integrated_mode_app_count, editor.packages.size, editor.packages.size),
                        color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                            .background(ModeBackground, RoundedCornerShape(14.dp)).padding(16.dp))
                }
                items(state.choices, key = AppSelectionChoice::packageName) { choice ->
                    ModeAppRow(choice, choice.packageName in editor.packages, state.canMutate, onToggle)
                }
                if (editor.id != null) item {
                    TextButton(onClick = onDelete, enabled = state.canMutate,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("mode-delete")) {
                        Text(stringResource(R.string.integrated_mode_delete), color = if (state.canMutate) Color(0xFFB3261E) else ModeSecondary)
                    }
                }
            }
        }
        if (editor != null || state.reloadRequired) {
            HorizontalDivider(color = Color(0xFFE5E5EA))
            Button(onClick = if (state.reloadRequired) onReload else onSave,
                enabled = !state.loading && !state.saving && (state.reloadRequired || state.canSave),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                    .heightIn(min = 54.dp).testTag("mode-save"), shape = RoundedCornerShape(50.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE5E5EA), disabledContentColor = ModeSecondary)) {
                Text(stringResource(if (state.saving) R.string.integrated_mode_saving
                    else if (state.reloadRequired) R.string.integrated_mode_reload else R.string.integrated_mode_save))
            }
        } else if (state.saving) {
            Text(stringResource(R.string.integrated_mode_saving), color = ModeSecondary,
                modifier = Modifier.fillMaxWidth().padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ModeRow(mode: StoredMode, isSelected: Boolean, enabled: Boolean,
    onSelect: (String) -> Unit, onEdit: (String) -> Unit) {
    val foreground = if (isSelected) Color.White else Color.Black
    val selectLabel = stringResource(R.string.integrated_mode_select_description, mode.name)
    val editLabel = stringResource(R.string.integrated_mode_edit_description, mode.name)
    Surface(color = if (isSelected) Color.Black else ModeBackground, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).heightIn(min = 56.dp).testTag("mode-row:${mode.id}")
                .clickable(enabled = enabled, role = Role.RadioButton, onClickLabel = selectLabel) { onSelect(mode.id) }
                .semantics { selected = isSelected }.padding(vertical = 8.dp)) {
                Text(mode.name, color = foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (mode.selectedPackages.isEmpty()) stringResource(R.string.integrated_mode_no_apps)
                    else pluralStringResource(R.plurals.integrated_mode_app_count, mode.selectedPackages.size, mode.selectedPackages.size),
                    color = if (isSelected) Color(0xFFD1D1D6) else ModeSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
            if (isSelected) Text("✓", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp)
                .semantics { contentDescription = selectLabel })
            TextButton(onClick = { onEdit(mode.id) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("mode-edit:${mode.id}")
                    .semantics { contentDescription = editLabel }) {
                Text(stringResource(R.string.integrated_mode_edit), color = if (isSelected) Color.White else ModeBlue)
            }
        }
    }
}

@Composable
private fun ModeAppRow(choice: AppSelectionChoice, checked: Boolean, editable: Boolean,
    onToggle: (String, Boolean) -> Unit) {
    val enabled = editable && (choice.available || checked)
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("mode-app:${choice.packageName}")
        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) { onToggle(choice.packageName, it) }
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(choice.label?.takeIf(String::isNotBlank) ?: choice.packageName,
                color = if (enabled) Color.Black else ModeSecondary, fontSize = 16.sp)
            if (!choice.available) Text(stringResource(R.string.integrated_mode_unavailable_app),
                color = ModeSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Color.Black, checkmarkColor = Color.White,
                uncheckedColor = ModeSecondary, disabledCheckedColor = ModeSecondary))
    }
}

@Composable
private fun ModeConfirmation(title: String, body: String, confirm: String, busy: Boolean,
    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = Color.White,
        titleContentColor = Color.Black, textContentColor = ModeSecondary,
        title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy,
            modifier = Modifier.testTag("mode-confirm")) { Text(confirm, color = Color.Black) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) {
            Text(stringResource(R.string.integrated_mode_cancel), color = ModeBlue)
        } })
}

@Composable
private fun modeProblemText(problem: IntegratedModeProblem): String = stringResource(when (problem) {
    IntegratedModeProblem.LOAD_FAILED -> R.string.integrated_mode_load_failed
    IntegratedModeProblem.STORAGE_FAILED -> R.string.integrated_mode_storage_failed
    IntegratedModeProblem.ACTIVE_SESSION -> R.string.integrated_mode_active
    IntegratedModeProblem.RECOVERY_REQUIRED -> R.string.integrated_mode_recovery
    IntegratedModeProblem.CONFLICT -> R.string.integrated_mode_conflict
    IntegratedModeProblem.INVALID_NAME -> R.string.integrated_mode_invalid_name
    IntegratedModeProblem.INVALID_SELECTION -> R.string.integrated_mode_invalid_selection
    IntegratedModeProblem.MODE_UNAVAILABLE -> R.string.integrated_mode_not_found
    IntegratedModeProblem.REFERENCED_BY_SCHEDULE -> R.string.integrated_mode_referenced
    IntegratedModeProblem.LIMIT_REACHED -> R.string.integrated_mode_limit
    IntegratedModeProblem.REMOVAL_ONLY -> R.string.integrated_mode_missing_apps
}, ModeRules.MAX_NAME_LENGTH)

@Preview(name = "Real modes catalog", widthDp = 384, heightDp = 760)
@Preview(name = "Real modes compact enlarged text", widthDp = 320, heightDp = 640, fontScale = 1.6f)
@Composable
private fun ModeCatalogPreview() {
    val mode = StoredMode("preview-only", 1L, "Sleep", setOf("preview.app"))
    val state = IntegratedModeState(visible = true, catalog = ModeSnapshot(modes = listOf(mode),
        recentModeId = mode.id, token = SelectionEditToken("preview-only", 0L, 0L, null, 0L)))
    JustnoiseIntegratedTheme {
        ModeSheetContent(state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, Modifier.fillMaxHeight())
    }
}

@Preview(name = "Real mode editor", widthDp = 384, heightDp = 760)
@Composable
private fun ModeEditorPreview() {
    val state = IntegratedModeState(visible = true,
        catalog = ModeSnapshot(token = SelectionEditToken("preview-only", 0L, 0L, null, 0L)),
        editor = IntegratedModeEditor(name = "Sleep", packages = setOf("preview.app")),
        choices = listOf(AppSelectionChoice("preview.app", "Example app", true)))
    JustnoiseIntegratedTheme {
        ModeSheetContent(state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, Modifier.fillMaxHeight())
    }
}
