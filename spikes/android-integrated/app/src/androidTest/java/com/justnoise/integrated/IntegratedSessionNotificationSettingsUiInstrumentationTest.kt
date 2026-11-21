package com.justnoise.integrated

import android.content.Context
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.R
import com.justnoise.gate0.feature.SessionLiveUpdateStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory presentation only: no notification permission or system setting is changed. */
@RunWith(AndroidJUnit4::class)
class IntegratedSessionNotificationSettingsUiInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var context: Context
    private var scenario: ActivityScenario<MainActivity>? = null
    private var notificationsEnabled by mutableStateOf(false)
    private var enlarged by mutableStateOf(false)
    private var liveUpdateStatus by mutableStateOf(SessionLiveUpdateStatus.UNSUPPORTED)
    private var page by mutableStateOf(IntegratedSettingsPage.MAIN)
    private val events = mutableListOf<String>()

    @Before
    fun guardEmulatorBeforeLaunchingTheFixtureHost() {
        assumeTrue("Rendered fixtures must not replace the owner's physical phone UI",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        context = ApplicationProvider.getApplicationContext()
        assumeTrue("Only the disposable integrated app is allowed", context.packageName == "com.justnoise.androiddev")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario?.onActivity { activity ->
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides if (enlarged) Density(density.density, 2f) else density) {
                    JustnoiseIntegratedTheme {
                        Box(Modifier.fillMaxSize()) {
                            Box(if (enlarged) Modifier.widthIn(max = 360.dp).heightIn(max = 640.dp) else Modifier.fillMaxSize()) {
                                IntegratedSettingsSurface(
                                    page = page,
                                    appVersion = "notification-ui-fixture",
                                    setupSummary = "Setup status fixture",
                                    onDone = { events += "done" },
                                    onBackFromPrivacy = { events += "privacy-back" },
                                    onOpenSetup = { events += "setup" },
                                    onShowTutorial = { events += "tutorial" },
                                    onShowPrivacy = { events += "privacy" },
                                    onReviewLocalData = { events += "review-local-data" },
                                    onOpenAppInfo = { events += "app-info" },
                                    notificationsEnabled = notificationsEnabled,
                                    onManageNotifications = { events += "manage-notifications" },
                                    liveUpdateStatus = liveUpdateStatus,
                                    onManageLiveUpdates = { events += "manage-live-updates" },
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun closeOnlyTheGuardedFixtureHost() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun liveUpdateStatesDoNotPromiseSamsungRenderingOrChangeSettingsOnTheirOwn() {
        for ((state, resource) in listOf(
            SessionLiveUpdateStatus.UNSUPPORTED to R.string.live_update_status_unsupported,
            SessionLiveUpdateStatus.NOTIFICATIONS_OFF to R.string.integrated_settings_notifications_off,
            SessionLiveUpdateStatus.PROMOTION_OFF to R.string.integrated_settings_notifications_off,
            SessionLiveUpdateStatus.NOT_PROMOTED to R.string.live_update_status_unavailable,
            SessionLiveUpdateStatus.ALLOWED to R.string.live_update_status_allowed,
            SessionLiveUpdateStatus.PROMOTED to R.string.live_update_status_active,
        )) {
            compose.runOnIdle { liveUpdateStatus = state }
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-live-update"))
            compose.onNodeWithTag("settings-live-update").assertTextContains(context.getString(resource))
            assertEvents()
        }
        compose.onNodeWithTag("settings-live-update").performClick()
        assertEvents("manage-live-updates")
    }

    @Test
    fun offAndOnAreHostSuppliedAndOnlyAnExplicitTapRequestsManagement() {
        scrollToNotification()
        assertStatus(false)
        assertEvents()
        compose.runOnIdle { notificationsEnabled = true }
        assertStatus(true)
        assertEvents()
        compose.onNodeWithTag("settings-session-notification").assertIsEnabled().assertHasClickAction().performClick()
        assertEvents("manage-notifications")
        assertStatus(true)
        compose.runOnIdle { notificationsEnabled = false }
        assertStatus(false)
        assertEvents()
    }

    @Test
    fun compactTwoHundredPercentTextKeepsNotificationAndExistingNavigationReachable() {
        compose.runOnIdle { enlarged = true }
        scrollToNotification()
        assertStatus(false)
        compose.onNodeWithTag("settings-session-notification").assertIsDisplayed().assertHasClickAction().performClick()
        assertEvents("manage-notifications")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-app-info"))
        compose.onNodeWithTag("settings-app-info").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("settings-done").assertIsDisplayed().assertIsEnabled()
        scrollToNotification()
        compose.runOnIdle { notificationsEnabled = true }
        assertStatus(true)
        assertEvents()
        captureZapEvidence("fixture-session-notification-settings-360x640-font200")
    }

    private fun scrollToNotification() {
        compose.runOnIdle { page = IntegratedSettingsPage.ADVANCED }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-session-notification"))
    }

    private fun assertStatus(enabled: Boolean) {
        val status = context.getString(if (enabled) R.string.integrated_settings_notifications_on
            else R.string.integrated_settings_notifications_off)
        compose.onNodeWithTag("settings-session-notification")
            .assertIsDisplayed()
            .assertTextContains(context.getString(R.string.integrated_settings_session_notification))
            .assertTextContains(status)
    }

    private fun assertEvents(vararg expected: String) {
        compose.runOnIdle { assertEquals(expected.toList(), events.toList()); events.clear() }
    }
}
