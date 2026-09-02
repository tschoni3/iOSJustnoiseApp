package com.justnoise.gate0

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import com.justnoise.gate0.platform.restriction.Gate0AccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStateInstrumentationTest {
    @Test
    fun sameStoredTargetPreservesActiveAndRecoveryState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        preferences.clearForWithdrawal()

        try {
            assertTrue(preferences.setSelectedPackage("synthetic.target"))
            for (recoveryRequired in listOf(false, true)) {
                assertTrue(preferences.startSession(200_000L))
                if (recoveryRequired) preferences.markRecoveryRequired()
                val before = preferences.snapshot()

                assertTrue(preferences.setSelectedPackage("synthetic.target"))

                assertEquals(before, preferences.snapshot())
            }
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun sameControllerTargetDoesNotClearStateOrBroadcast() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val target = LauncherAppRepository(context).load().first().packageName
        val recordingContext = BroadcastRecordingContext(context)
        preferences.clearForWithdrawal()

        try {
            assertTrue(preferences.setSelectedPackage(target))
            assertTrue(preferences.startSession(200_000L))
            preferences.markRecoveryRequired()
            val before = preferences.snapshot()
            val controller = Gate0Controller(recordingContext, preferences)

            assertTrue(controller.selectTarget(target))
            assertEquals(before, preferences.snapshot())
            assertTrue(recordingContext.broadcasts.isEmpty())

            assertFalse(controller.selectTarget("synthetic.missing.target"))
            assertEquals(before, preferences.snapshot())
            assertTrue(recordingContext.broadcasts.isEmpty())
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun differentEligibleTargetStillClearsSessionAndBroadcasts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val target = LauncherAppRepository(context).load().first().packageName
        val recordingContext = BroadcastRecordingContext(context)
        preferences.clearForWithdrawal()

        try {
            assertTrue(preferences.setSelectedPackage("synthetic.previous.target"))
            assertTrue(preferences.startSession(200_000L))
            preferences.markRecoveryRequired()
            val before = preferences.snapshot()

            assertTrue(Gate0Controller(recordingContext, preferences).selectTarget(target))

            val after = preferences.snapshot()
            assertEquals(target, after.selectedPackage)
            assertFalse(after.desiredActive)
            assertFalse(after.recoveryRequired)
            assertEquals(0L, after.activeSinceEpochMilliseconds)
            assertEquals(before.sessionRevision + 1L, after.sessionRevision)
            assertEquals(1, recordingContext.broadcasts.size)
            assertEquals(
                Gate0AccessibilityService.ACTION_STATE_CHANGED,
                recordingContext.broadcasts.single().action,
            )
            assertEquals(context.packageName, recordingContext.broadcasts.single().`package`)
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun consentTimestampAndSessionRevisionAreDurableAndMonotonic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        preferences.clearForWithdrawal()

        assertTrue(preferences.acceptDisclosure("gate0-accessibility-v1", 123_456L))
        assertEquals(123_456L, preferences.snapshot().acceptedDisclosureEpochMilliseconds)
        assertTrue(preferences.setSelectedPackage("synthetic.target"))
        val selectedRevision = preferences.snapshot().sessionRevision
        assertTrue(preferences.startSession(200_000L))
        val startedRevision = preferences.snapshot().sessionRevision
        assertTrue(preferences.stopSession())
        val stoppedRevision = preferences.snapshot().sessionRevision

        assertTrue(selectedRevision > 0L)
        assertTrue(startedRevision > selectedRevision)
        assertTrue(stoppedRevision > startedRevision)
        preferences.clearForWithdrawal()
    }

    private class BroadcastRecordingContext(context: Context) : ContextWrapper(context) {
        val broadcasts = mutableListOf<Intent>()

        override fun getApplicationContext(): Context = this

        override fun sendBroadcast(intent: Intent) {
            broadcasts += intent
        }
    }
}
