package com.justnoise.gate0

import android.content.Context
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.gate0.app.MainActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.data.StoredGate0State
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import java.security.KeyStore
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

    @Test
    fun withdrawButtonClearsPairingAndStateAndRequiresFreshConsentAfterRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val store = HmacZapStore(preferences)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keyAlias = "justnoise_gate0_zap_hmac_v1"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Canonical well-known UTF-8 Text record, language "en"; no physical NFC access.
        val syntheticRecord = byteArrayOf(0x01, 0x54, 0x02, 0x65, 0x6E) +
            "synthetic-withdrawal-test".toByteArray(Charsets.UTF_8)
        val emptyState = StoredGate0State(
            acceptedDisclosureVersion = null,
            acceptedDisclosureEpochMilliseconds = 0L,
            selectedPackage = null,
            zapDigest = null,
            desiredActive = false,
            activeSinceEpochMilliseconds = 0L,
            sessionRevision = 0L,
            recoveryRequired = false,
            serviceConnected = false,
            serviceHeartbeatElapsedMilliseconds = 0L,
        )

        try {
            assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION))
            val target = LauncherAppRepository(context).load().first().packageName
            assertTrue(preferences.setSelectedPackage(target))
            assertTrue(store.pair(syntheticRecord))
            assertTrue(store.isAuthorized(syntheticRecord))
            assertTrue(keyStore.containsAlias(keyAlias))
            assertTrue(preferences.startSession(200_000L))
            preferences.markRecoveryRequired()
            assertTrue(preferences.updateServiceHeartbeat(5_000L))

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertTrue(preferences.snapshot().desiredActive)
                    assertTrue(activity.findViewById<CheckBox>(R.id.consentCheckbox).isChecked)
                    activity.findViewById<Button>(R.id.withdrawButton).performClick()
                    assertEquals(
                        activity.getString(R.string.status_withdrawn),
                        activity.findViewById<TextView>(R.id.statusText).text.toString(),
                    )
                }
                instrumentation.waitForIdleSync()
                assertEquals(emptyState, preferences.snapshot())
                assertFalse(store.isAuthorized(syntheticRecord))
                assertFalse(
                    "Withdrawal must delete the key, not just its digest",
                    keyStore.containsAlias(keyAlias),
                )

                scenario.recreate()
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val checkbox = activity.findViewById<CheckBox>(R.id.consentCheckbox)
                    assertFalse(checkbox.isChecked)
                    assertTrue(checkbox.isEnabled)
                    assertFalse(
                        activity.findViewById<Button>(R.id.accessibilitySettingsButton).isEnabled,
                    )
                    assertFalse(Gate0Controller(activity).hasAcceptedCurrentDisclosure())
                }
                assertEquals(
                    "Recreation must not restore cleared configuration",
                    emptyState,
                    preferences.snapshot(),
                )
                assertFalse(keyStore.containsAlias(keyAlias))
            }
        } finally {
            syntheticRecord.fill(0)
        }
    }

    private fun clearGate0State() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        HmacZapStore(preferences).clear()
        preferences.clearForWithdrawal()
    }
}
