package com.justnoise.integrated

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justnoise.gate0.R
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.uiprototype.PortableHistoryDurationStyle
import com.justnoise.uiprototype.PortableHistoryRules

/** Displays one exact committed record. No stop, history write or adapter action occurs here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegratedCompletionSurface(
    entry: SessionHistoryEntry,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        contentColor = Color.Black,
        // A confirmed stop returns to dark idle Zap. The sheet owns a separate
        // window, so its white lower surface must not darken the status icons above it.
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = false,
            isAppearanceLightNavigationBars = true,
        ),
        dragHandle = {
            Box(Modifier.padding(top = 8.dp, bottom = 16.dp).width(42.dp).height(5.dp)
                .background(Color.Black.copy(alpha = .18f), RoundedCornerShape(50)))
        },
    ) {
        CompletionContent(entry, onDismiss, modifier)
    }
}

@Composable
private fun CompletionContent(entry: SessionHistoryEntry, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val duration = PortableHistoryRules.formatDuration(entry.durationMilliseconds / 1_000.0,
        PortableHistoryDurationStyle.SUMMARY)
    Column(modifier.fillMaxWidth().heightIn(min = 360.dp).testTag("completion-sheet")
        .verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.completion_title), color = Color.Black.copy(alpha = .55f),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(pluralStringResource(R.plurals.completion_selected_apps, entry.targetCount, entry.targetCount),
                color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.testTag("completion-targets"))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(duration, color = Color.Black, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.testTag("completion-duration"))
            Text(stringResource(R.string.completion_session_time), color = Color.Black.copy(alpha = .55f),
                fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("completion-done"),
            shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black, contentColor = Color.White)) {
            Text(stringResource(R.string.completion_done), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Preview(name = "Completed session · synthetic presentation", widthDp = 384, heightDp = 640)
@Composable
private fun CompletionPreview() {
    // Preview-only value: never inserted into History or used by real session transitions.
    JustnoiseIntegratedTheme {
        CompletionContent(SessionHistoryEntry("preview-only", 0L, 1_500_000L, 1_500_000L, 2, false), {})
    }
}

@Preview(name = "Completion warnings · large text", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun CompletionWarningsPreview() {
    JustnoiseIntegratedTheme {
        CompletionContent(SessionHistoryEntry("preview-only", 0L, 1_200_000L, 1_500_000L, 2, true), {})
    }
}
