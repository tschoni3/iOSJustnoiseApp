package com.justnoise.integrated

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.justnoise.gate0.R
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

/** Presentation only: the owner persists progress and performs every NFC action. */
@Composable
fun IntegratedOnboardingSurface(
    page: Int,
    busy: Boolean,
    errorMessage: String?,
    onPageChange: (Int) -> Unit,
    onContinue: () -> Unit,
    replaying: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    FirstRunSystemBars(lightSurface = false)
    val selectedPage = page.coerceIn(0, 1)
    val pager = rememberPagerState(initialPage = selectedPage, pageCount = { 2 })
    val latestPage by rememberUpdatedState(selectedPage)
    val latestPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(selectedPage) {
        if (pager.currentPage != selectedPage) pager.scrollToPage(selectedPage)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { settled ->
            if (settled != latestPage) latestPageChange(settled)
        }
    }
    BackHandler(enabled = selectedPage > 0 && !busy) { onPageChange(selectedPage - 1) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val footerHeightLimit = maxHeight / 2
        Column(Modifier.fillMaxSize()) {
            FirstRunTitleBar(
                title = stringResource(R.string.first_run_how_to_use),
                foreground = Color.White,
                onBack = if (selectedPage > 0 && !busy) ({ onPageChange(0) }) else onClose,
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val pageHeight = maxHeight
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !busy,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                            .heightIn(min = pageHeight).padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterVertically),
                    ) {
                        Text(
                            text = stringResource(
                                if (index == 0) R.string.first_run_select_apps
                                else R.string.first_run_stay_focused,
                            ),
                            color = Color.White,
                            fontSize = 34.sp,
                            lineHeight = 41.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
                        )
                        IntegratedOnboardingMedia(
                            videoResource = if (index == 0) R.raw.jn_video1 else R.raw.jn_video2,
                            isActive = index == pager.settledPage && !pager.isScrollInProgress && !busy,
                            description = stringResource(
                                if (index == 0) R.string.first_run_select_video
                                else R.string.first_run_focus_video,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                                .height(250.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Text(
                            text = stringResource(
                                if (index == 0) R.string.first_run_select_apps_body
                                else R.string.first_run_stay_focused_body,
                            ),
                            color = Color(0xFF98989D),
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 30.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = footerHeightLimit)
                    .verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FirstRunPageControls(selectedPage, busy, onPageChange)
                if (!errorMessage.isNullOrBlank()) {
                    FirstRunStatus(errorMessage, Color(0xFFFFB4AB), Modifier.padding(horizontal = 30.dp))
                }
                FirstRunPrimaryButton(
                    title = stringResource(
                        if (replaying && selectedPage == 1) R.string.first_run_replay_done
                        else if (selectedPage == 0) R.string.first_run_get_started
                        else R.string.first_run_start_activating,
                    ),
                    busy = busy,
                    onClick = onContinue,
                    modifier = Modifier.padding(horizontal = 30.dp).padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
fun IntegratedActivationSurface(
    isScanning: Boolean,
    isPaired: Boolean,
    status: String?,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onContinueToSetup: () -> Unit,
    onSkip: () -> Unit,
    onPurchaseZap: () -> Unit,
    continueLabel: String? = null,
) {
    val background = if (isPaired) Color.Black else Color.White
    val foreground = if (isPaired) Color.White else Color.Black
    FirstRunSystemBars(lightSurface = !isPaired)
    BackHandler(enabled = isScanning) { onCancelScan() }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            FirstRunTitleBar(stringResource(R.string.first_run_activate_zap), foreground)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 40.dp).size(240.dp)
                        .clip(CircleShape).clickable(
                            enabled = !isScanning && !isPaired,
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.first_run_scan_zap),
                            onClick = onScan,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.zap_button),
                        contentDescription = stringResource(R.string.first_run_zap_button),
                        modifier = Modifier.size(220.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Text(
                    text = stringResource(
                        if (isPaired) R.string.first_run_zap_connected
                        else if (isScanning) R.string.first_run_hold_zap
                        else R.string.first_run_activation_title,
                    ),
                    color = foreground,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
                )
                Text(
                    text = stringResource(
                        if (isPaired) R.string.first_run_connected_body
                        else if (isScanning) R.string.first_run_scanning_body
                        else R.string.first_run_activation_body,
                    ),
                    color = foreground.copy(alpha = 0.8f),
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                )
                if (!status.isNullOrBlank()) {
                    FirstRunStatus(status, foreground, Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                }
                if (isScanning) {
                    CircularProgressIndicator(
                        Modifier.padding(top = 16.dp).size(24.dp), color = foreground, strokeWidth = 2.dp,
                    )
                    TextButton(onClick = onCancelScan) {
                        Text(stringResource(R.string.first_run_cancel_scan), color = foreground)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isPaired) {
                    FirstRunPrimaryButton(
                        continueLabel ?: stringResource(R.string.first_run_continue_setup), false, onContinueToSetup,
                    )
                } else {
                    TextButton(onClick = onPurchaseZap, enabled = !isScanning) {
                        Text(
                            stringResource(R.string.first_run_purchase_zap),
                            color = foreground.copy(alpha = if (isScanning) 0.4f else 1f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                    TextButton(onClick = onSkip, enabled = !isScanning) {
                        Text(
                            stringResource(R.string.first_run_setup_later),
                            color = foreground.copy(alpha = if (isScanning) 0.4f else 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstRunTitleBar(title: String, foreground: Color, onBack: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(stringResource(R.string.first_run_back), color = foreground)
            }
        }
        Text(
            title, color = foreground, fontSize = 17.sp, lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 80.dp, vertical = 16.dp).semantics { heading() },
        )
    }
}

@Composable
private fun FirstRunPageControls(page: Int, busy: Boolean, onPageChange: (Int) -> Unit) {
    // The visual dots keep iOS spacing; two non-overlapping 48 dp targets sit over them.
    Box(Modifier.width(96.dp).height(48.dp).selectableGroup(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { index ->
                Box(Modifier.size(10.dp).background(if (index == page) Color.White else Color(0xFF48484A), CircleShape))
            }
        }
        Row(Modifier.fillMaxSize()) {
            repeat(2) { index ->
                val label = stringResource(R.string.first_run_page, index + 1, 2)
                Box(
                    Modifier.weight(1f).fillMaxHeight().selectable(
                        selected = index == page, enabled = !busy, role = Role.Tab,
                        onClick = { onPageChange(index) },
                    ).semantics { contentDescription = label },
                )
            }
        }
    }
}

@Composable
private fun FirstRunPrimaryButton(
    title: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick, enabled = !busy,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(50.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White, contentColor = Color.Black,
            disabledContainerColor = Color(0xFFCCCCCC), disabledContentColor = Color.Black,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(
                Modifier.padding(end = 12.dp).size(20.dp), color = Color.Black, strokeWidth = 2.dp,
            )
        }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FirstRunStatus(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun FirstRunSystemBars(lightSurface: Boolean) {
    val activity = LocalActivity.current
    LaunchedEffect(activity, lightSurface) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightSurface
                isAppearanceLightNavigationBars = lightSurface
            }
        }
    }
}
