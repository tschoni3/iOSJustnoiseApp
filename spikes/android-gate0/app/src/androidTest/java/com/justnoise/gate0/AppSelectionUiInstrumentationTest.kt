package com.justnoise.gate0

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.app.MainActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSelectionUiInstrumentationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private val preferences: Gate0Preferences
        get() = Gate0Preferences(context)

    @Before
    @After
    fun clearLocalState() {
        assertTrue(preferences.clearForWithdrawal())
    }

    @Test
    fun explicitSavePersistsMultipleAppsAndRestoresAfterRecreation() {
        val expected = eligibleTargets().toSet()
        val before = preferences.snapshot()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = openPicker(activity)
                clickChoice(dialog, 0)
                clickChoice(dialog, 1)
                assertEquals("Checking choices is not a save", before, preferences.snapshot())
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            assertEquals(expected, preferences.snapshot().selectedPackages)
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(expected, preferences.snapshot().selectedPackages)
                val summary = activity.findViewById<TextView>(R.id.selectedAppsText).text.toString()
                assertTrue(summary.contains(
                    activity.resources.getQuantityString(R.plurals.selected_apps_count, 2, 2),
                ))
                val dialog = openPicker(activity)
                assertTrue(dialog.listView.isItemChecked(0))
                assertTrue(dialog.listView.isItemChecked(1))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
        }
    }

    @Test
    fun cancelDiscardsDraftWithoutChangingStoredState() {
        val targets = eligibleTargets()
        assertTrue(preferences.setSelectedPackages(setOf(targets.first())))
        val before = preferences.snapshot()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = openPicker(activity)
                clickChoice(dialog, 1)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                activity.findViewById<Button>(R.id.refreshAppsButton).performClick()
            }
            scenario.recreate()
            assertEquals(before, preferences.snapshot())
        }
    }

    @Test
    fun activeRecoveryIntentPreventsEditingWithoutStoppingSession() {
        assertTrue(preferences.setSelectedPackages(eligibleTargets().toSet()))
        assertTrue(preferences.startSession(200_000L))
        preferences.markRecoveryRequired()
        val before = preferences.snapshot()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.selectAppsButton)
                assertFalse(button.isEnabled)
                val summary = activity.findViewById<TextView>(R.id.selectedAppsText).text.toString()
                assertTrue(summary.contains(activity.getString(R.string.target_edit_requires_stop)))
                // Exercise the listener guard too; a programmatic click bypasses touch dispatch.
                button.performClick()
                assertNull(currentPicker(activity))
                activity.findViewById<Button>(R.id.refreshAppsButton).performClick()
            }
            assertEquals(before, preferences.snapshot())
        }
    }

    @Test
    fun unavailableSelectedAppRemainsVisibleAndIsNotSilentlyDropped() {
        val missingPackage = "synthetic.unavailable.selection"
        val selected = setOf(eligibleTargets().first(), missingPackage)
        assertTrue(preferences.setSelectedPackages(selected))
        val before = preferences.snapshot()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.refreshAppsButton).performClick()
                val missingLabel = activity.getString(R.string.target_unavailable, missingPackage)
                assertTrue(
                    activity.findViewById<TextView>(R.id.selectedAppsText).text.contains(missingLabel),
                )
                val dialog = openPicker(activity)
                val choices = dialog.listView.adapter
                val missingIndex = (0 until choices.count).single {
                    choices.getItem(it).toString() == missingLabel
                }
                assertTrue(dialog.listView.isItemChecked(missingIndex))
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            assertEquals(
                "Saving an unchanged set retains the unavailable member",
                before,
                preferences.snapshot(),
            )
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(
                    activity.findViewById<TextView>(R.id.selectedAppsText).text.contains(missingPackage),
                )
            }
            assertEquals(before, preferences.snapshot())
        }
    }

    private fun eligibleTargets(): List<String> {
        val packages = LauncherAppRepository(context).load().take(2).map { it.packageName }
        assertEquals("Emulator fixture requires two eligible launcher apps", 2, packages.size)
        return packages
    }

    private fun openPicker(activity: MainActivity): AlertDialog {
        activity.findViewById<Button>(R.id.selectAppsButton).performClick()
        return checkNotNull(currentPicker(activity)) { "App picker did not open" }
    }

    private fun currentPicker(activity: MainActivity): AlertDialog? =
        MainActivity::class.java.getDeclaredField("appPickerDialog").run {
            isAccessible = true
            get(activity) as? AlertDialog
        }

    private fun clickChoice(dialog: AlertDialog, index: Int) {
        val list = dialog.listView
        assertTrue(list.performItemClick(
            list.getChildAt(index - list.firstVisiblePosition),
            index,
            list.adapter.getItemId(index),
        ))
        assertTrue(list.isItemChecked(index))
    }
}
