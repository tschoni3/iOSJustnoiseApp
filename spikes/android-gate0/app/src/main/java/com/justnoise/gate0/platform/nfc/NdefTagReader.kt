package com.justnoise.gate0.platform.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import java.io.IOException

object NdefMessageParser {
    fun parseSingleStrictTextRecord(message: NdefMessage): NdefReadResult {
        val records = message.records
        val textRecordIndexes = records.indices.filter { index ->
            isTextRecord(records[index])
        }
        if (textRecordIndexes.isEmpty()) {
            return NdefReadResult.Rejected(
                reason = NdefReadFailure.NO_TEXT_RECORD,
                recordCount = records.size,
                textRecordCount = 0,
            )
        }
        if (textRecordIndexes.size != 1) {
            return NdefReadResult.Rejected(
                reason = NdefReadFailure.AMBIGUOUS_TEXT_RECORDS,
                recordCount = records.size,
                textRecordCount = textRecordIndexes.size,
            )
        }

        val candidateIndex = textRecordIndexes.single()
        val transientFields = records.mapIndexed { index, record ->
            NdefRecordFields(
                tnf = record.tnf.toInt(),
                type = record.type,
                payload = if (index == candidateIndex) record.payload.copyOf() else byteArrayOf(),
            )
        }
        return try {
            when (
                val selection = NdefTextPayloadParser.selectSingleCanonicalStrictUtf8Record(
                    transientFields,
                )
            ) {
                is NdefTextSelection.Accepted -> NdefReadResult.Accepted(
                    transientCanonicalRecord = selection.transientCanonicalRecord,
                    recordCount = selection.recordCount,
                )

                is NdefTextSelection.Rejected -> NdefReadResult.Rejected(
                    reason = when (selection.reason) {
                        NdefTextSelectionFailure.NO_TEXT_RECORD ->
                            NdefReadFailure.NO_TEXT_RECORD

                        NdefTextSelectionFailure.INVALID_TEXT_RECORD ->
                            NdefReadFailure.INVALID_TEXT_RECORD

                        NdefTextSelectionFailure.AMBIGUOUS_TEXT_RECORDS ->
                            NdefReadFailure.AMBIGUOUS_TEXT_RECORDS
                    },
                    recordCount = selection.recordCount,
                    textRecordCount = selection.textRecordCount,
                )
            }
        } finally {
            transientFields.forEach { fields -> fields.payload.fill(0) }
        }
    }

    fun isTextRecord(record: NdefRecord): Boolean =
        NdefTextPayloadParser.isTextRecord(
            tnf = record.tnf.toInt(),
            type = record.type,
        )
}

object NdefTagReader {
    fun readSingleStrictTextRecord(tag: Tag): NdefReadResult {
        val ndef = try {
            Ndef.get(tag)
        } catch (_: SecurityException) {
            return NdefReadResult.Rejected(NdefReadFailure.SECURITY_DENIED)
        } catch (_: RuntimeException) {
            return NdefReadResult.Rejected(NdefReadFailure.READ_FAILED)
        }
        if (ndef == null) {
            return NdefReadResult.Rejected(NdefReadFailure.NDEF_UNAVAILABLE)
        }
        return try {
            ndef.connect()
            val message = ndef.ndefMessage
                ?: return NdefReadResult.Rejected(NdefReadFailure.EMPTY_NDEF_MESSAGE)
            when (val parsed = NdefMessageParser.parseSingleStrictTextRecord(message)) {
                is NdefReadResult.Accepted -> parsed.copy(transientUid = transientUid(tag))
                is NdefReadResult.Rejected -> parsed
            }
        } catch (_: TagLostException) {
            NdefReadResult.Rejected(NdefReadFailure.TAG_LOST)
        } catch (_: FormatException) {
            NdefReadResult.Rejected(NdefReadFailure.MALFORMED_NDEF)
        } catch (_: IOException) {
            NdefReadResult.Rejected(NdefReadFailure.IO_FAILED)
        } catch (_: SecurityException) {
            NdefReadResult.Rejected(NdefReadFailure.SECURITY_DENIED)
        } catch (_: RuntimeException) {
            NdefReadResult.Rejected(NdefReadFailure.READ_FAILED)
        } finally {
            try {
                ndef.close()
            } catch (_: Exception) {
                // Closing a transient NFC connection is best-effort. No tag data is retained.
            }
        }
    }

    /**
     * Owner decision 2026-11-21: the chip UID is read only to verify UID-bound JNZ1
     * registrations. It is copied into caller-owned memory that is zeroed after the
     * scan, and is never persisted, logged, sent to analytics or the network.
     */
    private fun transientUid(tag: Tag): ByteArray = try {
        tag.id?.copyOf() ?: ByteArray(0)
    } catch (_: RuntimeException) {
        ByteArray(0)
    }
}

sealed interface NdefReadResult {
    data class Accepted(
        val transientCanonicalRecord: ByteArray,
        val recordCount: Int,
        /** Transient 7-byte NFC UID for JNZ1 verification; empty when unavailable. */
        val transientUid: ByteArray = ByteArray(0),
    ) : NdefReadResult {
        /** Zeroes the transient record and UID copies after the scan has been handled. */
        fun clear() {
            transientCanonicalRecord.fill(0)
            transientUid.fill(0)
        }
    }

    data class Rejected(
        val reason: NdefReadFailure,
        val recordCount: Int = 0,
        val textRecordCount: Int = 0,
    ) : NdefReadResult
}

enum class NdefReadFailure {
    NDEF_UNAVAILABLE,
    EMPTY_NDEF_MESSAGE,
    TAG_LOST,
    MALFORMED_NDEF,
    IO_FAILED,
    SECURITY_DENIED,
    READ_FAILED,
    NO_TEXT_RECORD,
    INVALID_TEXT_RECORD,
    AMBIGUOUS_TEXT_RECORDS,
}
