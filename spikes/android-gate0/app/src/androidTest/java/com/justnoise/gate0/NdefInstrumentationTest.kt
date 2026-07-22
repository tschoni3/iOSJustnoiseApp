package com.justnoise.gate0

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.platform.nfc.NdefMessageParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NdefInstrumentationTest {
    @Test
    fun AndroidNdefRecord_isAcceptedOnlyAsOneStrictTextRecord() {
        val textRecord = NdefRecord.createTextRecord("en", "instrumentation-only")
        val parsed = NdefMessageParser.parseSingleStrictTextRecord(NdefMessage(textRecord))

        val payload = textRecord.payload
        assertArrayEquals(byteArrayOf(1, 0x54) + payload, parsed)
        parsed?.fill(0)
        assertNull(
            NdefMessageParser.parseSingleStrictTextRecord(
                NdefMessage(arrayOf(textRecord, textRecord)),
            ),
        )
    }
}
