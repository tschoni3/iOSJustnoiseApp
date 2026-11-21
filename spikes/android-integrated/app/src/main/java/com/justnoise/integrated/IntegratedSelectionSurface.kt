package com.justnoise.integrated

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justnoise.gate0.R
import com.justnoise.gate0.feature.AppSelectionChoice

/** Native app-selection sheet. It renders only manager-backed choices supplied by its owner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegratedSelectionSheet(
    state: IntegratedSelectionState,
    onToggle: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return
    val mayDismiss by rememberUpdatedState(!state.saving)
    var confirmingReload by remember { mutableStateOf(false) }
    IntegratedStableSheet(
        mayDismiss = mayDismiss, onDismiss = onDismiss, tag = "selection-sheet",
    ) {
        SelectionSheetContent(
            state = state,
            onToggle = onToggle,
            onSave = onSave,
            onReload = { if (state.hasChanges) confirmingReload = true else onReload() },
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (confirmingReload) {
        AlertDialog(
            onDismissRequest = { confirmingReload = false },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color(0xFF636366),
            title = { Text(stringResource(R.string.integrated_selection_reload_title)) },
            text = { Text(stringResource(R.string.integrated_selection_reload_body)) },
            confirmButton = {
                TextButton(onClick = { confirmingReload = false; onReload() }) {
                    Text(stringResource(R.string.integrated_selection_reload), color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReload = false }) {
                    Text(stringResource(R.string.integrated_selection_keep_editing), color = Color.Black)
                }
            },
        )
    }
}

@Composable
private fun SelectionSheetContent(
    state: IntegratedSelectionState,
    onToggle: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Color.White)) {
        SelectionSheetHeader(state.saving, onDismiss)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("selection-app-list"),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.integrated_selection_prompt),
                    color = Color.Black, fontSize = 28.sp, lineHeight = 35.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp)
                        .semantics { heading() },
                )
                if (!state.loading && state.problem != IntegratedSelectionProblem.LOAD_FAILED) {
                    Text(
                        pluralStringResource(R.plurals.integrated_selection_count, state.draft.size, state.draft.size),
                        color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            .background(Color(0xFFF2F2F7), RoundedCornerShape(14.dp)).padding(16.dp),
                    )
                }
            }
            if (state.problem != null) {
                item {
                    Text(
                        selectionProblemText(state.problem),
                        color = Color(0xFF8A241B), fontSize = 15.sp, lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            if (state.loading) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 2.dp)
                        Text(stringResource(R.string.integrated_selection_loading), color = Color(0xFF636366))
                    }
                }
            } else if (state.choices.isEmpty() && state.problem == null) {
                item {
                    Text(
                        stringResource(R.string.integrated_selection_empty), color = Color(0xFF636366),
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
            items(state.choices, key = AppSelectionChoice::packageName) { choice ->
                SelectionChoiceRow(choice, choice.packageName in state.draft, state.canEdit, onToggle)
            }
        }
        HorizontalDivider(color = Color(0xFFE5E5EA))
        Button(
            onClick = if (state.reloadRequired) onReload else onSave,
            enabled = !state.loading && !state.saving && (state.canSave || state.reloadRequired),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                .heightIn(min = 54.dp).testTag("selection-primary"),
            shape = RoundedCornerShape(50.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black, contentColor = Color.White,
                disabledContainerColor = Color(0xFFE5E5EA), disabledContentColor = Color(0xFF636366),
            ),
        ) {
            if (state.saving) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), color = Color.Black, strokeWidth = 2.dp)
            }
            Text(
                stringResource(
                    if (state.saving) R.string.integrated_selection_saving
                    else if (state.reloadRequired) R.string.integrated_selection_reload
                    else R.string.integrated_selection_save,
                ),
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SelectionSheetHeader(saving: Boolean, onDismiss: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The iOS-style centered header needs separate rows when enlarged text would
        // make Cancel intrude into the title's fixed normal-size side gutters.
        val stacked = LocalDensity.current.fontScale > 1.3f || maxWidth < 360.dp
        Column(Modifier.fillMaxWidth()) {
            if (stacked) SelectionCancelButton(saving, onDismiss)
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                if (!stacked) {
                    SelectionCancelButton(saving, onDismiss, Modifier.align(Alignment.CenterStart))
                }
                Text(
                    stringResource(R.string.integrated_selection_title),
                    fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, color = Color.Black,
                    modifier = Modifier.padding(horizontal = if (stacked) 24.dp else 88.dp, vertical = 12.dp)
                        .semantics { heading() },
                )
            }
        }
    }
}

@Composable
private fun SelectionCancelButton(saving: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onDismiss, enabled = !saving, modifier = modifier.testTag("selection-cancel")) {
        Text(stringResource(R.string.integrated_selection_cancel), color = if (saving) Color.Gray else Color.Black)
    }
}

@Composable
private fun SelectionChoiceRow(
    choice: AppSelectionChoice,
    selected: Boolean,
    canEdit: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    val enabled = canEdit && (choice.available || selected)
    Row(
        modifier = Modifier.fillMaxWidth().testTag("selection-app:${choice.packageName}").toggleable(
            value = selected, enabled = enabled, role = Role.Checkbox,
            onValueChange = { onToggle(choice.packageName, it) },
        ).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                choice.label ?: choice.packageName,
                color = if (enabled) Color.Black else Color(0xFF636366), fontSize = 17.sp, lineHeight = 23.sp,
            )
            if (!choice.available) {
                Text(
                    stringResource(R.string.integrated_selection_removal_label),
                    color = Color(0xFF636366), fontSize = 13.sp, lineHeight = 19.sp,
                )
            }
        }
        Checkbox(
            checked = selected, onCheckedChange = null, enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = Color.Black, checkmarkColor = Color.White,
                uncheckedColor = Color(0xFF8E8E93), disabledUncheckedColor = Color(0xFFC7C7CC),
                disabledCheckedColor = Color(0xFF8E8E93),
            ),
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Color(0xFFE5E5EA))
}

@Composable
private fun selectionProblemText(problem: IntegratedSelectionProblem): String = stringResource(
    when (problem) {
        IntegratedSelectionProblem.LOAD_FAILED -> R.string.integrated_selection_load_failed
        IntegratedSelectionProblem.SAVE_FAILED -> R.string.integrated_selection_save_failed
        IntegratedSelectionProblem.CONFLICT -> R.string.integrated_selection_conflict
        IntegratedSelectionProblem.ACTIVE_SESSION -> R.string.integrated_selection_active
        IntegratedSelectionProblem.RECOVERY_REQUIRED -> R.string.integrated_selection_recovery
        IntegratedSelectionProblem.INVALID_SELECTION -> R.string.integrated_selection_invalid
        IntegratedSelectionProblem.REMOVAL_ONLY -> R.string.integrated_selection_removal_only
    },
)

@Preview(name = "Selection — loading", widthDp = 390, heightDp = 740)
@Composable
private fun SelectionLoadingPreview() {
    SelectionSheetContent(
        IntegratedSelectionState(visible = true, loading = true), { _, _ -> }, {}, {}, {}, Modifier.fillMaxSize(),
    )
}

@Preview(name = "Selection — unavailable", widthDp = 390, heightDp = 740, fontScale = 1.5f)
@Composable
private fun SelectionUnavailablePreview() {
    SelectionSheetContent(
        IntegratedSelectionState(visible = true, problem = IntegratedSelectionProblem.LOAD_FAILED, reloadRequired = true),
        { _, _ -> }, {}, {}, {}, Modifier.fillMaxSize(),
    )
}
