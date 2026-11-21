package com.justnoise.gate0.data

import com.justnoise.gate0.domain.HistoryDateRange
import com.justnoise.gate0.domain.ScheduleTiming
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real mode/schedule/session preference transactions; synthetic in-memory file/SQLite ports only. */
class WorkspacePersistenceTest {
    private val raw = MemoryPreferences()
    private val histories = WorkspaceHistories()
    private var elapsed = 10_000L
    private val store = Gate0Preferences(raw, histories) { elapsed }

    @Before fun prepare() { assertTrue(store.clearForWithdrawal()) }
    @After fun clean() { histories.failClear = null; histories.failInsert = false; assertTrue(store.clearForWithdrawal()) }

    @Test fun localUpgradeKeepsExactLegacyHistoryAndNeverAssignsItToAnAccount() {
        setup("synthetic.local")
        val completion = finishSession()
        assertEquals(listOf(completion), store.loadHistory().entries)
        switchTo(A)
        assertEquals(A, store.currentWorkspace())
        assertTrue(store.loadModes().modes.isEmpty())
        assertTrue(store.loadHistory().entries.isEmpty())
        switchTo(LocalWorkspaceId.LOCAL)
        assertEquals(setOf("synthetic.local"), store.snapshot().selectedPackages)
        assertEquals(listOf(completion), store.loadHistory().entries)
        assertEquals("session_history.db", LocalWorkspaceId.LOCAL.historyDatabaseName)
    }

    @Test fun modesSchedulesAndHistoryRemainSeparateAcrossAAndB() {
        setup("synthetic.local")
        switchTo(A)
        setup("synthetic.accounta")
        val modeA = store.loadModes().recentMode!!
        assertTrue(store.saveSchedule(ScheduleDraft("A schedule", false,
            ScheduleTiming(oneTimeEpochMilliseconds = 500_000L), "UTC", modeA.id),
            null, null, 1_000L))
        val scheduleA = store.loadSchedules().schedules.single()
        val completionA = finishSession()
        switchTo(B)
        assertTrue(store.loadModes().modes.isEmpty())
        assertTrue(store.loadSchedules().schedules.isEmpty())
        assertTrue(store.loadHistory().entries.isEmpty())
        setup("synthetic.accountb")
        val completionB = finishSession()
        switchTo(A)
        assertEquals(modeA, store.loadModes().recentMode)
        assertEquals(listOf(scheduleA), store.loadSchedules().schedules)
        assertEquals(listOf(completionA), store.loadHistory().entries)
        switchTo(B)
        assertEquals(setOf("synthetic.accountb"), store.snapshot().selectedPackages)
        assertEquals(listOf(completionB), store.loadHistory().entries)
    }

    @Test fun installationPairingConsentAndOnboardingSurviveWithoutBeingCopiedIntoProfiles() {
        setup("synthetic.local")
        assertTrue(store.setZapDigest("synthetic-pairing-digest"))
        assertTrue(store.completeOnboarding())
        val before = store.snapshot()
        switchTo(A)
        val after = store.snapshot()
        assertEquals(before.zapDigest, after.zapDigest)
        assertEquals(before.acceptedDisclosureVersion, after.acceptedDisclosureVersion)
        assertEquals(before.acceptedDisclosureEpochMilliseconds, after.acceptedDisclosureEpochMilliseconds)
        assertTrue(store.hasCompletedOnboarding())
        val archive = raw.getString("workspace_profile_v1_local", null)!!
        assertFalse(archive.contains("synthetic-pairing-digest"))
        assertFalse(archive.contains("accepted_disclosure"))
        assertFalse(archive.contains("service_heartbeat"))
    }

    @Test fun emergencyUnlocksBelongToInstallationAndSurviveAccountSwitchAndErasure() {
        setup("synthetic.local")
        assertTrue(store.startSession(100_000L))
        assertTrue(store.emergencyUnzap(104_000L, requireNotNull(store.snapshot().activeSessionId)).succeeded)
        assertEquals(4, store.emergencyUnlocksRemaining())
        switchTo(A)
        assertEquals(4, store.emergencyUnlocksRemaining())
        switchTo(B)
        assertEquals(4, store.emergencyUnlocksRemaining())
        switchTo(A)
        val token = leaveAccountForErasure()
        assertTrue(store.eraseAccountWorkspace(token, A))
        assertTrue(store.completeWorkspaceSwitch(token))
        assertEquals(4, store.emergencyUnlocksRemaining())
        raw.all.filterKeys { it.startsWith("workspace_profile_v1_") }.values.forEach {
            assertFalse(it.toString().contains("emergency_unlocks"))
        }
    }

    @Test fun durableHandoffBlocksHydrationPairingStartsAndAlarmsUntilAcknowledged() {
        setup("synthetic.local")
        val oldToken = store.loadModes().token!!
        assertEquals(WorkspaceSwitchResult.SWITCHED_PENDING, store.switchWorkspace(store.prepareWorkspaceSwitch()!!, A))
        val restarted = Gate0Preferences(raw, histories) { elapsed }
        assertTrue(restarted.workspaceTransitionPending())
        assertTrue(restarted.loadModes().error)
        assertTrue(restarted.loadHistory().error)
        assertTrue(restarted.loadSchedules().error)
        assertTrue(restarted.areSchedulesSuspended())
        assertNull(restarted.prepareZapPairing())
        assertFalse(restarted.startSession(100_000L))
        assertNull(restarted.prepareWorkspaceSwitch())
        val pending = restarted.pendingWorkspaceSwitch()!!
        assertFalse(restarted.completeWorkspaceSwitch(pending.copy(workspace = B)))
        assertTrue(restarted.completeWorkspaceSwitch(pending))
        assertFalse(restarted.completeWorkspaceSwitch(pending))
        assertFalse(restarted.loadModes().error)
        assertTrue(restarted.areSchedulesSuspended()) // explicit schedule recovery is still required
        assertEquals(ModeMutationResult.CONFLICT, restarted.saveMode(oldToken, null, "stale", setOf("synthetic.stale")))
    }

    @Test fun activeAndRecoverySessionsRejectSwitchButZapStopStillWorks() {
        setup("synthetic.local")
        val token = store.prepareWorkspaceSwitch()!!
        assertTrue(store.startSession(100_000L))
        assertEquals(WorkspaceSwitchResult.ACTIVE_SESSION, store.switchWorkspace(token, A))
        assertNull(store.prepareWorkspaceSwitch())
        elapsed += 4_000L
        assertTrue(store.stopSession(104_000L, store.snapshot().activeSessionId))
        store.markRecoveryRequired()
        assertEquals(WorkspaceSwitchResult.RECOVERY_REQUIRED, store.switchWorkspace(token, A))
        assertEquals(LocalWorkspaceId.LOCAL, store.currentWorkspace())
    }

    @Test fun pendingCompletionDrainsToItsOriginalWorkspaceExactlyOnce() {
        setup("synthetic.local")
        val completion = finishSession()
        assertNotNull(raw.getString("pending_session_history", null))
        switchTo(A)
        assertNull(raw.getString("pending_session_history", null))
        assertTrue(store.loadHistory().entries.isEmpty())
        switchTo(LocalWorkspaceId.LOCAL)
        repeat(2) { assertEquals(listOf(completion), store.loadHistory().entries) }
    }

    @Test fun failedOutboxDeliveryCannotMoveTheWorkspaceOrLoseTheCompletion() {
        setup("synthetic.local")
        val completion = finishSession()
        histories.failInsert = true
        assertEquals(WorkspaceSwitchResult.STORAGE_FAILED, store.switchWorkspace(store.prepareWorkspaceSwitch()!!, A))
        assertEquals(LocalWorkspaceId.LOCAL, store.currentWorkspace())
        assertNotNull(raw.getString("pending_session_history", null))
        histories.failInsert = false
        switchTo(A)
        assertEquals(listOf(completion), histories.forWorkspace(LocalWorkspaceId.LOCAL).load(100).entries)
    }

    @Test fun failedWorkspaceCommitRestoresOriginalOwnerAndQuarantinesWrites() {
        setup("synthetic.local")
        val token = store.prepareWorkspaceSwitch()!!
        raw.failNextCommit = true
        assertEquals(WorkspaceSwitchResult.STORAGE_FAILED, store.switchWorkspace(token, A))
        assertEquals(LocalWorkspaceId.LOCAL, store.currentWorkspace())
        assertEquals(setOf("synthetic.local"), store.snapshot().selectedPackages)
        assertTrue(store.snapshot().recoveryRequired)
        assertNull(store.prepareWorkspaceSwitch())
        assertFalse(store.workspaceTransitionPending())
    }

    @Test fun failedAuthHandoffCanReturnToLocalWithoutOpeningWrongAccount() {
        setup("synthetic.local")
        val local = store.loadModes().recentMode
        store.switchWorkspace(store.prepareWorkspaceSwitch()!!, A)
        val abandoned = store.pendingWorkspaceSwitch()!!
        val restarted = Gate0Preferences(raw, histories) { elapsed }
        assertEquals(WorkspaceSwitchResult.SWITCHED_PENDING,
            restarted.switchWorkspace(restarted.pendingWorkspaceSwitch()!!, LocalWorkspaceId.LOCAL))
        assertTrue(restarted.workspaceTransitionPending())
        assertTrue(restarted.loadModes().error)
        assertFalse(restarted.completeWorkspaceSwitch(abandoned))
        assertTrue(restarted.completeWorkspaceSwitch(restarted.pendingWorkspaceSwitch()!!))
        assertEquals(local, restarted.loadModes().recentMode)
        assertEquals(LocalWorkspaceId.LOCAL, restarted.currentWorkspace())
    }

    @Test fun malformedCurrentOwnerIsNotTreatedAsTheLegacyWorkspace() {
        setup("synthetic.local")
        raw.edit().putString("workspace_owner_v1", "../../invalid").commit()
        assertTrue(store.loadModes().error)
        assertTrue(store.loadSchedules().error)
        assertTrue(store.loadHistory().error)
        assertFalse(store.startSession(100_000L))
        // Restore only the synthetic corruption so the normal after-test cleanup is meaningful.
        raw.edit().remove("workspace_owner_v1").commit()
    }

    @Test fun queuedWorkspaceWorkCannotReviveEvenAfterSwitchingBackToSameAccount() {
        setup("synthetic.local")
        val local = store.workspaceReadToken()!!
        finishSession()
        assertTrue(store.isWorkspaceCurrent(local)) // ordinary session changes are not an account switch
        switchTo(A)
        assertFalse(store.isWorkspaceCurrent(local))
        val a = store.workspaceReadToken()!!
        switchTo(LocalWorkspaceId.LOCAL)
        assertFalse(store.isWorkspaceCurrent(local))
        assertFalse(store.isWorkspaceCurrent(a))
        var oldWorkRan = false
        store.withScheduleLock {
            if (store.isWorkspaceCurrent(local)) oldWorkRan = true
        }
        assertFalse(oldWorkRan)
        val beforeWithdrawal = store.workspaceReadToken()!!
        assertTrue(store.clearForWithdrawal())
        assertTrue(store.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 2_000L))
        assertTrue(store.isWorkspaceCurrent(beforeWithdrawal)) // same local namespace; consent has a separate epoch
    }

    @Test fun localReconsentKeepsWorkspaceBridgeUsableButChangesConsentAuthority() {
        setup("synthetic.local")
        val bridge = store.workspaceReadToken()!!
        val oldConsent = store.snapshot().acceptedDisclosureEpochMilliseconds
        assertTrue(store.clearForWithdrawal())
        assertTrue(store.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, oldConsent + 1L))
        assertTrue(store.isWorkspaceCurrent(bridge))
        assertNotEquals(oldConsent, store.snapshot().acceptedDisclosureEpochMilliseconds)
        assertTrue(store.loadSchedules().schedules.isEmpty())
    }

    @Test fun invalidOrFutureWorkspaceDataIsNotOverwrittenAsEmpty() {
        setup("synthetic.local")
        raw.edit().putString("workspace_profile_v1_${A.value}", "{\"version\":999}").commit()
        val before = raw.all
        assertEquals(WorkspaceSwitchResult.STORAGE_FAILED, store.switchWorkspace(store.prepareWorkspaceSwitch()!!, A))
        assertEquals(before, raw.all)
        assertThrows(IllegalArgumentException::class.java) { LocalWorkspaceId.parse("../../session_history") }
        assertThrows(IllegalArgumentException::class.java) { LocalWorkspaceId.parse("1-1-1-1-1") }
    }

    @Test fun withdrawalRetainsAllHistoryCleanupOwnersAcrossFailureAndRetry() {
        setup("synthetic.local")
        finishSession()
        switchTo(A)
        setup("synthetic.accounta")
        finishSession()
        switchTo(B)
        setup("synthetic.accountb")
        finishSession()
        store.loadHistory()
        histories.failClear = A
        assertFalse(store.clearForWithdrawal())
        assertEquals(setOf("local", A.value, B.value), raw.getStringSet("history_delete_workspaces_v1", null))
        assertTrue(store.loadHistory().error)
        assertNull(store.snapshot().zapDigest)
        histories.failClear = null
        assertTrue(store.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 2_000L))
        listOf(LocalWorkspaceId.LOCAL, A, B).forEach { assertTrue(histories.forWorkspace(it).load(100).entries.isEmpty()) }
        assertNull(raw.getStringSet("history_delete_workspaces_v1", null))
        assertTrue(store.loadModes().modes.isEmpty())
    }

    @Test fun accountErasurePreservesLocalOtherAccountPairingAndConsent() {
        setup("synthetic.local")
        val localMode = store.loadModes().recentMode
        val localCompletion = finishSession()
        assertTrue(store.setZapDigest("synthetic-pairing"))
        switchTo(B)
        setup("synthetic.accountb")
        val bMode = store.loadModes().recentMode
        val bCompletion = finishSession()
        switchTo(A)
        setup("synthetic.accounta")
        finishSession()
        val token = leaveAccountForErasure()
        assertTrue(store.eraseAccountWorkspace(token, A))
        assertNull(raw.getString("workspace_profile_v1_${A.value}", null))
        assertEquals(listOf(A), histories.erased)
        assertTrue(store.workspaceTransitionPending()) // auth/analytics cleanup is not yet acknowledged
        assertTrue(store.completeWorkspaceSwitch(token))
        assertEquals(localMode, store.loadModes().recentMode)
        assertEquals(listOf(localCompletion), store.loadHistory().entries)
        assertEquals("synthetic-pairing", store.snapshot().zapDigest)
        assertEquals(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, store.snapshot().acceptedDisclosureVersion)
        switchTo(B)
        assertEquals(bMode, store.loadModes().recentMode)
        assertEquals(listOf(bCompletion), store.loadHistory().entries)
        switchTo(A)
        assertTrue(store.loadModes().modes.isEmpty())
        assertTrue(store.loadHistory().entries.isEmpty())
    }

    @Test fun erasureRequiresClosedLocalHandoffAndRejectsLocalOrStaleTargets() {
        setup("synthetic.local")
        val local = store.prepareWorkspaceSwitch()!!
        assertFalse(store.eraseAccountWorkspace(local, A))
        switchTo(A)
        setup("synthetic.accounta")
        assertFalse(store.eraseAccountWorkspace(store.prepareWorkspaceSwitch()!!, A))
        val pending = leaveAccountForErasure()
        assertFalse(store.eraseAccountWorkspace(pending, LocalWorkspaceId.LOCAL))
        assertFalse(store.eraseAccountWorkspace(local, A))
        assertTrue(histories.erased.isEmpty())
        assertNotNull(raw.getString("workspace_profile_v1_${A.value}", null))
    }

    @Test fun failedAccountErasureRemainsClosedAcrossRestartAndCannotChangeCleanupOwner() {
        setup("synthetic.local")
        switchTo(A)
        setup("synthetic.accounta")
        finishSession()
        val pending = leaveAccountForErasure()
        histories.failClear = A
        assertFalse(store.eraseAccountWorkspace(pending, A))
        assertEquals(A.value, raw.getString("account_erasure_pending_v1", null))
        assertNull(raw.getString("workspace_profile_v1_${A.value}", null))
        val restarted = Gate0Preferences(raw, histories) { elapsed }
        val retry = restarted.pendingWorkspaceSwitch()!!
        assertFalse(restarted.completeWorkspaceSwitch(retry))
        assertEquals(WorkspaceSwitchResult.STORAGE_FAILED, restarted.switchWorkspace(retry, B))
        assertFalse(restarted.eraseAccountWorkspace(retry, B))
        assertTrue(restarted.loadHistory().error)
        histories.failClear = null
        assertTrue(restarted.eraseAccountWorkspace(retry, A))
        assertTrue(restarted.eraseAccountWorkspace(retry, A)) // retry after lost successful response
        assertTrue(restarted.completeWorkspaceSwitch(retry))
        assertNull(raw.getString("account_erasure_pending_v1", null))
    }

    @Test fun failedErasureMarkerWriteDoesNotTouchDatabaseAndCanRetry() {
        setup("synthetic.local")
        switchTo(A)
        setup("synthetic.accounta")
        finishSession()
        val pending = leaveAccountForErasure()
        raw.failNextCommit = true
        assertFalse(store.eraseAccountWorkspace(pending, A))
        assertTrue(histories.erased.isEmpty())
        assertNotNull(raw.getString("workspace_profile_v1_${A.value}", null))
        assertTrue(store.eraseAccountWorkspace(pending, A))
        assertEquals(listOf(A), histories.erased)
        assertTrue(store.completeWorkspaceSwitch(pending))
        assertTrue(store.snapshot().recoveryRequired) // failed write is never silently waived
    }

    @Test fun failedFinalErasureCommitRetainsCleanupOwnerAfterDatabaseWasRemoved() {
        setup("synthetic.local")
        switchTo(A)
        setup("synthetic.accounta")
        finishSession()
        val pending = leaveAccountForErasure()
        histories.afterErase = { raw.failNextCommit = true }
        assertFalse(store.eraseAccountWorkspace(pending, A))
        assertEquals(A.value, raw.getString("account_erasure_pending_v1", null))
        assertTrue(histories.forWorkspace(A).load(100).entries.isEmpty())
        assertFalse(store.completeWorkspaceSwitch(pending))
        histories.afterErase = {}
        assertTrue(store.eraseAccountWorkspace(pending, A))
        assertTrue(store.completeWorkspaceSwitch(pending))
    }

    @Test fun globalWithdrawalIncludesAccountWhoseProfileWasAlreadyRemoved() {
        setup("synthetic.local")
        switchTo(A)
        setup("synthetic.accounta")
        finishSession()
        val pending = leaveAccountForErasure()
        histories.failClear = A
        assertFalse(store.eraseAccountWorkspace(pending, A))
        assertFalse(store.clearForWithdrawal())
        assertTrue(A.value in raw.getStringSet("history_delete_workspaces_v1", null).orEmpty())
        histories.failClear = null
        assertTrue(store.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 2_000L))
        assertTrue(histories.forWorkspace(A).load(100).entries.isEmpty())
    }

    private fun leaveAccountForErasure(): WorkspaceEditToken {
        assertEquals(WorkspaceSwitchResult.SWITCHED_PENDING,
            store.switchWorkspace(store.prepareWorkspaceSwitch()!!, LocalWorkspaceId.LOCAL))
        return store.pendingWorkspaceSwitch()!!
    }

    private fun setup(target: String) {
        assertTrue(store.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        val existing = store.loadModes().token!!
        assertEquals(ModeMutationResult.SAVED, store.saveMode(existing, null, "Synthetic mode", setOf(target)))
        val mode = store.loadModes().modes.single()
        assertEquals(ModeMutationResult.SAVED, store.selectMode(store.loadModes().token!!, mode.id))
    }

    private fun finishSession(): SessionHistoryEntry {
        store.updateServiceHeartbeat(elapsed)
        assertTrue(store.startSession(100_000L))
        elapsed += 4_000L
        return store.stopSessionWithCompletion(104_000L).completion!!
    }

    private fun switchTo(workspace: LocalWorkspaceId) {
        assertEquals(WorkspaceSwitchResult.SWITCHED_PENDING, store.switchWorkspace(store.prepareWorkspaceSwitch()!!, workspace))
        assertTrue(store.completeWorkspaceSwitch(store.pendingWorkspaceSwitch()!!))
    }

    companion object {
        private val A = LocalWorkspaceId.parse("11111111-1111-4111-8111-111111111111")
        private val B = LocalWorkspaceId.parse("22222222-2222-4222-8222-222222222222")
    }
}

private class WorkspaceHistories : SessionHistoryStorage {
    private val rows = mutableMapOf<LocalWorkspaceId, MutableList<SessionHistoryEntry>>()
    var failInsert = false
    var failClear: LocalWorkspaceId? = null
    val erased = mutableListOf<LocalWorkspaceId>()
    var afterErase: () -> Unit = {}
    override fun forWorkspace(workspace: LocalWorkspaceId): SessionHistoryStorage = object : SessionHistoryStorage {
        private val entries get() = rows.getOrPut(workspace) { mutableListOf() }
        override fun insert(entry: SessionHistoryEntry) {
            check(!failInsert)
            if (entries.none { it.id == entry.id }) entries += entry
        }
        override fun load(limit: Int) = SessionHistorySnapshot(entries.toList(), entries.size)
        override fun loadTimeline(range: HistoryDateRange) = SessionHistoryTimelineSnapshot(range)
        override fun clear() { check(failClear != workspace); entries.clear() }
        override fun eraseAccountData() { check(workspace != LocalWorkspaceId.LOCAL); clear(); erased += workspace; afterErase() }
    }
    override fun insert(entry: SessionHistoryEntry) = forWorkspace(LocalWorkspaceId.LOCAL).insert(entry)
    override fun load(limit: Int) = forWorkspace(LocalWorkspaceId.LOCAL).load(limit)
    override fun loadTimeline(range: HistoryDateRange) = forWorkspace(LocalWorkspaceId.LOCAL).loadTimeline(range)
    override fun clear() = forWorkspace(LocalWorkspaceId.LOCAL).clear()
}
