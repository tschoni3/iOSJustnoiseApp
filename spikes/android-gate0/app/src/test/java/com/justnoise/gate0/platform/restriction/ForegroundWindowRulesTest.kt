package com.justnoise.gate0.platform.restriction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundWindowRulesTest {
    @Test fun `scheduled start keeps selected app beneath keyboard show and hide`() {
        var foreground: String? = TARGET
        repeat(2) {
            foreground = observe(foreground)
            assertEquals(TARGET, foreground)
        }
        assertEquals(OverlayEventDecision.SHOW, overlay(foreground, showing = false))
    }

    @Test fun `keyboard event cannot dismiss an existing selected app blocker`() {
        assertEquals(OverlayEventDecision.SHOW, overlay(observe(TARGET), showing = true))
    }

    @Test fun `Home Settings emergency and unselected app transitions remain free with keyboards`() {
        for (destination in listOf("fixture.home", "com.android.settings", "fixture.dialer",
            "com.android.systemui", "fixture.other", "fixture.justnoise")) {
            val next = observe(TARGET, destination, "fixture.Activity", protected = true)
            assertEquals(destination, next)
            assertEquals(destination, observe(next))
            assertEquals(OverlayEventDecision.HIDE, overlay(observe(next), showing = true))
        }
    }

    @Test fun `keyboard settings activity and other classes are not swallowed`() {
        for (className in listOf(null, "fixture.KeyboardSettings", "android.app.Dialog",
            ForegroundWindowRules.SOFT_INPUT_WINDOW + "Other")) {
            assertEquals(IME, observe(TARGET, className = className))
        }
    }

    @Test fun `unknown cold foreground is not invented by keyboard event`() {
        assertNull(observe(null))
    }

    @Test fun `unknown changed or spoofed input method identity does not reuse target`() {
        for (ime in listOf(null, "", "fixture.different")) {
            assertEquals(IME, observe(TARGET, currentIme = ime))
        }
        assertEquals("fixture.other", observe(TARGET, eventPackage = "fixture.other"))
    }

    @Test fun `protected event wins even with matching keyboard class and package`() {
        assertEquals(IME, observe(TARGET, protected = true))
    }

    @Test fun `missing package invalidates context rather than retaining a stale target`() {
        assertNull(observe(TARGET, eventPackage = null))
    }

    @Test fun `stopped or recovery session cannot regain blocker through keyboard`() {
        assertEquals(OverlayEventDecision.HIDE,
            overlay(observe(TARGET), showing = true, eligible = false))
    }

    private fun observe(previous: String?, eventPackage: String? = IME,
        className: String? = ForegroundWindowRules.SOFT_INPUT_WINDOW,
        currentIme: String? = IME, protected: Boolean = false) =
        ForegroundWindowRules.observedPackage(previous, eventPackage, className, currentIme, protected)

    private fun overlay(foreground: String?, showing: Boolean, eligible: Boolean = true) =
        OverlayEventRules.decide(true, foreground, "fixture.overlay", setOf(TARGET), eligible, showing)

    companion object {
        private const val TARGET = "fixture.browser"
        private const val IME = "fixture.keyboard"
    }
}
