package com.justnoise.gate0.platform.nfc

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object NdefTextPayloadParser {
    private const val TNF_WELL_KNOWN = 0x01
    private val RTD_TEXT = byteArrayOf(0x54)
    const val MAX_UTF8_TEXT_BYTES = 512
    const val MAX_LANGUAGE_BYTES = 35

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
