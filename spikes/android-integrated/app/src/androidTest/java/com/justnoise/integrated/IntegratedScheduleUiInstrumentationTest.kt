package com.justnoise.integrated

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.StoredSchedule
import com.justnoise.gate0.data.StoredMode
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.domain.ScheduleTiming
import com.justnoise.gate0.feature.ScheduleManagementSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pure rendered fixtures: callback/layout coverage, not stored schedules or alarm delivery.
 * The guarded real Activity supplies resources and a window only. No test route,
 * separate Activity, fake persistence, permission grant, or fixture record enters production.
 */
@RunWith(AndroidJUnit4::class)
class IntegratedScheduleUiInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private var renderedState by mutableStateOf(IntegratedScheduleState(snapshot = readySnapshot()))
    private val events = mutableListOf<String>()

    @Before
    fun guardEmulatorBeforeLaunchingTheFixtureHost() {
        assumeTrue("Never replace the owner's physical phone UI with rendered fixtures",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Only the disposable integrated application is allowed", context.packageName == "com.justnoise.androiddev")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario?.onActivity { activity ->
            activity.setContent {
                // Modal sheets use their own Android window density. Enlarged-text runs
                // must set the guarded emulator's actual system font scale, not merely
                // override LocalDensity around the parent Activity composition.
                JustnoiseIntegratedTheme {
                        IntegratedScheduleSurface(
                            state = renderedState,
                            onBack = { events += "back" },
                            onRefresh = { events += "refresh" },
                            onNew = { events += "new" },
                            onEdit = { events += "edit:${it.id}" },
                            onEditorChange = {
                                renderedState = renderedState.withEditorChange(it)
                                events += "draft"
                            },
                            onSave = { events += "save" },
                            onCancelEditor = {
                                events += "cancel"
                                renderedState = renderedState.copy(editor = null)
                            },
                            onReloadEditor = { events += "reload" },
                            onDelete = { events += "delete:${it.id}:${it.revision}" },
                            onToggle = { schedule, enabled -> events += "toggle:${schedule.id}:$enabled" },
                            onOpenSetup = { events += "setup" },
                            onAlarmAccess = { events += "alarm-access" },
                        )
                }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun closeOnlyTheHostLaunchedAfterTheGuard() {
        // A failed guard leaves scenario null. There is no fixture storage to clear.
        scenario?.close()
        scenario = null
    }

    @Test
    fun compactListProvidesSeparateEditToggleAndTrailingAddActions() {
        val schedule = sampleSchedule()
        render(IntegratedScheduleState(snapshot = readySnapshot(listOf(schedule))))
        compose.onNodeWithTag("schedules-add").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("schedule-row:${schedule.id}").assertIsDisplayed().performClick()
        compose.onNodeWithTag("schedule-enabled:${schedule.id}").assertIsOn().performClick()
        compose.onNodeWithTag("schedules-add").performClick()
        assertEvents("edit:${schedule.id}", "toggle:${schedule.id}:false", "new")
        // The switch remains snapshot-driven; a callback is not treated as a saved change.
        compose.onNodeWithTag("schedule-enabled:${schedule.id}").assertIsOn()
        captureZapEvidence("fixture-schedules-compact-list")
    }

    @Test
    fun newSheetChangesOnlyItsDraftAndCancelDoesNotRequestSave() {
        val editor = sampleEditor().copy(id = null, expectedRevision = null, isRepeating = false, weekdays = emptySet())
        render(IntegratedScheduleState(snapshot = readySnapshot(), editor = editor))
        compose.onNodeWithTag("schedule-editor-sheet").assertIsDisplayed()
        compose.onNodeWithTag("schedule-save").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("schedule-date").assertExists()
        compose.onNodeWithTag("schedule-repeating").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-day:MONDAY").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag("schedule-day:MONDAY").assertIsOn()
        compose.runOnIdle {
            assertTrue(renderedState.editor!!.isRepeating)
            assertEquals(setOf(PortableWeekday.MONDAY), renderedState.editor!!.weekdays)
            assertTrue(renderedState.snapshot!!.schedules.isEmpty())
        }
        compose.onNodeWithTag("schedule-cancel").performClick()
        compose.onNodeWithTag("schedule-editor-sheet").assertDoesNotExist()
        assertEvents("draft", "draft", "cancel")
    }

    @Test
    fun missingAlarmAccessRemainsExplicitAndSaveIsOnlyAUserCallback() {
        render(IntegratedScheduleState(
            snapshot = readySnapshot().copy(exactAlarmAccess = false),
            editor = sampleEditor().copy(id = null, expectedRevision = null),
        ))
        assertEvents()
        compose.onNodeWithText("Allow alarms & reminders").performScrollTo().assertIsEnabled().performClick()
        assertEvents("alarm-access")
        compose.runOnIdle { assertTrue(renderedState.snapshot?.exactAlarmAccess == false) }
        // Requested-enabled configuration can be saved without fabricating access.
        compose.onNodeWithTag("schedule-save").assertIsEnabled().performClick()
        assertEvents("save")
        compose.runOnIdle { assertTrue(renderedState.snapshot!!.schedules.isEmpty()) }
    }

    @Test
    fun loadingSavingUncertaintyAndMissingConsentPreserveMutationRefusals() {
        val base = IntegratedScheduleState(snapshot = readySnapshot(listOf(sampleSchedule())), editor = sampleEditor())
        listOf(base.copy(loading = true), base.copy(saving = true), base.copy(mutationUncertain = true)).forEach { state ->
            render(state)
            compose.onNodeWithTag("schedule-save").assertIsNotEnabled()
            if (state.saving) compose.onNodeWithTag("schedule-cancel").assertIsNotEnabled()
            else compose.onNodeWithTag("schedule-cancel").assertIsEnabled()
            compose.onNodeWithTag("schedule-day:MONDAY").performScrollTo().assertIsNotEnabled()
            assertEvents()
        }
        render(IntegratedScheduleState(snapshot = readySnapshot().copy(consentAccepted = false)))
        compose.onNodeWithTag("schedules-add").assertIsNotEnabled()
        compose.onNodeWithText("Open setup").assertDoesNotExist()
        assertEvents()
        render(IntegratedScheduleState(snapshot = readySnapshot().copy(error = true)))
        compose.onNodeWithTag("schedules-add").assertIsNotEnabled()
        // At compact/large text this lazy item may not be composed yet.
        compose.onNodeWithTag("schedules-list").performScrollToNode(hasTestTag("schedules-refresh"))
        compose.onNodeWithTag("schedules-refresh").assertIsDisplayed().performClick()
        assertEvents("refresh")
    }

    @Test
    fun staleDraftKeepsExplicitReloadAndDeleteRequiresConfirmationOfTheSameRevision() {
        val schedule = sampleSchedule()
        val base = IntegratedScheduleState(snapshot = readySnapshot(listOf(schedule)), editor = sampleEditor())
        render(base.copy(editorError = "This schedule changed. Reload it before trying again; your edits were not saved."))
        compose.onNodeWithTag("schedule-reload").performScrollTo().performClick()
        assertEvents()
        compose.onNodeWithText("Keep editing").performClick()
        assertEvents()
        compose.onNodeWithTag("schedule-reload").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-reload-confirm").performClick()
        assertEvents("reload")
        compose.onNodeWithTag("schedule-delete").performScrollTo().performClick()
        assertEvents()
        compose.onNodeWithTag("schedule-delete-confirm").performClick()
        assertEvents("delete:${schedule.id}:${schedule.revision}")

        render(base.copy(snapshot = readySnapshot(listOf(schedule.copy(revision = schedule.revision + 1)))))
        compose.onNodeWithTag("schedule-delete").assertDoesNotExist()
        compose.onNodeWithTag("schedule-reload").performScrollTo().assertIsEnabled()
        assertEvents()
    }

    @Test
    fun sheetActionsAndEveryCircularWeekdayRemainReachableAtTheSystemFontScale() {
        render(IntegratedScheduleState(snapshot = readySnapshot(), editor = sampleEditor().copy(id = null, expectedRevision = null)))
        compose.onNodeWithTag("schedule-cancel").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("schedule-save").assertIsDisplayed().assertIsEnabled()
        val cancel = compose.onNodeWithTag("schedule-cancel").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithTag("schedule-save").fetchSemanticsNode().boundsInRoot
        assertTrue("Save and Cancel must not overlap", cancel.right <= save.left)
        PortableWeekday.entries.forEach { day ->
            compose.onNodeWithTag("schedule-day:${day.name}").performScrollTo().assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).assertHasClickAction()
        }
        compose.onNodeWithTag("schedule-day:SUNDAY").performClick()
        compose.onNodeWithTag("schedule-day:SUNDAY").assertIsOn()
        assertEvents("draft")
        captureZapEvidence("fixture-schedule-editor-weekday-controls")
        compose.onNodeWithTag("schedule-save").performClick()
        assertEvents("save")
    }

    @Test
    fun modePickerChangesOnlyThisDraftAndNeverChangesRecentMode() {
        val snapshot = readySnapshot().copy(modes = listOf(
            StoredMode("mode-one", 1L, "Work", setOf("synthetic.one")),
            StoredMode("mode-two", 1L, "Rest", setOf("synthetic.two", "synthetic.three")),
        ), recentModeId = "mode-one")
        val editor = sampleEditor().copy(modeReference = "mode-one")
        render(IntegratedScheduleState(snapshot = snapshot, editor = editor))
        compose.onNodeWithTag("schedule-mode").performScrollTo().performClick()
        compose.onNodeWithTag("schedule-mode-choice:mode-one").assertIsSelected()
        compose.onNodeWithTag("schedule-mode-choice:mode-two").performClick()
        compose.runOnIdle {
            assertEquals(editor.copy(modeReference = "mode-two"), renderedState.editor)
            assertEquals("mode-one", renderedState.snapshot!!.recentModeId)
            assertTrue(renderedState.snapshot!!.schedules.isEmpty())
        }
        assertEvents("draft")
    }

    @Test
    fun processRestorationKeepsChosenMissingAndAbsentModeReferencesWithoutFallback() {
        for (reference in listOf("mode-chosen", "mode-deleted", null)) {
            val editor = sampleEditor().copy(name = "Unsaved name", minute = 17, modeReference = reference)
            val stored = IntegratedScheduleEditorPersistence.store(editor)
            assertEquals(editor, IntegratedScheduleEditorPersistence.restore(stored))
            val viewModel = IntegratedScheduleViewModel(ApplicationProvider.getApplicationContext())
            viewModel.initialize(Bundle().apply {
                putBoolean("mutation_running", true)
                putBundle("editor", stored)
            })
            assertEquals(editor, viewModel.state.editor)
            assertTrue(viewModel.state.mutationUncertain)
            assertTrue(!viewModel.state.saving)
            assertNull(viewModel.state.snapshot)
            assertEquals(reference, viewModel.saveInstanceState().getBundle("editor")?.getString("mode_reference"))
        }
        val olderBundle = IntegratedScheduleEditorPersistence.store(sampleEditor()).apply { remove("mode_reference") }
        assertNull(IntegratedScheduleEditorPersistence.restore(olderBundle)?.modeReference)
    }

    private fun render(state: IntegratedScheduleState) {
        compose.runOnIdle { events.clear(); renderedState = state }
        compose.waitForIdle()
    }

    private fun assertEvents(vararg expected: String) {
        compose.runOnIdle { assertEquals(expected.toList(), events.toList()); events.clear() }
    }

    private companion object {
        fun sampleSchedule() = StoredSchedule(
            id = "00000000-0000-0000-0000-000000000073", revision = 1L, name = "Morning focus", enabled = true,
            timing = ScheduleTiming(repeatWeekdays = setOf(PortableWeekday.MONDAY, PortableWeekday.WEDNESDAY), hour = 9, minute = 0),
            creationZoneId = "Europe/Berlin",
        )

        fun sampleEditor() = IntegratedScheduleEditor(
            id = sampleSchedule().id, expectedRevision = 1L, name = "Morning focus", isRepeating = true,
            localDate = "2026-09-07", hour = 9, minute = 0,
            weekdays = setOf(PortableWeekday.MONDAY, PortableWeekday.WEDNESDAY), enabled = true, zoneId = "Europe/Berlin",
            modeReference = "local-default",
        )

        fun readySnapshot(schedules: List<StoredSchedule> = emptyList()) = ScheduleManagementSnapshot(
            schedules = schedules, exactAlarmAccess = true, consentAccepted = true, zapPaired = true,
            accessibilityEnabled = true,
            modes = listOf(StoredMode("local-default", 1L, "Focus", setOf("synthetic.one", "synthetic.two", "synthetic.three"))),
            recentModeId = "local-default",
        )
    }
}
