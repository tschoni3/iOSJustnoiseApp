package com.justnoise.integrated

import com.justnoise.gate0.feature.Gate0Truth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedProductEntryRulesTest {
    @Test fun normalReadyStatesNeedNoRecoveryPresentation() {
        assertFalse(IntegratedProductEntryRules.needsRecovery(Gate0Truth.IDLE, false, false, false, false))
        assertFalse(IntegratedProductEntryRules.needsRecovery(Gate0Truth.ACTIVE, true, false, false, false))
    }
    @Test fun unconfirmedSessionNeverReachesTheNormalActiveOrIdleHero() {
        Gate0Truth.entries.filter { it != Gate0Truth.ACTIVE }.forEach {
            assertTrue(IntegratedProductEntryRules.needsRecovery(it, true, false, false, false))
        }
    }
    @Test fun storageQuarantineCannotBeHiddenByReadyOrActiveTruth() {
        Gate0Truth.entries.forEach {
            assertTrue(IntegratedProductEntryRules.needsRecovery(it, false, true, false, false))
        }
    }
    @Test fun missingDisclosureOrBlockingUsesTheSetupPresentation() {
        assertTrue(IntegratedProductEntryRules.needsBlocking(Gate0Truth.CONSENT_REQUIRED, false, true))
        assertTrue(IntegratedProductEntryRules.needsBlocking(Gate0Truth.PERMISSION_REQUIRED, true, false))
        assertFalse(IntegratedProductEntryRules.needsBlocking(Gate0Truth.IDLE, true, true))
        assertFalse(IntegratedProductEntryRules.needsBlocking(Gate0Truth.ACTIVE, true, true))
    }
    @Test fun missingModeCannotMaskRevokedBlockingAccessOrMissingDisclosure() {
        assertTrue(IntegratedProductEntryRules.needsBlocking(Gate0Truth.TARGET_REQUIRED, true, false))
        assertTrue(IntegratedProductEntryRules.needsBlocking(Gate0Truth.TARGET_REQUIRED, false, true))
        assertFalse(IntegratedProductEntryRules.needsBlocking(Gate0Truth.TARGET_REQUIRED, true, true))
    }
    @Test fun aStaleGrantSampleCannotPutPermissionEducationOverAnActiveStopRoute() {
        assertFalse(IntegratedProductEntryRules.needsBlocking(Gate0Truth.ACTIVE, true, false))
        assertFalse(IntegratedProductEntryRules.needsBlocking(Gate0Truth.ACTIVE, false, false))
        assertTrue(IntegratedProductEntryRules.needsRecovery(Gate0Truth.RECOVERY_REQUIRED, true, false, false, false))
    }
    @Test fun suspendedSchedulesAreGuidedOutsideTheListButDoNotBlockManualZapUse() {
        assertTrue(IntegratedProductEntryRules.needsRecovery(Gate0Truth.IDLE, false, false, true, true))
        assertFalse(IntegratedProductEntryRules.needsRecovery(Gate0Truth.IDLE, false, false, false, true))
    }
}
