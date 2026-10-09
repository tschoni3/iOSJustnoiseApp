package com.justnoise.app.auth

import com.justnoise.gate0.data.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test

class ProcessBoundAnalyticsConsentTest {
    @Test fun newInstallationAndLegacyUnscopedConsentAreOff() {
        val preferences = MemoryPreferences()
        val store = ProcessBoundAnalyticsConsent(preferences, PROCESS_A)
        assertFalse(store.granted())
        preferences.edit().putBoolean("granted", true).commit()
        assertFalse(store.granted())
        assertTrue(store.write(true))
        assertFalse(preferences.contains("granted"))
        assertTrue(store.granted())
    }

    @Test fun sameProcessCanRestoreButNewProcessCannot() {
        val preferences = MemoryPreferences()
        assertTrue(ProcessBoundAnalyticsConsent(preferences, PROCESS_A).write(true))
        assertTrue(ProcessBoundAnalyticsConsent(preferences, PROCESS_A).granted())
        assertFalse(ProcessBoundAnalyticsConsent(preferences, PROCESS_B).granted())
    }

    @Test fun newProcessDoesNotNeedAWritableDiskToRejectOldConsent() {
        val preferences = MemoryPreferences()
        assertTrue(ProcessBoundAnalyticsConsent(preferences, PROCESS_A).write(true))
        preferences.failNextCommit = true
        val store = ProcessBoundAnalyticsConsent(preferences, PROCESS_B)
        assertFalse(store.granted())
        assertTrue(preferences.failNextCommit) // Restore did not depend on a write.
    }

    @Test fun explicitNewOptInAndRevocationUseCurrentEpoch() {
        val preferences = MemoryPreferences()
        val old = ProcessBoundAnalyticsConsent(preferences, PROCESS_A)
        val current = ProcessBoundAnalyticsConsent(preferences, PROCESS_B)
        assertTrue(old.write(true))
        assertTrue(current.write(true))
        assertFalse(old.granted())
        assertTrue(current.granted())
        assertTrue(current.write(false))
        assertFalse(current.granted())
        assertNull(preferences.getString("process_grant_v2", null))
    }

    @Test fun failedOptInDoesNotEnableRuntimeEvenWhenPreferencesMemoryChanges() {
        val preferences = MemoryPreferences().apply { failNextCommit = true }
        val store = ProcessBoundAnalyticsConsent(preferences, PROCESS_A)
        val runtime = AccountAnalytics(store) { _, _ -> error("No capture expected") }
        try {
            assertFalse(runtime.optIn())
            assertFalse(runtime.isEnabled())
            assertTrue(runtime.restoreConsent())
            assertFalse(runtime.isEnabled())
        } finally { runtime.closeAndPurge() }
    }

    companion object {
        private const val PROCESS_A = "11111111-1111-4111-8111-111111111111"
        private const val PROCESS_B = "22222222-2222-4222-8222-222222222222"
    }
}
