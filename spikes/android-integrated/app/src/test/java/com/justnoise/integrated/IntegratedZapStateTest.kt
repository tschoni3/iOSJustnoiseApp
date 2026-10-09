package com.justnoise.integrated

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.justnoise.androidcontract.JustnoiseContract
import com.justnoise.gate0.feature.Gate0Truth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedZapStateTest {
    @Test
    fun activeTimerUsesOpaqueReadableGrayWithoutChangingSharedIosToken() {
        val color = integratedZapSubtitleColor(IntegratedZapState(truth = Gate0Truth.ACTIVE))
        assertEquals(Color(0xFF595959), color)
        assertEquals(1f, color.alpha, 0f)
        assertTrue((Color.White.luminance() + .05f) / (color.luminance() + .05f) >= 7f)
        assertEquals(Color(0f, 0f, 0f, 41f / 255f), JustnoiseContract.color("text.zap.active.secondary"))
    }

    @Test
    fun recoveryWarningIsReadableAndNeverUsesActiveTimerStyling() {
        val state = IntegratedZapState(truth = Gate0Truth.RECOVERY_REQUIRED, desiredActive = true)
        assertFalse(state.isActive)
        assertEquals(Color.White, integratedZapSubtitleColor(state))
    }

    @Test
    fun idleDecorativeSubtitleKeepsExistingStyling() {
        val expected = JustnoiseContract.color("text.zap.idle.secondary")
        Gate0Truth.entries.filter { it != Gate0Truth.ACTIVE }.forEach { truth ->
            assertEquals(expected, integratedZapSubtitleColor(IntegratedZapState(truth = truth)))
        }
    }

    @Test
    fun onlyReadyAndConfirmedActiveHideSetupGuidance() {
        Gate0Truth.entries.forEach { truth ->
            assertEquals(truth != Gate0Truth.IDLE && truth != Gate0Truth.ACTIVE,
                IntegratedZapState(truth = truth).needsAttention)
        }
    }

    @Test
    fun standardGeometryKeepsSourceGapsAndClampsFlexibleSpace() {
        assertEquals(IntegratedZapGeometry(174f, 440f), integratedZapGeometry(600f))
        assertEquals(IntegratedZapGeometry(184f, 450f), integratedZapGeometry(724f))
        assertEquals(IntegratedZapGeometry(214f, 480f), integratedZapGeometry(900f))
    }

    @Test
    fun readyAndActiveTruthExposeTheMatchingScanAction() {
        val idle = IntegratedZapState(truth = Gate0Truth.IDLE)
        assertEquals(IntegratedZapAction.START_SCAN, idle.primaryAction)
        assertEquals("Scan Zap to start", idle.actionLabel)
        assertTrue(idle.canScan)
        assertFalse(idle.isActive)

        val active = IntegratedZapState(truth = Gate0Truth.ACTIVE, desiredActive = true)
        assertEquals(IntegratedZapAction.STOP_SCAN, active.primaryAction)
        assertEquals("Scan Zap to stop", active.actionLabel)
        assertTrue(active.canScan)
        assertTrue(active.isActive)
    }

    @Test
    fun recoveryMayStopPersistedSessionButCannotClaimProtectionOrStart() {
        val recovery = IntegratedZapState(truth = Gate0Truth.RECOVERY_REQUIRED)
        assertEquals(IntegratedZapAction.OPEN_SETUP, recovery.primaryAction)
        assertEquals("Open setup", recovery.actionLabel)
        assertFalse(recovery.canScan)
        assertFalse(recovery.isActive)

        val recoverableStop = recovery.copy(desiredActive = true)
        assertEquals(IntegratedZapAction.STOP_SCAN, recoverableStop.primaryAction)
        assertEquals("Scan Zap to stop", recoverableStop.actionLabel)
        assertTrue(recoverableStop.canScan)
        assertFalse(recoverableStop.isActive)
    }

    @Test
    fun missingSetupNeverOffersAScanEvenIfAnOldDesiredFlagRemains() {
        val missingSetup = listOf(
            Gate0Truth.CONSENT_REQUIRED,
            Gate0Truth.TARGET_REQUIRED,
            Gate0Truth.ZAP_REQUIRED,
            Gate0Truth.PERMISSION_REQUIRED,
        )
        missingSetup.forEach { truth ->
            listOf(false, true).forEach { desiredActive ->
                val state = IntegratedZapState(truth = truth, desiredActive = desiredActive)
                assertEquals(IntegratedZapAction.OPEN_SETUP, state.primaryAction)
                assertEquals("Open setup", state.actionLabel)
                assertFalse(state.canScan)
                assertFalse(state.isActive)
            }
        }
    }

    @Test
    fun scannerPromptDoesNotChangeTheUnderlyingActionOrProtectionTruth() {
        Gate0Truth.entries.forEach { truth ->
            val state = IntegratedZapState(truth = truth, desiredActive = true)
            val scanning = state.copy(isScanning = true)
            assertEquals("Hold Zap near phone", scanning.actionLabel)
            assertEquals(state.primaryAction, scanning.primaryAction)
            assertEquals(state.canScan, scanning.canScan)
            assertEquals(state.isActive, scanning.isActive)
        }
    }
}
