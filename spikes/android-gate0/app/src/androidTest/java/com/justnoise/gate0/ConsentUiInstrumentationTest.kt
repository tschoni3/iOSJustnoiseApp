package com.justnoise.gate0

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.gate0.app.MainActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsentUiInstrumentationTest {
    @Before
    fun clearGate0StateBeforeTest() {
        clearGate0State()
    }

    @After
    fun clearGate0StateAfterTest() {
        clearGate0State()
    }

    @Test
    fun openingRefreshingAndRecreatingSetupPreservesActiveSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val target = LauncherAppRepository(context).load().first().packageName
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION))
        assertTrue(preferences.setSelectedPackage(target))

        for (recoveryRequired in listOf(false, true)) {
            assertTrue(preferences.startSession(200_000L))
            if (recoveryRequired) preferences.markRecoveryRequired()
            val before = preferences.snapshot()

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                assertEquals("Activity creation must not reselect the stored target", before, preferences.snapshot())

                scenario.onActivity { activity ->
                    activity.findViewById<android.widget.Button>(R.id.refreshAppsButton).performClick()
                }
                instrumentation.waitForIdleSync()
                assertEquals("Refreshing app choices must not reset the session", before, preferences.snapshot())

                scenario.recreate()
                instrumentation.waitForIdleSync()
                assertEquals("Activity recreation must not reset the session", before, preferences.snapshot())
            }
        }
    }

    @Test
    fun disclosureRequiresUncheckedAffirmativeControlBeforeSettingsButton() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val checkbox = activity.findViewById<android.widget.CheckBox>(R.id.consentCheckbox)
                val settingsButton = activity.findViewById<android.widget.Button>(
                    R.id.accessibilitySettingsButton,
                )

                assertFalse(checkbox.isChecked)
                assertFalse(settingsButton.isEnabled)
                checkbox.isChecked = true
                assertTrue(settingsButton.isEnabled)
            }
        }
    }

    @Test
    fun acceptedDisclosureStaysCheckedUntilExplicitWithdrawal() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(Gate0Controller(activity).acceptDisclosure())
                activity.recreate()
            }
            scenario.onActivity { activity ->
                val checkbox = activity.findViewById<android.widget.CheckBox>(R.id.consentCheckbox)
                assertTrue(checkbox.isChecked)
                assertFalse(checkbox.isEnabled)
            }
        }
    }

    private fun clearGate0State() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        HmacZapStore(preferences).clear()
        preferences.clearForWithdrawal()
    }
}
