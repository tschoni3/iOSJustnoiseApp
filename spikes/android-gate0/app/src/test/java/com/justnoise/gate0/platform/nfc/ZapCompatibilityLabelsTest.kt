package com.justnoise.gate0.platform.nfc

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ZapCompatibilityLabelsTest {
    private val root = File(requireNotNull(System.getProperty("justnoise.repositoryRoot")))
    private fun iosLabels(): List<String> {
        val source = File(root, "justnoiseradio/Features/NFC/NFCViewModel.swift").readText()
        val list = source.substringAfter("private let authorizedTagPayloads: Set<String> = [")
            .substringBefore("]")
        return Regex("\"([^\"]+)\"").findAll(list).map { it.groupValues[1] }.toList()
    }
    private fun record(text: String, language: String = "en"): ByteArray =
        byteArrayOf(1, 0x54, language.length.toByte()) + language.toByteArray() + text.toByteArray()

    @Test fun `all unchanged iOS compatibility labels are recognized in any valid language`() {
        val labels = iosLabels()
        assertEquals(3, labels.size)
        labels.forEach {
            assertTrue(ZapCompatibilityLabels.isRecognized(record(it)))
            assertTrue(ZapCompatibilityLabels.isRecognized(record(it, "de")))
        }
    }
    @Test fun `unknown altered and whitespace padded labels cannot be provisioned`() {
        assertFalse(ZapCompatibilityLabels.isRecognized(record("unrelated-test-tag")))
        iosLabels().forEach {
            assertFalse(ZapCompatibilityLabels.isRecognized(record(" $it")))
            assertFalse(ZapCompatibilityLabels.isRecognized(record("$it\n")))
            assertFalse(ZapCompatibilityLabels.isRecognized(record("${it}x")))
        }
    }
    @Test fun `malformed utf16 wrong type and oversized records fail closed`() {
        val valid = record(iosLabels().first())
        assertFalse(ZapCompatibilityLabels.isRecognized(byteArrayOf()))
        assertFalse(ZapCompatibilityLabels.isRecognized(valid.copyOf().apply { this[1] = 0x55 }))
        assertFalse(ZapCompatibilityLabels.isRecognized(valid.copyOf().apply { this[2] = 0x82.toByte() }))
        assertFalse(ZapCompatibilityLabels.isRecognized(record("x".repeat(513))))
        assertFalse(ZapCompatibilityLabels.isRecognized(byteArrayOf(1, 0x54, 2, 0x65, 0x6e, 0xff.toByte())))
    }
    @Test fun `validation does not overwrite caller-owned canonical bytes`() {
        val valid = record(iosLabels().first())
        val original = valid.copyOf()
        assertTrue(ZapCompatibilityLabels.isRecognized(valid))
        assertArrayEquals(original, valid)
    }
    @Test fun `all identifier free authorization migration fixtures pass`() {
        val cases = JSONObject(File(root, "product/behavior/android-zap-labels.v1.json").readText())
            .getJSONArray("cases")
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            assertEquals(case.getString("id"), case.getBoolean("expected"),
                ZapCompatibilityLabels.allowsSessionRead(case.getBoolean("recognized"),
                    case.getBoolean("paired"), case.getBoolean("active")))
        }
    }
    @Test fun `initial and replacement pairing and common session path all apply label guard`() {
        val source = File(root, "spikes/android-gate0/app/src/main/java/com/justnoise/gate0/feature/Gate0Controller.kt").readText()
        assertEquals(3, Regex("ZapCompatibilityLabels.isRecognized\\(transientCanonicalRecord, transientUid, recordCount\\)").findAll(source).count())
        assertTrue(source.contains("ZapCompatibilityLabels.allowsSessionRead("))
    }
}
