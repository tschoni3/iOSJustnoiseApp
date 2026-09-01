package com.justnoise.gate0

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.platform.nfc.NdefMessageParser
import com.justnoise.gate0.platform.nfc.NdefReadFailure
import com.justnoise.gate0.platform.nfc.NdefReadResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NdefInstrumentationTest {
    @Test
    fun AndroidNdefMessage_acceptsOneStrictTextRecordWithAuxiliaryRecord() {
        val textRecord = NdefRecord.createTextRecord("en", "instrumentation-only")
        val appRecord = NdefRecord.createExternal(
            "android.com",
            "pkg",
            "com.example".toByteArray(),
        )
        val result = NdefMessageParser.parseSingleStrictTextRecord(
            NdefMessage(arrayOf(appRecord, textRecord)),
        )

        assertTrue(result is NdefReadResult.Accepted)
        result as NdefReadResult.Accepted
        val payload = textRecord.payload
        assertArrayEquals(byteArrayOf(1, 0x54) + payload, result.transientCanonicalRecord)
        assertEquals(2, result.recordCount)
        result.transientCanonicalRecord.fill(0)
    }

    @Test
    fun AndroidNdefMessage_rejectsNoTextAndAmbiguousTextMessages() {
        val appRecord = NdefRecord.createExternal(
            "android.com",
            "pkg",
            "com.example".toByteArray(),
        )
        val firstText = NdefRecord.createTextRecord("en", "first")
        val secondText = NdefRecord.createTextRecord("de", "second")

        assertEquals(
            NdefReadFailure.NO_TEXT_RECORD,
            (NdefMessageParser.parseSingleStrictTextRecord(
                NdefMessage(appRecord),
            ) as NdefReadResult.Rejected).reason,
        )
        assertEquals(
            NdefReadFailure.AMBIGUOUS_TEXT_RECORDS,
            (NdefMessageParser.parseSingleStrictTextRecord(
                NdefMessage(arrayOf(firstText, secondText)),
            ) as NdefReadResult.Rejected).reason,
        )
    }

    @Test
    fun AndroidNdefMessage_rejectsMalformedTextBeforeValidTextAsAmbiguous() {
        val malformedText = NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            byteArrayOf(),
            byteArrayOf(0x82.toByte(), 'e'.code.toByte(), 'n'.code.toByte(), 1),
        )
        val validText = NdefRecord.createTextRecord("en", "valid")

        val result = NdefMessageParser.parseSingleStrictTextRecord(
            NdefMessage(arrayOf(malformedText, validText)),
        )

        assertEquals(
            NdefReadFailure.AMBIGUOUS_TEXT_RECORDS,
            (result as NdefReadResult.Rejected).reason,
        )
    }
}
