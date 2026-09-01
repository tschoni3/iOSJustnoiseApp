package com.justnoise.gate0.platform.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `accepts exactly one strict text record when unrelated records are present`() {
        val expectedText = byteArrayOf(1, 2, 3, 4)
        val records = listOf(
            NdefRecordFields(
                tnf = 4,
                type = "android.com:pkg".toByteArray(),
                payload = "com.example".toByteArray(),
            ),
            NdefRecordFields(
                tnf = 1,
                type = byteArrayOf(0x54),
                payload = byteArrayOf(2, 'e'.code.toByte(), 'n'.code.toByte()) + expectedText,
            ),
        )

        val selection = NdefTextPayloadParser.selectSingleCanonicalStrictUtf8Record(records)

        assertTrue(selection is NdefTextSelection.Accepted)
        selection as NdefTextSelection.Accepted
        assertArrayEquals(
            byteArrayOf(1, 0x54, 2, 'e'.code.toByte(), 'n'.code.toByte()) + expectedText,
            selection.transientCanonicalRecord,
        )
        assertEquals(2, selection.recordCount)
        selection.transientCanonicalRecord.fill(0)

        val reversed = NdefTextPayloadParser.selectSingleCanonicalStrictUtf8Record(
            records.reversed(),
        ) as NdefTextSelection.Accepted
        assertArrayEquals(
            byteArrayOf(1, 0x54, 2, 'e'.code.toByte(), 'n'.code.toByte()) + expectedText,
            reversed.transientCanonicalRecord,
        )
        reversed.transientCanonicalRecord.fill(0)
    }

    @Test
    fun `rejects a message with no text record`() {
        val records = listOf(
            NdefRecordFields(
                tnf = 4,
                type = "android.com:pkg".toByteArray(),
                payload = "com.example".toByteArray(),
            ),
        )

        assertEquals(
            NdefTextSelection.Rejected(
                reason = NdefTextSelectionFailure.NO_TEXT_RECORD,
                recordCount = 1,
                textRecordCount = 0,
            ),
            NdefTextPayloadParser.selectSingleCanonicalStrictUtf8Record(records),
        )
    }

    @Test
    fun `rejects two text records even when one is valid`() {
        val valid = NdefRecordFields(
            tnf = 1,
            type = byteArrayOf(0x54),
            payload = byteArrayOf(2, 'e'.code.toByte(), 'n'.code.toByte(), 1),
        )
        val second = valid.copy(
            payload = byteArrayOf(2, 'd'.code.toByte(), 'e'.code.toByte(), 2),
        )

        assertEquals(
            NdefTextSelection.Rejected(
                reason = NdefTextSelectionFailure.AMBIGUOUS_TEXT_RECORDS,
                recordCount = 2,
                textRecordCount = 2,
            ),
            NdefTextPayloadParser.selectSingleCanonicalStrictUtf8Record(listOf(valid, second)),
        )
    }

    @Test
    fun `rejects malformed text followed by valid text as ambiguous`() {
        val malformed = NdefRecordFields(
            tnf = 1,
            type = byteArrayOf(0x54),
            payload = byteArrayOf(0x82.toByte(), 'e'.code.toByte(), 'n'.code.toByte(), 1),
        )
        val valid = NdefRecordFields(
            tnf = 1,
            type = byteArrayOf(0x54),
            payload = byteArrayOf(2, 'e'.code.toByte(), 'n'.code.toByte(), 1),
        )

        assertEquals(
            NdefTextSelectionFailure.AMBIGUOUS_TEXT_RECORDS,
            (NdefTextPayloadParser.selectSingleCanonicalStrictUtf8Record(
                listOf(malformed, valid),
            ) as NdefTextSelection.Rejected).reason,
        )
    }

    @Test
    fun `rejects one malformed text record as invalid`() {
        val malformed = NdefRecordFields(
            tnf = 1,
            type = byteArrayOf(0x54),
            payload = byteArrayOf(0x82.toByte(), 'e'.code.toByte(), 'n'.code.toByte(), 1),
        )

        assertEquals(
            NdefTextSelectionFailure.INVALID_TEXT_RECORD,
            (NdefTextPayloadParser.selectSingleCanonicalStrictUtf8Record(
                listOf(malformed),
            ) as NdefTextSelection.Rejected).reason,
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
