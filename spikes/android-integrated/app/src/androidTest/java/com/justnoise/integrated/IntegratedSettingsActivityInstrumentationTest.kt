package com.justnoise.integrated

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Base64
import android.view.KeyEvent
import android.widget.CheckBox
import android.widget.Button
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.gate0.R
import com.justnoise.gate0.app.MainActivity as Gate0SetupActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.data.SessionHistorySnapshot
import com.justnoise.gate0.data.SqliteSessionHistoryStorage
import com.justnoise.gate0.data.StoredGate0State
import com.justnoise.gate0.domain.HistoryWindow
import com.justnoise.gate0.platform.nfc.OneShotScanCoordinator
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

/** Real Activity routing and storage. Synthetic authority/history exist only after both emulator guards. */
@RunWith(AndroidJUnit4::class)
class IntegratedSettingsActivityInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var emulatorGuardPassed = false
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences
    private lateinit var storage: SqliteSessionHistoryStorage
    private lateinit var originalRows: SessionHistorySnapshot
    private var scenario: ActivityScenario<MainActivity>? = null
    private var nativeSetup: Activity? = null

    @Before
    fun guardBeforeAnyFixtureCleanupOrMutation() {
        assumeTrue("Never seed Settings authority/history on a physical phone",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        context = ApplicationProvider.getApplicationContext()
        assumeTrue("Only the disposable development package is allowed", context.packageName == "com.justnoise.androiddev")
        preferences = Gate0Preferences(context)
        storage = SqliteSessionHistoryStorage(context)
        emulatorGuardPassed = true
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.settings.readonly.target")))
        val now = System.currentTimeMillis()
        storage.insert(SessionHistoryEntry("00000000-0000-0000-0000-000000000271",
            now - 3_600_000L, now - 1_800_000L, 1_800_000L, targetCount = 2, interrupted = true))
        originalRows = storage.load(1_000)
        assertEquals(1, originalRows.totalCount)
    }

    @After
    fun cleanupOnlyAfterTheEmulatorAndPackageGuards() {
        if (!emulatorGuardPassed) return
        try {
            nativeSetup?.let { activity ->
                InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.finish() }
            }
            nativeSetup = null
            scenario?.close()
            scenario = null
        } finally {
            assertTrue(preferences.clearForWithdrawal())
        }
    }

    @Test
    fun historyGearOpensRealSettingsAndDoneRestoresYearWithoutRewritingHistory() {
        launchAndOpenHistory()
        compose.onNodeWithTag("history-window-menu").performClick()
        compose.onNodeWithTag("history-window-YEAR").performClick()
        awaitHistory(HistoryWindow.YEAR)
        val before = preferences.snapshot()
        openSettings()
        withHistory { assertNull(it.state.snapshot) }
        captureZapEvidence("real-settings-from-history")
        compose.onNodeWithTag("settings-done").performClick()
        awaitHistory(HistoryWindow.YEAR)
        assertAuthorityAndRowsUnchanged(before)
    }

    @Test
    fun privacyPageSurvivesRecreationAndNativeBackReturnsThroughSettingsToHistory() {
        launchAndOpenHistory()
        openSettings()
        clickSettingsRow("settings-privacy")
        compose.onNodeWithTag("settings-privacy-main").assertIsDisplayed()
        val before = preferences.snapshot()
        requireNotNull(scenario).recreate()
        compose.onNodeWithTag("settings-privacy-main").assertIsDisplayed()
        compose.onNodeWithTag("privacy-draft-banner").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("settings-advanced-main").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("settings-main").assertIsDisplayed()
        pressBack()
        awaitHistory(HistoryWindow.NINETY_DAYS)
        assertAuthorityAndRowsUnchanged(before)
    }

    @Test
    fun tutorialReplayDonePreservesExistingPairingConsentRequestedSessionAndHistory() {
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION))
        assertTrue(preferences.setZapDigest(Base64.encodeToString(ByteArray(32) { 7 }, Base64.NO_WRAP)))
        // This guarded storage fixture is not a physical NFC start or proof of live blocking.
        assertTrue(preferences.startSession(System.currentTimeMillis() - 10_000L))
        launchAndOpenHistory()
        openSettings()
        val before = preferences.snapshot()
        assertTrue(before.desiredActive)
        assertFalse(preferences.hasCompletedOnboarding())
        clickSettingsRow("settings-tutorial")
        compose.onNodeWithText(context.getString(R.string.first_run_get_started)).performClick()
        compose.onNodeWithText(context.getString(R.string.first_run_replay_done)).assertIsDisplayed()
        requireNotNull(scenario).recreate()
        compose.onNodeWithText(context.getString(R.string.first_run_replay_done)).assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-main").assertIsDisplayed()
        assertFalse("Replay must not change the first-run completion flag", preferences.hasCompletedOnboarding())
        assertAuthorityAndRowsUnchanged(before)
    }

    @Test
    fun localDataReviewOpensNativeSetupOnlyAfterClickWithoutConsentScanOrWithdrawal() {
        launchAndOpenHistory()
        openSettings()
        val before = preferences.snapshot()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(Gate0SetupActivity::class.java.name, null, false)
        try {
            compose.waitForIdle()
            assertEquals("Rendering Settings must not launch setup", 0, monitor.hits)
            assertAuthorityAndRowsUnchanged(before)
            clickSettingsRow("settings-local-data")
            nativeSetup = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
            assertNotNull("Explicit review should open the existing native Setup Activity", nativeSetup)
            instrumentation.runOnMainSync {
                val setup = requireNotNull(nativeSetup)
                assertFalse(setup.findViewById<CheckBox>(R.id.consentCheckbox).isChecked)
                assertFalse(setup.findViewById<Button>(R.id.accessibilitySettingsButton).isEnabled)
                val disclosure = setup.findViewById<TextView>(R.id.disclosureText).text.toString()
                assertTrue(disclosure.startsWith("Justnoise uses Android Accessibility"))
                assertFalse(disclosure.contains("prototype"))
                val coordinator = Gate0SetupActivity::class.java.getDeclaredField("scanCoordinator")
                    .apply { isAccessible = true }.get(setup) as OneShotScanCoordinator<*>
                assertFalse("Opening setup must not begin a scan", coordinator.isActive())
            }
            assertAuthorityAndRowsUnchanged(before)
            captureZapEvidence("real-customer-setup-no-consent")
            instrumentation.runOnMainSync { nativeSetup?.finish() }
            nativeSetup = null
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithTag("settings-advanced-main").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("settings-advanced-main").assertIsDisplayed()
            assertAuthorityAndRowsUnchanged(before)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun launchAndOpenHistory() {
        check(emulatorGuardPassed)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("zap-main").assertIsDisplayed()
        val entry = compose.onNodeWithContentDescription(context.getString(R.string.zap_history_accessibility))
        try { entry.assertIsDisplayed() } catch (_: AssertionError) { entry.performScrollTo() }
        entry.performClick()
        awaitHistory(HistoryWindow.NINETY_DAYS)
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription(context.getString(R.string.history_settings)).performClick()
        compose.onNodeWithTag("settings-main").assertIsDisplayed()
        compose.onNodeWithTag("history-main").assertDoesNotExist()
    }

    private fun clickSettingsRow(tag: String) {
        if (tag in setOf("settings-privacy", "settings-local-data", "settings-setup", "settings-app-info") &&
            compose.onAllNodesWithTag("settings-main").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-more"))
            compose.onNodeWithTag("settings-more").performClick()
        }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }

    private fun awaitHistory(window: HistoryWindow) {
        compose.waitUntil(timeoutMillis = 10_000) {
            var ready = false
            withHistory { ready = it.state.window == window && !it.state.loading && it.state.snapshot?.error == false }
            ready
        }
        compose.onNodeWithTag("history-main").assertIsDisplayed()
    }

    private fun withHistory(block: (IntegratedHistoryViewModel) -> Unit) {
        requireNotNull(scenario).onActivity { block(ViewModelProvider(it)[IntegratedHistoryViewModel::class.java]) }
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun assertAuthorityAndRowsUnchanged(before: StoredGate0State) {
        val after = preferences.snapshot()
        assertEquals(before.selectedPackages, after.selectedPackages)
        assertEquals(before.zapDigest, after.zapDigest)
        assertEquals(before.acceptedDisclosureVersion, after.acceptedDisclosureVersion)
        assertEquals(before.acceptedDisclosureEpochMilliseconds, after.acceptedDisclosureEpochMilliseconds)
        assertEquals(before.desiredActive, after.desiredActive)
        assertEquals(before.activeSessionId, after.activeSessionId)
        assertEquals(before.activePackages, after.activePackages)
        assertEquals(before.activeSinceEpochMilliseconds, after.activeSinceEpochMilliseconds)
        assertEquals(before.sessionRevision, after.sessionRevision)
        assertEquals(originalRows, storage.load(1_000))
    }
}
