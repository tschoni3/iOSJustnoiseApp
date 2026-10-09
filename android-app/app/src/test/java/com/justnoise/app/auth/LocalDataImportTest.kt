package com.justnoise.app.auth

import com.justnoise.gate0.data.*
import com.justnoise.gate0.domain.HistoryDateRange
import com.justnoise.gate0.domain.ScheduleTiming
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Actual coordinator/preferences with isolated, fault-injected disk/History ports. */
class LocalDataImportTest {
    private val raw = MemoryPreferences()
    private val history = ImportHistories()
    private val access = SessionStartAccess(accountRequired = true)
    private val products = Gate0Preferences(raw, history, access) { 10_000L }
    private val journal = AuthSessionJournal(AccountTestAuthStore())
    private val accounts = AccountCoordinator(journal, products, AccountTelemetryBoundary { true })
    private val entry = SessionHistoryEntry("33333333-3333-3333-3333-333333333333", 1000, 1500, 500, 2, true)
    private lateinit var localMode: StoredMode

    @Before fun prepare() {
        assertTrue(products.clearForWithdrawal())
        assertTrue(products.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1000))
        assertEquals(ModeMutationResult.SAVED, products.saveMode(products.loadModes().token!!, null, "Local Focus", setOf("synthetic.local")))
        localMode = products.loadModes().modes.single()
        assertEquals(ModeMutationResult.SAVED, products.selectMode(products.loadModes().token!!, localMode.id))
        assertTrue(products.saveSchedule(ScheduleDraft("Local schedule", true, ScheduleTiming(oneTimeEpochMilliseconds = 500_000),
            "UTC", localMode.id), null, null, 1000))
        history.forWorkspace(LocalWorkspaceId.LOCAL).insert(entry)
        assertTrue(products.setZapDigest("synthetic-pairing"))
        login(A)
    }
    @After fun clean() { history.failMerge = false; history.failClear = false; history.afterClear = {}; assertTrue(products.clearForWithdrawal()) }

    @Test fun offerAndDeferNeverMutateOrAdoptDataAndRemainAvailableAfterRestart() {
        val offer = accounts.localImportOffer()
        assertEquals(LocalDataImportStatus.AVAILABLE, offer.status)
        assertEquals(listOf(1, 1, 1), listOf(offer.modeCount, offer.scheduleCount, offer.sessionCount))
        assertTrue(offer.shouldPrompt)
        assertTrue(products.loadModes().modes.isEmpty())
        assertTrue(accounts.deferLocalImport(offer.token!!))
        assertFalse(accounts.localImportOffer().shouldPrompt)
        val cold = AccountCoordinator(journal, Gate0Preferences(raw, history, access) { 10_000L }, AccountTelemetryBoundary { true })
        assertEquals(AccountOutcome.READY, cold.restore().outcome)
        assertEquals(LocalDataImportStatus.AVAILABLE, cold.localImportOffer().status)
        assertFalse(cold.localImportOffer().shouldPrompt)
        assertEquals(listOf(entry), history.forWorkspace(LocalWorkspaceId.LOCAL).load(10).entries)
    }

    @Test fun importsModesDisabledSchedulesAndExactHistoryOnlyOnceAndRetainsPairing() {
        val before = products.snapshot()
        val consent = accounts.localImportOffer().token!!
        assertEquals(LocalDataImportResult.IMPORTED, accounts.importLocalData(consent))
        assertEquals(AccountOutcome.READY, accounts.restore().outcome)
        assertEquals(localMode, products.loadModes().recentMode)
        assertFalse(products.loadSchedules().schedules.single().enabled)
        assertEquals(localMode.id, products.loadSchedules().schedules.single().modeReference)
        assertEquals(listOf(entry), products.loadHistory().entries)
        assertEquals(before.zapDigest, products.snapshot().zapDigest)
        assertEquals(before.acceptedDisclosureVersion, products.snapshot().acceptedDisclosureVersion)
        assertEquals(LocalDataImportStatus.COMPLETE, accounts.localImportOffer().status)
        assertEquals(LocalDataImportResult.CONFLICT, accounts.importLocalData(consent))
        accounts.restore()
        assertEquals(1, products.loadHistory().totalCount)
        accounts.signOut(journal.snapshot().operationToken)
        assertTrue(products.loadModes().modes.isEmpty())
        assertTrue(history.forWorkspace(LocalWorkspaceId.LOCAL).load(1).entries.isEmpty())
        login(B)
        assertEquals(LocalDataImportStatus.UNAVAILABLE, accounts.localImportOffer().status)
        assertTrue(products.loadModes().modes.isEmpty())
        assertTrue(products.loadHistory().entries.isEmpty())
    }

    @Test fun keepsExistingAccountModeAndSelection() {
        assertEquals(ModeMutationResult.SAVED, products.saveMode(products.loadModes().token!!, null, "Account mode", setOf("synthetic.account")))
        val mode = products.loadModes().modes.single()
        products.selectMode(products.loadModes().token!!, mode.id)
        assertEquals(LocalDataImportResult.IMPORTED, accounts.importLocalData(accounts.localImportOffer().token!!))
        accounts.restore()
        assertEquals(mode, products.loadModes().recentMode)
        assertEquals(2, products.loadModes().modes.size)
    }

    @Test fun staleAccountConfirmationCannotImportIntoDifferentAccount() {
        val offer = accounts.localImportOffer()
        accounts.signOut(journal.snapshot().operationToken); login(B)
        assertEquals(LocalDataImportResult.CONFLICT, accounts.importLocalData(offer.token!!))
        assertTrue(products.loadModes().modes.isEmpty())
        assertEquals(LocalDataImportStatus.AVAILABLE, accounts.localImportOffer().status)
    }

    @Test fun activeSessionCannotBeInterruptedByImport() {
        products.saveMode(products.loadModes().token!!, null, "Account mode", setOf("synthetic.account"))
        products.selectMode(products.loadModes().token!!, products.loadModes().modes.single().id)
        assertTrue(products.startSession(100_000))
        val offer = accounts.localImportOffer()
        assertEquals(LocalDataImportResult.ACTIVE_SESSION, accounts.importLocalData(offer.token!!))
        accounts.restore()
        assertTrue(products.snapshot().desiredActive)
        assertTrue(products.stopSession(104_000))
    }

    @Test fun failedHistoryMergeKeepsSourceAndDurableBarrierAndResumesOnColdRestore() {
        history.failMerge = true
        assertEquals(LocalDataImportResult.STORAGE_FAILED, accounts.importLocalData(accounts.localImportOffer().token!!))
        assertTrue(products.workspaceTransitionPending())
        assertTrue(products.loadModes().error)
        assertFalse(products.startSession(100_000))
        assertEquals(AccountOutcome.STORAGE_ERROR, accounts.signOut(journal.snapshot().operationToken).outcome)
        assertEquals(A, journal.snapshot().accountId)
        assertEquals(listOf(entry), history.forWorkspace(LocalWorkspaceId.LOCAL).load(10).entries)
        history.failMerge = false
        val cold = Gate0Preferences(raw, history, SessionStartAccess(accountRequired = true)) { 10_000L }
        val restored = AccountCoordinator(journal, cold, AccountTelemetryBoundary { true }).restore()
        assertEquals(AccountOutcome.READY, restored.outcome)
        assertEquals(localMode, cold.loadModes().recentMode)
        assertEquals(listOf(entry), cold.loadHistory().entries)
    }

    @Test fun sourceClearFailureRetriesWithoutDuplicateHistory() {
        history.failClear = true
        assertEquals(LocalDataImportResult.STORAGE_FAILED, accounts.importLocalData(accounts.localImportOffer().token!!))
        assertEquals(1, history.forWorkspace(LocalWorkspaceId.parse(A)).load(10).totalCount)
        history.failClear = false
        assertEquals(AccountOutcome.READY, accounts.restore().outcome)
        assertEquals(listOf(entry), products.loadHistory().entries)
    }

    @Test fun failedFinalCommitRetainsPlanAndDestinationHistoryEvenAfterSourceCleared() {
        history.afterClear = { raw.failNextCommit = true; history.afterClear = {} }
        assertEquals(LocalDataImportResult.STORAGE_FAILED, accounts.importLocalData(accounts.localImportOffer().token!!))
        assertTrue(products.workspaceTransitionPending())
        assertTrue(history.forWorkspace(LocalWorkspaceId.LOCAL).load(10).entries.isEmpty())
        assertEquals(AccountOutcome.READY, accounts.restore().outcome)
        assertEquals(listOf(entry), products.loadHistory().entries)
        assertEquals(localMode, products.loadModes().recentMode)
        assertTrue(products.snapshot().recoveryRequired) // Never erase a legitimate storage warning.
    }

    @Test fun failedClaimCommitDoesNotTouchEitherHistoryDatabase() {
        val offer = accounts.localImportOffer()
        raw.failNextCommit = true
        assertEquals(LocalDataImportResult.STORAGE_FAILED, accounts.importLocalData(offer.token!!))
        assertFalse(raw.contains("local_import_owner_v1"))
        assertEquals(listOf(entry), history.forWorkspace(LocalWorkspaceId.LOCAL).load(10).entries)
        assertTrue(history.forWorkspace(LocalWorkspaceId.parse(A)).load(10).entries.isEmpty())
    }

    @Test fun conflictingHistoryIdNeverOverwritesOrErasesSource() {
        val existing = entry.copy(durationMilliseconds = 700)
        history.forWorkspace(LocalWorkspaceId.parse(A)).insert(existing)
        assertEquals(LocalDataImportResult.HISTORY_CONFLICT, accounts.importLocalData(accounts.localImportOffer().token!!))
        assertEquals(listOf(existing), history.forWorkspace(LocalWorkspaceId.parse(A)).load(10).entries)
        assertEquals(listOf(entry), history.forWorkspace(LocalWorkspaceId.LOCAL).load(10).entries)
        assertFalse(products.workspaceTransitionPending())
        assertFalse(raw.contains("local_import_owner_v1"))
        assertEquals(AccountOutcome.READY, accounts.restore().outcome)
        assertTrue(products.accountAllowsSessionStart())
    }

    @Test fun accountDeletionCannotMakeImportedDataClaimableByAnotherAccount() {
        assertEquals(LocalDataImportResult.IMPORTED, accounts.importLocalData(accounts.localImportOffer().token!!))
        accounts.restore()
        assertEquals(AccountOutcome.DELETION_PENDING, accounts.beginDeletion(journal.snapshot().operationToken).outcome)
        assertEquals(AccountOutcome.READY, accounts.confirmRemoteDeletion(journal.snapshot().operationToken).outcome)
        login(B)
        assertEquals(LocalDataImportStatus.UNAVAILABLE, accounts.localImportOffer().status)
        assertTrue(products.loadHistory().entries.isEmpty())
    }

    @Test fun emptySourceIsNotOffered() {
        accounts.signOut(journal.snapshot().operationToken)
        assertTrue(products.clearForWithdrawal())
        assertTrue(products.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1000))
        login(A)
        assertEquals(LocalDataImportStatus.UNAVAILABLE, accounts.localImportOffer().status)
    }

    private fun login(id: String) {
        assertEquals(AccountOutcome.READY, accounts.acceptSignIn(journal.snapshot().operationToken,
            StoredAuthSession(id, "synthetic@example.invalid", "synthetic-access", "synthetic-refresh", 10), LocalDataChoice.KEEP_SEPARATE).outcome)
    }
    companion object {
        private const val A = "11111111-1111-1111-1111-111111111111"
        private const val B = "22222222-2222-2222-2222-222222222222"
    }
}

private class ImportHistories : SessionHistoryStorage {
    val rows = mutableMapOf<LocalWorkspaceId, MutableList<SessionHistoryEntry>>()
    var failMerge = false
    var failClear = false
    var afterClear: () -> Unit = {}
    override fun forWorkspace(workspace: LocalWorkspaceId): SessionHistoryStorage = object : SessionHistoryStorage {
        val entries get() = rows.getOrPut(workspace) { mutableListOf() }
        override fun insert(entry: SessionHistoryEntry) {
            val same = entries.find { it.id == entry.id }
            check(same == null || same == entry)
            if (same == null) entries += entry
        }
        override fun visitEntries(visit: (SessionHistoryEntry) -> Unit) = entries.toList().forEach(visit)
        override fun canMergeFrom(source: SessionHistoryStorage): Boolean {
            var compatible = true
            source.visitEntries { row -> if (entries.any { it.id == row.id && it != row }) compatible = false }
            return compatible
        }
        override fun mergeFrom(source: SessionHistoryStorage) {
            check(!failMerge)
            val before = entries.toList()
            try { source.visitEntries(::insert) } catch (e: Exception) { entries.clear(); entries.addAll(before); throw e }
        }
        override fun load(limit: Int) = SessionHistorySnapshot(entries.take(limit), entries.size,
            entries.sumOf { it.durationMilliseconds }, entries.count { it.interrupted }, entries.size > limit)
        override fun loadTimeline(range: HistoryDateRange) = SessionHistoryTimelineSnapshot(range)
        override fun clear() { check(!failClear); entries.clear(); afterClear() }
        override fun eraseAccountData() { clear() }
    }
    override fun insert(entry: SessionHistoryEntry) = forWorkspace(LocalWorkspaceId.LOCAL).insert(entry)
    override fun load(limit: Int) = forWorkspace(LocalWorkspaceId.LOCAL).load(limit)
    override fun loadTimeline(range: HistoryDateRange) = forWorkspace(LocalWorkspaceId.LOCAL).loadTimeline(range)
    override fun clear() = forWorkspace(LocalWorkspaceId.LOCAL).clear()
}
