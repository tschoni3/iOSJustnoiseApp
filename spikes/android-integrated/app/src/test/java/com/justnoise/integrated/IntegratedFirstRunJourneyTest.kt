package com.justnoise.integrated

import com.justnoise.gate0.feature.Gate0Truth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Combined routing regressions only: no real Google, NFC or Settings acceptance claim. */
class IntegratedFirstRunJourneyTest {
    private val fresh = IntegratedFirstRunFacts(false, false, false, false, false)

    @Test fun freshJourneyCannotReachProductBeforeAccountAndBlockingFactsAreReady() {
        assertEquals(IntegratedFirstRunRoute.ONBOARDING, IntegratedFirstRunRules.route(fresh))
        val tutorial = fresh.copy(tutorialCompleted = true)
        assertEquals(IntegratedFirstRunRoute.ACTIVATION, IntegratedFirstRunRules.route(tutorial))
        val paired = tutorial.copy(zapPaired = true)
        assertEquals(IntegratedFirstRunRoute.MAIN, IntegratedFirstRunRules.route(paired))
        assertTrue(IntegratedAccountAccess(ready = true).blocksRoute(IntegratedFirstRunRoute.MAIN, 0))
        assertFalse(IntegratedAccountAccess(ready = true, signedIn = true)
            .blocksRoute(IntegratedFirstRunRoute.MAIN, 0))
        assertTrue(IntegratedFirstRunRules.needsBlockingSetup(paired, false, false))
        assertEquals(IntegratedPermissionStep.BLOCKING,
            IntegratedPermissionFlow.next(false, false, true, false))
        // Agreeing to disclosure cannot substitute for enabling the system service.
        assertEquals(IntegratedPermissionStep.BLOCKING,
            IntegratedPermissionFlow.next(true, false, true, false))
        assertEquals(IntegratedPermissionStep.SCHEDULES,
            IntegratedPermissionFlow.next(true, true, true, false))
        assertEquals(IntegratedPermissionStep.COMPLETE,
            IntegratedPermissionFlow.next(true, true, true, true))
        assertFalse(paired.sessionRequested)
    }

    @Test fun missingModeDoesNotRequireSetupOrAutomaticallyOpenItsEditor() {
        val paired = fresh.copy(tutorialCompleted = true, zapPaired = true)
        assertFalse(IntegratedFirstRunRules.needsBlockingSetup(paired, true, true))
        assertFalse(IntegratedProductEntryRules.needsBlocking(Gate0Truth.TARGET_REQUIRED, true, true))
        val root = generateSequence(java.io.File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) {
            it.parentFile
        }.map { java.io.File(it, "spikes/android-integrated/app") }
            .first { java.io.File(it, "build.gradle.kts").isFile }
        val activity = java.io.File(root, "src/main/java/com/justnoise/integrated/MainActivity.kt").readText()
        val finishSetup = activity.substringAfter("private fun finishPermissionSetup()")
            .substringBefore("private fun skipScheduleSetup()")
        assertFalse(finishSetup.contains("modes.open()"))
        assertFalse(activity.contains("showingFirstModeSetup"))
        assertTrue(activity.contains("disclosureAccepted, accessibilityEnabled"))
        val refresh = activity.substringAfter("private fun refreshTruth()")
            .substringBefore("private fun beginOneShotScan(")
        assertTrue(refresh.contains("accessibilityEnabled = AccessibilityServiceStatus.isEnabled(this)"))
    }

    @Test fun coldReturnAfterPairingRetainsMissingPermissionGateInsteadOfReplayingEducation() {
        val paired = fresh.copy(tutorialCompleted = true, zapPaired = true)
        for (restored in listOf(null, IntegratedFirstRunRoute.ONBOARDING, IntegratedFirstRunRoute.MAIN)) {
            assertEquals(IntegratedFirstRunRoute.MAIN, IntegratedFirstRunRules.route(paired, restored))
            assertTrue(IntegratedFirstRunRules.needsBlockingSetup(paired, true, false))
            assertEquals(IntegratedPermissionStep.BLOCKING,
                IntegratedPermissionFlow.next(true, false, true, false))
        }
    }

    @Test fun declinedAlarmOfferCompletesManualSetupButLaterScheduleEntryStillRequiresAccess() {
        assertEquals(IntegratedPermissionStep.COMPLETE,
            IntegratedPermissionFlow.next(true, true, false, false))
        assertEquals(IntegratedPermissionStep.SCHEDULES,
            IntegratedPermissionFlow.next(true, true, true, false))
        assertEquals(IntegratedPermissionStep.COMPLETE,
            IntegratedPermissionFlow.next(true, true, true, true))
    }

    @Test fun previouslyGrantedAccessSkipsEducationWithoutManufacturingAnActiveSession() {
        val existing = fresh.copy(zapPaired = true, existingSetup = true)
        assertEquals(IntegratedFirstRunRoute.MAIN, IntegratedFirstRunRules.route(existing))
        assertFalse(IntegratedFirstRunRules.needsBlockingSetup(existing, true, true))
        assertEquals(IntegratedPermissionStep.COMPLETE,
            IntegratedPermissionFlow.next(true, true, true, true))
        assertFalse(existing.sessionRequested)
        assertFalse(IntegratedProductEntryRules.needsRecovery(Gate0Truth.IDLE, false, false, false, false))
    }

    @Test fun activeSessionWithLostReadinessCannotBeHiddenByRestoredOnboarding() {
        val interrupted = fresh.copy(zapPaired = true, sessionRequested = true)
        IntegratedFirstRunRoute.entries.forEach { restored ->
            assertEquals(IntegratedFirstRunRoute.MAIN, IntegratedFirstRunRules.route(interrupted, restored))
            assertFalse(IntegratedFirstRunRules.needsBlockingSetup(interrupted, true, false))
            assertTrue(IntegratedProductEntryRules.needsRecovery(
                Gate0Truth.RECOVERY_REQUIRED, true, false, false, false))
        }
    }
}
