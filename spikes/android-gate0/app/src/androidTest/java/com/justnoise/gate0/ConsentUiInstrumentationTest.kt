package com.justnoise.gate0

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.app.MainActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.feature.Gate0Controller
import org.junit.After
import org.junit.Assert.assertFalse
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
