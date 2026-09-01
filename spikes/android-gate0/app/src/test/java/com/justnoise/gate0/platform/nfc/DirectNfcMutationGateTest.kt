package com.justnoise.gate0.platform.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectNfcMutationGateTest {
    @Test
    fun `rejects concurrent or bouncing claim within interval`() {
        val gate = DirectNfcMutationGate(minimumIntervalMilliseconds = 3_000L)

        assertTrue(gate.tryClaim(nowElapsedMilliseconds = 10_000L))
        assertFalse(gate.tryClaim(nowElapsedMilliseconds = 10_000L))
        assertFalse(gate.tryClaim(nowElapsedMilliseconds = 12_999L))
    }

    @Test
    fun `accepts a later physical interaction`() {
        val gate = DirectNfcMutationGate(minimumIntervalMilliseconds = 3_000L)

        assertTrue(gate.tryClaim(nowElapsedMilliseconds = 10_000L))
        assertTrue(gate.tryClaim(nowElapsedMilliseconds = 13_000L))
    }
}
