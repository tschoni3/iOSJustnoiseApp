package com.justnoise.gate0.platform.nfc

import java.io.File
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Runs the portable `product/behavior/registered-zap.v1.json` vectors (TEST issuers only). */
class RegisteredZapTest {
    private val root = File(requireNotNull(System.getProperty("justnoise.repositoryRoot")))
    private val fixture = JSONObject(File(root, "product/behavior/registered-zap.v1.json").readText())
    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun trust(json: JSONObject) = RegisteredZap(
        Base64.getDecoder().decode(json.getString("verificationKey")),
        Base64.getDecoder().decode(json.getString("contentKey")),
    )
    private val testTrust = trust(fixture.getJSONObject("testTrust"))
    private val text = fixture.getString("credentialText")
    private val uid = hex(fixture.getString("uidHex"))

    /** Mirrors the reader pipeline: single text record selection, then UID-bound recognition. */
    private fun recognized(records: List<NdefRecordFields>, physicalUid: ByteArray, issuer: RegisteredZap) =
        when (val selection = NdefTextPayloadParser.selectSingleCanonicalStrictUtf8Record(records)) {
            is NdefTextSelection.Rejected -> false
            is NdefTextSelection.Accepted -> try {
                ZapCompatibilityLabels.isRecognized(
                    selection.transientCanonicalRecord, physicalUid, selection.recordCount, issuer,
                )
            } finally { selection.transientCanonicalRecord.fill(0) }
        }

    private fun utf8Record(value: String) =
        NdefRecordFields(1, byteArrayOf(0x54), byteArrayOf(2, 0x65, 0x6e) + value.toByteArray())

    @Test fun `all portable registered Zap vectors match iOS decisions`() {
        val cases = fixture.getJSONArray("cases")
        assertTrue(cases.length() >= 15)
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val records = case.getJSONArray("records").let { list ->
                (0 until list.length()).map { list.getJSONObject(it) }.map {
                    NdefRecordFields(it.getInt("tnf"), hex(it.getString("type")), hex(it.getString("payloadHex")))
                }
            }
            assertEquals(case.getString("id"), case.getBoolean("expected"),
                recognized(records, hex(case.getString("uidHex")), testTrust))
        }
    }

    @Test fun `record produced by the factory issuer script verifies on Android`() {
        val vector = fixture.getJSONObject("issuerScriptVector")
        val issuer = trust(vector.getJSONObject("trust"))
        val record = listOf(utf8Record(vector.getString("text")))
        assertTrue(recognized(record, hex(vector.getString("uidHex")), issuer))
        assertFalse(recognized(record, hex(vector.getString("wrongUidHex")), issuer))
        assertFalse(recognized(record, hex(vector.getString("uidHex")), testTrust))
    }

    @Test fun `production trust rejects test credentials and wrong content keys fail closed`() {
        assertFalse(RegisteredZap.bundled.accepts(text, uid))
        assertFalse(ZapCompatibilityLabels.isRecognized(byteArrayOf(1, 0x54, 2, 0x65, 0x6e) + text.toByteArray(), uid))
        val wrongContent = RegisteredZap(
            Base64.getDecoder().decode(fixture.getJSONObject("testTrust").getString("verificationKey")),
            ByteArray(32),
        )
        assertFalse(wrongContent.accepts(text, uid))
        assertTrue(testTrust.accepts(text, uid))
    }

    @Test fun `credential requires exactly one record and a seven byte uid`() {
        val canonical = byteArrayOf(1, 0x54, 2, 0x65, 0x6e) + text.toByteArray()
        assertTrue(ZapCompatibilityLabels.isRecognized(canonical, uid, 1, testTrust))
        assertFalse(ZapCompatibilityLabels.isRecognized(canonical, uid, 2, testTrust))
        assertFalse(ZapCompatibilityLabels.isRecognized(canonical, ByteArray(0), 1, testTrust))
        assertFalse(ZapCompatibilityLabels.isRecognized(canonical, uid + byteArrayOf(0), 1, testTrust))
        assertFalse(testTrust.accepts(text + "A".repeat(300), uid))
    }

    @Test fun `verification does not mutate caller owned uid or record`() {
        val canonical = byteArrayOf(1, 0x54, 2, 0x65, 0x6e) + text.toByteArray()
        val originalRecord = canonical.copyOf()
        val originalUid = uid.copyOf()
        assertTrue(ZapCompatibilityLabels.isRecognized(canonical, uid, 1, testTrust))
        assertArrayEquals(originalRecord, canonical)
        assertArrayEquals(originalUid, uid)
    }

    @Test fun `strict base64 matches Foundation padding rules`() {
        assertNotNull(RegisteredZap.strictBase64("QUI="))
        assertNull(RegisteredZap.strictBase64("QUI"))
        assertNull(RegisteredZap.strictBase64("Q=UI"))
        assertNull(RegisteredZap.strictBase64("QU I"))
        assertNull(RegisteredZap.strictBase64("QUI-"))
        assertNull(RegisteredZap.strictBase64(""))
    }

    @Test fun `utf16 is accepted only for registered credentials`() {
        val legacyUtf16 = NdefRecordFields(1, byteArrayOf(0x54),
            byteArrayOf(0x82.toByte(), 0x65, 0x6e) + "legacy-test".toByteArray(Charsets.UTF_16BE))
        assertTrue(NdefTextPayloadParser.selectSingleCanonicalStrictUtf8Record(listOf(legacyUtf16))
            is NdefTextSelection.Rejected)
    }

    @Test fun `android trust constants equal the iOS ZapTrust json`() {
        val ios = JSONObject(File(root, "justnoiseradio/Features/NFC/ZapTrust.json").readText())
        assertEquals(ios.getString("verificationKey"), ZapTrust.VERIFICATION_KEY)
        assertEquals(ios.getString("contentKey"), ZapTrust.CONTENT_KEY)
    }
}
