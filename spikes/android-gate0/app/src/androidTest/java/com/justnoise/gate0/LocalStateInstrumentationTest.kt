package com.justnoise.gate0

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStateInstrumentationTest {
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
}
