package com.justnoise.integrated

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source regression guards, not evidence of physical rendering. */
class IntegratedSheetPresentationTest {
    private val sources = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
        .map { File(it, "spikes/android-integrated/app/src/main/java/com/justnoise/integrated") }
        .first { it.isDirectory }

    @Test fun allScrollingEditorsUseTheSameNonDraggableSheet() {
        listOf("IntegratedModeSurface.kt", "IntegratedSelectionSurface.kt", "IntegratedScheduleSurface.kt",
            "IntegratedCompletionSurface.kt").forEach {
            val source = File(sources, it).readText()
            assertTrue(it, source.contains("IntegratedStableSheet("))
            assertFalse(it, source.contains("ModalBottomSheet("))
            assertFalse("$it must not apply duplicate IME padding", source.contains(".imePadding()"))
        }
        val shared = File(sources, "IntegratedStableSheet.kt").readText()
        assertTrue(shared.contains("sheetGesturesEnabled = false"))
        assertTrue(shared.contains("skipPartiallyExpanded = true"))
        assertTrue(shared.contains("dragHandle = null"))
        assertTrue(shared.contains("shouldDismissOnBackPress = canDismiss"))
        assertTrue(shared.contains("if (canDismiss) onDismiss()"))
        assertTrue(shared.contains("it != SheetValue.Hidden || canDismiss"))
    }

    @Test fun productScreensDoNotContainSetupCardsOrPermanentDiagnostics() {
        val source = File(sources, "IntegratedScheduleSurface.kt").readText()
        listOf("Alarm access required", "Saving a schedule alone", "Review setup first",
            "Enable Accessibility in setup.", "ScheduleSetupCards", "Last attempt:", "Setup required")
            .forEach { assertFalse(source.contains(it)) }
        val zap = File(sources, "IntegratedZapSurface.kt").readText()
        listOf("R.string.zap_recovery_title", "R.string.zap_recovery_subtitle",
            "R.string.zap_recovery_needed", "R.string.zap_pair_needed", "R.string.zap_session_help")
            .forEach { assertFalse(zap.contains(it)) }
        val activity = File(sources, "MainActivity.kt").readText()
        assertTrue(activity.indexOf("IntegratedProductEntryRules.needsRecovery(") < activity.indexOf("IntegratedZapSurface("))
        assertTrue(activity.indexOf("IntegratedProductEntryRules.needsBlocking(") < activity.indexOf("IntegratedScheduleSurface("))
    }

    @Test fun guidedSetupRetainsRestorationRefusalAndRecoveryRoutes() {
        val source = File(sources, "MainActivity.kt").readText()
        assertTrue(source.contains("outState.putBoolean(SCHEDULE_SETUP_ROUTE_KEY, showingScheduleSetup)"))
        assertTrue(source.contains("outState.putBoolean(SCHEDULE_SETUP_PENDING_KEY, scheduleSetupPending)"))
        assertTrue(source.contains("if (blockingSetupIsRequired) finish() else finishPermissionSetup()"))
        assertTrue(source.contains("if (showingAccessibilitySetup || showingScheduleSetup) advancePermissionSetup()"))
        assertTrue(source.contains("stored.desiredActive || stored.recoveryRequired || !firstRunFacts(stored).zapPaired"))
        assertTrue(source.contains("startActivity(ScheduleAlarmScheduler(this).permissionIntent())"))
        assertFalse(source.contains("Settings.Secure.put"))
    }
}
