package com.justnoise.integrated

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.data.SessionHistorySnapshot
import com.justnoise.uiprototype.PortableHistoryDurationStyle
import com.justnoise.uiprototype.PortableHistoryRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class IntegratedHistoryState(
    val snapshot: SessionHistorySnapshot? = null,
    val loading: Boolean = false,
)

@Composable
fun IntegratedHistorySurface(
    state: IntegratedHistoryState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val activity = LocalActivity.current
    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    val locale = LocalLocale.current.platformLocale
    val zone = ZoneId.systemDefault()
    val dateFormatter = remember(locale, zone) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
    }
    val snapshot = state.snapshot

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    text = "History",
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics { heading() },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onRetry, enabled = !state.loading) { Text("Refresh") }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = "Completed sessions saved on this phone. Session time is elapsed " +
                            "duration, not a guarantee of continuous blocking. Active sessions appear after they end.",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }

                when {
                    snapshot == null || (state.loading && snapshot.error) -> item {
                        HistoryLoading()
                    }
                    snapshot.error -> item {
                        HistoryNotice(
                            title = "History couldn’t load",
                            body = "We couldn’t read or save your sessions. Check that your phone has free storage, then retry.",
                            action = onRetry,
                        )
                    }
                    else -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HistoryTotal(
                                    label = "Sessions",
                                    value = snapshot.totalCount.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                                HistoryTotal(
                                    label = "Session time",
                                    value = PortableHistoryRules.formatDuration(
                                        snapshot.totalDurationMilliseconds / 1_000.0,
                                        PortableHistoryDurationStyle.SUMMARY,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (snapshot.interruptedCount > 0) {
                            item {
                                Text(
                                    text = "${snapshot.interruptedCount} " +
                                        if (snapshot.interruptedCount == 1) {
                                            "session includes a known or uncertain interruption."
                                        } else {
                                            "sessions include known or uncertain interruptions."
                                        },
                                    color = Color.White.copy(alpha = 0.68f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        if (snapshot.entries.isEmpty()) {
                            item {
                                HistoryNotice(
                                    title = "No completed sessions yet",
                                    body = "Start a session with your paired Zap, then tap it again to finish. " +
                                        "New completed sessions will appear here.",
                                )
                            }
                        } else {
                            items(snapshot.entries, key = { it.id }) { entry ->
                                HistoryEntry(entry, dateFormatter)
                            }
                        }
                        if (state.loading) {
                            item { HistoryLoading() }
                        } else if (snapshot.hasMore) {
                            item {
                                Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                                    Text("Load earlier sessions")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTotal(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.07f),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.68f), fontSize = 13.sp)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoryEntry(entry: SessionHistoryEntry, formatter: DateTimeFormatter) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = formatter.format(Instant.ofEpochMilli(entry.startedAtEpochMilliseconds)),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(
                text = PortableHistoryRules.formatDuration(
                    entry.durationMilliseconds / 1_000.0,
                    PortableHistoryDurationStyle.DAY_DETAIL,
                ) + " · " + if (entry.targetCount == 1) "1 app" else "${entry.targetCount} apps",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 14.sp,
            )
            if (entry.interrupted) {
                Text("Blocking interrupted", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HistoryLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp).semantics { contentDescription = "Loading History" },
        )
    }
}

@Composable
private fun HistoryNotice(title: String, body: String, action: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Color.White.copy(alpha = 0.68f), fontSize = 14.sp, lineHeight = 20.sp)
        if (action != null) Button(onClick = action) { Text("Retry") }
    }
}

@Preview(name = "History empty", widthDp = 384, heightDp = 832)
@Composable
private fun HistoryEmptyPreview() {
    JustnoiseIntegratedTheme {
        IntegratedHistorySurface(IntegratedHistoryState(SessionHistorySnapshot()), {}, {}, {})
    }
}

@Preview(name = "History error", widthDp = 384, heightDp = 832)
@Composable
private fun HistoryErrorPreview() {
    JustnoiseIntegratedTheme {
        IntegratedHistorySurface(IntegratedHistoryState(SessionHistorySnapshot(error = true)), {}, {}, {})
    }
}

@Preview(name = "History loading", widthDp = 384, heightDp = 832)
@Composable
private fun HistoryLoadingPreview() {
    JustnoiseIntegratedTheme {
        IntegratedHistorySurface(IntegratedHistoryState(loading = true), {}, {}, {})
    }
}
