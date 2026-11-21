package com.justnoise.integrated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySetupNavigationTest {
    @Test fun `direct route is attempted first and success needs no extra navigation`() {
        val visited = mutableListOf<AccessibilitySetupNavigation.Route>()
        assertTrue(AccessibilitySetupNavigation.launchFirstSupported { visited += it; true })
        assertEquals(listOf(AccessibilitySetupNavigation.Route.SERVICE_DETAILS), visited)
    }

    @Test fun `unsupported shortcuts fall back once to ordinary accessibility settings`() {
        val visited = mutableListOf<AccessibilitySetupNavigation.Route>()
        assertTrue(AccessibilitySetupNavigation.launchFirstSupported {
            visited += it
            it == AccessibilitySetupNavigation.Route.ACCESSIBILITY_LIST
        })
        assertEquals(AccessibilitySetupNavigation.Route.entries, visited)
    }

    @Test fun `no supported settings route reports failure without looping`() {
        var attempts = 0
        assertFalse(AccessibilitySetupNavigation.launchFirstSupported { attempts++; false })
        assertEquals(3, attempts)
    }

    @Test fun `Samsung installed services shortcut stops before general settings when supported`() {
        val visited = mutableListOf<AccessibilitySetupNavigation.Route>()
        assertTrue(AccessibilitySetupNavigation.launchFirstSupported {
            visited += it
            it == AccessibilitySetupNavigation.Route.INSTALLED_SERVICES
        })
        assertEquals(listOf(AccessibilitySetupNavigation.Route.SERVICE_DETAILS,
            AccessibilitySetupNavigation.Route.INSTALLED_SERVICES), visited)
    }

    @Test fun `routes use settings actions not hidden fragment or permission mutation APIs`() {
        assertEquals("android.settings.ACCESSIBILITY_DETAILS_SETTINGS",
            AccessibilitySetupNavigation.action(AccessibilitySetupNavigation.Route.SERVICE_DETAILS))
        assertEquals("android.settings.ACCESSIBILITY_SETTINGS",
            AccessibilitySetupNavigation.action(AccessibilitySetupNavigation.Route.ACCESSIBILITY_LIST))
        assertEquals("com.samsung.accessibility.installed_service",
            AccessibilitySetupNavigation.action(AccessibilitySetupNavigation.Route.INSTALLED_SERVICES))
        assertEquals("android.intent.extra.COMPONENT_NAME", AccessibilitySetupNavigation.COMPONENT_EXTRA)
    }
}
