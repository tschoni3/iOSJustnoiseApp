package com.justnoise.gate0

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.app.MainActivity
import com.justnoise.gate0.feature.Gate0Controller
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsentUiInstrumentationTest {
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
}
