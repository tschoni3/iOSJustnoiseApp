package com.justnoise.integrated

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.gate0.R
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.data.SessionHistorySnapshot
import com.justnoise.gate0.data.SqliteSessionHistoryStorage
import com.justnoise.gate0.domain.HistoryWindow
import com.justnoise.uiprototype.PortableHistoryDurationStyle
import com.justnoise.uiprototype.PortableHistoryRules
import java.time.LocalDate
import java.time.ZoneId
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

/**
 * Actual MainActivity navigation and real SQLite reads, using emulator-only synthetic rows.
 * No Surface replacement, fake timeline, pairing, consent grant, or NFC interaction.
 */
@RunWith(AndroidJUnit4::class)
class IntegratedHistoryActivityInstrumentationTest {
    // Nothing launches before the hardware and disposable-package checks in @Before.
    @get:Rule val compose = createEmptyComposeRule()

    private var emulatorGuardPassed = false
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences
    private lateinit var storage: SqliteSessionHistoryStorage
    private lateinit var fixtureDay: LocalDate
    private lateinit var originalRows: SessionHistorySnapshot
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun guardBeforeAnySyntheticCleanupOrStorageMutation() {
        assumeTrue("Never delete or seed History on a physical phone",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        context = ApplicationProvider.getApplicationContext()
        assumeTrue("Only the disposable integrated app is allowed", context.packageName == "com.justnoise.androiddev")
        preferences = Gate0Preferences(context)
        storage = SqliteSessionHistoryStorage(context)
        emulatorGuardPassed = true
        assertTrue(preferences.clearForWithdrawal())

        // A saved target bypasses the introductory route without granting any capability.
        // This deliberately unavailable test-only target is never used to start a session.
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.history.readonly.target")))
        val zone = ZoneId.systemDefault()
        fixtureDay = LocalDate.now(zone)
        storage.insert(entry("00000000-0000-0000-0000-000000000101", fixtureDay, 25, false, zone))
        storage.insert(entry("00000000-0000-0000-0000-000000000102", fixtureDay.minusDays(1), 65, true, zone))
        // All-time totals must still include a row outside the 90-day calendar window.
        storage.insert(entry("00000000-0000-0000-0000-000000000103", fixtureDay.minusDays(120), 40, false, zone))
        originalRows = storage.load(1_000)
        assertEquals(3, originalRows.totalCount)
        assertEquals(130L * MINUTE, originalRows.totalDurationMilliseconds)
        assertFalse(originalRows.error)
    }

    @After
    fun cleanupOnlyAfterBothEmulatorGuardsPassed() {
        if (!emulatorGuardPassed) return
        try {
            scenario?.close()
            scenario = null
        } finally {
            assertTrue(preferences.clearForWithdrawal())
        }
    }

    @Test
    fun actualHistoryToolbarLoadsPersistedRowsAndDayDetailsThenNativeBackReturnsToZap() {
        launchAndOpenHistory()
        assertTimeline(HistoryWindow.NINETY_DAYS)
        captureZapEvidence("real-history-ninety-day-persisted-fixtures")

        openDay(fixtureDay)
        assertDayDetails(25L * MINUTE, interrupted = false)
        captureZapEvidence("real-history-persisted-day-dialog")
        pressNativeBack()
        awaitNoDayDialog()
        compose.onNodeWithTag("history-main").assertIsDisplayed()
        withModel { assertNull(it.state.selectedDate) }

        pressNativeBack()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("zap-main").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("zap-main").assertIsDisplayed()
        compose.onNodeWithTag("history-main").assertDoesNotExist()
        assertRowsAndAuthorityUnchanged()
    }

    @Test
    fun rangeMenuLoadsYearAndNinetyDaysWithoutChangingCompletedRecords() {
        launchAndOpenHistory()
        selectWindow(HistoryWindow.YEAR)
        assertTimeline(HistoryWindow.YEAR)
        captureZapEvidence("real-history-year-persisted-fixtures")
        openDay(fixtureDay)
        assertDayDetails(25L * MINUTE, interrupted = false)
        pressNativeBack()
        awaitNoDayDialog()

        selectWindow(HistoryWindow.NINETY_DAYS)
        assertTimeline(HistoryWindow.NINETY_DAYS)
        openDay(fixtureDay.minusDays(1))
        assertDayDetails(65L * MINUTE, interrupted = true)
        pressNativeBack()
        awaitNoDayDialog()
        compose.onNodeWithContentDescription(context.getString(R.string.history_back)).performClick()
        compose.onNodeWithTag("zap-main").assertIsDisplayed()
        assertRowsAndAuthorityUnchanged()
    }

    @Test
    fun backgroundClearsSnapshotAndRecreationReloadsTheSelectedDayWithoutRewritingRows() {
        launchAndOpenHistory()
        selectWindow(HistoryWindow.YEAR)
        openDay(fixtureDay)
        assertDayDetails(25L * MINUTE, interrupted = false)

        requireNotNull(scenario).moveToState(Lifecycle.State.CREATED)
        withModel { model ->
            assertNull("Background must discard displayed storage results", model.state.snapshot)
            assertNull("A retained date must not expose a stale day card", model.state.selectedDay)
            assertFalse(model.state.loading)
            assertEquals(fixtureDay, model.state.selectedDate)
        }
        assertEquals(originalRows, storage.load(1_000))

        requireNotNull(scenario).moveToState(Lifecycle.State.RESUMED)
        awaitTimeline(HistoryWindow.YEAR)
        compose.onNodeWithTag("history-day-dialog").assertIsDisplayed()
        assertDayDetails(25L * MINUTE, interrupted = false)

        requireNotNull(scenario).recreate()
        awaitTimeline(HistoryWindow.YEAR)
        withModel { assertEquals(fixtureDay, it.state.selectedDate) }
        compose.onNodeWithTag("history-day-dialog").assertIsDisplayed()
        assertDayDetails(25L * MINUTE, interrupted = false)
        assertRowsAndAuthorityUnchanged()
    }

    private fun launchAndOpenHistory() {
        check(emulatorGuardPassed)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("zap-main").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.zap_history_accessibility)).performClick()
        awaitTimeline(HistoryWindow.NINETY_DAYS)
    }

    private fun selectWindow(window: HistoryWindow) {
        compose.onNodeWithTag("history-window-menu").performClick()
        compose.onNodeWithTag("history-window-${window.name}").assertIsDisplayed().performClick()
        awaitTimeline(window)
        withModel { assertNull(it.state.selectedDate) }
    }

    private fun awaitTimeline(window: HistoryWindow) {
        compose.waitUntil(timeoutMillis = 10_000) {
            var loaded = false
            withModel { model ->
                loaded = model.state.window == window && !model.state.loading && model.state.snapshot?.error == false
            }
            loaded
        }
        compose.waitForIdle()
        compose.onNodeWithTag("history-main").assertIsDisplayed()
    }

    private fun assertTimeline(window: HistoryWindow) {
        withModel { model ->
            val state = model.state
            val snapshot = requireNotNull(state.snapshot)
            assertEquals(window, state.window)
            assertEquals(state.range.dayCount, snapshot.days.size)
            assertEquals(if (window == HistoryWindow.NINETY_DAYS) 90 else state.today.lengthOfYear(), snapshot.days.size)
            assertEquals(3, snapshot.totalCount)
            assertEquals(130L * MINUTE, snapshot.totalDurationMilliseconds)
            assertEquals(1, snapshot.interruptedCount)
            assertEquals(0, snapshot.timingUncertainCount)
            val today = snapshot.days.single { it.date == fixtureDay }
            assertEquals(25L * MINUTE, today.durationMilliseconds)
            assertEquals(1, today.sessionCount)
        }
    }

    private fun openDay(date: LocalDate) {
        var window = HistoryWindow.NINETY_DAYS
        withModel { window = it.state.window }
        if (window == HistoryWindow.NINETY_DAYS)
            compose.onNodeWithTag("history-calendar").performScrollToKey(date.toEpochDay())
        compose.onNodeWithTag("history-day-$date").assertIsDisplayed().performClick()
        compose.onNodeWithTag("history-day-dialog").assertIsDisplayed()
        withModel { assertEquals(date, it.state.selectedDate) }
    }

    private fun assertDayDetails(durationMilliseconds: Long, interrupted: Boolean) {
        val duration = PortableHistoryRules.formatDuration(durationMilliseconds / 1_000.0,
            PortableHistoryDurationStyle.DAY_DETAIL)
        dayText(context.getString(R.string.history_day_duration, duration)).inView()
        dayText(context.resources.getQuantityString(R.plurals.history_day_sessions, 1, 1)).inView()
        val interruption = dayText(context.resources.getQuantityString(R.plurals.history_interrupted_count, 1, 1))
        interruption.assertDoesNotExist()
        withModel { assertEquals(if (interrupted) 1 else 0, it.state.selectedDay!!.interruptedCount) }
    }

    /** Presentation omits helper warnings; the selected day's stored metadata remains intact. */
    private fun dayText(value: String): SemanticsNodeInteraction =
        compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("history-day-dialog")))

    private fun SemanticsNodeInteraction.inView(): SemanticsNodeInteraction {
        try { assertIsDisplayed() } catch (_: AssertionError) { performScrollTo() }
        return assertIsDisplayed()
    }

    private fun pressNativeBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun awaitNoDayDialog() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("history-day-dialog").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun withModel(block: (IntegratedHistoryViewModel) -> Unit) {
        requireNotNull(scenario).onActivity { block(ViewModelProvider(it)[IntegratedHistoryViewModel::class.java]) }
    }

    private fun assertRowsAndAuthorityUnchanged() {
        assertEquals("Calendar navigation must not rewrite stored completion fields", originalRows, storage.load(1_000))
        val state = preferences.snapshot()
        assertFalse(state.desiredActive)
        assertTrue(state.activePackages.isEmpty())
        assertNull(state.activeSessionId)
        assertNull(state.acceptedDisclosureVersion)
        assertEquals(0L, state.acceptedDisclosureEpochMilliseconds)
        assertNull(state.zapDigest)
    }

    private fun entry(id: String, date: LocalDate, minutes: Int, interrupted: Boolean, zone: ZoneId): SessionHistoryEntry {
        val started = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val duration = minutes.toLong() * MINUTE
        return SessionHistoryEntry(id, started, started + duration, duration, targetCount = 2, interrupted = interrupted)
    }

    private companion object { const val MINUTE = 60_000L }
}
