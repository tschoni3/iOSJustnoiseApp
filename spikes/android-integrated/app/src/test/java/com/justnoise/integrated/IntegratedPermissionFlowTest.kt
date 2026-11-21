package com.justnoise.integrated

import org.junit.Assert.assertEquals
import org.junit.Test

class IntegratedPermissionFlowTest {
    @Test fun disclosureAndSystemSwitchAreIndependentRequiredFacts() {
        for (consent in listOf(false, true)) for (enabled in listOf(false, true)) {
            if (!consent || !enabled) assertEquals(IntegratedPermissionStep.BLOCKING,
                IntegratedPermissionFlow.next(consent, enabled, true, true))
        }
    }

    @Test fun scheduledStartsAreOfferedOnlyAfterBlockingIsActuallyEnabled() {
        assertEquals(IntegratedPermissionStep.SCHEDULES,
            IntegratedPermissionFlow.next(true, true, true, false))
    }

    @Test fun grantedAlarmAccessIsSkippedWithoutAnotherSettingsVisit() {
        assertEquals(IntegratedPermissionStep.COMPLETE,
            IntegratedPermissionFlow.next(true, true, true, true))
    }

    @Test fun optionalSchedulesCanBeSkippedWithoutPretendingAlarmAccessWasGranted() {
        assertEquals(IntegratedPermissionStep.COMPLETE,
            IntegratedPermissionFlow.next(true, true, false, false))
        assertEquals(IntegratedPermissionStep.SCHEDULES,
            IntegratedPermissionFlow.next(true, true, true, false))
    }

    @Test fun revokedBlockingReturnsToBlockingBeforeAnyAlarmStep() {
        assertEquals(IntegratedPermissionStep.BLOCKING,
            IntegratedPermissionFlow.next(true, false, true, true))
    }
}
