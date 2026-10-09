package com.justnoise.app.auth

import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.LocalWorkspaceId
import com.justnoise.gate0.data.MemoryPreferences
import com.justnoise.gate0.data.ModeMutationResult
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.data.SessionHistorySnapshot
import com.justnoise.gate0.data.SessionHistoryStorage
import com.justnoise.gate0.data.SessionHistoryTimelineSnapshot
import com.justnoise.gate0.data.WorkspaceSwitchResult
import com.justnoise.gate0.domain.HistoryDateRange
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real journal + real product transactions, synthetic disk/SQLite/telemetry ports. */
class AccountCoordinatorTest {
    private val raw = MemoryPreferences()
    private val history = AccountHistories()
    private var elapsed = 10_000L
    private val products = Gate0Preferences(raw, history) { elapsed }
    private val authStore = AccountTestAuthStore()
    private val journal = AuthSessionJournal(authStore)
    private var telemetryOk = true
    private var purgeCount = 0
    private val telemetry = AccountTelemetryBoundary { purgeCount++; telemetryOk }
    private val accounts = AccountCoordinator(journal, products, telemetry)

    @Before fun prepare() {
        assertTrue(products.clearForWithdrawal())
        assertTrue(products.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        saveMode("synthetic.local")
        journal.snapshot()
    }
    @After fun clean() { assertTrue(products.clearForWithdrawal()) }

    @Test fun offlineRestoreDoesNotStopLocalSessionsOrRequireTelemetry() {
        start()
        telemetryOk = false
        val restored = accounts.restore()
        assertEquals(AccountOutcome.READY, restored.outcome)
        assertEquals(AuthPhase.SIGNED_OUT, restored.view!!.auth.phase)
        assertTrue(products.snapshot().desiredActive)
        assertEquals(0, purgeCount)
        assertTrue(products.stopSession(104_000L))
    }

    @Test fun signInPublishesOnlyMatchingAccountAndKeepsLocalDataSeparate() {
        val local = products.loadModes().recentMode
        val oldView = accounts.restore().view!!
        assertTrue(products.setZapDigest("synthetic-pairing"))
        val result = signIn(A)
        assertEquals(AccountOutcome.READY, result.outcome)
        assertEquals(A, result.view!!.auth.accountId)
        assertEquals(LocalWorkspaceId.parse(A), result.view.workspace.workspace)
        assertTrue(accounts.isCurrent(result.view))
        assertFalse(accounts.isCurrent(oldView))
        assertFalse(products.workspaceTransitionPending())
        assertTrue(products.loadModes().modes.isEmpty())
        assertEquals("synthetic-pairing", products.snapshot().zapDigest)
        saveMode("synthetic.a")
        assertEquals(AccountOutcome.READY, accounts.signOut(result.view.auth.operationToken).outcome)
        assertEquals(local, products.loadModes().recentMode)
    }

    @Test fun signOutRejectsLateRefreshAndDoesNotCopyAIntoB() {
        val a = signIn(A).view!!
        saveMode("synthetic.a")
        completeSession()
        assertEquals(AccountOutcome.READY, accounts.signOut(a.auth.operationToken).outcome)
        val b = signIn(B).view!!
        assertEquals(AccountOutcome.STALE, accounts.acceptRefresh(a.auth.operationToken, session(A)).outcome)
        assertEquals(B, journal.snapshot().accountId)
        assertTrue(products.loadModes().modes.isEmpty())
        assertTrue(products.loadHistory().entries.isEmpty())
        assertTrue(accounts.isCurrent(b))
        assertNull(journal.credentials(a.auth.operationToken))
    }

    @Test fun activeSessionRejectsSignInAndSignOutWithoutLosingStop() {
        start()
        assertEquals(AccountOutcome.ACTIVE_SESSION, signIn(A).outcome)
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertTrue(products.stopSession(104_000L))
        val a = signIn(A).view!!
        saveMode("synthetic.a")
        start()
        assertEquals(AccountOutcome.ACTIVE_SESSION, accounts.signOut(a.auth.operationToken).outcome)
        assertEquals(A, journal.snapshot().accountId)
        assertTrue(products.snapshot().desiredActive)
        assertTrue(products.stopSession(104_000L))
    }

    @Test fun expiredCredentialsDoNotPreventOfflineAccountRestoreOrStop() {
        signIn(A)
        saveMode("synthetic.a")
        start()
        telemetryOk = false
        val result = AccountCoordinator(AuthSessionJournal(authStore), products, telemetry).restore()
        assertEquals(AccountOutcome.READY, result.outcome)
        assertEquals(A, result.view!!.auth.accountId)
        assertEquals(0L, journal.credentials(result.view.auth.operationToken)!!.expiresAtEpochSeconds)
        assertTrue(products.stopSession(104_000L))
    }

    @Test fun telemetryCleanupFailureCannotMutateAuthOrProductOwner() {
        val before = journal.snapshot().operationToken
        val mode = products.loadModes().recentMode
        telemetryOk = false
        assertEquals(AccountOutcome.TELEMETRY_CLEANUP_REQUIRED, signIn(A).outcome)
        assertEquals(before, journal.snapshot().operationToken)
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
        assertEquals(mode, products.loadModes().recentMode)
        assertFalse(products.workspaceTransitionPending())
        start()
        assertTrue(products.stopSession(104_000L))
    }

    @Test fun failedCredentialWriteLeavesHandoffClosedThenRestoresDurableLocalOwner() {
        val original = products.loadModes().recentMode
        authStore.failNextWrite = true
        assertEquals(AccountOutcome.STORAGE_ERROR, signIn(A).outcome)
        assertTrue(products.workspaceTransitionPending())
        assertTrue(products.loadModes().error)
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        val restored = AccountCoordinator(AuthSessionJournal(authStore), products, telemetry).restore()
        assertEquals(AccountOutcome.READY, restored.outcome)
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
        assertEquals(original, products.loadModes().recentMode)
    }

    @Test fun crashAfterAuthCommitFinishesOnlyThatMatchingHandoff() {
        val auth = journal.snapshot().operationToken
        stage(A)
        assertTrue(journal.acceptSignIn(auth, session(A)))
        val restored = accounts.restore()
        assertEquals(AccountOutcome.READY, restored.outcome)
        assertEquals(A, restored.view!!.auth.accountId)
        assertFalse(products.workspaceTransitionPending())
    }

    @Test fun interruptedSignOutReturnsToStillDurablySignedInOwner() {
        signIn(A)
        saveMode("synthetic.a")
        stage("local") // Crash before journal.signOut: still A, never publish local as A.
        assertTrue(products.loadModes().error)
        val restored = accounts.restore()
        assertEquals(AccountOutcome.READY, restored.outcome)
        assertEquals(A, restored.view!!.auth.accountId)
        assertEquals(setOf("synthetic.a"), products.snapshot().selectedPackages)
    }

    @Test fun deletionWaitsForVerifiedRemoteSuccessAndPreservesOtherWorkspaces() {
        val local = products.loadModes().recentMode
        val b = signIn(B).view!!
        saveMode("synthetic.b")
        val bHistory = completeSession()
        accounts.signOut(b.auth.operationToken)
        val a = signIn(A).view!!
        saveMode("synthetic.a")
        completeSession()
        assertTrue(products.setZapDigest("synthetic-pairing"))
        val pending = accounts.beginDeletion(a.auth.operationToken)
        assertEquals(AccountOutcome.DELETION_PENDING, pending.outcome)
        assertTrue(history.erased.isEmpty())
        assertEquals(AccountOutcome.STALE, accounts.signOut(pending.view!!.auth.operationToken).outcome)
        assertEquals(AccountOutcome.DELETION_PENDING, accounts.restore().outcome)
        val complete = accounts.confirmRemoteDeletion(journal.snapshot().operationToken)
        assertEquals(AccountOutcome.READY, complete.outcome)
        assertEquals(AuthPhase.SIGNED_OUT, complete.view!!.auth.phase)
        assertEquals(listOf(LocalWorkspaceId.parse(A)), history.erased)
        assertEquals(local, products.loadModes().recentMode)
        assertEquals("synthetic-pairing", products.snapshot().zapDigest)
        signIn(B)
        assertEquals(setOf("synthetic.b"), products.snapshot().selectedPackages)
        assertEquals(listOf(bHistory), products.loadHistory().entries)
    }

    @Test fun remoteSuccessDuringNewSessionDefersErasureButRemovesCredentials() {
        val a = signIn(A).view!!
        saveMode("synthetic.a")
        val pending = accounts.beginDeletion(a.auth.operationToken).view!!
        start()
        val waiting = accounts.confirmRemoteDeletion(pending.auth.operationToken)
        assertEquals(AccountOutcome.ACTIVE_SESSION, waiting.outcome)
        assertEquals(AuthPhase.DELETE_LOCAL_PENDING, journal.snapshot().phase)
        assertNull(journal.credentials(journal.snapshot().operationToken))
        assertTrue(history.erased.isEmpty())
        assertTrue(products.snapshot().desiredActive)
        assertEquals(LocalWorkspaceId.parse(A), products.currentWorkspace())
        assertTrue(products.stopSession(104_000L))
        assertEquals(AccountOutcome.READY, accounts.restore().outcome)
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
    }

    @Test fun finalJournalFailureAfterProductErasureIsRetryable() {
        val a = signIn(A).view!!
        saveMode("synthetic.a")
        accounts.beginDeletion(a.auth.operationToken)
        assertTrue(journal.acknowledgeRemoteDeletion(journal.snapshot().operationToken))
        authStore.failNextWrite = true
        assertEquals(AccountOutcome.STORAGE_ERROR, accounts.restore().outcome)
        assertEquals(AuthPhase.DELETE_LOCAL_PENDING, journal.snapshot().phase)
        assertTrue(products.workspaceTransitionPending())
        assertEquals(listOf(LocalWorkspaceId.parse(A)), history.erased)
        assertEquals(AccountOutcome.READY, accounts.restore().outcome)
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertFalse(products.workspaceTransitionPending())
    }

    @Test fun staleRemoteDeleteResponseCannotEraseTheNewAccount() {
        val old = signIn(A).view!!
        accounts.signOut(old.auth.operationToken)
        signIn(B)
        assertEquals(AccountOutcome.STALE, accounts.confirmRemoteDeletion(old.auth.operationToken).outcome)
        assertTrue(history.erased.isEmpty())
        assertEquals(B, journal.snapshot().accountId)
    }

    @Test fun failedProductCommitCannotCommitSignInOrPublishAView() {
        raw.failNextCommit = true
        val result = signIn(A)
        assertEquals(AccountOutcome.STORAGE_ERROR, result.outcome)
        assertNull(result.view)
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
        assertTrue(products.snapshot().recoveryRequired)
    }

    @Test fun failedFinalHandoffReleaseRecoversWithoutForgettingCommittedSignIn() {
        authStore.afterWrite = { raw.failNextCommit = true; authStore.afterWrite = {} }
        val failed = signIn(A)
        assertEquals(AccountOutcome.STORAGE_ERROR, failed.outcome)
        assertNull(failed.view)
        assertEquals(A, journal.snapshot().accountId)
        assertTrue(products.workspaceTransitionPending())
        val restored = AccountCoordinator(AuthSessionJournal(authStore), products, telemetry).restore()
        assertEquals(AccountOutcome.READY, restored.outcome)
        assertEquals(A, restored.view!!.auth.accountId)
        assertFalse(products.workspaceTransitionPending())
        assertTrue(products.snapshot().recoveryRequired)
        assertTrue(products.areSchedulesSuspended())
    }

    @Test fun deletionAfterGlobalWithdrawalPreservesNewLocalDataThroughSameOwnerHandoff() {
        val a = signIn(A).view!!
        accounts.beginDeletion(a.auth.operationToken)
        assertTrue(journal.acknowledgeRemoteDeletion(journal.snapshot().operationToken))
        // Independent explicit disclosure withdrawal resets product stores, not the
        // encrypted deletion journal. A later local re-consent must not be erased again.
        assertTrue(products.clearForWithdrawal())
        assertTrue(products.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 2_000L))
        saveMode("synthetic.newlocal")
        val local = products.loadModes().recentMode
        assertEquals(AccountOutcome.READY, accounts.restore().outcome)
        assertEquals(local, products.loadModes().recentMode)
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
    }

    private fun signIn(owner: String) = accounts.acceptSignIn(journal.snapshot().operationToken, session(owner), LocalDataChoice.KEEP_SEPARATE)
    private fun stage(owner: String) {
        assertEquals(WorkspaceSwitchResult.SWITCHED_PENDING,
            products.switchWorkspace(products.prepareWorkspaceSwitch()!!, LocalWorkspaceId.parse(owner)))
    }
    private fun saveMode(app: String) {
        assertEquals(ModeMutationResult.SAVED, products.saveMode(products.loadModes().token!!, null, "Synthetic mode", setOf(app)))
        val id = products.loadModes().modes.single().id
        assertEquals(ModeMutationResult.SAVED, products.selectMode(products.loadModes().token!!, id))
    }
    private fun start() {
        products.updateServiceHeartbeat(elapsed)
        assertTrue(products.startSession(100_000L))
    }
    private fun completeSession(): SessionHistoryEntry {
        start()
        elapsed += 4_000L
        return products.stopSessionWithCompletion(104_000L).completion!!
    }
    private fun session(owner: String) = StoredAuthSession(owner, "synthetic@example.invalid", "synthetic-access", "synthetic-refresh", 0)

    companion object {
        private const val A = "11111111-1111-4111-8111-111111111111"
        private const val B = "22222222-2222-4222-8222-222222222222"
    }
}

internal class AccountTestAuthStore : AuthRecordStore {
    private var record: AuthRecord? = null
    var failNextWrite = false
    var afterWrite: () -> Unit = {}
    override fun read() = record
    override fun write(record: AuthRecord) {
        if (failNextWrite) { failNextWrite = false; throw AuthStorageException() }
        this.record = record
        afterWrite()
    }
}

internal class AccountHistories : SessionHistoryStorage {
    private val rows = mutableMapOf<LocalWorkspaceId, MutableList<SessionHistoryEntry>>()
    val erased = mutableListOf<LocalWorkspaceId>()
    override fun forWorkspace(workspace: LocalWorkspaceId): SessionHistoryStorage = object : SessionHistoryStorage {
        private val entries get() = rows.getOrPut(workspace) { mutableListOf() }
        override fun insert(entry: SessionHistoryEntry) { if (entries.none { it.id == entry.id }) entries += entry }
        override fun visitEntries(visit: (SessionHistoryEntry) -> Unit) = entries.toList().forEach(visit)
        override fun canMergeFrom(source: SessionHistoryStorage): Boolean {
            var compatible = true
            source.visitEntries { row -> if (entries.any { it.id == row.id && it != row }) compatible = false }
            return compatible
        }
        override fun mergeFrom(source: SessionHistoryStorage) {
            val incoming = mutableListOf<SessionHistoryEntry>()
            source.visitEntries { row ->
                check(entries.none { it.id == row.id && it != row })
                if (entries.none { it.id == row.id }) incoming += row
            }
            entries.addAll(incoming)
        }
        override fun load(limit: Int) = SessionHistorySnapshot(entries.toList(), entries.size)
        override fun loadTimeline(range: HistoryDateRange) = SessionHistoryTimelineSnapshot(range)
        override fun clear() { entries.clear() }
        override fun eraseAccountData() { check(workspace != LocalWorkspaceId.LOCAL); clear(); erased += workspace }
    }
    override fun insert(entry: SessionHistoryEntry) = forWorkspace(LocalWorkspaceId.LOCAL).insert(entry)
    override fun load(limit: Int) = forWorkspace(LocalWorkspaceId.LOCAL).load(limit)
    override fun loadTimeline(range: HistoryDateRange) = forWorkspace(LocalWorkspaceId.LOCAL).loadTimeline(range)
    override fun clear() = forWorkspace(LocalWorkspaceId.LOCAL).clear()
}
