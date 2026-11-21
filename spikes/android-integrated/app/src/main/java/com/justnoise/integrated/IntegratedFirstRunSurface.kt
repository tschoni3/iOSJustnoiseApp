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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
                        if (index == 0) {
                            Image(painterResource(R.drawable.zap_button), contentDescription = null,
                                modifier = Modifier.size(200.dp), contentScale = ContentScale.Fit)
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.first_run_select_apps), color = Color.White,
                                    fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() })
                                Text(stringResource(R.string.first_run_select_apps_body), color = Color(0xFF98989D),
                                    fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 30.dp))
                            }
                        } else {
                            Text(stringResource(R.string.first_run_stay_focused), color = Color.White,
                                fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() })
                            IntegratedOnboardingMedia(
                                videoResource = R.raw.jn_video2,
                                isActive = index == pager.settledPage && !pager.isScrollInProgress && !busy,
                                description = stringResource(R.string.first_run_focus_video),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                                    .height(250.dp).clip(RoundedCornerShape(12.dp)),
                            )
                            Text(stringResource(R.string.first_run_stay_focused_body), color = Color(0xFF98989D),
                                fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 30.dp))
                        }
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
                if (!isScanning) {
                    Text(
                        text = stringResource(if (isPaired) R.string.first_run_zap_connected
                            else R.string.first_run_activation_title),
                        color = foreground,
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
                    )
                    if (isPaired) Text(
                        text = stringResource(R.string.first_run_connected_body),
                        color = foreground.copy(alpha = 0.8f),
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                    )
                }
                if (!isScanning && !isPaired && !status.isNullOrBlank()) {
                    FirstRunStatus(status, foreground, Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
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
                }
            }
        }
    }
    if (isScanning) IntegratedNfcScanDialog(onCancelScan)
}

/** Android owns this modal; it does not impersonate the iOS system NFC sheet. */
@Composable
fun IntegratedNfcScanDialog(onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.zap_button), contentDescription = null,
                    modifier = Modifier.size(116.dp), contentScale = ContentScale.Fit)
                CircularProgressIndicator(Modifier.padding(top = 24.dp).size(28.dp),
                    color = Color.Black, strokeWidth = 2.dp)
                Text(stringResource(R.string.first_run_scan_overlay_title), color = Color.Black,
                    fontSize = 23.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp).semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    })
                Text(stringResource(R.string.first_run_scan_overlay_body), color = Color(0xFF555555),
                    fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = onCancel, modifier = Modifier.padding(top = 18.dp)) {
                    Text(stringResource(R.string.first_run_cancel_scan), color = Color.Black)
                }
            }
        }
    }
}

/** Prominent, affirmative disclosure immediately before Android's manual Accessibility switch. */
@Composable
fun IntegratedAccessibilitySetupSurface(
    consentAccepted: Boolean,
    errorMessage: String?,
    onEnable: () -> Unit,
    onLater: () -> Unit,
    exitOnLater: Boolean,
) {
    FirstRunSystemBars(lightSurface = true)
    Column(
        Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.first_run_blocking_title), style = IntegratedGuidanceTypography.title,
                modifier = Modifier.padding(top = 24.dp).semantics { heading() },
            )
            Text(stringResource(R.string.first_run_blocking_intro), style = IntegratedGuidanceTypography.explanation,
                modifier = Modifier.padding(top = 8.dp))
            AccessibilitySettingsGuide(Modifier.padding(top = 20.dp))
            Text(
                stringResource(R.string.first_run_accessibility_disclosure), style = IntegratedGuidanceTypography.disclosure,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(stringResource(R.string.first_run_accessibility_path), style = IntegratedGuidanceTypography.explanation,
                modifier = Modifier.padding(top = 14.dp, bottom = 16.dp))
            if (!errorMessage.isNullOrBlank()) {
                Text(errorMessage, color = Color(0xFFB3261E), modifier = Modifier.padding(top = 12.dp))
            }
        }
        Button(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(top = 8.dp),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
        ) {
            Text(stringResource(if (consentAccepted) R.string.first_run_open_android_settings
                else R.string.first_run_agree_open_settings))
        }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(stringResource(if (exitOnLater) R.string.first_run_blocking_not_now
                else R.string.first_run_back), color = Color(0xFF555555))
        }
    }
}

/** Static illustration, not a permission switch or a replica of interactive system UI. */
@Composable
private fun AccessibilitySettingsGuide(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.first_run_settings_guide_description)
    Surface(modifier = modifier.fillMaxWidth().testTag("accessibility-settings-guide")
        .clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(20.dp), color = Color(0xFFF4F4F5)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.first_run_settings_guide_title), style = IntegratedGuidanceTypography.caption)
            AccessibilityActivationStep(1, stringResource(R.string.first_run_settings_find))
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsGuideRow(stringResource(R.string.first_run_settings_installed_apps))
                    SettingsGuideRow(stringResource(R.string.first_run_settings_app))
                }
            }
            AccessibilityActivationStep(2, stringResource(R.string.first_run_accessibility_allow))
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.first_run_settings_on), modifier = Modifier.weight(1f),
                    color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.size(width = 44.dp, height = 26.dp).background(Color.Black, CircleShape)
                    .padding(3.dp), contentAlignment = Alignment.CenterEnd) {
                    Box(Modifier.size(20.dp).background(Color.White, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun SettingsGuideRow(label: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = Color.Black, fontSize = 15.sp)
        Text("›", color = Color(0xFF777777), fontSize = 22.sp)
    }
}

@Composable
fun IntegratedRecoverySurface(
    sessionRequested: Boolean,
    storageRecovery: Boolean,
    schedulesOnly: Boolean,
    consentNeeded: Boolean,
    errorMessage: String?,
    isScanning: Boolean,
    onRestore: () -> Unit,
    onStop: () -> Unit,
    onCancelScan: () -> Unit,
    onHelp: () -> Unit,
    blockingEnabled: Boolean = true,
    onEmergencySettings: (() -> Unit)? = null,
) {
    FirstRunSystemBars(lightSurface = true)
    Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp).testTag("guided-recovery")) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(stringResource(if (storageRecovery) R.string.first_run_restore_saved_change
                else if (schedulesOnly) R.string.first_run_resume_schedules else R.string.first_run_reconnect_title),
                color = Color.Black, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 36.dp).semantics { heading() })
            Text(stringResource(if (storageRecovery) R.string.first_run_storage_retry_body
                else if (schedulesOnly) R.string.first_run_resume_schedules_body
                else if (!blockingEnabled) R.string.first_run_reenable_body
                else R.string.first_run_reconnect_body),
                color = Color(0xFF555555), fontSize = 17.sp, lineHeight = 24.sp,
                modifier = Modifier.padding(top = 12.dp))
            if (sessionRequested) Text(stringResource(R.string.first_run_recovery_stop_body),
                color = Color.Black, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 24.dp))
            if (consentNeeded) Text(stringResource(R.string.first_run_accessibility_disclosure),
                color = Color(0xFF333333), fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 24.dp))
            if (!errorMessage.isNullOrBlank() && !isScanning) Text(errorMessage, color = Color(0xFFB3261E),
                modifier = Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        Button(onClick = if (sessionRequested) onStop else onRestore, enabled = !isScanning,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) {
            Text(stringResource(if (sessionRequested) R.string.first_run_end_session
                else if (storageRecovery) R.string.first_run_retry
                else if (schedulesOnly) R.string.first_run_resume_schedules
                else if (consentNeeded) R.string.first_run_agree_open_settings
                else R.string.first_run_restore_blocking))
        }
        if (sessionRequested) TextButton(onClick = onRestore, enabled = !isScanning,
            modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (storageRecovery) R.string.first_run_retry
                else if (consentNeeded) R.string.first_run_agree_open_settings
                else R.string.first_run_restore_blocking), color = Color.Black)
        }
        if (sessionRequested && onEmergencySettings != null) TextButton(onClick = onEmergencySettings,
            enabled = !isScanning, modifier = Modifier.fillMaxWidth().testTag("recovery-emergency-settings")) {
            Text(stringResource(R.string.integrated_emergency_title), color = Color.Black)
        }
        TextButton(onClick = onHelp, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(stringResource(R.string.first_run_get_help), color = Color(0xFF555555))
        }
    }
    if (isScanning) IntegratedNfcScanDialog(onCancelScan)
}

@Composable
fun IntegratedScheduleSetupSurface(
    errorMessage: String?,
    onEnable: () -> Unit,
    onLater: () -> Unit,
) {
    FirstRunSystemBars(lightSurface = true)
    Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.first_run_schedules_title), color = Color.Black,
                fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 36.dp).semantics { heading() })
            Text(stringResource(R.string.first_run_schedules_body), color = Color(0xFF555555),
                fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 12.dp))
            Column(Modifier.padding(top = 24.dp)) {
                AccessibilityActivationStep(1, stringResource(R.string.first_run_schedules_instruction))
            }
            if (!errorMessage.isNullOrBlank()) Text(errorMessage, color = Color(0xFFB3261E),
                modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        Button(onClick = onEnable,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(top = 8.dp),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) {
            Text(stringResource(R.string.first_run_enable_schedules))
        }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(stringResource(R.string.first_run_schedules_later), color = Color(0xFF555555))
        }
    }
}

@Composable
private fun AccessibilityActivationStep(number: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).background(Color(0xFFEEEEEE), CircleShape), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, style = IntegratedGuidanceTypography.instruction)
    }
}

@Composable
fun IntegratedAccountTransitionSurface() {
    FirstRunSystemBars(lightSurface = false)
    Box(Modifier.fillMaxSize().background(Color(0xFF151515)), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White,
            modifier = Modifier.semantics { contentDescription = "Opening sign in" })
    }
}

@Preview(name = "App blocking disclosure", showBackground = true)
@Composable
private fun AccessibilitySetupPreview() {
    JustnoiseIntegratedTheme {
        IntegratedAccessibilitySetupSurface(
            consentAccepted = false, errorMessage = null, onEnable = {}, onLater = {}, exitOnLater = true,
        )
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
