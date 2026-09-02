package com.justnoise.gate0

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.app.DirectNfcActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.platform.nfc.NdefMessageParser
import com.justnoise.gate0.platform.nfc.NdefReadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectNfcActivityInstrumentationTest {
    @Test
    fun copiedAuthorizedMessageWithoutLiveTagCannotStopSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        preferences.clearForWithdrawal()
        val zapStore = HmacZapStore(preferences)
        val message = NdefMessage(
            arrayOf(NdefRecord.createTextRecord("en", "direct-forgery-test-only")),
        )
        val accepted = NdefMessageParser.parseSingleStrictTextRecord(message)
            as NdefReadResult.Accepted
        val canonicalRecord = accepted.transientCanonicalRecord

        try {
            assertTrue(zapStore.pair(canonicalRecord))
            assertTrue(preferences.setSelectedPackages(setOf("synthetic.blocked.app")))
            assertTrue(preferences.startSession(System.currentTimeMillis() - 10_000L))
            val before = preferences.snapshot()

            val forgedIntent = Intent(context, DirectNfcActivity::class.java).apply {
                action = NfcAdapter.ACTION_NDEF_DISCOVERED
                type = "text/plain"
                putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(message))
            }
            ActivityScenario.launch<DirectNfcActivity>(forgedIntent).use { }

            val after = preferences.snapshot()
            assertTrue(after.desiredActive)
            assertEquals(before.sessionRevision, after.sessionRevision)
            assertEquals(
                before.activeSinceEpochMilliseconds,
                after.activeSinceEpochMilliseconds,
            )
            assertEquals(before.recoveryRequired, after.recoveryRequired)
        } finally {
            canonicalRecord.fill(0)
            zapStore.clear()
            preferences.clearForWithdrawal()
        }
    }
}
