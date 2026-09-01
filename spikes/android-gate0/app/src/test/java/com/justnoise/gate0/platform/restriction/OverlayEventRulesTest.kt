package com.justnoise.gate0.platform.restriction

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayEventRulesTest {
    @Test
    fun `selected eligible app shows overlay`() {
        assertEquals(
            OverlayEventDecision.SHOW,
            decide(foregroundPackage = TARGET_PACKAGE),
        )
    }

    @Test
    fun `Justnoise owned overlay event cannot hide visible overlay`() {
        assertEquals(
            OverlayEventDecision.IGNORE,
            decide(
                foregroundPackage = JUSTNOISE_PACKAGE,
                overlayIsShowing = true,
            ),
        )
    }

    @Test
    fun `definitive other app transition hides overlay`() {
        assertEquals(
            OverlayEventDecision.HIDE,
            decide(foregroundPackage = "fixture.other"),
        )
    }

    @Test
    fun `unexpected event type or missing package cannot cause flicker`() {
        assertEquals(
            OverlayEventDecision.IGNORE,
            decide(foregroundPackage = TARGET_PACKAGE, isWindowStateChange = false),
        )
        assertEquals(
            OverlayEventDecision.IGNORE,
            decide(foregroundPackage = null),
        )
    }

    @Test
    fun `ineligible enforcement never shows`() {
        assertEquals(
            OverlayEventDecision.HIDE,
            decide(foregroundPackage = TARGET_PACKAGE, enforcementEligible = false),
        )
    }

    private fun decide(
        foregroundPackage: String?,
        isWindowStateChange: Boolean = true,
        enforcementEligible: Boolean = true,
        overlayIsShowing: Boolean = false,
    ): OverlayEventDecision = OverlayEventRules.decide(
        isWindowStateChange = isWindowStateChange,
        foregroundPackage = foregroundPackage,
        justnoisePackage = JUSTNOISE_PACKAGE,
        selectedPackage = TARGET_PACKAGE,
        enforcementEligible = enforcementEligible,
        overlayIsShowing = overlayIsShowing,
    )

    companion object {
        private const val JUSTNOISE_PACKAGE = "fixture.justnoise"
        private const val TARGET_PACKAGE = "fixture.target"
    }
}
