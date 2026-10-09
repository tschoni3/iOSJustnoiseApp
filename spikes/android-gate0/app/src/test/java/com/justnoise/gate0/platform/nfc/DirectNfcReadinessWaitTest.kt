package com.justnoise.gate0.platform.nfc

import org.junit.Assert.*
import org.junit.Test

class DirectNfcReadinessWaitTest {
    @Test fun coldAccountCanWaitWithoutServiceRecovery() {
        assertTrue(DirectNfcReadinessWait.shouldWait(true, true, false, 100, 1_500))
        assertFalse(DirectNfcReadinessWait.shouldWait(true, false, false, 100, 1_500))
    }
    @Test fun cancelledClaimNeverWaitsForEitherDependency() {
        assertFalse(DirectNfcReadinessWait.shouldWait(false, true, true, 100, 1_500))
    }
    @Test fun deadlineIsStrictAndNeverExtendedByAnotherDependency() {
        assertTrue(DirectNfcReadinessWait.shouldWait(true, true, true, 1_499, 1_500))
        assertFalse(DirectNfcReadinessWait.shouldWait(true, true, true, 1_500, 1_500))
        assertFalse(DirectNfcReadinessWait.shouldWait(true, true, true, 2_000, 1_500))
    }
    @Test fun existingServiceRecoveryWaitRemainsAvailable() {
        assertTrue(DirectNfcReadinessWait.shouldWait(true, false, true, 100, 1_500))
    }
}
