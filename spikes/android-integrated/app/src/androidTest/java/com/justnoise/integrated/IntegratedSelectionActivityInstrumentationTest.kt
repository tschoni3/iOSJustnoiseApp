package com.justnoise.integrated

import android.content.Context
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.ScheduleDraft
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.domain.ScheduleTiming
import com.justnoise.gate0.R
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import com.justnoise.gate0.platform.nfc.OneShotScanCoordinator
import com.justnoise.gate0.platform.nfc.NdefReadResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real MainActivity → real modes/editor → real manager/storage. No preview catalog/backend. */
@RunWith(AndroidJUnit4::class)
class IntegratedSelectionActivityInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var emulatorGuardPassed = false
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun guardBeforeAnyFixtureCleanupOrLaunch() {
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        context = ApplicationProvider.getApplicationContext()
        assumeTrue(context.packageName == "com.justnoise.androiddev")
        preferences = Gate0Preferences(context)
        emulatorGuardPassed = true
        assertTrue(preferences.clearForWithdrawal())
    }

    @After
    fun cleanupOnlyGuardedEmulator() {
        if (!emulatorGuardPassed) return
        scenario?.close()
        scenario = null
        assertTrue(preferences.clearForWithdrawal())
    }

    @Test
    fun cancelLeavesModeAppsUntouchedAndExplicitSavePersistsMultipleApps() {
        val targets = targets()
        assertTrue(preferences.setSelectedPackages(setOf(targets[0])))
        launchMain()
        val before = rawState()
        openSheet()
        openEditor()
        row(targets[1]).assertIsOff().performClick().assertIsOn()
        assertEquals(before, rawState())
        captureZapEvidence("real-mode-unsaved-two-apps")
        compose.onNodeWithTag("mode-cancel").performClick()
        awaitModeList()
        compose.onNodeWithTag("modes-done").performClick()
        awaitClosedSheet()
        assertEquals(before, rawState())

        openSheet()
        openEditor()
        row(targets[1]).assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("mode-save").performClick()
        awaitModeList()
        compose.onNodeWithTag("modes-done").performClick()
        awaitClosedSheet()
        assertEquals(targets.toSet(), preferences.snapshot().selectedPackages)
        assertNull(preferences.snapshot().acceptedDisclosureVersion)
        assertNull(preferences.snapshot().zapDigest)
        assertFalse(preferences.snapshot().desiredActive)
        compose.onNodeWithTag("zap-main").assertIsDisplayed()
    }

    @Test
    fun protectedAndUnavailableRowsBecomeRemovalOnlyAndAreExcludedFromSavedSet() {
        val eligible = targets().first()
        val removalOnly = listOf(context.packageName, "synthetic.compose.unavailable")
        assertTrue(preferences.setSelectedPackages(setOf(eligible) + removalOnly))
        launchMain()
        val before = rawState()
        openSheet()
        openEditor()
        removalOnly.forEach { packageName ->
            row(packageName).assertIsOn().performClick().assertIsOff().assertIsNotEnabled()
        }
        assertEquals(before, rawState())
        compose.onNodeWithTag("mode-save").performClick()
        awaitModeList()
        compose.onNodeWithTag("modes-done").performClick()
        awaitClosedSheet()
        assertEquals(setOf(eligible), preferences.snapshot().selectedPackages)
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun openingModesCancelsClaimedScanAndRejectsLateResultWithoutMutation() {
        assertTrue(preferences.setSelectedPackages(setOf(targets().first())))
        launchMain()
        val before = rawState()
        lateinit var coordinator: OneShotScanCoordinator<IntegratedScanPurpose>
        lateinit var token: OneShotScanCoordinator.Token<IntegratedScanPurpose>
        requireNotNull(scenario).onActivity { activity ->
            val field = MainActivity::class.java.getDeclaredField("scanCoordinator").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val stored = field.get(activity) as OneShotScanCoordinator<IntegratedScanPurpose>
            coordinator = stored
            token = coordinator.begin(IntegratedScanPurpose.TOGGLE_SESSION)
            assertTrue(coordinator.claim(token))
        }
        openSheet()
        assertFalse(coordinator.isActive())
        val bytes = "synthetic-canceled-read".toByteArray()
        requireNotNull(scenario).onActivity { activity ->
            MainActivity::class.java.getDeclaredMethod("completeOneShotScan",
                OneShotScanCoordinator.Token::class.java, NdefReadResult::class.java)
                .apply { isAccessible = true }
                .invoke(activity, token, NdefReadResult.Accepted(bytes, recordCount = 1))
        }
        assertTrue("Canceled callback must clear its transient bytes", bytes.all { it == 0.toByte() })
        assertEquals(before, rawState())
        assertFalse(preferences.snapshot().desiredActive)
        compose.onNodeWithTag("modes-done").performClick()
        awaitClosedSheet()
    }

    @Test
    fun recreationClosesUnsavedSheetAndDoesNotRestoreOrSaveDraft() {
        val targets = targets()
        assertTrue(preferences.setSelectedPackages(setOf(targets[0])))
        launchMain()
        val before = rawState()
        openSheet()
        openEditor()
        row(targets[1]).assertIsOff().performClick().assertIsOn()
        requireNotNull(scenario).recreate()
        awaitClosedSheet()
        assertEquals(before, rawState())
        openSheet()
        openEditor()
        row(targets[1]).assertIsOff()
        assertEquals(before, rawState())
        compose.onNodeWithTag("mode-cancel").performClick()
        awaitModeList()
        compose.onNodeWithTag("modes-done").performClick()
        awaitClosedSheet()
    }

    @Test
    fun newModeSavesIndependentAppsAndOnlyExplicitRowSelectionChangesNextZap() {
        val targets = targets()
        assertTrue(preferences.setSelectedPackages(setOf(targets[0])))
        val original = requireNotNull(preferences.loadModes().recentMode)
        launchMain()
        openSheet()
        compose.onNodeWithTag("mode-new").performScrollTo().performClick()
        compose.onNodeWithTag("mode-name").performTextInput("Sleep")
        row(targets[1]).assertIsOff().performClick().assertIsOn()
        assertEquals(listOf(original), preferences.loadModes().modes)
        compose.onNodeWithTag("mode-save").assertIsEnabled().performClick()
        awaitModeList()
        val afterSave = preferences.loadModes()
        val sleep = afterSave.modes.single { it.name == "Sleep" }
        assertEquals(original.id, afterSave.recentModeId)
        assertEquals(original, afterSave.modes.single { it.id == original.id })
        assertEquals(setOf(targets[1]), sleep.selectedPackages)
        captureZapEvidence("real-modes-new-sleep-not-yet-selected")
        compose.onNodeWithTag("mode-list").performScrollToNode(hasTestTag("mode-row:${sleep.id}"))
        compose.onNodeWithTag("mode-row:${sleep.id}").performClick()
        awaitModeList()
        assertEquals(sleep.id, preferences.loadModes().recentModeId)
        assertEquals(sleep.selectedPackages, preferences.snapshot().selectedPackages)
        assertFalse(preferences.snapshot().desiredActive)
        assertNull(preferences.snapshot().zapDigest)
        compose.onNodeWithTag("modes-done").performClick()
        awaitClosedSheet()
        compose.onNodeWithText("Sleep").assertIsDisplayed()
    }

    @Test
    fun cancelModeRenameRetainsItsNameAndRecentSelection() {
        assertTrue(preferences.setSelectedPackages(setOf(targets().first())))
        launchMain()
        val before = preferences.loadModes()
        openSheet()
        openEditor()
        compose.onNodeWithTag("mode-name").performTextReplacement("Unsaved rename")
        compose.onNodeWithTag("mode-cancel").performClick()
        awaitModeList()
        assertEquals(before.modes, preferences.loadModes().modes)
        assertEquals(before.recentModeId, preferences.loadModes().recentModeId)
    }

    @Test
    fun referencedModeDeletionShowsExplanationAndPreservesModeAndSchedule() {
        assertTrue(preferences.setSelectedPackages(setOf(targets().first())))
        val mode = requireNotNull(preferences.loadModes().recentMode)
        assertTrue(preferences.saveSchedule(ScheduleDraft("Synthetic disabled schedule", false,
            ScheduleTiming(repeatWeekdays = setOf(PortableWeekday.MONDAY), hour = 9, minute = 0),
            "UTC", mode.id)))
        launchMain()
        openSheet()
        openEditor()
        val beforeModes = preferences.loadModes().modes
        val beforeSchedules = preferences.loadSchedules().schedules
        compose.onNodeWithTag("mode-app-list").performScrollToNode(hasTestTag("mode-delete"))
        compose.onNodeWithTag("mode-delete").performClick()
        compose.onNodeWithText(context.getString(R.string.integrated_mode_delete_body)).assertIsDisplayed()
        compose.onNodeWithTag("mode-confirm").performClick()
        awaitState { it.problem == IntegratedModeProblem.REFERENCED_BY_SCHEDULE && !it.saving }
        compose.onNodeWithTag("mode-app-list").performScrollToNode(
            androidx.compose.ui.test.hasText(context.getString(R.string.integrated_mode_referenced)))
        compose.onNodeWithText(context.getString(R.string.integrated_mode_referenced)).assertIsDisplayed()
        assertEquals(beforeModes, preferences.loadModes().modes)
        assertEquals(beforeSchedules, preferences.loadSchedules().schedules)
        assertFalse(preferences.snapshot().desiredActive)
        captureZapEvidence("real-mode-delete-blocked-by-schedule")
    }

    @Test
    fun freshConsentedTargetRequiredUserCanCreateAndSelectTheirFirstMode() {
        // Local synthetic consent only. No system permission, service enablement or pairing.
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION))
        val target = targets().first()
        assertTrue(preferences.loadModes().modes.isEmpty())
        launchMain()
        openSheet()
        compose.onNodeWithTag("mode-new").performScrollTo().performClick()
        compose.onNodeWithTag("mode-name").performTextInput("Focus")
        row(target).performClick()
        compose.onNodeWithTag("mode-save").performClick()
        awaitModeList()
        val first = preferences.loadModes().modes.single()
        assertNull(preferences.loadModes().recentModeId)
        compose.onNodeWithTag("mode-row:${first.id}").performClick()
        awaitModeList()
        assertEquals(first.id, preferences.loadModes().recentModeId)
        assertEquals(setOf(target), preferences.snapshot().selectedPackages)
        assertNull(preferences.snapshot().zapDigest)
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun compactEnlargedRealModeEditorKeepsSaveCancelAndAppsReachable() {
        val targets = targets()
        assertTrue(preferences.setSelectedPackages(setOf(targets[0])))
        launchMain()
        // Render the production content with the real retained model/store, not preview records.
        // Only this guarded emulator host's Compose density/size changes; no system setting changes.
        requireNotNull(scenario).onActivity { activity ->
            val model = androidx.lifecycle.ViewModelProvider(activity)[IntegratedModeViewModel::class.java]
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    JustnoiseIntegratedTheme {
                        Box(Modifier.fillMaxSize()) {
                            ModeSheetContent(model.state, model::select, model::newMode, model::edit,
                                model::changeName, model::toggle, model::save, model::delete, model::cancelEditor,
                                model::reload, model::dismiss,
                                Modifier.widthIn(max = 360.dp).heightIn(max = 640.dp))
                        }
                    }
                }
            }
            model.open()
        }
        awaitModeList()
        captureZapEvidence("real-modes-compact-360x640-font200-list")
        openEditor()
        row(targets[1]).performClick()
        compose.onNodeWithTag("mode-save").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("mode-cancel").assertIsDisplayed().assertIsEnabled()
        captureZapEvidence("real-mode-compact-360x640-font200-editor")
        compose.onNodeWithTag("mode-cancel").performClick()
        awaitModeList()
        assertEquals(setOf(targets[0]), preferences.snapshot().selectedPackages)
    }

    private fun launchMain() {
        check(emulatorGuardPassed)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("zap-main").assertIsDisplayed()
    }

    private fun openSheet() {
        val entry = compose.onNodeWithTag("zap-selection")
        try { entry.assertIsDisplayed() } catch (_: AssertionError) { entry.performScrollTo() }
        entry.performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("modes-sheet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("modes-sheet").assertIsDisplayed()
        awaitModeList()
    }

    private fun openEditor() {
        val id = requireNotNull(preferences.loadModes().recentModeId)
        compose.onNodeWithTag("mode-list").performScrollToNode(hasTestTag("mode-edit:$id"))
        compose.onNodeWithTag("mode-edit:$id").performClick()
        awaitState { it.editor?.id == id && !it.loading }
    }

    private fun awaitModeList() = awaitState { !it.loading && !it.saving && it.editor == null && it.visible }

    private fun awaitState(predicate: (IntegratedModeState) -> Boolean) {
        compose.waitUntil(timeoutMillis = 10_000) {
            var ready = false
            requireNotNull(scenario).onActivity { activity ->
                ready = predicate(androidx.lifecycle.ViewModelProvider(activity)[IntegratedModeViewModel::class.java].state)
            }
            ready
        }
        compose.waitForIdle()
    }

    private fun row(packageName: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        val tag = "mode-app:$packageName"
        // Wait for real asynchronous discovery before asking the lazy list to find the row.
        compose.waitUntil(timeoutMillis = 10_000) {
            var loaded = false
            requireNotNull(scenario).onActivity { activity ->
                val model = androidx.lifecycle.ViewModelProvider(activity)[IntegratedModeViewModel::class.java]
                loaded = !model.state.loading && model.state.choices.any { it.packageName == packageName }
            }
            loaded
        }
        compose.onNodeWithTag("mode-app-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun awaitClosedSheet() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("modes-sheet").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun targets(): List<String> = LauncherAppRepository(context).load().take(2).map { it.packageName }
        .also { assertEquals("Emulator needs two selectable apps", 2, it.size) }

    private fun rawState(): Map<String, *> =
        context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE).all.toMap()
}
