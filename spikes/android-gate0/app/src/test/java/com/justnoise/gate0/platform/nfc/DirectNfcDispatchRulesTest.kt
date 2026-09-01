package com.justnoise.gate0.platform.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectNfcDispatchRulesTest {
    @Test
    fun `accepts exact NDEF text dispatch with live tag`() {
        assertTrue(
            DirectNfcDispatchRules.accepts(
                action = "android.nfc.action.NDEF_DISCOVERED",
                mimeType = "text/plain",
                hasLiveTag = true,
            ),
        )
    }

    @Test
    fun `rejects copied message dispatch without live tag`() {
        assertFalse(
            DirectNfcDispatchRules.accepts(
                action = "android.nfc.action.NDEF_DISCOVERED",
                mimeType = "text/plain",
                hasLiveTag = false,
            ),
        )
    }

    @Test
    fun `rejects wrong action or MIME`() {
        assertFalse(DirectNfcDispatchRules.accepts("forged", "text/plain", true))
        assertFalse(
            DirectNfcDispatchRules.accepts(
                "android.nfc.action.NDEF_DISCOVERED",
                "application/octet-stream",
                true,
            ),
        )
    }
}
