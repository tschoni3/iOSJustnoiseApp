package com.justnoise.app.auth

import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.LocalDataImportStatus
import com.justnoise.gate0.data.LocalWorkspaceId
import com.justnoise.gate0.data.MemoryPreferences
import com.justnoise.gate0.data.ModeMutationResult
import com.justnoise.gate0.data.SessionStartAccess
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The first mode saved during onboarding must still be selected after Zap pairing,
 * Accessibility disclosure, and required sign-in — the same persistence iOS gives
 * via addMode + selectedMode, without a workspace switch leaving the mode behind.
 */
class FirstRunModePersistenceTest {
    private val raw = MemoryPreferences()
    private val history = AccountHistories()
    private val access = SessionStartAccess(accountRequired = true)
    private val products = Gate0Preferences(raw, history, access) { 10_000L }
    private val journal = AuthSessionJournal(AccountTestAuthStore())
    private val accounts = AccountCoordinator(journal, products, AccountTelemetryBoundary { true })

    @Before fun prepare() {
        assertTrue(products.clearForWithdrawal())
    }

    @After fun clean() {
        assertTrue(products.clearForWithdrawal())
    }

    @Test fun firstRunModeStaysSelectedThroughPairingDisclosureAndSignIn() {
        assertTrue(products.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertEquals(
            ModeMutationResult.SAVED,
            products.saveMode(products.loadModes().token!!, null, "Focus", setOf("synthetic.focus"), selectNewMode = true),
        )
        val created = products.loadModes().recentMode
        assertNotNull(created)
        assertEquals("Focus", created!!.name)
        assertEquals(setOf("synthetic.focus"), created.selectedPackages)
        assertTrue(products.completeOnboarding())
        assertTrue(products.setZapDigest("synthetic-pairing"))

        assertEquals(
            AccountOutcome.READY,
            accounts.acceptSignIn(
                journal.snapshot().operationToken,
                StoredAuthSession(ACCOUNT, "synthetic@example.invalid", "synthetic-access", "synthetic-refresh", 10),
                LocalDataChoice.KEEP_SEPARATE,
            ).outcome,
        )

        val selected = products.loadModes().recentMode
        assertNotNull(selected)
        assertEquals("Focus", selected!!.name)
        assertEquals(setOf("synthetic.focus"), selected.selectedPackages)
        assertEquals("Focus", products.snapshot().recentModeName)
        assertEquals("synthetic-pairing", products.snapshot().zapDigest)
        assertEquals(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, products.snapshot().acceptedDisclosureVersion)
        assertEquals(LocalDataImportStatus.COMPLETE, accounts.localImportOffer().status)
        assertEquals(LocalWorkspaceId.parse(ACCOUNT), products.currentWorkspace())

        val cold = AccountCoordinator(
            journal,
            Gate0Preferences(raw, history, SessionStartAccess(accountRequired = true)) { 10_000L },
            AccountTelemetryBoundary { true },
        )
        assertEquals(AccountOutcome.READY, cold.restore().outcome)
        val restored = Gate0Preferences(raw, history, SessionStartAccess(accountRequired = true)) { 10_000L }
        assertEquals("Focus", restored.loadModes().recentMode?.name)
        assertEquals(setOf("synthetic.focus"), restored.loadModes().recentMode?.selectedPackages)
    }

    @Test fun restoreAdoptsOnboardingModeLeftBehindInLocalWorkspace() {
        assertTrue(products.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertEquals(
            ModeMutationResult.SAVED,
            products.saveMode(products.loadModes().token!!, null, "Focus", setOf("synthetic.focus"), selectNewMode = true),
        )
        assertEquals(
            AccountOutcome.READY,
            accounts.acceptSignIn(
                journal.snapshot().operationToken,
                StoredAuthSession(ACCOUNT, "synthetic@example.invalid", "synthetic-access", "synthetic-refresh", 10),
                LocalDataChoice.KEEP_SEPARATE,
            ).outcome,
        )
        assertTrue(products.loadModes().modes.isEmpty())
        assertTrue(products.completeOnboarding())

        val cold = AccountCoordinator(
            journal,
            Gate0Preferences(raw, history, SessionStartAccess(accountRequired = true)) { 10_000L },
            AccountTelemetryBoundary { true },
        )
        assertEquals(AccountOutcome.READY, cold.restore().outcome)
        val adopted = Gate0Preferences(raw, history, SessionStartAccess(accountRequired = true)) { 10_000L }
        assertEquals("Focus", adopted.loadModes().recentMode?.name)
        assertEquals(setOf("synthetic.focus"), adopted.loadModes().recentMode?.selectedPackages)
        assertEquals(LocalDataImportStatus.COMPLETE, cold.localImportOffer().status)
    }

    companion object {
        private const val ACCOUNT = "11111111-1111-1111-1111-111111111111"
    }
}
