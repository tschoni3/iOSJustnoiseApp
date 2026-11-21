package com.justnoise.app.auth

import com.justnoise.gate0.data.*
import com.justnoise.gate0.domain.ScheduleTiming
import com.justnoise.gate0.feature.ValidatedSessionWriter
import com.justnoise.integrated.IntegratedAccountAccess
import com.justnoise.integrated.IntegratedFirstRunRoute
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Real coordinator/store commits with the SAME required-account port as production. */
class RequiredAccountAccessTest {
    private val raw = MemoryPreferences()
    private val history = AccountHistories()
    private val access = SessionStartAccess(accountRequired = true)
    private val products = Gate0Preferences(raw, history, access) { 10_000L }
    private val authStore = AccountTestAuthStore()
    private val journal = AuthSessionJournal(authStore)
    private var telemetryOk = true
    private val accounts = AccountCoordinator(journal, products, AccountTelemetryBoundary { telemetryOk })

    @Before fun prepare() {
        assertTrue(products.clearForWithdrawal())
        assertTrue(products.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000))
        saveMode()
    }
    @After fun cleanup() { assertTrue(products.clearForWithdrawal()) }

    @Test fun signedOutAndColdStartsAreBlockedWithoutLosingLocalData() {
        val local = products.loadModes().modes
        assertFalse(products.startSession(100_000))
        assertTrue(products.awaitingAccountStartRestoration())
        assertEquals(AccountOutcome.READY, accounts.restore().outcome)
        assertFalse(products.awaitingAccountStartRestoration())
        assertFalse(products.accountAllowsSessionStart())
        assertFalse(products.startSession(100_000))
        assertEquals(local, products.loadModes().modes)
        assertFalse(products.snapshot().recoveryRequired)
    }

    @Test fun signedOutScheduleIsConsumedTruthfullyWithoutStartingOrCatchingUp() {
        accounts.restore()
        val occurrence = schedule()
        var capabilityConsulted = false
        val result = products.dispatchSchedule(occurrence, 100_000) { _, _ -> capabilityConsulted = true; null }
        assertEquals(ScheduleOutcomeCode.ACCOUNT_REQUIRED, result.outcome!!.code)
        assertFalse(capabilityConsulted)
        assertFalse(products.snapshot().desiredActive)
        assertNull(products.dispatchSchedule(occurrence, 100_001) { _, _ -> null }.outcome)
    }

    @Test fun expiredSignedInCredentialsRestoreOfflineAndAllowManualAndScheduledStarts() {
        signIn(); saveMode()
        val restarted = Gate0Preferences(raw, history, SessionStartAccess(accountRequired = true)) { 10_000L }
        assertFalse(restarted.startSession(100_000))
        val coordinator = AccountCoordinator(AuthSessionJournal(authStore), restarted, AccountTelemetryBoundary { false })
        assertEquals(AccountOutcome.READY, coordinator.restore().outcome)
        assertTrue(restarted.startSession(100_000))
        assertTrue(restarted.stopSession(104_000))
        val occurrence = schedule()
        assertTrue(restarted.dispatchSchedule(occurrence, 100_000) { _, _ -> null }.started)
        assertTrue(restarted.stopSession(104_000))
    }

    @Test fun coldScheduleWaitsForLocalAuthRestorationInsteadOfConsumingTheOccurrence() {
        signIn(); saveMode()
        val occurrence = schedule()
        val cold = Gate0Preferences(raw, history, SessionStartAccess(accountRequired = true)) { 10_000L }
        assertNull(cold.dispatchSchedule(occurrence, 100_000) { _, _ -> null }.outcome)
        assertEquals(occurrence, cold.loadScheduleExecutions().entries.single().pending)
        assertEquals(AccountOutcome.READY,
            AccountCoordinator(journal, cold, AccountTelemetryBoundary { true }).restore().outcome)
        assertTrue(cold.dispatchSchedule(occurrence, 100_001) { _, _ -> null }.started)
        assertTrue(cold.stopSession(104_000))
    }

    @Test fun signOutClosesStartsAndPreservesUnownedMode() {
        val local = products.loadModes().modes
        signIn(); saveMode()
        assertTrue(products.accountAllowsSessionStart())
        assertEquals(AccountOutcome.READY, accounts.signOut(journal.snapshot().operationToken).outcome)
        assertFalse(products.accountAllowsSessionStart())
        assertFalse(products.startSession(100_000))
        assertEquals(local, products.loadModes().modes)
    }

    @Test fun deletionIntentBlocksNewStartsBeforeRemoteWork() {
        signIn(); saveMode()
        assertEquals(AccountOutcome.DELETION_PENDING, accounts.beginDeletion(journal.snapshot().operationToken).outcome)
        assertFalse(products.startSession(100_000))
        assertFalse(products.accountAllowsSessionStart())
    }

    @Test fun unreadableAuthOnRestoreCannotPreventExistingStop() {
        signIn(); saveMode(); assertTrue(products.startSession(100_000))
        val broken = AuthSessionJournal(object : AuthRecordStore {
            override fun read(): AuthRecord? = throw AuthStorageException()
            override fun write(record: AuthRecord) { throw AuthStorageException() }
        })
        assertEquals(AccountOutcome.STORAGE_ERROR,
            AccountCoordinator(broken, products, AccountTelemetryBoundary { true }).restore().outcome)
        assertFalse(products.accountAllowsSessionStart())
        assertTrue(products.snapshot().desiredActive)
        assertTrue(products.stopSession(104_000))
    }

    @Test fun upgradingActiveGuestSessionRetainsStopButDoesNotAllowAnotherStart() {
        val old = Gate0Preferences(raw, history) { 10_000L }
        assertTrue(old.startSession(100_000))
        assertFalse(products.awaitingAccountStartRestoration())
        assertEquals(AccountOutcome.READY, accounts.restore().outcome)
        assertTrue(products.stopSession(104_000))
        assertFalse(products.startSession(105_000))
    }

    @Test fun authRevocationBetweenNfcPreflightAndCommitIsNotStorageRecovery() {
        signIn(); saveMode()
        val before = products.snapshot()
        val writer = ValidatedSessionWriter(products, before, products.readSelectionEdit().token, { true }, {})
        products.closeAccountSessionStarts()
        assertFalse(writer.startSession(100_000))
        writer.markRecoveryRequired()
        assertFalse(writer.persistenceFailed)
        assertFalse(products.snapshot().recoveryRequired)
    }

    @Test fun startCannotRaceThroughAuthJournalSignOutCommit() {
        signIn(); saveMode()
        val reachedWrite = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        var result: AccountResult? = null
        authStore.afterWrite = { reachedWrite.countDown(); check(finishWrite.await(3, TimeUnit.SECONDS)) }
        val worker = thread { result = accounts.signOut(journal.snapshot().operationToken) }
        try {
            assertTrue(reachedWrite.await(3, TimeUnit.SECONDS))
            assertFalse(products.accountAllowsSessionStart())
            assertFalse(products.startSession(100_000))
        } finally { finishWrite.countDown(); worker.join(3_000); authStore.afterWrite = {} }
        assertFalse(worker.isAlive)
        assertEquals(AccountOutcome.READY, result!!.outcome)
    }

    @Test fun uiRequiresBothCompletedRestorationAndSignedInAccount() {
        assertFalse(IntegratedAccountAccess().productAllowed)
        assertFalse(IntegratedAccountAccess(ready = true).productAllowed)
        assertFalse(IntegratedAccountAccess(signedIn = true).productAllowed)
        assertTrue(IntegratedAccountAccess(ready = true, signedIn = true).productAllowed)
    }

    @Test fun introductionAndPairingPrecedeLoginButCannotOpenSignedOutProduct() {
        val signedOut = IntegratedAccountAccess(ready = true)
        assertFalse(signedOut.blocksRoute(IntegratedFirstRunRoute.ONBOARDING, -1))
        assertFalse(signedOut.blocksRoute(IntegratedFirstRunRoute.FIRST_MODE, -1))
        assertFalse(signedOut.blocksRoute(IntegratedFirstRunRoute.ACTIVATION, -1))
        assertTrue(signedOut.blocksRoute(IntegratedFirstRunRoute.MAIN, 0))
        val unrestored = IntegratedAccountAccess()
        assertFalse(unrestored.blocksRoute(IntegratedFirstRunRoute.ONBOARDING, 0))
        assertFalse(unrestored.blocksRoute(IntegratedFirstRunRoute.FIRST_MODE, 0))
        assertFalse(unrestored.blocksRoute(IntegratedFirstRunRoute.ACTIVATION, 0))
        assertTrue(unrestored.blocksRoute(IntegratedFirstRunRoute.MAIN, 0))
        val signedIn = IntegratedAccountAccess(ready = true, generation = 2, signedIn = true)
        assertTrue(signedIn.blocksRoute(IntegratedFirstRunRoute.MAIN, 1))
        assertFalse(signedIn.blocksRoute(IntegratedFirstRunRoute.MAIN, 2))
    }

    private fun signIn() {
        assertEquals(AccountOutcome.READY, accounts.acceptSignIn(journal.snapshot().operationToken,
            StoredAuthSession(A, "synthetic@example.invalid", "synthetic-access", "synthetic-refresh", 0),
            LocalDataChoice.KEEP_SEPARATE).outcome)
    }
    private fun saveMode() {
        assertEquals(ModeMutationResult.SAVED,
            products.saveMode(products.loadModes().token!!, null, "Synthetic", setOf("synthetic.target")))
        val id = products.loadModes().modes.single().id
        assertEquals(ModeMutationResult.SAVED, products.selectMode(products.loadModes().token!!, id))
    }
    private fun schedule(): ScheduledOccurrence {
        val mode = products.loadModes().recentMode!!
        assertTrue(products.saveSchedule(ScheduleDraft("Synthetic", true,
            ScheduleTiming(oneTimeEpochMilliseconds = 100_000), "UTC", mode.id), nowEpochMilliseconds = 1_000))
        return products.reconcileSchedulePlan(1_000, "UTC").entries.single().pending!!
    }
    companion object { private const val A = "11111111-1111-4111-8111-111111111111" }
}
