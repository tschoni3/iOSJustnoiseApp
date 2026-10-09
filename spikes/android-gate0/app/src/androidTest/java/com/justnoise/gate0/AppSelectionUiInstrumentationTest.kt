package com.justnoise.gate0

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.gate0.app.MainActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSelectionUiInstrumentationTest {
    private var emulatorGuardPassed = false
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private val preferences: Gate0Preferences
        get() = Gate0Preferences(context)
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun guardEmulatorBeforeFixtureCleanup() {
        assumeTrue(
            "Never clear app-selection fixtures on a physical phone",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish",
        )
        assumeTrue("Only the disposable Gate 0 app is allowed", context.packageName == "com.justnoise.gate0")
        emulatorGuardPassed = true
        assertTrue(preferences.clearForWithdrawal())
    }

    @After
    fun clearLocalStateOnlyAfterGuardPassed() {
        if (!emulatorGuardPassed) return
        assertTrue(preferences.clearForWithdrawal())
    }

    @Test
    fun explicitSavePersistsMultipleAppsAndRestoresAfterRecreation() {
        val expected = eligibleTargets().toSet()
        val before = preferences.snapshot()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dialog = openPicker(scenario)
            scenario.onActivity {
                clickChoice(dialog, 0)
                clickChoice(dialog, 1)
                assertEquals("Checking choices is not a save", before, preferences.snapshot())
            }
            saveAndAwait(scenario, dialog, expected)
            assertEquals(expected, preferences.snapshot().selectedPackages)
            scenario.recreate()
            val restoredDialog = openPicker(scenario)
            scenario.onActivity { activity ->
                assertEquals(expected, preferences.snapshot().selectedPackages)
                val summary = activity.findViewById<TextView>(R.id.selectedAppsText).text.toString()
                assertTrue(summary.contains(
                    activity.resources.getQuantityString(R.plurals.selected_apps_count, 2, 2),
                ))
                assertTrue(restoredDialog.listView.isItemChecked(0))
                assertTrue(restoredDialog.listView.isItemChecked(1))
                restoredDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun cancelDiscardsDraftWithoutChangingStoredState() {
        val targets = eligibleTargets()
        assertTrue(preferences.setSelectedPackages(setOf(targets.first())))
        val before = preferences.snapshot()
        val rawBefore = rawLocalState()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dialog = openPicker(scenario)
            scenario.onActivity {
                clickChoice(dialog, 1)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            refreshAndAwait(scenario)
            scenario.recreate()
            val reopened = openPicker(scenario)
            scenario.onActivity {
                assertTrue(reopened.listView.isItemChecked(0))
                assertFalse(reopened.listView.isItemChecked(1))
                reopened.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            assertEquals(before, preferences.snapshot())
            assertEquals("Cancel/refresh/recreation must not write any preference", rawBefore, rawLocalState())
        }
    }

    @Test
    fun activeRecoveryIntentPreventsEditingWithoutStoppingSession() {
        assertTrue(preferences.setSelectedPackages(eligibleTargets().toSet()))
        assertTrue(preferences.startSession(200_000L))
        preferences.markRecoveryRequired()
        val before = preferences.snapshot()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitSelectionIdle(scenario)
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.selectAppsButton)
                assertFalse(button.isEnabled)
                val summary = activity.findViewById<TextView>(R.id.selectedAppsText).text.toString()
                assertTrue(summary.contains(activity.getString(R.string.target_edit_requires_stop)))
                // Exercise the listener guard too; a programmatic click bypasses touch dispatch.
                button.performClick()
                assertNull(currentPicker(activity))
            }
            refreshAndAwait(scenario)
            assertEquals(before, preferences.snapshot())
        }
    }

    @Test
    fun unavailableSelectedAppRemainsVisibleAndIsNotSilentlyDropped() {
        val missingPackage = "synthetic.unavailable.selection"
        val selected = setOf(eligibleTargets().first(), missingPackage)
        assertTrue(preferences.setSelectedPackages(selected))
        val before = preferences.snapshot()
        val missingLabel = context.getString(R.string.target_unavailable, missingPackage)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            refreshAndAwait(scenario)
            awaitActivity(scenario, "Unavailable saved app was not rendered after refresh") {
                it.findViewById<TextView>(R.id.selectedAppsText).text.contains(missingLabel)
            }
            val dialog = openPicker(scenario, scrollToLabel = missingLabel)
            scenario.onActivity {
                val choices = dialog.listView.adapter
                val missingIndex = (0 until choices.count).single {
                    choices.getItem(it).toString() == missingLabel
                }
                assertTrue(dialog.listView.isItemChecked(missingIndex))
                val row = dialog.listView.getChildAt(
                    missingIndex - dialog.listView.firstVisiblePosition,
                ) as? CheckedTextView
                assertTrue("Unavailable selected row must visibly remain checked", row?.isChecked == true)
            }
            saveAndAwait(scenario, dialog, selected)
            assertEquals(
                "Saving an unchanged set retains the unavailable member",
                before,
                preferences.snapshot(),
            )
            scenario.recreate()
            awaitSelectionIdle(scenario)
            awaitActivity(scenario, "Unavailable saved app disappeared after recreation") {
                it.findViewById<TextView>(R.id.selectedAppsText).text.contains(missingPackage)
            }
            assertEquals(before, preferences.snapshot())
        }
    }

    @Test
    fun protectedAndUnavailableSavedRowsCanBeKeptUnchangedButCannotBeRecheckedAfterRemoval() {
        val eligible = eligibleTargets().first()
        val protectedPackage = context.packageName
        val unavailablePackage = "synthetic.unavailable.removal.only"
        val saved = setOf(eligible, protectedPackage, unavailablePackage)
        assertTrue(preferences.setSelectedPackages(saved))
        val before = preferences.snapshot()
        val removalOnlyLabels = listOf(protectedPackage, unavailablePackage).map {
            context.getString(R.string.target_unavailable, it)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val unchanged = openPicker(scenario)
            removalOnlyLabels.forEach { label ->
                var index = -1
                scenario.onActivity { index = choiceIndex(unchanged, label) }
                scrollToChoice(scenario, unchanged, index)
                scenario.onActivity {
                    assertTrue(unchanged.listView.isItemChecked(index))
                    val row = unchanged.listView.getChildAt(index - unchanged.listView.firstVisiblePosition)
                        as CheckedTextView
                    assertTrue("Unchanged removal-only row must visibly remain checked", row.isChecked)
                }
            }
            saveAndAwait(scenario, unchanged, saved)
            assertEquals("An unchanged legacy set must not be silently normalized", before, preferences.snapshot())

            val edited = openPicker(scenario)
            removalOnlyLabels.forEach { label ->
                var index = -1
                scenario.onActivity { index = choiceIndex(edited, label) }
                scrollToChoice(scenario, edited, index)
                scenario.onActivity {
                    clickChoice(edited, index, expectedChecked = false)
                    // Rechecking an unavailable/protected saved row must not re-add it to the draft,
                    // even when the native listener is invoked programmatically.
                    clickChoice(edited, index, expectedChecked = false)
                    assertEquals("Editing a draft cannot write storage", before, preferences.snapshot())
                }
                awaitActivity(scenario, "Rejected recheck must visibly return to unchecked") {
                    val list = edited.listView
                    val row = list.getChildAt(index - list.firstVisiblePosition) as? CheckedTextView
                    return@awaitActivity !list.isItemChecked(index) && row?.isChecked == false
                }
                scrollToChoice(scenario, edited, 0)
                scrollToChoice(scenario, edited, index)
                awaitActivity(scenario, "Removal-only row must remain unchecked after rebinding") {
                    val list = edited.listView
                    val row = list.getChildAt(index - list.firstVisiblePosition) as? CheckedTextView
                    return@awaitActivity !list.isItemChecked(index) && row?.isChecked == false
                }
                scenario.onActivity {
                    assertFalse(edited.listView.isItemChecked(index))
                    val row = edited.listView.getChildAt(index - edited.listView.firstVisiblePosition)
                        as CheckedTextView
                    assertFalse("Rebound removal-only row must visibly remain unchecked", row.isChecked)
                }
            }
            saveAndAwait(scenario, edited, setOf(eligible))
            val reopened = openPicker(scenario)
            scenario.onActivity {
                val labels = (0 until reopened.listView.adapter.count).map { index ->
                    reopened.listView.adapter.getItem(index).toString()
                }
                removalOnlyLabels.forEach { assertFalse("Removed unsafe target must not become selectable", it in labels) }
                reopened.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            assertEquals(setOf(eligible), preferences.snapshot().selectedPackages)
            assertFalse(preferences.snapshot().desiredActive)
        }
    }

    @Test
    fun pausingAndRecreatingUnsavedPickerDiscardsDraftWithoutWritingOrReopening() {
        val targets = eligibleTargets()
        assertTrue(preferences.setSelectedPackages(setOf(targets.first())))
        val before = preferences.snapshot()
        val rawBefore = rawLocalState()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val pausedDraft = openPicker(scenario)
            scenario.onActivity { clickChoice(pausedDraft, 1) }
            scenario.moveToState(Lifecycle.State.CREATED)
            instrumentation.runOnMainSync {
                assertFalse("Leaving the activity must dismiss the unsaved picker", pausedDraft.isShowing)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitSelectionIdle(scenario)
            scenario.onActivity { assertNull("Resuming must not reopen the picker", currentPicker(it)) }
            assertEquals(before, preferences.snapshot())
            assertEquals(rawBefore, rawLocalState())

            val recreatedDraft = openPicker(scenario)
            scenario.onActivity {
                assertTrue(recreatedDraft.listView.isItemChecked(0))
                assertFalse("Paused draft must not restore an unsaved check", recreatedDraft.listView.isItemChecked(1))
                clickChoice(recreatedDraft, 1)
            }
            scenario.recreate()
            awaitSelectionIdle(scenario)
            scenario.onActivity { assertNull("Recreation must not reopen the picker", currentPicker(it)) }
            assertEquals(before, preferences.snapshot())
            assertEquals(rawBefore, rawLocalState())

            val restored = openPicker(scenario)
            scenario.onActivity {
                assertTrue(restored.listView.isItemChecked(0))
                assertFalse("Recreated draft must not restore an unsaved check", restored.listView.isItemChecked(1))
                restored.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            assertEquals(before, preferences.snapshot())
            assertEquals("Lifecycle changes must not save a draft or rewrite setup", rawBefore, rawLocalState())
        }
    }

    @Test
    fun withdrawalInvalidatesOpenDraftAndRequiresExplicitReloadWithoutResurrectingTargets() {
        val targets = eligibleTargets()
        assertTrue(preferences.setSelectedPackages(setOf(targets.first())))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dialog = openPicker(scenario)
            scenario.onActivity { clickChoice(dialog, 1) }
            // Another entry point may withdraw while this editor remains visible.
            // The queued Save must not capture a fresh token or resurrect the old draft.
            assertTrue(preferences.clearForWithdrawal())
            val withdrawn = rawLocalState()
            scenario.onActivity { dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
            awaitActivity(scenario, "Stale editor did not offer explicit reload") {
                selectionIdle(it) && currentPicker(it) === dialog &&
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString() ==
                    context.getString(R.string.target_reload)
            }
            assertEquals(withdrawn, rawLocalState())
            assertTrue(preferences.snapshot().selectedPackages.isEmpty())
            scenario.onActivity { dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
            awaitActivity(scenario, "Explicit reload did not create a fresh picker") {
                selectionIdle(it) && currentPicker(it)?.let { fresh -> fresh !== dialog && fresh.isShowing } == true
            }
            scenario.onActivity {
                val fresh = requireNotNull(currentPicker(it))
                assertFalse(fresh.listView.isItemChecked(0))
                assertFalse(fresh.listView.isItemChecked(1))
                fresh.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            assertEquals(withdrawn, rawLocalState())
        }
    }

    @Test
    fun failedAsyncSaveKeepsDraftOpenForRemovalAndExplicitRetry() {
        val targets = eligibleTargets()
        val unavailable = "synthetic.unavailable.retry"
        assertTrue(preferences.setSelectedPackages(setOf(targets.first(), unavailable)))
        val before = preferences.snapshot()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dialog = openPicker(scenario)
            scenario.onActivity {
                clickChoice(dialog, 1)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            }
            awaitActivity(scenario, "Invalid save did not return a usable picker") {
                selectionIdle(it) && currentPicker(it) === dialog && dialog.isShowing &&
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled &&
                    it.findViewById<TextView>(R.id.statusText).text.toString() ==
                    context.getString(R.string.target_invalid_selection)
            }
            assertEquals("Failed validation must leave saved state untouched", before, preferences.snapshot())
            var unavailableIndex = -1
            scenario.onActivity {
                assertTrue("Retry must preserve the user's unsaved eligible choice", dialog.listView.isItemChecked(1))
                unavailableIndex = choiceIndex(dialog, context.getString(R.string.target_unavailable, unavailable))
            }
            scrollToChoice(scenario, dialog, unavailableIndex)
            scenario.onActivity { clickChoice(dialog, unavailableIndex, expectedChecked = false) }
            saveAndAwait(scenario, dialog, targets.toSet())
            assertFalse(preferences.snapshot().desiredActive)
        }
    }

    private fun eligibleTargets(): List<String> {
        check(emulatorGuardPassed)
        val packages = LauncherAppRepository(context).load().take(2).map { it.packageName }
        assertEquals("Emulator fixture requires two eligible launcher apps", 2, packages.size)
        return packages
    }

    private fun rawLocalState(): Map<String, *> {
        check(emulatorGuardPassed)
        return context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE).all.toMap()
    }

    private fun openPicker(
        scenario: ActivityScenario<MainActivity>,
        scrollToLabel: String? = null,
    ): AlertDialog {
        check(emulatorGuardPassed)
        lateinit var dialog: AlertDialog
        scenario.onActivity { activity ->
            activity.findViewById<Button>(R.id.selectAppsButton).performClick()
        }
        awaitActivity(scenario, "Async app picker did not open") { activity ->
            currentPicker(activity)?.takeIf { selectionIdle(activity) && it.isShowing && it.listView.adapter.count > 0 }
                ?.also { dialog = it } != null
        }
        var index = 0
        scenario.onActivity {
            if (scrollToLabel != null) index = choiceIndex(dialog, scrollToLabel)
        }
        scrollToChoice(scenario, dialog, index)
        return dialog
    }

    private fun currentPicker(activity: MainActivity): AlertDialog? =
        MainActivity::class.java.getDeclaredField("appPickerDialog").run {
            isAccessible = true
            get(activity) as? AlertDialog
        }

    private fun clickChoice(dialog: AlertDialog, index: Int, expectedChecked: Boolean = true) {
        val list = dialog.listView
        assertTrue(list.performItemClick(
            list.getChildAt(index - list.firstVisiblePosition),
            index,
            list.adapter.getItemId(index),
        ))
        assertEquals(expectedChecked, list.isItemChecked(index))
    }

    private fun choiceIndex(dialog: AlertDialog, label: String): Int =
        (0 until dialog.listView.adapter.count).single {
            dialog.listView.adapter.getItem(it).toString() == label
        }

    private fun scrollToChoice(scenario: ActivityScenario<MainActivity>, dialog: AlertDialog, index: Int) {
        scenario.onActivity {
            dialog.listView.setSelection(index)
            dialog.listView.requestLayout()
        }
        // Inspect native rows only after binding/layout, not immediately after dialog.show().
        awaitActivity(scenario, "Picker row $index did not become visible") {
            val list = dialog.listView
            index in list.firstVisiblePosition..list.lastVisiblePosition &&
                list.getChildAt(index - list.firstVisiblePosition) is CheckedTextView
        }
    }

    private fun saveAndAwait(
        scenario: ActivityScenario<MainActivity>,
        dialog: AlertDialog,
        expectedPackages: Set<String>,
    ) {
        scenario.onActivity {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            assertTrue(save.isEnabled)
            save.performClick()
            assertFalse("Save must be disabled while the off-main write is pending", save.isEnabled)
        }
        awaitActivity(scenario, "Async app selection save did not complete") {
            selectionIdle(it) && !dialog.isShowing && currentPicker(it) == null &&
                preferences.snapshot().selectedPackages == expectedPackages
        }
    }

    private fun refreshAndAwait(scenario: ActivityScenario<MainActivity>) {
        awaitSelectionIdle(scenario)
        scenario.onActivity {
            val refresh = it.findViewById<Button>(R.id.refreshAppsButton)
            assertTrue(refresh.isEnabled)
            refresh.performClick()
            assertFalse("Refresh must expose its pending load", selectionIdle(it))
        }
        awaitSelectionIdle(scenario)
    }

    private fun awaitSelectionIdle(scenario: ActivityScenario<MainActivity>) {
        awaitActivity(scenario, "App-selection background work did not settle", ::selectionIdle)
    }

    private fun selectionIdle(activity: MainActivity): Boolean =
        listOf("appSelectionLoading", "appSelectionSaving").none { field ->
            MainActivity::class.java.getDeclaredField(field).run {
                isAccessible = true
                getBoolean(activity)
            }
        }

    private fun awaitActivity(
        scenario: ActivityScenario<MainActivity>,
        message: String,
        condition: (MainActivity) -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        do {
            var complete = false
            scenario.onActivity { complete = condition(it) }
            if (complete) return
            SystemClock.sleep(25L)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(message)
    }
}
