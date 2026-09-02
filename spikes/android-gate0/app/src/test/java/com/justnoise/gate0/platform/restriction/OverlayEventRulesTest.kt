package com.justnoise.gate0.platform.restriction

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayEventRulesTest {
    @Test
    fun `every selected eligible app shows overlay including target to target transitions`() {
        for (target in TARGET_PACKAGES) {
            for (overlayIsShowing in listOf(false, true)) {
                assertEquals(
                    "$target, overlayIsShowing=$overlayIsShowing",
                    OverlayEventDecision.SHOW,
                    decide(foregroundPackage = target, overlayIsShowing = overlayIsShowing),
                )
            }
        }
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
        assertEquals(
            OverlayEventDecision.HIDE,
            decide(foregroundPackage = "$TARGET_PACKAGE.other"),
        )
    }

    @Test
    fun `empty selection cannot show an overlay`() {
        assertEquals(
            OverlayEventDecision.HIDE,
            decide(foregroundPackage = TARGET_PACKAGE, selectedPackages = emptySet()),
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
        assertEquals(
            OverlayEventDecision.IGNORE,
            decide(foregroundPackage = " "),
        )
    }

    @Test
    fun `ineligible enforcement never shows`() {
        for (target in TARGET_PACKAGES) {
            assertEquals(
                OverlayEventDecision.HIDE,
                decide(foregroundPackage = target, enforcementEligible = false),
            )
        }
    }

    private fun decide(
        foregroundPackage: String?,
        isWindowStateChange: Boolean = true,
        enforcementEligible: Boolean = true,
        overlayIsShowing: Boolean = false,
        selectedPackages: Set<String> = TARGET_PACKAGES,
    ): OverlayEventDecision = OverlayEventRules.decide(
        isWindowStateChange = isWindowStateChange,
        foregroundPackage = foregroundPackage,
        justnoisePackage = JUSTNOISE_PACKAGE,
        selectedPackages = selectedPackages,
        enforcementEligible = enforcementEligible,
        overlayIsShowing = overlayIsShowing,
    )

    companion object {
        private const val JUSTNOISE_PACKAGE = "fixture.justnoise"
        private const val TARGET_PACKAGE = "fixture.target"
        private val TARGET_PACKAGES = setOf(TARGET_PACKAGE, "fixture.second.target")
    }
}
