package com.justnoise.gate0.platform.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NdefTextPayloadParserTest {
    @Test
    fun `accepts one well-known UTF-8 text payload`() {
        val expected = byteArrayOf(1, 2, 3, 4)
        val payload = byteArrayOf(2, 'e'.code.toByte(), 'n'.code.toByte()) + expected

        assertArrayEquals(
            byteArrayOf(1, 0x54, 2, 'e'.code.toByte(), 'n'.code.toByte()) + expected,
            NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
                tnf = 1,
                type = byteArrayOf(0x54),
                payload = payload,
            ),
        )
    }

    @Test
    fun `rejects UTF-16 malformed and wrong-record shapes`() {
        val utf16 = byteArrayOf(0x82.toByte(), 'e'.code.toByte(), 'n'.code.toByte(), 1, 2)
        val malformedUtf8 = byteArrayOf(
            2,
            'e'.code.toByte(),
            'n'.code.toByte(),
            0xC3.toByte(),
            0x28,
        )

        assertNull(
            NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
                1,
                byteArrayOf(0x54),
                utf16,
            ),
        )
        assertNull(
            NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
                1,
                byteArrayOf(0x54),
                malformedUtf8,
            ),
        )
        assertNull(
            NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
                2,
                byteArrayOf(0x54),
                malformedUtf8,
            ),
        )
        assertNull(
            NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
                1,
                byteArrayOf(0x55),
                malformedUtf8,
            ),
        )
    }

    @Test
    fun `rejects blank text reserved status and invalid language code`() {
        assertNull(
            NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
                1,
                byteArrayOf(0x54),
                byteArrayOf(2, 'e'.code.toByte(), 'n'.code.toByte()),
            ),
        )
        assertNull(
            NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
                1,
                byteArrayOf(0x54),
                byteArrayOf(0x42, 'e'.code.toByte(), 'n'.code.toByte(), 1),
            ),
        )
        assertNull(
            NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
                1,
                byteArrayOf(0x54),
                byteArrayOf(2, 'e'.code.toByte(), '_'.code.toByte(), 1),
            ),
        )
    }

    @Test
    fun `rejects an NDEF text value above the explicit scan bound`() {
        val oversized = byteArrayOf(2, 'e'.code.toByte(), 'n'.code.toByte()) +
            ByteArray(NdefTextPayloadParser.MAX_UTF8_TEXT_BYTES + 1) { 1 }

        assertNull(
            NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
                1,
                byteArrayOf(0x54),
                oversized,
            ),
        )
    }

    @Test
    fun `canonical record distinguishes metadata even when text bytes match`() {
        val english = NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
            1,
            byteArrayOf(0x54),
            byteArrayOf(2, 'e'.code.toByte(), 'n'.code.toByte(), 1, 2, 3),
        )
        val german = NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
            1,
            byteArrayOf(0x54),
            byteArrayOf(2, 'd'.code.toByte(), 'e'.code.toByte(), 1, 2, 3),
        )

        requireNotNull(english)
        requireNotNull(german)
        assertFalse(english.contentEquals(german))
        english.fill(0)
        german.fill(0)
    }
}
