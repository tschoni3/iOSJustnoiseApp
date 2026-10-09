package com.justnoise.integrated

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.justnoise.gate0.R
import com.justnoise.gate0.app.MainActivity as SetupActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.feature.Gate0Controller
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real integrated activity tests. Synthetic pairing and deletion are emulator-only. */
@RunWith(AndroidJUnit4::class)
class IntegratedFirstRunInstrumentationTest {
    // An empty rule observes Compose without launching an activity before @Before's guard.
    @get:Rule
    val compose = createEmptyComposeRule()

    private var emulatorGuardPassed = false
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences
    private var scenario: ActivityScenario<MainActivity>? = null
    private var accessibilityServicesBefore: String? = null
    private var accessibilityEnabledBefore: String? = null

    @Before
    fun guardEmulatorBeforeAnySyntheticStateOrCleanup() {
        assumeTrue(
            "Never run first-run fixture cleanup on a physical phone",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish",
        )
        context = ApplicationProvider.getApplicationContext()
        assumeTrue("Only the disposable integrated app is allowed", context.packageName == "com.justnoise.androiddev")
        preferences = Gate0Preferences(context)
        emulatorGuardPassed = true
        finishTestActivities()
        clearSyntheticState()
        accessibilityServicesBefore = accessibilitySetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        accessibilityEnabledBefore = accessibilitySetting(Settings.Secure.ACCESSIBILITY_ENABLED)
    }

    @After
    fun cleanupOnlyAfterEmulatorGuardPassed() {
        if (!emulatorGuardPassed) return
        try {
            scenario?.close()
            scenario = null
            finishTestActivities()
        } finally {
            clearSyntheticState()
        }
    }

    @Test
    fun twoPagesReachActivationWithoutGrantingConsentPairingOrStartingSession() {
        // Model schedule-only uncertainty explicitly: suspendSchedules intentionally
        // does not persist anything in a completely empty store. This emulator-only
        // fixture must not grant consent just to exercise the suspended-state route.
        assertTrue(context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)
            .edit().putBoolean("schedule_suspended", true).commit())
        launchActualActivity()
        compose.onNodeWithText(text(R.string.first_run_how_to_use)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.first_run_select_apps)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.first_run_select_apps_body)).performScrollTo().assertIsDisplayed()
        captureZapEvidence("real-onboarding-create-mode")
        assertFalse(preferences.hasCompletedOnboarding())
        assertNoConsentOrSessionGrant(expectPaired = false)

        advanceToSecondPage()
        compose.onNodeWithText(text(R.string.first_run_stay_focused_body)).performScrollTo().assertIsDisplayed()
        captureZapEvidence("real-onboarding-mode-sessions")
        assertFalse(preferences.hasCompletedOnboarding())
        completeTutorial()

        assertTrue(preferences.hasCompletedOnboarding())
        compose.onNodeWithText(text(R.string.first_run_activation_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.first_run_continue_setup)).assertDoesNotExist()
        assertNoConsentOrSessionGrant(expectPaired = false)
        assertNull(resumedSetupActivity())
        assertTrue(preferences.areSchedulesSuspended())
    }

    @Test
    fun rotatingSecondPageKeepsPageAndDoesNotCompleteTutorial() {
        launchActualActivity()
        advanceToSecondPage()
        val activityScenario = requireNotNull(scenario)
        var requestedConfiguration = Configuration.ORIENTATION_UNDEFINED
        activityScenario.onActivity { activity ->
            val landscape = activity.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            requestedConfiguration = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            var rotated = false
            activityScenario.onActivity { rotated = it.resources.configuration.orientation == requestedConfiguration }
            rotated
        }
        waitForText(text(R.string.first_run_start_activating))
        compose.onNodeWithText(text(R.string.first_run_stay_focused)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.first_run_back)).assertIsDisplayed()
        assertFalse(preferences.hasCompletedOnboarding())
        assertNoConsentOrSessionGrant(expectPaired = false)
    }

    @Test
    fun completedTutorialSurvivesFreshActivityLaunchWithoutSavedState() {
        launchActualActivity()
        advanceToSecondPage()
        completeTutorial()
        requireNotNull(scenario).close()
        scenario = null

        // A new launch has no Activity saved-state bundle. Persistence chooses the route.
        launchActualActivity()
        waitForText(text(R.string.first_run_activate_zap))
        compose.onNodeWithText(text(R.string.first_run_how_to_use)).assertDoesNotExist()
        assertTrue(Gate0Preferences(context).hasCompletedOnboarding())
        assertNoConsentOrSessionGrant(expectPaired = false)
    }

    @Test
    fun existingLegacyPairSkipsEducationWithoutReplacingPairOrGrantingConsent() {
        withSyntheticZap { record ->
            assertTrue(Gate0Controller(context).pairZap(record))
            assertFalse(preferences.hasCompletedOnboarding())
            val before = preferences.snapshot()
            launchActualActivity()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTag("zap-main").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText(text(R.string.first_run_how_to_use)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.first_run_activate_zap)).assertDoesNotExist()
            assertEquals(before, preferences.snapshot())
            assertTrue(HmacZapStore(preferences).isAuthorized(record))
            assertFalse(preferences.hasCompletedOnboarding())
            assertNoConsentOrSessionGrant(expectPaired = true)
        }
    }

    @Test
    fun pairedConfirmationRequiresExplicitContinueBeforeOpeningUncheckedSetup() {
        launchActualActivity()
        advanceToSecondPage()
        completeTutorial()
        withSyntheticZap { record ->
            // No NFC callback or fake route: the real controller stores an emulator-only pair;
            // the actual activity's normal truth refresh must observe that persisted fact.
            assertTrue(Gate0Controller(context).pairInitialZap(record))
            waitForText(text(R.string.first_run_zap_connected))
            compose.onNodeWithText(text(R.string.first_run_activate_zap)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.first_run_continue_setup)).performScrollTo().assertIsDisplayed()
            assertNull("Pairing must not open consent or settings by itself", resumedSetupActivity())
            assertNoConsentOrSessionGrant(expectPaired = true)

            compose.onNodeWithText(text(R.string.first_run_continue_setup)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) { resumedSetupActivity() != null }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val setup = requireNotNull(resumedSetupActivityOnMain())
                assertFalse(setup.findViewById<CheckBox>(R.id.consentCheckbox).isChecked)
                assertFalse(setup.findViewById<Button>(R.id.accessibilitySettingsButton).isEnabled)
            }
            assertTrue(HmacZapStore(preferences).isAuthorized(record))
            assertNoConsentOrSessionGrant(expectPaired = true)
        }
    }

    private fun launchActualActivity() {
        check(emulatorGuardPassed)
        check(scenario == null)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    private fun advanceToSecondPage() {
        compose.onNodeWithText(text(R.string.first_run_get_started)).performScrollTo().performClick()
        waitForText(text(R.string.first_run_start_activating))
        compose.onNodeWithText(text(R.string.first_run_stay_focused)).performScrollTo().assertIsDisplayed()
    }

    private fun completeTutorial() {
        compose.onNodeWithText(text(R.string.first_run_start_activating)).performScrollTo().performClick()
        waitForText(text(R.string.first_run_activate_zap))
    }

    private fun waitForText(value: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoConsentOrSessionGrant(expectPaired: Boolean) {
        val state = preferences.snapshot()
        assertNull(state.acceptedDisclosureVersion)
        assertEquals(0L, state.acceptedDisclosureEpochMilliseconds)
        assertFalse(state.desiredActive)
        assertEquals(0L, state.sessionRevision)
        assertEquals(0L, state.activeSinceEpochMilliseconds)
        assertNull(state.activeSessionId)
        assertTrue(state.selectedPackages.isEmpty())
        assertTrue(state.activePackages.isEmpty())
        assertFalse(state.serviceConnected)
        assertFalse(state.recoveryRequired)
        if (expectPaired) assertNotNull(state.zapDigest) else assertNull(state.zapDigest)
        assertFalse(Gate0Controller(context).hasAcceptedCurrentDisclosure())
        assertEquals(accessibilityServicesBefore, accessibilitySetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        assertEquals(accessibilityEnabledBefore, accessibilitySetting(Settings.Secure.ACCESSIBILITY_ENABLED))
    }

    private fun clearSyntheticState() {
        check(emulatorGuardPassed)
        assertTrue(HmacZapStore(preferences).clear())
        assertTrue(preferences.clearForWithdrawal())
    }

    private inline fun withSyntheticZap(block: (ByteArray) -> Unit) {
        check(emulatorGuardPassed)
        // Canonical well-known UTF-8 Text record with language en; no physical Zap data.
        val record = byteArrayOf(0x01, 0x54, 0x02, 0x65, 0x6E) +
            "synthetic-integrated-first-run".toByteArray(Charsets.UTF_8)
        try {
            block(record)
        } finally {
            record.fill(0)
        }
    }

    private fun finishTestActivities() {
        check(emulatorGuardPassed)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Stage.values().filter { it != Stage.DESTROYED && it != Stage.PRE_ON_CREATE }
                .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .distinct().filter { it.packageName == context.packageName }.forEach(Activity::finish)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun resumedSetupActivity(): SetupActivity? {
        var activity: SetupActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity = resumedSetupActivityOnMain() }
        return activity
    }

    private fun resumedSetupActivityOnMain(): SetupActivity? =
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<SetupActivity>().singleOrNull()

    private fun accessibilitySetting(key: String): String? = Settings.Secure.getString(context.contentResolver, key)

    private fun text(resource: Int): String = context.getString(resource)
}
