package com.justnoise.integrated

import com.justnoise.gate0.feature.Gate0Truth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedFirstSessionGuideTest {
    @Test fun readyHomeCanShowGuideWithoutRequiringAModeInOnboarding() {
        listOf(Gate0Truth.IDLE, Gate0Truth.TARGET_REQUIRED).forEach {
            assertTrue(IntegratedFirstSessionGuideRules.shouldShow(false, it, false, false, false))
        }
    }

    @Test fun permissionsPairingAndRecoveryMustPrecedeTheVideo() {
        Gate0Truth.entries.filterNot { it in setOf(Gate0Truth.IDLE, Gate0Truth.TARGET_REQUIRED) }
            .forEach {
                assertFalse(IntegratedFirstSessionGuideRules.shouldShow(false, it, false, false, false))
            }
    }

    @Test fun onceDismissedTheVideoDoesNotReturnToNormalHome() {
        assertFalse(IntegratedFirstSessionGuideRules.shouldShow(true, Gate0Truth.IDLE, false, false, false))
    }

    @Test fun guideNeverObscuresSessionScanOrAnotherSheet() {
        assertFalse(IntegratedFirstSessionGuideRules.shouldShow(false, Gate0Truth.IDLE, true, false, false))
        assertFalse(IntegratedFirstSessionGuideRules.shouldShow(false, Gate0Truth.IDLE, false, true, false))
        assertFalse(IntegratedFirstSessionGuideRules.shouldShow(false, Gate0Truth.IDLE, false, false, true))
    }
}
