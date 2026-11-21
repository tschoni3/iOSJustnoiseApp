package com.justnoise.integrated

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Wiring regressions only; real gesture/rendering checks remain separately identified. */
class IntegratedSessionShortcutPresentationTest {
    private val sources = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
        .map { File(it, "spikes/android-integrated/app/src/main/java/com/justnoise/integrated") }
        .first { it.isDirectory }

    @Test fun longPressAndEmergencyUseRealGuardedControllerActions() {
        val host = File(sources, "MainActivity.kt").readText()
        assertTrue(host.contains("onLongPressZap = ::handleLongPressZap"))
        assertTrue(host.contains("controller.startWithoutScan()"))
        assertTrue(host.contains("onEmergencyUnzap = ::handleEmergencyUnzap"))
        assertTrue(host.contains("controller.emergencyUnzap(sessionId)"))
        assertTrue(host.contains("onEmergencySettings = ::openSettings"))
        val zap = File(sources, "IntegratedZapSurface.kt").readText()
        assertTrue(zap.contains(".combinedClickable("))
        assertTrue(zap.contains("if (!state.desiredActive && !state.isActive) onLongPressZap()"))
        val settings = File(sources, "IntegratedSettingsSurface.kt").readText()
        assertTrue(settings.contains("confirmedSession == emergencySessionId"))
        assertTrue(settings.contains("onEmergencyUnzap(confirmedSession)"))
        assertFalse(settings.contains("disableSelf("))
    }
}
