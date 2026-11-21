package com.justnoise.gate0.platform.restriction

import org.junit.Assert.*
import org.junit.Test

class AccessibilityReturnGateTest {
    @Test fun explicitRecentRequestReturnsOnceFromSettings() {
        val gate = AccessibilityReturnGate()
        assertFalse(gate.take(100L, "com.android.settings", true))
        gate.arm(100L)
        assertTrue(gate.take(101L, "com.android.settings", true))
        assertFalse(gate.take(102L, "com.android.settings", true))
        gate.arm(200L)
        assertTrue(gate.take(201L, "com.samsung.accessibility", true))
    }

    @Test fun denialExpiryOtherAppsAndClockResetNeverLaunchLater() {
        listOf(Triple(101L, "com.android.settings", false),
            Triple(120_101L, "com.android.settings", true),
            Triple(99L, "com.android.settings", true),
            Triple(101L, "example.other", true), Triple(101L, null, true)).forEach { (now, app, eligible) ->
            val gate = AccessibilityReturnGate()
            gate.arm(100L)
            assertFalse(gate.take(now, app, eligible))
            assertFalse(gate.isPending())
            assertFalse(gate.take(102L, "com.android.settings", true))
        }
    }

    @Test fun cancellationAndProcessRecreationDiscardTheRequest() {
        val gate = AccessibilityReturnGate()
        gate.arm(100L)
        assertTrue(gate.isPending())
        gate.cancel()
        assertFalse(gate.take(101L, "com.android.settings", true))
        assertFalse(AccessibilityReturnGate().take(101L, "com.android.settings", true))
    }
}
