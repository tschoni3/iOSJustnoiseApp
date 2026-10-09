package com.justnoise.integrated

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
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
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Surface-only fixtures. No permission, pairing, deletion or route authority is exercised. */
@RunWith(AndroidJUnit4::class)
class IntegratedSettingsUiInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var context: Context
    private var scenario: ActivityScenario<MainActivity>? = null
    private var page by mutableStateOf(IntegratedSettingsPage.MAIN)
    private var enlarged by mutableStateOf(false)
    private var version by mutableStateOf("0.1-instrumentation-fixture")
    private var message by mutableStateOf<String?>(null)
    private var emailCanOpen = true
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
                                    page = page, appVersion = version, setupSummary = "Setup status fixture", message = message,
                                    onDone = { events += "done" },
                                    onBackFromPrivacy = { events += "privacy-back" },
                                    onOpenSetup = { events += "setup" },
                                    onShowTutorial = { events += "tutorial" },
                                    onShowPrivacy = { events += "privacy" },
                                    onReviewLocalData = { events += "review-local-data" },
                                    onOpenAppInfo = { events += "app-info" },
                                    onComposeSupportEmail = { events += "email"; emailCanOpen },
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
    fun closeOnlyTheHostLaunchedAfterTheGuard() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun renderingHasNoSideEffectAndRowsOnlyDispatchNamedNavigationCallbacks() {
        assertEvents()
        compose.onNodeWithTag("settings-main").assertIsDisplayed()
        listOf("Delete Account", "Sign out", "Replace Zap", "Use Emergency Unzap", "Subscription").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        val rows = listOf("settings-setup" to "setup", "settings-tutorial" to "tutorial",
            "settings-privacy" to "privacy", "settings-local-data" to "review-local-data", "settings-app-info" to "app-info")
        rows.forEach { (tag, event) ->
            scrollMainTo(tag)
            compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().assertHasClickAction().performClick()
            assertEvents(event)
        }
        compose.onNodeWithTag("settings-done").assertIsDisplayed().performClick()
        assertEvents("done")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-setup"))
        captureZapEvidence("fixture-grouped-settings")
    }

    @Test
    fun supportHasExplicitEmailActionAndCopyableFallbackWithoutSideEffectsOnRender() {
        compose.runOnIdle { enlarged = true }
        scrollMainTo("support-address")
        assertEvents()
        compose.onNodeWithTag("support-address").assertIsDisplayed()
        assertEquals("hello@justnoise.shop", context.getString(R.string.integrated_settings_support_email))
        compose.runOnIdle { emailCanOpen = false }
        scrollMainTo("support-email")
        compose.onNodeWithTag("support-email").assertIsDisplayed().performClick()
        assertEvents("email")
        scrollMainTo("support-email-unavailable")
        compose.onNodeWithTag("support-email-unavailable").assertIsDisplayed()
        captureZapEvidence("fixture-support-email-fallback-font200")
        compose.runOnIdle { emailCanOpen = true }
        scrollMainTo("support-email")
        compose.onNodeWithTag("support-email").performClick()
        assertEvents("email")
        compose.onNodeWithTag("support-email-unavailable").assertDoesNotExist()
    }

    @Test
    fun emailHandoffContainsOnlyRecipientAndHandlesUnavailableAppsWithoutSending() {
        var captured: Intent? = null
        assertTrue(launchSupportEmail("hello@justnoise.shop") { captured = it })
        val intent = requireNotNull(captured)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        assertEquals("hello@justnoise.shop", intent.data?.schemeSpecificPart)
        assertNull(intent.extras)
        assertNull(intent.clipData)
        assertFalse(launchSupportEmail("hello@justnoise.shop") { throw ActivityNotFoundException() })
        assertFalse(launchSupportEmail("hello@justnoise.shop") { throw SecurityException() })
        assertEvents()
    }

    @Test
    fun installedVersionIsSuppliedByHostAndFailureRemainsVisibleWithoutStartingAnAction() {
        scrollMainTo("settings-version")
        compose.onNodeWithText("0.1-instrumentation-fixture").assertIsDisplayed()
        compose.onNodeWithTag("settings-version").assertHasNoClickAction()
        compose.runOnIdle { version = ""; message = "Android could not open app settings." }
        scrollMainTo("settings-version")
        compose.onNodeWithText(context.getString(R.string.integrated_settings_version_unavailable)).assertIsDisplayed()
        scrollMainTo("settings-message")
        compose.onNodeWithText("Android could not open app settings.").assertIsDisplayed()
        assertEvents()
    }

    @Test
    fun privacyIsReadableMarkedDraftAndOwnerContactDoesNotStartAnAction() {
        compose.runOnIdle { page = IntegratedSettingsPage.PRIVACY }
        compose.waitForIdle()
        compose.onNodeWithTag("privacy-draft-banner").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.integrated_settings_privacy_draft_title)).assertIsDisplayed()
        val paragraphs = context.resources.getStringArray(R.array.integrated_android_privacy_draft_paragraphs)
        assertTrue("The draft identifies the owner-supplied publisher",
            paragraphs.contains("Publisher: Justnoise Media"))
        val contactIndex = paragraphs.indexOfFirst { "hello@justnoise.shop" in it }
        assertTrue("The draft displays the owner-supplied privacy contact", contactIndex >= 0)
        assertTrue("The public policy URL remains explicitly unfinished",
            paragraphs.any { "[YOUR PUBLISHED ANDROID PRIVACY POLICY URL]" in it })
        assertTrue("Owner contact details do not turn the draft into a published policy",
            context.getString(R.string.integrated_settings_privacy_draft_warning)
                .contains("not a published policy"))
        compose.onNodeWithTag("settings-privacy-list").performScrollToNode(hasTestTag("privacy-paragraph-$contactIndex"))
        compose.onNodeWithTag("privacy-paragraph-$contactIndex").assertIsDisplayed().assertHasNoClickAction()
        val body = paragraphs.joinToString("\n\n")
        assertTrue("The former email placeholder must not be presented as the contact",
            !body.contains("privacy@example.com"))
        listOf("window-change", "History", "replay", "Force Stop", "Withdraw consent", "750 ms", "v4").forEach {
            assertTrue("The complete relevant draft retains $it", body.contains(it))
        }
        compose.onNodeWithTag("settings-privacy-list").performScrollToNode(hasTestTag("privacy-draft-end"))
        compose.onNodeWithTag("privacy-draft-end").assertIsDisplayed().assertHasNoClickAction()
        assertEvents()
        compose.onNodeWithTag("settings-privacy-back").assertIsDisplayed().performClick()
        assertEvents("privacy-back")
    }

    @Test
    fun compactTwoHundredPercentTextKeepsEverySettingsRowAndPrivacyBackReachable() {
        compose.runOnIdle { enlarged = true }
        listOf("settings-setup", "settings-tutorial", "settings-privacy", "settings-local-data", "settings-app-info").forEach {
            scrollMainTo(it)
            compose.onNodeWithTag(it).assertIsDisplayed().assertHasClickAction()
        }
        compose.onNodeWithTag("settings-done").assertIsDisplayed().assertIsEnabled()
        captureZapEvidence("fixture-grouped-settings-360x640-font200-lower-rows")
        compose.runOnIdle { page = IntegratedSettingsPage.PRIVACY }
        compose.onNodeWithTag("settings-privacy-list").performScrollToNode(hasTestTag("privacy-draft-end"))
        compose.onNodeWithTag("privacy-draft-end").assertIsDisplayed()
        compose.onNodeWithTag("settings-privacy-back").assertIsDisplayed().assertIsEnabled().performClick()
        assertEvents("privacy-back")
    }

    private fun scrollMainTo(tag: String) {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
    }

    private fun assertEvents(vararg expected: String) {
        compose.runOnIdle { assertEquals(expected.toList(), events.toList()); events.clear() }
    }
}
