package com.justnoise.gate0.platform.nfc

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object NdefTextPayloadParser {
    private const val TNF_WELL_KNOWN = 0x01
    private val RTD_TEXT = byteArrayOf(0x54)
    const val MAX_UTF8_TEXT_BYTES = 512
    const val MAX_LANGUAGE_BYTES = 35
    /** UTF-16 is accepted only for registered JNZ1 credentials: BOM plus 256 code units. */
    const val MAX_UTF16_TEXT_BYTES = 2 + 2 * RegisteredZap.MAX_TEXT_UTF8_BYTES

    fun selectSingleCanonicalStrictUtf8Record(
        records: Iterable<NdefRecordFields>,
    ): NdefTextSelection {
        var recordCount = 0
        val textRecords = mutableListOf<NdefRecordFields>()
        records.forEach { record ->
            recordCount += 1
            if (isTextRecord(record.tnf, record.type)) {
                textRecords += record
            }
        }

        if (textRecords.isEmpty()) {
            return NdefTextSelection.Rejected(
                reason = NdefTextSelectionFailure.NO_TEXT_RECORD,
                recordCount = recordCount,
                textRecordCount = 0,
            )
        }
        if (textRecords.size != 1) {
            return NdefTextSelection.Rejected(
                reason = NdefTextSelectionFailure.AMBIGUOUS_TEXT_RECORDS,
                recordCount = recordCount,
                textRecordCount = textRecords.size,
            )
        }

        val record = textRecords.single()
        val canonicalRecord = parseCanonicalStrictUtf8Record(
            tnf = record.tnf,
            type = record.type,
            payload = record.payload,
        ) ?: parseCanonicalRegisteredUtf16Record(
            tnf = record.tnf,
            type = record.type,
            payload = record.payload,
        ) ?: return NdefTextSelection.Rejected(
            reason = NdefTextSelectionFailure.INVALID_TEXT_RECORD,
            recordCount = recordCount,
            textRecordCount = 1,
        )
        return NdefTextSelection.Accepted(
            transientCanonicalRecord = canonicalRecord,
            recordCount = recordCount,
        )
    }

    fun parseCanonicalStrictUtf8Record(
        tnf: Int,
        type: ByteArray,
        payload: ByteArray,
    ): ByteArray? {
        if (tnf != TNF_WELL_KNOWN || !type.contentEquals(RTD_TEXT) || payload.isEmpty()) {
            return null
        }

        val status = payload[0].toInt() and 0xFF
        val usesUtf16 = status and 0x80 != 0
        val reservedBitSet = status and 0x40 != 0
        val languageLength = status and 0x3F
        if (
            usesUtf16 ||
            reservedBitSet ||
            languageLength == 0 ||
            languageLength > MAX_LANGUAGE_BYTES
        ) return null
        if (payload.size <= 1 + languageLength) return null
        val textLength = payload.size - 1 - languageLength
        if (textLength > MAX_UTF8_TEXT_BYTES) return null

        val language = payload.copyOfRange(1, 1 + languageLength)
        if (!language.all(::isLanguageByte)) return null

        val text = payload.copyOfRange(1 + languageLength, payload.size)
        if (text.isEmpty() || !isValidUtf8(text)) {
            text.fill(0)
            return null
        }
        text.fill(0)
        return byteArrayOf(TNF_WELL_KNOWN.toByte(), RTD_TEXT.single()) + payload
    }

    /**
     * Earlier Zap Station builds wrote some JNZ1 credentials as UTF-16 Text records.
     * Mirrors iOS `ZapTextPayload.decode(_:allowsUTF16:)`: only a UTF-16 record whose
     * text is a JNZ1 credential is selected; legacy labels keep strict UTF-8 rules.
     */
    fun parseCanonicalRegisteredUtf16Record(
        tnf: Int,
        type: ByteArray,
        payload: ByteArray,
    ): ByteArray? {
        if (!isTextRecord(tnf, type) || payload.isEmpty()) return null
        if ((payload[0].toInt() and 0x80) == 0) return null
        val text = decodeTextPayload(payload, allowsUtf16 = true) ?: return null
        if (!text.startsWith(RegisteredZap.PREFIX)) return null
        return byteArrayOf(TNF_WELL_KNOWN.toByte(), RTD_TEXT.single()) + payload
    }

    /** Returns the JNZ1 credential text of a canonical record, or null for any other text. */
    fun decodeRegisteredCredentialText(canonicalRecord: ByteArray): String? {
        if (canonicalRecord.size < 4) return null
        if (!isTextRecord(canonicalRecord[0].toInt(), byteArrayOf(canonicalRecord[1]))) return null
        val payload = canonicalRecord.copyOfRange(2, canonicalRecord.size)
        return try {
            decodeTextPayload(payload, allowsUtf16 = true)
                ?.takeIf { it.startsWith(RegisteredZap.PREFIX) }
        } finally {
            payload.fill(0)
        }
    }

    internal fun decodeTextPayload(payload: ByteArray, allowsUtf16: Boolean): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt() and 0xFF
        val usesUtf16 = status and 0x80 != 0
        val languageLength = status and 0x3F
        if (status and 0x40 != 0 || languageLength == 0 || languageLength > MAX_LANGUAGE_BYTES) {
            return null
        }
        if (payload.size <= 1 + languageLength) return null
        for (index in 1..languageLength) if (!isLanguageByte(payload[index])) return null
        val body = payload.copyOfRange(1 + languageLength, payload.size)
        return try {
            if (!usesUtf16) {
                if (body.size > MAX_UTF8_TEXT_BYTES) null else strictDecode(StandardCharsets.UTF_8, body, 0)
            } else if (!allowsUtf16 || body.size % 2 != 0 || body.size > MAX_UTF16_TEXT_BYTES) {
                null
            } else {
                // External UTF-16 uses its BOM for byte order, defaulting to big-endian.
                val first = body[0].toInt() and 0xFF
                val second = body[1].toInt() and 0xFF
                when {
                    first == 0xFF && second == 0xFE -> strictDecode(StandardCharsets.UTF_16LE, body, 2)
                    first == 0xFE && second == 0xFF -> strictDecode(StandardCharsets.UTF_16BE, body, 2)
                    else -> strictDecode(StandardCharsets.UTF_16BE, body, 0)
                }
            }
        } finally {
            body.fill(0)
        }
    }

    private fun strictDecode(charset: java.nio.charset.Charset, value: ByteArray, offset: Int): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(value, offset, value.size - offset))
            .toString()
    } catch (_: Exception) {
        null
    }

    fun isTextRecord(tnf: Int, type: ByteArray): Boolean =
        tnf == TNF_WELL_KNOWN && type.contentEquals(RTD_TEXT)

    private fun isLanguageByte(value: Byte): Boolean {
        val character = value.toInt() and 0xFF
        return character in 'A'.code..'Z'.code ||
            character in 'a'.code..'z'.code ||
            character in '0'.code..'9'.code ||
            character == '-'.code
    }

    private fun isValidUtf8(value: ByteArray): Boolean = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(value))
        true
    } catch (_: Exception) {
        false
    }
}

sealed interface NdefTextSelection {
    data class Accepted(
        val transientCanonicalRecord: ByteArray,
        val recordCount: Int,
    ) : NdefTextSelection

    data class Rejected(
        val reason: NdefTextSelectionFailure,
        val recordCount: Int,
        val textRecordCount: Int,
    ) : NdefTextSelection
}

enum class NdefTextSelectionFailure {
    NO_TEXT_RECORD,
    INVALID_TEXT_RECORD,
    AMBIGUOUS_TEXT_RECORDS,
}

data class NdefRecordFields(
    val tnf: Int,
    val type: ByteArray,
    val payload: ByteArray,
)
