package com.justnoise.gate0.data

import android.content.SharedPreferences
import com.justnoise.gate0.domain.HistoryDateRange
import com.justnoise.gate0.domain.ScheduleTiming
import com.justnoise.gate0.domain.RestrictionAction
import com.justnoise.gate0.domain.SessionDecision
import com.justnoise.gate0.domain.SessionDecisionPersistence
import com.justnoise.gate0.domain.SessionState
import com.justnoise.gate0.domain.SessionTransition
import com.justnoise.gate0.feature.ValidatedSessionWriter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real preference transaction code with only disk/clock/SQLite replaced; no phone data. */
class ModePersistenceTest {
    private val raw = MemoryPreferences()
    private val history = MemoryHistory()
    private var elapsed = 10_000L
    private val store = Gate0Preferences(raw, history) { elapsed }

    @Before fun prepare() { assertTrue(store.clearForWithdrawal()) }
    @After fun finish() { assertTrue(store.clearForWithdrawal()) }

    @Test fun legacyMigrationIsIdempotentAndPreservesPairingHistorySchedulesAndActiveSnapshot() {
        raw.edit().putStringSet("selected_packages", setOf("synthetic.first", "synthetic.second"))
            .putString("zap_hmac_digest", "synthetic-digest")
            .putString("accepted_disclosure_version", Gate0Preferences.CURRENT_DISCLOSURE_VERSION)
            .putLong("accepted_disclosure_epoch_millis", 1_000L)
            .putBoolean("desired_active", true).putStringSet("active_packages", setOf("synthetic.active"))
            .putString("active_session_id", "synthetic-active-id").putLong("active_since_epoch_millis", 100L)
            .putLong("session_revision", 9L).putString("schedules_v1", "preserve-without-rewrite")
            .putString("schedule_executions_v1", "preserve-receipts").commit()
        val row = SessionHistoryEntry("existing", 10L, 20L, 10L, 1, false)
        history.insert(row)
        val before = store.snapshot()
        val modes = store.loadModes()
        assertFalse(modes.error)
        assertNull(modes.token)
        assertEquals("Noise", modes.recentMode?.name)
        assertEquals(before.selectedPackages, modes.recentMode?.selectedPackages)
        assertEquals(DEFAULT_SCHEDULE_MODE_REFERENCE, modes.recentModeId)
        val migrated = raw.all
        repeat(3) { assertEquals(modes, store.loadModes()) }
        assertEquals(migrated, raw.all)
        val after = store.snapshot()
        assertEquals(before, after)
        assertEquals(listOf(row), history.entries)
        assertEquals("preserve-without-rewrite", raw.getString("schedules_v1", null))
        assertEquals("preserve-receipts", raw.getString("schedule_executions_v1", null))
    }

    @Test fun oldSingleTargetActiveSessionRetainsItsTargetsWithoutFabricatedModeIdentity() {
        raw.edit().putString("selected_package", "synthetic.first").putBoolean("desired_active", true).commit()
        val before = store.snapshot()
        assertFalse(store.loadModes().error)
        assertEquals(before.activePackages, store.snapshot().activePackages)
        assertEquals(setOf("synthetic.first"), store.snapshot().activePackages)
        assertNull(store.snapshot().activeModeId)
        assertNull(store.snapshot().activeModeName)
    }

    @Test fun independentModesSaveWithoutChangingRecentChoiceAndStartSnapshotsExactMode() {
        assertTrue(store.setSelectedPackages(setOf("synthetic.noise")))
        val sleep = create(" Sleep ", setOf("synthetic.sleep", "synthetic.video"))
        assertEquals("Sleep", sleep.name)
        assertEquals(DEFAULT_SCHEDULE_MODE_REFERENCE, store.loadModes().recentModeId)
        assertEquals(ModeMutationResult.SAVED, store.selectMode(token(), sleep.id))
        val reloaded = Gate0Preferences(raw, history) { elapsed }
        assertEquals(sleep, reloaded.loadModes().recentMode)
        assertTrue(reloaded.startSession(100_000L))
        val active = reloaded.snapshot()
        assertEquals(sleep.id, active.activeModeId)
        assertEquals("Sleep", active.activeModeName)
        assertEquals(sleep.selectedPackages, active.activePackages)
        assertNotNull(active.activeSessionId)
        assertEquals(setOf("synthetic.noise"), reloaded.modeForReference(DEFAULT_SCHEDULE_MODE_REFERENCE)?.selectedPackages)
    }

    @Test fun draftsAreNotWritesAndStaleSaveCannotOverwriteAnotherChange() {
        val mode = create("Sleep", setOf("synthetic.sleep"))
        val draftToken = token()
        val before = raw.all
        val draft = mode.copy(name = "Uncommitted", selectedPackages = setOf("synthetic.other"))
        assertEquals(before, raw.all) // Cancel discards this transient value.
        assertEquals(ModeMutationResult.SAVED, store.saveMode(draftToken, mode.id, "Night", mode.selectedPackages))
        assertEquals(ModeMutationResult.CONFLICT, store.saveMode(draftToken, draft.id, draft.name, draft.selectedPackages))
        assertEquals("Night", store.modeForReference(mode.id)?.name)
    }

    @Test fun requestedOrRecoverySessionRejectsEveryModeMutation() {
        val mode = create("Sleep", setOf("synthetic.sleep"))
        assertEquals(ModeMutationResult.SAVED, store.selectMode(token(), mode.id))
        val editToken = token()
        assertTrue(store.startSession(100_000L))
        val active = raw.all
        assertEquals(ModeMutationResult.ACTIVE_SESSION, store.saveMode(editToken, mode.id, "Night", mode.selectedPackages))
        assertEquals(ModeMutationResult.ACTIVE_SESSION, store.selectMode(editToken, mode.id))
        assertEquals(ModeMutationResult.ACTIVE_SESSION, store.deleteMode(editToken, mode.id))
        assertEquals(active, raw.all)
        assertTrue(store.stopSession(200_000L))
        val idleToken = token()
        store.markRecoveryRequired()
        assertEquals(ModeMutationResult.RECOVERY_REQUIRED, store.saveMode(idleToken, mode.id, "Night", mode.selectedPackages))
        assertEquals(ModeMutationResult.RECOVERY_REQUIRED, store.selectMode(idleToken, mode.id))
        assertEquals(ModeMutationResult.RECOVERY_REQUIRED, store.deleteMode(idleToken, mode.id))
    }

    @Test fun deletingRecentClearsChoiceAndNeverFallsBackOrRegeneratesDefaults() {
        val first = create("Sleep", setOf("synthetic.sleep"))
        create("Work", setOf("synthetic.work"))
        assertEquals(ModeMutationResult.SAVED, store.selectMode(token(), first.id))
        assertEquals(ModeMutationResult.SAVED, store.deleteMode(token(), first.id))
        assertNull(store.loadModes().recentModeId)
        assertFalse(store.startSession(100_000L))
        val last = store.loadModes().modes.single()
        assertEquals(ModeMutationResult.SAVED, store.deleteMode(token(), last.id))
        assertTrue(store.loadModes().modes.isEmpty())
        assertTrue(Gate0Preferences(raw, history) { elapsed }.loadModes().modes.isEmpty())
        assertFalse(store.setSelectedPackages(setOf("synthetic.noise")))
    }

    @Test fun referencedModeCannotBeDeletedEvenWhenScheduleIsDisabled() {
        val mode = create("Sleep", setOf("synthetic.sleep"))
        assertTrue(store.saveSchedule(schedule(mode.id, enabled = false), nowEpochMilliseconds = 1_000L))
        assertEquals(ModeMutationResult.REFERENCED_BY_SCHEDULE, store.deleteMode(token(), mode.id))
        val saved = store.loadSchedules().schedules.single()
        assertTrue(store.deleteSchedule(saved.id, saved.revision))
        assertEquals(ModeMutationResult.SAVED, store.deleteMode(token(), mode.id))
    }

    @Test fun scheduledStartUsesItsModeAndNeverChangesLastExplicitSelection() {
        val sleep = create("Sleep", setOf("synthetic.sleep"))
        val work = create("Work", setOf("synthetic.work", "synthetic.mail"))
        assertEquals(ModeMutationResult.SAVED, store.selectMode(token(), work.id))
        assertTrue(store.saveSchedule(schedule(sleep.id), nowEpochMilliseconds = 1_000L))
        val occurrence = requireNotNull(store.reconcileSchedulePlan(1_000L, "UTC").entries.single().pending)
        val result = store.dispatchSchedule(occurrence, 100_000L) { checkedState, checkedSchedule ->
            assertEquals(sleep.id, checkedSchedule.modeReference)
            assertEquals(sleep.selectedPackages, checkedState.selectedPackages)
            null
        }
        assertTrue(result.started)
        val active = store.snapshot()
        assertEquals(sleep.id, active.activeModeId)
        assertEquals(sleep.selectedPackages, active.activePackages)
        assertEquals(work.id, active.recentModeId)
        assertEquals(work.selectedPackages, active.selectedPackages)
        assertEquals(result.outcome?.sessionId, active.activeSessionId)
        val before = raw.all
        assertFalse(store.dispatchSchedule(occurrence, 100_001L) { _, _ -> fail("No duplicate preflight"); null }.started)
        assertEquals(before, raw.all)
    }

    @Test fun missingModeFailsScheduleWithoutUsingRecentAndCanStillBeDisabled() {
        val sleep = create("Sleep", setOf("synthetic.sleep"))
        assertEquals(ModeMutationResult.SAVED, store.selectMode(token(), sleep.id))
        assertFalse(store.saveSchedule(schedule("00000000-0000-0000-0000-000000000001"), nowEpochMilliseconds = 1_000L))
        assertTrue(store.saveSchedule(schedule(sleep.id), nowEpochMilliseconds = 1_000L))
        val saved = store.loadSchedules().schedules.single()
        raw.edit().putString("modes_v1", ModeStoreCodec.encode(ModeCatalog())).commit()
        assertTrue(store.setScheduleEnabled(saved.id, saved.revision, false, 1_000L))
        assertFalse(store.loadSchedules().schedules.single().enabled)
    }

    @Test fun startRejectsModeChangedAfterPreflightWithoutTakingWrongTargets() {
        val first = create("Sleep", setOf("synthetic.sleep"))
        val second = create("Work", setOf("synthetic.work"))
        store.selectMode(token(), first.id)
        val checked = store.snapshot()
        val authority = token()
        store.selectMode(token(), second.id)
        val before = raw.all
        assertFalse(store.startSession(100_000L, checked, authority))
        assertEquals(before, raw.all)
        assertFalse(store.snapshot().desiredActive)
    }

    @Test fun staleValidatedStartDoesNotTurnAnOrdinaryModeChangeIntoRecovery() {
        val first = create("Sleep", setOf("synthetic.sleep"))
        val second = create("Work", setOf("synthetic.work"))
        store.selectMode(token(), first.id)
        val writer = ValidatedSessionWriter(store, store.snapshot(), token(), { true }, {})
        store.selectMode(token(), second.id)
        val before = raw.all
        val result = SessionDecisionPersistence.apply(startDecision(), 100_000L, writer)
        assertEquals(SessionTransition.START_FAILED, result.transition)
        assertFalse(writer.persistenceFailed)
        assertEquals(before, raw.all)
        assertFalse(store.snapshot().recoveryRequired)
    }

    @Test fun scheduledStartWinningAnNfcRaceKeepsItsActiveStateAndHistoryContinuity() {
        val mode = create("Sleep", setOf("synthetic.sleep"))
        store.selectMode(token(), mode.id)
        val writer = ValidatedSessionWriter(store, store.snapshot(), token(), { true }, {})
        store.saveSchedule(schedule(mode.id), nowEpochMilliseconds = 1_000L)
        val occurrence = requireNotNull(store.reconcileSchedulePlan(1_000L, "UTC").entries.single().pending)
        assertTrue(store.dispatchSchedule(occurrence, 100_000L) { _, _ -> null }.started)
        val active = raw.all
        assertEquals(SessionTransition.START_FAILED, SessionDecisionPersistence.apply(startDecision(), 100_000L, writer).transition)
        assertEquals(active, raw.all)
        assertFalse(writer.persistenceFailed)
        assertFalse(store.snapshot().recoveryRequired)
        assertTrue(store.snapshot().desiredActive)
    }

    @Test fun legacyNullIdStopCannotEndANewerSessionOrMarkItForRecovery() {
        raw.edit().putString("selected_package", "synthetic.legacy").putBoolean("desired_active", true).commit()
        val writer = ValidatedSessionWriter(store, store.snapshot(), store.readSelectionEdit().token, { true }, {})
        assertTrue(store.stopSession(100_000L))
        assertTrue(store.startSession(200_000L))
        val active = raw.all
        val decision = SessionDecision(SessionTransition.STOP, SessionState.IDLE, RestrictionAction.CLEAR)
        assertEquals(RestrictionAction.NONE, SessionDecisionPersistence.apply(decision, 210_000L, writer, null).restrictionAction)
        assertEquals(active, raw.all)
        assertFalse(writer.persistenceFailed)
    }

    @Test fun validatedStartStillQuarantinesAFailedDurableWrite() {
        store.setSelectedPackages(setOf("synthetic.noise"))
        val writer = ValidatedSessionWriter(store, store.snapshot(), token(), { true }, {})
        raw.failNextCommit = true
        val result = SessionDecisionPersistence.apply(startDecision(), 100_000L, writer)
        assertEquals(SessionTransition.START_FAILED, result.transition)
        assertTrue(writer.persistenceFailed)
        assertFalse(store.snapshot().desiredActive)
        assertTrue(store.snapshot().recoveryRequired)
        assertTrue(store.loadModes().error)
    }

    @Test fun missingRecentOrCorruptCatalogDoesNotPreventStoppingExistingSession() {
        assertTrue(store.setSelectedPackages(setOf("synthetic.noise")))
        assertTrue(store.startSession(100_000L))
        val active = store.snapshot()
        raw.edit().putString("modes_v1", "{\"schemaVersion\":99}").commit()
        assertTrue(store.loadModes().error)
        assertTrue(store.snapshot().modeStorageError)
        assertTrue(store.stopSession(200_000L, active.activeSessionId))
        assertFalse(store.snapshot().desiredActive)
        assertTrue(store.snapshot().activePackages.isEmpty())
        assertEquals("{\"schemaVersion\":99}", raw.getString("modes_v1", null))
    }

    @Test fun corruptAndNewerCatalogsAreNotOverwrittenByMigrationOrSave() {
        for (encoded in listOf("broken", "{\"schemaVersion\":99}", "[]")) {
            raw.edit().putString("selected_package", "synthetic.legacy").putString("modes_v1", encoded).commit()
            val before = raw.all
            assertTrue(store.loadModes().error)
            assertNull(store.loadModes().token)
            assertFalse(store.startSession(100_000L))
            assertEquals(before, raw.all)
        }
    }

    @Test fun failedMigrationAndModeSaveRestoreOldMapAndQuarantineWrites() {
        raw.edit().putString("selected_package", "synthetic.legacy").commit()
        raw.failNextCommit = true
        assertTrue(store.loadModes().error)
        assertFalse(raw.contains("modes_v1"))
        assertEquals("synthetic.legacy", raw.getString("selected_package", null))
        assertTrue(store.snapshot().recoveryRequired)
        assertTrue(store.clearForWithdrawal())
        val mode = create("Sleep", setOf("synthetic.sleep"))
        val authority = token()
        val before = store.snapshot()
        raw.failNextCommit = true
        assertEquals(ModeMutationResult.STORAGE_FAILED, store.saveMode(authority, mode.id, "Night", mode.selectedPackages))
        assertEquals("Sleep", store.modeForReference(mode.id)?.name)
        assertEquals(before.sessionRevision, store.snapshot().sessionRevision)
        assertNull(store.loadModes().token)
        assertTrue(store.snapshot().recoveryRequired)
    }

    @Test fun withdrawalClearsModesAndStaleTokensCannotRecreateThemAfterReconsent() {
        assertTrue(store.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        val mode = create("Sleep", setOf("synthetic.sleep"))
        val stale = token()
        assertTrue(store.clearForWithdrawal())
        assertTrue(store.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertEquals(ModeMutationResult.CONFLICT, store.saveMode(stale, null, mode.name, mode.selectedPackages))
        assertTrue(store.loadModes().modes.isEmpty())
        assertNull(store.snapshot().activeModeId)
        assertNull(store.snapshot().activeModeName)
    }

    @Test fun modeChangesPreservePendingCompletionAndHistoryDeliversOnce() {
        store.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L)
        store.setSelectedPackages(setOf("synthetic.noise"))
        store.updateServiceHeartbeat(elapsed)
        assertTrue(store.startSession(100_000L))
        elapsed += 4_000L
        val completed = store.stopSessionWithCompletion(104_000L)
        assertTrue(completed.succeeded)
        val pending = raw.getString("pending_session_history", null)
        create("Sleep", setOf("synthetic.sleep"))
        assertEquals(pending, raw.getString("pending_session_history", null))
        assertEquals(listOf(completed.completion), store.loadHistory().entries)
        assertEquals(listOf(completed.completion), store.loadHistory().entries)
    }

    private fun token() = requireNotNull(store.loadModes().token)
    private fun startDecision() = SessionDecision(SessionTransition.START, SessionState.ACTIVE, RestrictionAction.APPLY)
    private fun create(name: String, targets: Set<String>): StoredMode {
        val previous = store.loadModes().modes.map { it.id }.toSet()
        assertEquals(ModeMutationResult.SAVED, store.saveMode(token(), null, name, targets))
        return store.loadModes().modes.single { it.id !in previous }
    }
    private fun schedule(mode: String, enabled: Boolean = true) = ScheduleDraft("Night", enabled,
        ScheduleTiming(oneTimeEpochMilliseconds = 100_000L), "UTC", mode)
}

private class MemoryHistory : SessionHistoryStorage {
    val entries = mutableListOf<SessionHistoryEntry>()
    override fun insert(entry: SessionHistoryEntry) { if (entries.none { it.id == entry.id }) entries += entry }
    override fun load(limit: Int) = SessionHistorySnapshot(entries.toList(), entries.size)
    override fun loadTimeline(range: HistoryDateRange) = SessionHistoryTimelineSnapshot(range)
    override fun clear() { entries.clear() }
}

internal class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any>()
    var failNextCommit = false
    override fun getAll(): Map<String, Any> = values.toMap()
    override fun contains(key: String) = values.containsKey(key)
    override fun getString(key: String, defValue: String?): String? = values[key]?.let { it as String } ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as Set<String>?)?.toMutableSet() ?: defValues
    override fun getLong(key: String, defValue: Long) = values[key]?.let { it as Long } ?: defValue
    override fun getInt(key: String, defValue: Int) = values[key]?.let { it as Int } ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key]?.let { it as Float } ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key]?.let { it as Boolean } ?: defValue
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val updates = mutableMapOf<String, Any?>()
        var clear = false
        override fun putString(key: String, value: String?) = apply { updates[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { updates[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { updates[key] = value }
        override fun putLong(key: String, value: Long) = apply { updates[key] = value }
        override fun putFloat(key: String, value: Float) = apply { updates[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { updates[key] = value }
        override fun remove(key: String) = apply { updates[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            apply()
            return !failNextCommit.also { failNextCommit = false }
        }
        override fun apply() {
            if (clear) values.clear()
            updates.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}
