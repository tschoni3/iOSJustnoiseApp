package com.justnoise.integrated

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.justnoise.gate0.R
import com.justnoise.gate0.feature.SessionLiveUpdateStatus

enum class IntegratedSettingsPage { MAIN, PRIVACY }

private val SettingsBackground = Color(0xFFF2F2F7)
private val SettingsSecondary = Color(0xFF636366)

/** Read-only presentation and explicit navigation callbacks. No consent, pairing or deletion authority. */
@Composable
fun IntegratedSettingsSurface(
    page: IntegratedSettingsPage,
    appVersion: String,
    setupSummary: String,
    message: String? = null,
    onDone: () -> Unit,
    onBackFromPrivacy: () -> Unit,
    onOpenSetup: () -> Unit,
    onShowTutorial: () -> Unit,
    onShowPrivacy: () -> Unit,
    onReviewLocalData: () -> Unit,
    onOpenAppInfo: () -> Unit,
    notificationsEnabled: Boolean = false,
    onManageNotifications: () -> Unit = {},
    onComposeSupportEmail: () -> Boolean = { false },
    liveUpdateStatus: SessionLiveUpdateStatus = SessionLiveUpdateStatus.UNSUPPORTED,
    onManageLiveUpdates: () -> Unit = {},
    onOpenAccount: (() -> Unit)? = null,
) {
    val activity = LocalActivity.current
    LaunchedEffect(activity) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color.Black, onPrimary = Color.White, surface = Color.White,
        onSurface = Color.Black, background = SettingsBackground,
    )) {
        Surface(color = SettingsBackground, modifier = Modifier.fillMaxSize()
            .testTag(if (page == IntegratedSettingsPage.PRIVACY) "settings-privacy-main" else "settings-main")) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page == IntegratedSettingsPage.PRIVACY) {
                        TextButton(onClick = onBackFromPrivacy,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("settings-privacy-back")) {
                            Text(stringResource(R.string.integrated_settings_back))
                        }
                    } else Spacer(Modifier.weight(1f))
                    if (page == IntegratedSettingsPage.MAIN) {
                        TextButton(onClick = onDone,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("settings-done")) {
                            Text(stringResource(R.string.integrated_settings_done), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (page == IntegratedSettingsPage.PRIVACY) {
                    PrivacyDraftContent()
                } else {
                    SettingsGroups(appVersion, setupSummary, message, onOpenSetup, onShowTutorial,
                        onShowPrivacy, onReviewLocalData, onOpenAppInfo, notificationsEnabled,
                        onManageNotifications, onComposeSupportEmail, liveUpdateStatus, onManageLiveUpdates, onOpenAccount)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroups(
    appVersion: String,
    setupSummary: String,
    message: String?,
    onOpenSetup: () -> Unit,
    onShowTutorial: () -> Unit,
    onShowPrivacy: () -> Unit,
    onReviewLocalData: () -> Unit,
    onOpenAppInfo: () -> Unit,
    notificationsEnabled: Boolean,
    onManageNotifications: () -> Unit,
    onComposeSupportEmail: () -> Boolean,
    liveUpdateStatus: SessionLiveUpdateStatus,
    onManageLiveUpdates: () -> Unit,
    onOpenAccount: (() -> Unit)?,
) {
    var emailUnavailable by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { SettingsTitle(stringResource(R.string.integrated_settings_title)) }
        if (onOpenAccount != null) item {
            SettingsGroup("Account") {
                SettingsActionRow(title = "Your account",
                    tag = "settings-account", onClick = onOpenAccount)
            }
        }
        if (!message.isNullOrBlank()) item {
            Text(message, color = Color(0xFF8A241B), fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.testTag("settings-message").semantics { liveRegion = LiveRegionMode.Polite })
        }
        item {
            SettingsGroup(stringResource(R.string.integrated_settings_setup_group)) {
                SettingsActionRow(
                    title = stringResource(R.string.integrated_settings_setup),
                    tag = "settings-setup", onClick = onOpenSetup,
                )
            }
        }
        item {
            SettingsGroup(stringResource(R.string.integrated_settings_notifications_group)) {
                val status = stringResource(if (notificationsEnabled) R.string.integrated_settings_notifications_on
                    else R.string.integrated_settings_notifications_off)
                SettingsActionRow(
                    title = stringResource(R.string.integrated_settings_session_notification),
                    detail = status,
                    tag = "settings-session-notification", onClick = onManageNotifications,
                )
                SettingsRowDivider()
                SettingsActionRow(
                    title = stringResource(R.string.integrated_settings_live_update),
                    detail = stringResource(when (liveUpdateStatus) {
                        SessionLiveUpdateStatus.UNSUPPORTED -> R.string.live_update_status_unsupported
                        SessionLiveUpdateStatus.NOTIFICATIONS_OFF -> R.string.integrated_settings_notifications_off
                        SessionLiveUpdateStatus.PROMOTION_OFF -> R.string.integrated_settings_notifications_off
                        SessionLiveUpdateStatus.NOT_PROMOTED -> R.string.live_update_status_unavailable
                        SessionLiveUpdateStatus.ALLOWED -> R.string.live_update_status_allowed
                        SessionLiveUpdateStatus.PROMOTED -> R.string.live_update_status_active
                    }),
                    tag = "settings-live-update", onClick = onManageLiveUpdates,
                )
            }
        }
        item {
            SettingsGroup(stringResource(R.string.integrated_settings_onboarding_group)) {
                SettingsActionRow(
                    title = stringResource(R.string.integrated_settings_tutorial),
                    tag = "settings-tutorial", onClick = onShowTutorial,
                )
            }
        }
        item {
            SettingsGroup(stringResource(R.string.integrated_settings_privacy_group)) {
                SettingsActionRow(
                    title = stringResource(R.string.integrated_settings_privacy),
                    tag = "settings-privacy", onClick = onShowPrivacy,
                )
                SettingsRowDivider()
                SettingsActionRow(
                    title = stringResource(R.string.integrated_settings_local_data),
                    tag = "settings-local-data", onClick = onReviewLocalData,
                )
            }
        }
        item {
            SettingsGroup(stringResource(R.string.integrated_settings_recovery_group)) {
                Column(Modifier.fillMaxWidth().padding(16.dp).testTag("settings-support"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.integrated_settings_support), fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                    SelectionContainer {
                        Text(stringResource(R.string.integrated_settings_support_email),
                            color = Color.Black, fontSize = 17.sp, modifier = Modifier.testTag("support-address"))
                    }
                    if (emailUnavailable) Text(stringResource(R.string.integrated_settings_email_unavailable),
                        color = Color(0xFF8A241B), fontSize = 15.sp, lineHeight = 21.sp,
                        modifier = Modifier.testTag("support-email-unavailable")
                            .semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = { emailUnavailable = !onComposeSupportEmail() },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("support-email")) {
                        Text(stringResource(R.string.integrated_settings_email_support))
                    }
                }
                SettingsRowDivider()
                SettingsActionRow(
                    title = stringResource(R.string.integrated_settings_app_info),
                    tag = "settings-app-info", onClick = onOpenAppInfo,
                )
            }
        }
        item {
            SettingsGroup(stringResource(R.string.integrated_settings_about_group)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Justnoise", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                SettingsRowDivider()
                Column(Modifier.fillMaxWidth().padding(16.dp).testTag("settings-version"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.integrated_settings_version), fontSize = 17.sp)
                    Text(appVersion.ifBlank { stringResource(R.string.integrated_settings_version_unavailable) },
                        color = SettingsSecondary, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }
    }
}

@Composable
private fun PrivacyDraftContent() {
    val paragraphs = stringArrayResource(R.array.integrated_android_privacy_draft_paragraphs)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings-privacy-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { SettingsTitle(stringResource(R.string.integrated_settings_privacy)) }
        item {
            Surface(color = Color(0xFFFFF3CD), contentColor = Color(0xFF5C4200), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp).testTag("privacy-draft-banner"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.integrated_settings_privacy_draft_title),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.integrated_settings_privacy_draft_warning), fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }
        itemsIndexed(paragraphs.toList()) { index, paragraph ->
            // Deliberately plain text: drafting contacts/URLs are not linkified or actionable.
            Text(paragraph, fontSize = 17.sp, lineHeight = 25.sp, color = Color.Black,
                modifier = Modifier.fillMaxWidth().testTag("privacy-paragraph-$index"))
        }
        item {
            Text(stringResource(R.string.integrated_settings_privacy_end), color = SettingsSecondary,
                fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.testTag("privacy-draft-end"))
        }
    }
}

@Composable
private fun SettingsTitle(title: String) {
    Text(title, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() })
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 13.sp, lineHeight = 18.sp, color = SettingsSecondary,
            modifier = Modifier.padding(start = 16.dp).semantics { heading() })
        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsActionRow(title: String, detail: String? = null, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 17.sp, lineHeight = 23.sp)
            if (detail != null) Text(detail, fontSize = 13.sp, lineHeight = 19.sp, color = SettingsSecondary)
        }
        Canvas(Modifier.size(12.dp)) {
            drawLine(SettingsSecondary, Offset(size.width * .25f, 0f), Offset(size.width * .75f, size.height / 2),
                1.5.dp.toPx(), StrokeCap.Round)
            drawLine(SettingsSecondary, Offset(size.width * .75f, size.height / 2), Offset(size.width * .25f, size.height),
                1.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun SettingsRowDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = SettingsBackground)

@Preview(name = "Grouped Settings", widthDp = 384, heightDp = 832)
@Composable
private fun SettingsPreview() {
    IntegratedSettingsSurface(IntegratedSettingsPage.MAIN, "", "", null, {}, {}, {}, {}, {}, {}, {})
}

@Preview(name = "Session notification enabled", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun SettingsNotificationPreview() {
    IntegratedSettingsSurface(IntegratedSettingsPage.MAIN, "", "", null, {}, {}, {}, {}, {}, {}, {},
        notificationsEnabled = true)
}

@Preview(name = "Privacy draft enlarged", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun PrivacyDraftPreview() {
    IntegratedSettingsSurface(IntegratedSettingsPage.PRIVACY, "", "", null, {}, {}, {}, {}, {}, {}, {})
}
