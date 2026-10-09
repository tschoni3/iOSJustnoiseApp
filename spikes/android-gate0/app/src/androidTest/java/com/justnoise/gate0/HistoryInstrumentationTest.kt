package com.justnoise.gate0

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.LocalWorkspaceId
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.data.SessionHistorySnapshot
import com.justnoise.gate0.data.SessionHistoryStorage
import com.justnoise.gate0.data.SessionHistoryTimelineSnapshot
import com.justnoise.gate0.data.SqliteSessionHistoryStorage
import com.justnoise.gate0.domain.HistoryDateRange
import com.justnoise.gate0.domain.HistoryDayAccumulator
import com.justnoise.gate0.domain.HistoryInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic emulator-only storage tests; never run against the owner's paired app. */
@RunWith(AndroidJUnit4::class)
class HistoryInstrumentationTest {
    private lateinit var context: Context

    @Before
    fun clearBeforeTest() {
        context = ApplicationProvider.getApplicationContext()
        // Surface database initialization errors directly instead of only a cleanup Boolean.
        SqliteSessionHistoryStorage(context).load(1)
        assertTrue(Gate0Preferences(context).clearForWithdrawal())
    }

    @After
    fun clearAfterTest() {
        assertTrue(Gate0Preferences(context).clearForWithdrawal())
    }

    @Test
    fun localHistoryMergeStreamsAllRowsAndRetriesWithoutDuplicates() {
        val local = SqliteSessionHistoryStorage(context)
        val account = local.forWorkspace(LocalWorkspaceId.parse(UUID.randomUUID().toString()))
        try {
            repeat(125) { index -> local.insert(entry("import-$index", 100_000, 130_000, 500)) }
            assertTrue(account.canMergeFrom(local))
            account.mergeFrom(local)
            account.mergeFrom(local)
            assertEquals(125, account.load(1).totalCount)
            assertEquals(62_500L, account.load(1).totalDurationMilliseconds)
            assertEquals(local.load(200).entries, account.load(200).entries)
            assertEquals(125, local.load(1).totalCount) // Copy itself never erases source.
        } finally { account.eraseAccountData() }
    }

    @Test
    fun localHistoryMergeConflictRollsBackEarlierRowsAndPreservesBothSources() {
        val local = SqliteSessionHistoryStorage(context)
        val account = local.forWorkspace(LocalWorkspaceId.parse(UUID.randomUUID().toString()))
        val first = entry("a-import", 100_000, 130_000, 30_000)
        val localConflict = entry("z-import", 100_000, 140_000, 40_000)
        val accountConflict = localConflict.copy(durationMilliseconds = 20_000)
        try {
            local.insert(first); local.insert(localConflict); account.insert(accountConflict)
            assertFalse(account.canMergeFrom(local))
            assertThrows(IllegalStateException::class.java) { account.mergeFrom(local) }
            assertEquals(listOf(accountConflict), account.load(100).entries)
            assertEquals(2, local.load(1).totalCount)
        } finally { account.eraseAccountData() }
    }

    @Test
    fun accountDatabaseErasureRemovesOnlyItsOwnFilesAndIsRetryable() {
        val a = LocalWorkspaceId.parse(UUID.randomUUID().toString())
        val b = LocalWorkspaceId.parse(UUID.randomUUID().toString())
        val local = SqliteSessionHistoryStorage(context)
        val accountA = local.forWorkspace(a)
        val accountB = local.forWorkspace(b)
        val row = entry("synthetic-account-erasure", 100_000L, 130_000L, 30_000L)
        try {
            listOf(local, accountA, accountB).forEach { it.insert(row) }
            assertTrue(context.getDatabasePath(a.historyDatabaseName).exists())
            assertThrows(IllegalStateException::class.java) { local.eraseAccountData() }
            accountA.eraseAccountData()
            accountA.eraseAccountData()
            for (suffix in listOf("", "-journal", "-wal", "-shm")) {
                assertFalse(java.io.File(context.getDatabasePath(a.historyDatabaseName).path + suffix).exists())
            }
            assertEquals(listOf(row), local.load(10).entries)
            assertEquals(listOf(row), accountB.load(10).entries)
            assertTrue(accountA.load(10).entries.isEmpty())
        } finally {
            accountA.eraseAccountData()
            accountB.eraseAccountData()
        }
    }

    @Test
    fun sqliteRetriesAreIdempotentAndPagesHaveStableOrderingWithWholeHistoryTotals() {
        val storage = SqliteSessionHistoryStorage(context)
        val first = entry("00000000-0000-0000-0000-000000000001", 100_000L, 130_000L, 30_000L)
        val second = entry("00000000-0000-0000-0000-000000000002", 210_000L, 240_000L, 30_000L)
        val third = entry("00000000-0000-0000-0000-000000000003", 150_000L, 240_000L, 90_000L)
            .copy(targetCount = 3, interrupted = true)
        for (item in listOf(first, third, second, second)) storage.insert(item)

        val reopened = SqliteSessionHistoryStorage(context)
        val page = reopened.load(2)
        assertEquals(listOf(third, second), page.entries)
        assertEquals(3, page.totalCount)
        assertEquals(150_000L, page.totalDurationMilliseconds)
        assertEquals(1, page.interruptedCount)
        assertTrue(page.hasMore)
        assertFalse(page.error)
        assertEquals(listOf(third, second, first), reopened.load(100).entries)
        assertFalse(reopened.load(100).hasMore)
        assertThrows(IllegalStateException::class.java) {
            reopened.insert(second.copy(durationMilliseconds = 31_000L))
        }
        assertEquals(150_000L, reopened.load(100).totalDurationMilliseconds)
        assertThrows(IllegalArgumentException::class.java) { reopened.load(0) }

        reopened.clear()
        assertEquals(SessionHistorySnapshot(), SqliteSessionHistoryStorage(context).load(100))
    }

    @Test
    fun timelineReadsAllRowsIndependentlyOfPageAndKeepsExistingStorageExactly() {
        val storage = SqliteSessionHistoryStorage(context)
        val end = Instant.parse("2026-09-06T00:00:00Z").toEpochMilli()
        val rows = (1..125).map { number ->
            entry("session-$number", end - 30_000L, end + 30_000L, 60_000L)
                .copy(interrupted = number == 125)
        }
        rows.forEach(storage::insert)
        val backwards = entry("clock-change", end + 86_400_000L, end + 1L, 42L)
        storage.insert(backwards)
        val original = storage.load(200)
        assertEquals(100, storage.load(100).entries.size)
        val range = HistoryDateRange(LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-07"), ZoneId.of("UTC"))
        val timeline = Gate0Preferences(context).loadHistoryTimeline(range)
        assertFalse(timeline.error)
        assertEquals(126, timeline.totalCount)
        assertEquals(7_500_042L, timeline.totalDurationMilliseconds)
        assertEquals(listOf(3_750_000L, 3_750_042L), timeline.days.map { it.durationMilliseconds })
        assertEquals(1, timeline.interruptedCount)
        assertEquals(1, timeline.timingUncertainCount)
        assertEquals(original, SqliteSessionHistoryStorage(context).load(200))
        assertEquals(timeline, Gate0Preferences(context).loadHistoryTimeline(range))
    }

    @Test
    fun v3StartGetsImmutableUuidAndTargetsAndOnlyStopProducesCompletedHistory() {
        val storage = RecordingHistoryStorage()
        val preferences = Gate0Preferences(context, storage)
        val id = startConsentedSession(preferences)
        val active = preferences.snapshot()
        assertEquals(id, UUID.fromString(id).toString())
        assertEquals(TARGETS, active.activePackages)
        assertEquals(0, preferences.loadHistory().totalCount)
        assertFalse(preferences.startSession(120_000L))
        assertFalse(preferences.setSelectedPackages(setOf("synthetic.changed")))
        assertEquals(active, preferences.snapshot())

        val receipt = preferences.stopSessionWithCompletion(160_000L, id)
        assertTrue(receipt.succeeded)
        assertEquals(id, receipt.completion?.id)

        assertFalse(preferences.snapshot().desiredActive)
        assertNull(preferences.snapshot().activeSessionId)
        assertTrue(storage.entries.isEmpty()) // Outbox delivery is not required to unblock.
        val pending = rawPreferences().getString("pending_session_history", null).orEmpty()
        assertFalse(TARGETS.any { it in pending })
        val history = Gate0Preferences(context, storage).loadHistory()
        val completed = history.entries.single()
        assertEquals(receipt.completion, completed)
        assertEquals(id, completed.id)
        assertEquals(100_000L, completed.startedAtEpochMilliseconds)
        assertEquals(160_000L, completed.endedAtEpochMilliseconds)
        assertEquals(2, completed.targetCount)
        assertTrue(completed.durationMilliseconds >= 0L)
        assertFalse(completed.interrupted)
    }

    @Test
    fun repeatedExpectedIdStopDoesNotDuplicateAndStaleIdCannotStopAnotherSession() {
        val storage = RecordingHistoryStorage()
        val preferences = Gate0Preferences(context, storage)
        val firstId = startConsentedSession(preferences)
        val firstReceipt = preferences.stopSessionWithCompletion(160_000L, firstId)
        assertTrue(firstReceipt.succeeded)
        assertEquals(firstId, firstReceipt.completion?.id)
        val stopped = preferences.snapshot()
        val duplicate = preferences.stopSessionWithCompletion(170_000L, firstId)
        assertTrue(duplicate.succeeded)
        assertNull(duplicate.completion)
        assertEquals(stopped, preferences.snapshot())
        assertEquals(1, preferences.loadHistory().totalCount)

        assertTrue(preferences.startSession(200_000L))
        val secondActive = preferences.snapshot()
        val secondId = requireNotNull(secondActive.activeSessionId)
        assertNotEquals(firstId, secondId)
        val stale = preferences.stopSessionWithCompletion(260_000L, firstId)
        assertFalse(stale.succeeded)
        assertNull(stale.completion)
        assertEquals(secondActive, preferences.snapshot())
        assertTrue(preferences.stopSession(260_000L, secondId))
        assertEquals(setOf(firstId, secondId), preferences.loadHistory().entries.map { it.id }.toSet())
        assertEquals(2, storage.entries.size)
    }

    @Test
    fun legacyV2ActiveSessionCanStopButLaterConsentDoesNotBackfillHistory() {
        val storage = RecordingHistoryStorage()
        val preferences = Gate0Preferences(context, storage)
        assertTrue(rawPreferences().edit()
            .putString("accepted_disclosure_version", "gate0-accessibility-v2")
            .putLong("accepted_disclosure_epoch_millis", 1_000L)
            .putStringSet("selected_packages", TARGETS)
            .putStringSet("active_packages", TARGETS)
            .putBoolean("desired_active", true)
            .putLong("active_since_epoch_millis", 100_000L)
            .commit())
        assertNull(preferences.snapshot().activeSessionId)
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 150_000L))

        val legacy = preferences.stopSessionWithCompletion(160_000L)
        assertTrue(legacy.succeeded)
        assertNull(legacy.completion)

        assertFalse(preferences.snapshot().desiredActive)
        assertEquals(0, preferences.loadHistory().totalCount)
        assertTrue(storage.entries.isEmpty())
    }

    @Test
    fun historyConsentIsCapturedAtStartNotGrantedRetroactivelyAtStop() {
        val storage = RecordingHistoryStorage()
        val preferences = Gate0Preferences(context, storage)
        assertTrue(preferences.acceptDisclosure("gate0-accessibility-v2", 1_000L))
        assertTrue(preferences.setSelectedPackages(TARGETS))
        assertTrue(preferences.startSession(100_000L))
        val id = requireNotNull(preferences.snapshot().activeSessionId)
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 150_000L))

        assertTrue(preferences.stopSession(160_000L, id))

        assertEquals(0, preferences.loadHistory().totalCount)
        assertTrue(storage.entries.isEmpty())
    }

    @Test
    fun disconnectRecoveryAndUnavailableSignalsStayInterruptedAfterFreshHeartbeat() {
        val signals: List<(Gate0Preferences) -> Unit> = listOf(
            { it.markServiceDisconnected(); Unit },
            { it.markRecoveryRequired() },
            { it.markHistoryInterrupted() },
        )
        for (signal in signals) {
            val storage = RecordingHistoryStorage()
            val preferences = Gate0Preferences(context, storage)
            assertTrue(preferences.clearForWithdrawal())
            val id = startConsentedSession(preferences)
            signal(preferences)
            assertTrue(preferences.updateServiceHeartbeat(SystemClock.elapsedRealtime()))
            assertTrue(preferences.snapshot().serviceConnected)

            assertTrue(preferences.stopSession(160_000L, id))

            assertTrue(preferences.loadHistory().entries.single().interrupted)
        }
    }

    @Test
    fun coldProcessMarkerUsesFallbackDurationAndRecordsInterruption() {
        val storage = RecordingHistoryStorage()
        val preferences = Gate0Preferences(context, storage)
        val id = startConsentedSession(preferences)
        assertTrue(rawPreferences().edit().putString("active_process_id", "synthetic-prior-process").commit())
        preferences.markServiceDisconnected()
        assertTrue(preferences.updateServiceHeartbeat(SystemClock.elapsedRealtime()))

        assertTrue(preferences.stopSession(160_000L, id))

        val completed = preferences.loadHistory().entries.single()
        assertTrue(completed.interrupted)
        assertEquals(60_000L, completed.durationMilliseconds)
    }

    @Test
    fun missingElapsedMetadataInSameProcessUsesInterruptedWallClockFallback() {
        val storage = RecordingHistoryStorage()
        val preferences = Gate0Preferences(context, storage)
        val id = startConsentedSession(preferences)
        assertTrue(preferences.updateServiceHeartbeat(SystemClock.elapsedRealtime()))
        assertTrue(rawPreferences().edit().putLong("active_since_elapsed_millis", -1L).commit())
        assertTrue(preferences.snapshot().serviceConnected)
        assertFalse(preferences.snapshot().recoveryRequired)

        assertTrue(preferences.stopSession(160_000L, id))

        val completed = preferences.loadHistory().entries.single()
        assertTrue(completed.interrupted)
        assertEquals(60_000L, completed.durationMilliseconds)
    }

    @Test
    fun databaseFailurePreservesSuccessfulStopAndPreventsOverwriteUntilDeliveryRetries() {
        val storage = RecordingHistoryStorage()
        val preferences = Gate0Preferences(context, storage)
        val id = startConsentedSession(preferences)
        storage.failInsert = true

        val receipt = preferences.stopSessionWithCompletion(160_000L, id)
        assertTrue(receipt.succeeded)
        assertEquals(id, receipt.completion?.id)
        val stopped = preferences.snapshot()
        assertFalse(stopped.desiredActive)
        assertTrue(rawPreferences().contains("pending_session_history"))
        assertTrue(preferences.loadHistory().error)
        assertFalse(preferences.startSession(200_000L))
        assertEquals(stopped, preferences.snapshot())
        assertTrue(storage.entries.isEmpty())

        storage.failInsert = false
        assertTrue(preferences.startSession(200_000L))
        assertNotEquals(id, preferences.snapshot().activeSessionId)
        assertFalse(rawPreferences().contains("pending_session_history"))
        assertEquals(listOf(id), preferences.loadHistory().entries.map { it.id })
        assertEquals(receipt.completion, preferences.loadHistory().entries.single())
    }

    @Test
    fun failedStopCommitRestoresActiveStateAndCannotPublishNondurableCompletion() {
        val storage = RecordingHistoryStorage()
        val failingContext = FailingCommitContext(context)
        val preferences = Gate0Preferences(failingContext, storage)
        val id = startConsentedSession(preferences)
        val active = preferences.snapshot()
        failingContext.failNextCommit = true

        val failed = preferences.stopSessionWithCompletion(160_000L, id)
        assertFalse(failed.succeeded)
        assertNull(failed.completion)

        assertFalse(failingContext.failNextCommit)
        val rolledBack = preferences.snapshot()
        assertTrue(rolledBack.desiredActive)
        assertEquals(id, rolledBack.activeSessionId)
        assertEquals(active.activePackages, rolledBack.activePackages)
        assertEquals(active.sessionRevision, rolledBack.sessionRevision)
        assertTrue(rolledBack.recoveryRequired)
        assertFalse(rawPreferences().contains("pending_session_history"))
        assertEquals(0, preferences.loadHistory().totalCount)
        assertTrue(storage.entries.isEmpty())
        assertEquals(0, storage.insertCalls)

        assertTrue(preferences.stopSession(160_000L, id))
        val completed = preferences.loadHistory().entries.single()
        assertEquals(id, completed.id)
        assertTrue(completed.interrupted)
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun failedOutboxAcknowledgementRetriesIdenticalUuidWithoutAddingAnotherRow() {
        val storage = RecordingHistoryStorage()
        val failingContext = FailingCommitContext(context)
        val preferences = Gate0Preferences(failingContext, storage)
        val id = startConsentedSession(preferences)
        assertTrue(preferences.stopSession(160_000L, id))
        failingContext.failNextCommit = true

        assertTrue(preferences.loadHistory().error)
        assertEquals(setOf(id), storage.entries.keys)
        assertTrue(rawPreferences().contains("pending_session_history"))
        assertFalse(preferences.snapshot().desiredActive)

        val retried = preferences.loadHistory()
        assertFalse(retried.error)
        assertEquals(1, retried.totalCount)
        assertEquals(2, storage.insertCalls)
        assertFalse(rawPreferences().contains("pending_session_history"))
    }

    @Test
    fun failedWithdrawalDeletionBlocksConsentStartAndHistoryUntilClearWithoutResurrection() {
        val storage = RecordingHistoryStorage()
        val preferences = Gate0Preferences(context, storage)
        val oldEntry = entry("00000000-0000-0000-0000-000000000010", 1_000L, 2_000L, 1_000L)
        storage.insert(oldEntry)
        val id = startConsentedSession(preferences)
        assertTrue(preferences.stopSession(160_000L, id))
        assertTrue(rawPreferences().contains("pending_session_history"))
        storage.failClear = true

        assertFalse(preferences.clearForWithdrawal())
        assertFalse(preferences.snapshot().desiredActive)
        assertNull(preferences.snapshot().acceptedDisclosureVersion)
        assertFalse(rawPreferences().contains("pending_session_history"))
        assertTrue(rawPreferences().getBoolean("history_deletion_pending", false))
        assertFalse(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION))
        val stateBeforeRejectedSelection = rawPreferences().all.toMap()
        assertFalse(preferences.setSelectedPackages(TARGETS))
        assertEquals(stateBeforeRejectedSelection, rawPreferences().all)
        assertTrue(preferences.snapshot().selectedPackages.isEmpty())
        assertFalse(preferences.startSession(200_000L))
        val denied = preferences.loadHistory()
        assertTrue(denied.error)
        assertTrue(denied.entries.isEmpty())
        assertEquals(setOf(oldEntry.id), storage.entries.keys)

        storage.failClear = false
        assertTrue(preferences.clearForWithdrawal())
        assertEquals(SessionHistorySnapshot(), preferences.loadHistory())
        assertFalse(rawPreferences().getBoolean("history_deletion_pending", false))
        val newId = startConsentedSession(preferences)
        assertNotEquals(id, newId)
        assertTrue(preferences.stopSession(160_000L, newId))
        assertEquals(listOf(newId), preferences.loadHistory().entries.map { it.id })
    }

    private fun startConsentedSession(preferences: Gate0Preferences): String {
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertTrue(preferences.setSelectedPackages(TARGETS))
        assertTrue(preferences.updateServiceHeartbeat(SystemClock.elapsedRealtime()))
        assertTrue(preferences.startSession(100_000L))
        return requireNotNull(preferences.snapshot().activeSessionId)
    }

    private fun rawPreferences(): SharedPreferences =
        context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)

    private fun entry(id: String, start: Long, end: Long, duration: Long) =
        SessionHistoryEntry(id, start, end, duration, targetCount = 2, interrupted = false)

    private class RecordingHistoryStorage : SessionHistoryStorage {
        val entries = linkedMapOf<String, SessionHistoryEntry>()
        var failInsert = false
        var failClear = false
        var insertCalls = 0

        override fun insert(entry: SessionHistoryEntry) {
            insertCalls += 1
            check(!failInsert) { "Synthetic insert failure" }
            val existing = entries[entry.id]
            check(existing == null || existing == entry) { "Synthetic retry conflict" }
            entries[entry.id] = entry
        }

        override fun load(limit: Int): SessionHistorySnapshot = SessionHistorySnapshot(
            entries = entries.values.take(limit),
            totalCount = entries.size,
            totalDurationMilliseconds = entries.values.sumOf { it.durationMilliseconds },
            interruptedCount = entries.values.count { it.interrupted },
            hasMore = entries.size > limit,
        )

        override fun loadTimeline(range: HistoryDateRange): SessionHistoryTimelineSnapshot {
            val intervals = entries.values.map {
                HistoryInterval(it.startedAtEpochMilliseconds, it.endedAtEpochMilliseconds, it.durationMilliseconds, it.interrupted)
            }
            val days = HistoryDayAccumulator(range).apply { intervals.forEach(::add) }.summaries()
            return SessionHistoryTimelineSnapshot(range, days, intervals.size,
                intervals.sumOf { it.durationMilliseconds }, intervals.count { it.interrupted }, intervals.count { it.timingUncertain })
        }

        override fun clear() {
            check(!failClear) { "Synthetic deletion failure" }
            entries.clear()
        }
    }

    private class FailingCommitContext(context: Context) : ContextWrapper(context) {
        var failNextCommit = false

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val storage = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by storage {
                override fun edit(): SharedPreferences.Editor =
                    FailingEditor(storage.edit()) {
                        failNextCommit.also { failNextCommit = false }
                    }
            }
        }
    }

    private class FailingEditor(
        private val editor: SharedPreferences.Editor,
        private val shouldFail: () -> Boolean,
    ) : SharedPreferences.Editor by editor {
        // Preserve this wrapper through every fluent call, not only a bare edit().commit().
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { editor.putString(key, value) }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { editor.putStringSet(key, values) }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { editor.putInt(key, value) }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { editor.putLong(key, value) }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { editor.putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { editor.putBoolean(key, value) }
        override fun remove(key: String): SharedPreferences.Editor = apply { editor.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { editor.clear() }

        override fun commit(): Boolean {
            if (!shouldFail()) return editor.commit()
            // Android may expose the changed map despite commit(false). Intentionally apply
            // the mutation before returning false; tests must observe an explicit rollback.
            editor.apply()
            return false
        }
    }

    companion object {
        private val TARGETS = setOf("synthetic.first.target", "synthetic.second.target")
    }
}
