package com.justnoise.gate0.platform.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef

object NdefMessageParser {
    fun parseSingleStrictTextRecord(message: NdefMessage): ByteArray? {
        if (message.records.size != 1) return null
        val record = message.records.single()
        return NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
            tnf = record.tnf.toInt(),
            type = record.type,
            payload = record.payload,
        )
    }

    fun isTextRecord(record: NdefRecord): Boolean =
        record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)
}

object NdefTagReader {
    fun readSingleStrictTextRecord(tag: Tag): ByteArray? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            NdefMessageParser.parseSingleStrictTextRecord(ndef.ndefMessage)
        } catch (_: Exception) {
            null
        } finally {
            try {
                ndef.close()
            } catch (_: Exception) {
                // Closing a transient NFC connection is best-effort. No tag data is retained.
            }
        }
    }
}
