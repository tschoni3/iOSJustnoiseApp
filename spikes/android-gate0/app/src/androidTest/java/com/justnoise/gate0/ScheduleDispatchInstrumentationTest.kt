package com.justnoise.gate0

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.ScheduleDispatchResult
import com.justnoise.gate0.data.ScheduleDraft
import com.justnoise.gate0.data.ScheduleExecutionCodec
import com.justnoise.gate0.data.ScheduleOutcomeCode
import com.justnoise.gate0.data.ScheduleStoreCodec
import com.justnoise.gate0.data.ScheduledOccurrence
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.data.StoredSchedule
import com.justnoise.gate0.domain.PortableScheduleTimingRules
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.domain.ScheduleDeliveryRules
import com.justnoise.gate0.domain.ScheduleTiming
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic, disposable emulator storage only: no UI, alarms, NFC or live permissions. */
@RunWith(AndroidJUnit4::class)
class ScheduleDispatchInstrumentationTest {
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences

    @Before
    fun prepare() {
        assumeTrue("Schedule storage tests require a disposable emulator",
            Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") ||
                Build.HARDWARE in setOf("ranchu", "goldfish"))
        context = ApplicationProvider.getApplicationContext()
        preferences = Gate0Preferences(context)
        assertTrue(preferences.clearForWithdrawal())
    }

    @After
    fun clean() {
        if (::context.isInitialized) assertTrue(Gate0Preferences(context).clearForWithdrawal())
    }

    @Test
    fun scheduledStartCommitsOneUuidTargetSnapshotReceiptAndOneTimeDisableTogether() {
        seedReadyConfiguration()
        val schedule = createOneTime()
        val candidate = pending(schedule)
        val before = preferences.snapshot()
        val result = dispatch(candidate)

        assertTrue(result.started)
        assertFalse(result.persistenceFailed)
        val outcome = requireNotNull(result.outcome)
        val id = requireNotNull(outcome.sessionId)
        val active = Gate0Preferences(context).snapshot()
        assertEquals(candidate, outcome.occurrence)
        assertEquals(candidate.epochMilliseconds, outcome.handledAtEpochMilliseconds)
        assertEquals(outcome.sessionId, active.activeSessionId)
        assertEquals(id, UUID.fromString(id).toString())
        assertTrue(active.desiredActive)
        assertEquals(TARGETS, active.activePackages)
        assertEquals(before.sessionRevision + 1L, active.sessionRevision)
        val execution = Gate0Preferences(context).loadScheduleExecutions().entries.single()
        assertNull(execution.pending)
        assertEquals(outcome, execution.lastOutcome)
        assertEquals(candidate.epochMilliseconds, execution.lastResolvedOneTimeEpochMilliseconds)
        assertFalse(preferences.loadSchedules().schedules.single().enabled)
        assertFalse(raw().contains("pending_session_history"))
        assertEquals(0, preferences.loadHistory().totalCount)
        assertFalse(preferences.setSelectedPackages(setOf("synthetic.changed")))
        assertEquals(TARGETS, preferences.snapshot().activePackages)
    }

    @Test
    fun productionDispatchSamplesTimeOnlyAfterAcquiringTheTransactionLock() {
        seedReadyConfiguration()
        val at = System.currentTimeMillis() - 1_000L
        assertTrue(preferences.saveSchedule(
            oneTimeDraft().copy(timing = ScheduleTiming(oneTimeEpochMilliseconds = at)),
            nowEpochMilliseconds = at - 1_000L,
        ))
        val candidate = pending(preferences.loadSchedules().schedules.single(), at - 1L)
        val finished = CountDownLatch(1)
        var result: ScheduleDispatchResult? = null
        val worker = Thread {
            try {
                result = preferences.dispatchSchedule(candidate) { _, _ -> null }
            } finally {
                finished.countDown()
            }
        }
        val releasedAt = preferences.withScheduleLock {
            worker.start()
            val until = SystemClock.elapsedRealtime() + 2_000L
            while (worker.state != Thread.State.BLOCKED && SystemClock.elapsedRealtime() < until) {
                SystemClock.sleep(1L)
            }
            assertEquals(Thread.State.BLOCKED, worker.state)
            SystemClock.sleep(25L)
            System.currentTimeMillis()
        }
        assertTrue(finished.await(5L, TimeUnit.SECONDS))
        assertTrue(requireNotNull(result).started)
        assertTrue(requireNotNull(result?.outcome).handledAtEpochMilliseconds >= releasedAt)
    }

    @Test
    fun fixedRecoveryCutoffDiscardsEarlierOneTimeAndPreservesWorkAfterExplicitEntry() {
        seedReadyConfiguration()
        val cutoff = NOW + 60_000L
        val before = pending(createOneTime())
        val after = pending(create(oneTimeDraft().copy(
            timing = ScheduleTiming(oneTimeEpochMilliseconds = cutoff + 1L),
        )))
        assertTrue(preferences.suspendSchedules())
        val discarded = preferences.dispatchSchedule(before, cutoff) { _, _ -> ScheduleOutcomeCode.RECOVERY_REQUIRED }
        assertEquals(ScheduleOutcomeCode.RECOVERY_REQUIRED, discarded.outcome?.code)
        assertTrue(preferences.acknowledgeScheduleRecovery(cutoff, "UTC"))
        assertEquals(cutoff, preferences.scheduleRecoveryAcknowledgedAt())
        assertEquals(after, preferences.loadScheduleExecutions().entries.single {
            it.scheduleId == after.scheduleId
        }.pending)
        // Processing may finish later; this occurrence belongs to the resumed interval.
        assertTrue(dispatch(after, cutoff + 100L).started)
        assertIgnored(before, cutoff + 100L)
        assertEquals(after.scheduleId, preferences.snapshot().activeScheduleId)
    }

    @Test
    fun duplicateCallbackCannotStartAgainAndStopCreatesHistoryWithTheSameUuidOnce() {
        seedReadyConfiguration()
        val candidate = pending(createOneTime())
        val started = dispatch(candidate)
        val id = requireNotNull(started.outcome?.sessionId)
        val active = preferences.snapshot()
        val execution = preferences.loadScheduleExecutions()

        assertIgnored(candidate, candidate.epochMilliseconds + 1L)
        assertEquals(active, preferences.snapshot())
        assertEquals(execution, preferences.loadScheduleExecutions())
        assertTrue(preferences.stopSession(candidate.epochMilliseconds + 3_000L, id))
        assertFalse(preferences.snapshot().desiredActive)
        assertTrue(raw().contains("pending_session_history"))
        assertTrue(preferences.stopSession(candidate.epochMilliseconds + 4_000L, id))
        val history = Gate0Preferences(context).loadHistory()
        assertFalse(history.error)
        assertEquals(1, history.totalCount)
        assertEquals(id, history.entries.single().id)
        assertEquals(2, history.entries.single().targetCount)
        assertTrue(history.entries.single().durationMilliseconds >= 0L)
        assertIgnored(candidate, candidate.epochMilliseconds + 5_000L)
        assertFalse(preferences.setScheduleEnabled(candidate.scheduleId, candidate.revision, true, NOW))
        assertEquals(1, preferences.loadHistory().totalCount)
        assertNull(preferences.snapshot().activeSessionId)
    }

    @Test
    fun manualAndRecoverySessionCollisionsPreserveIdentityTargetsRevisionAndPendingHistory() {
        seedReadyConfiguration()
        assertTrue(preferences.startSession(NOW))
        // Defensive synthetic case: a completion must not be drained by a skipped dispatch.
        val pendingHistory = SessionHistoryEntry(UUID.randomUUID().toString(), NOW - 60_000L,
            NOW - 10_000L, 50_000L, 2, false).encode()
        assertTrue(raw().edit().putString("pending_session_history", pendingHistory).commit())
        for (recovery in listOf(false, true)) {
            if (recovery) preferences.markRecoveryRequired()
            val candidate = pending(createOneTime())
            val before = preferences.snapshot()
            var calls = 0
            val result = preferences.dispatchSchedule(candidate, candidate.epochMilliseconds) { _, _ ->
                calls += 1
                null
            }
            assertEquals(ScheduleOutcomeCode.ACTIVE_SESSION, result.outcome?.code)
            assertFalse(result.started)
            assertFalse(result.persistenceFailed)
            assertNull(result.outcome?.sessionId)
            assertEquals(0, calls)
            assertEquals(before, preferences.snapshot())
            assertEquals(pendingHistory, raw().getString("pending_session_history", null))
            assertEquals(result.outcome, preferences.loadScheduleExecutions().entries
                .single { it.scheduleId == candidate.scheduleId }.lastOutcome)
        }
    }

    @Test
    fun earlyCallbackDoesNotConsumeTheCandidateOrCallStartValidation() {
        seedReadyConfiguration()
        val candidate = pending(createOneTime())
        val before = raw().all
        assertIgnored(candidate, candidate.epochMilliseconds - 1L)
        assertEquals(before, raw().all)
        assertTrue(dispatch(candidate).started)
    }

    @Test
    fun inclusiveGraceStartsButLaterDeliveryRecordsMissedWithoutCallingStartValidation() {
        seedReadyConfiguration()
        val within = pending(createOneTime())
        val result = dispatch(within, within.epochMilliseconds + ScheduleDeliveryRules.GRACE_MILLISECONDS)
        assertTrue(result.started)
        assertEquals(within.epochMilliseconds + ScheduleDeliveryRules.GRACE_MILLISECONDS,
            preferences.snapshot().activeSinceEpochMilliseconds)
        assertTrue(preferences.stopSession(within.epochMilliseconds + ScheduleDeliveryRules.GRACE_MILLISECONDS + 3_000L,
            result.outcome?.sessionId))
        val pendingHistory = raw().getString("pending_session_history", null)
        val late = pending(createOneTime())
        var calls = 0
        val missed = preferences.dispatchSchedule(late, late.epochMilliseconds + ScheduleDeliveryRules.GRACE_MILLISECONDS + 1L) { _, _ ->
            calls += 1
            null
        }
        assertEquals(ScheduleOutcomeCode.MISSED, missed.outcome?.code)
        assertFalse(missed.started)
        assertFalse(missed.persistenceFailed)
        assertEquals(0, calls)
        assertFalse(preferences.snapshot().desiredActive)
        assertEquals(pendingHistory, raw().getString("pending_session_history", null))
        assertFalse(preferences.loadSchedules().schedules.single { it.id == late.scheduleId }.enabled)
        assertNull(preferences.loadScheduleExecutions().entries.single { it.scheduleId == late.scheduleId }.pending)
    }

    @Test
    fun staleDisabledAndDeletedScheduleCallbacksNeverCallStartValidation() {
        seedReadyConfiguration()
        val schedule = createOneTime()
        val original = pending(schedule)
        assertTrue(preferences.saveSchedule(oneTimeDraft().copy(name = "Edited"), schedule.id, schedule.revision, NOW))
        assertIgnored(original)
        val edited = preferences.loadSchedules().schedules.single()
        val replacement = pending(edited)
        assertEquals(original.revision + 1L, replacement.revision)
        assertIgnored(original)
        assertTrue(preferences.setScheduleEnabled(edited.id, edited.revision, false, NOW))
        assertIgnored(replacement)
        val disabled = preferences.loadSchedules().schedules.single()
        assertTrue(preferences.deleteSchedule(disabled.id, disabled.revision))
        assertIgnored(original)
        assertIgnored(replacement)
        assertTrue(preferences.loadScheduleExecutions().entries.isEmpty())
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun alteredCallbackFieldsAndPersistedForgedInstantCannotAuthorizeAStart() {
        seedReadyConfiguration()
        val candidate = pending(createOneTime())
        val before = raw().all
        for (forged in listOf(
            candidate.copy(scheduleId = UUID.randomUUID().toString()), candidate.copy(revision = candidate.revision + 1L),
            candidate.copy(key = "oneTime:1"), candidate.copy(epochMilliseconds = candidate.epochMilliseconds + 1L),
            candidate.copy(localDate = "2026-09-03"), candidate.copy(zoneId = "Europe/Berlin"),
        )) assertIgnored(forged)
        assertEquals(before, raw().all)

        val later = candidate.epochMilliseconds + 1L
        val forged = candidate.copy(key = "oneTime:$later", epochMilliseconds = later)
        val execution = preferences.loadScheduleExecutions().entries.single().copy(pending = forged)
        assertTrue(raw().edit().putString("schedule_executions_v1", ScheduleExecutionCodec.encode(listOf(execution))).commit())
        val corruptSnapshot = raw().all
        assertIgnored(forged)
        assertEquals(corruptSnapshot, raw().all)
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun missingSelectionReferenceAndResolvedEmptySelectionHaveDifferentOutcomes() {
        val missing = pending(createOneTime())
        var calls = 0
        val unavailable = preferences.dispatchSchedule(missing, missing.epochMilliseconds) { _, _ -> calls += 1; null }
        assertEquals(ScheduleOutcomeCode.MODE_UNAVAILABLE, unavailable.outcome?.code)
        // Persist an explicit empty selection, not the absent legacy/default reference.
        assertTrue(preferences.setSelectedPackages(TARGETS))
        assertTrue(preferences.setSelectedPackages(emptySet()))
        val empty = pending(createOneTime())
        val noTargets = preferences.dispatchSchedule(empty, empty.epochMilliseconds) { _, _ -> calls += 1; null }
        assertEquals(ScheduleOutcomeCode.EMPTY_SELECTION, noTargets.outcome?.code)
        assertEquals(0, calls)
        assertFalse(preferences.snapshot().desiredActive)
        assertFalse(unavailable.persistenceFailed)
        assertFalse(noTargets.persistenceFailed)

        assertTrue(preferences.setSelectedPackages(TARGETS))
        val unknown = pending(createOneTime())
        val schedules = preferences.loadSchedules().schedules.map {
            if (it.id == unknown.scheduleId) it.copy(modeReference = "synthetic-missing-mode") else it
        }
        assertTrue(raw().edit().putString("schedules_v1", ScheduleStoreCodec.encode(schedules)).commit())
        val unknownMode = preferences.dispatchSchedule(unknown, unknown.epochMilliseconds) { _, _ -> calls += 1; null }
        assertEquals(ScheduleOutcomeCode.MODE_UNAVAILABLE, unknownMode.outcome?.code)
        assertEquals(0, calls)
        assertFalse(unknownMode.started)
        assertFalse(unknownMode.persistenceFailed)
    }

    @Test
    fun capabilityConsentZapAndRecoveryFailuresRecordNoSessionAndConsumeOneTimeOnce() {
        seedReadyConfiguration()
        for (code in listOf(ScheduleOutcomeCode.TARGET_UNAVAILABLE, ScheduleOutcomeCode.CONSENT_REQUIRED,
            ScheduleOutcomeCode.ZAP_REQUIRED, ScheduleOutcomeCode.CAPABILITY_UNAVAILABLE,
            ScheduleOutcomeCode.EXACT_ALARM_REQUIRED, ScheduleOutcomeCode.RECOVERY_REQUIRED)) {
            val candidate = pending(createOneTime())
            val before = preferences.snapshot()
            val result = preferences.dispatchSchedule(candidate, candidate.epochMilliseconds) { state, schedule ->
                assertEquals(before, state)
                assertEquals(candidate.scheduleId, schedule.id)
                code
            }
            assertEquals(code, result.outcome?.code)
            assertFalse(result.persistenceFailed)
            assertFalse(result.started)
            assertNull(result.outcome?.sessionId)
            assertEquals(before, preferences.snapshot())
            assertIgnored(candidate)
            assertFalse(preferences.loadSchedules().schedules.single { it.id == candidate.scheduleId }.enabled)
        }
    }

    @Test
    fun repeatingResolutionArmsOnlyFutureDatesAndClockRollbackCannotReplayTheResolvedDay() {
        seedReadyConfiguration()
        val schedule = createRepeating()
        val first = pending(schedule)
        assertEquals("2026-09-03", first.localDate)
        assertTrue(dispatch(first).started)
        val next = pending(schedule, first.epochMilliseconds)
        assertEquals("2026-09-04", next.localDate)
        assertTrue(next.epochMilliseconds > first.epochMilliseconds)
        val rolledBack = pending(schedule, NOW - DAY)
        assertEquals(next, rolledBack)
        assertIgnored(first)
        assertEquals(first.localDate, preferences.loadScheduleExecutions().entries.single().lastResolvedLocalDate)
        assertTrue(preferences.loadSchedules().schedules.single().enabled)
    }

    @Test
    fun editsAcrossTimingKindsKeepBothReplayMarkersAndLatestOutcome() {
        seedReadyConfiguration()
        var schedule = createRepeating()
        val first = pending(schedule)
        val firstResult = preferences.dispatchSchedule(first, first.epochMilliseconds) { _, _ -> ScheduleOutcomeCode.CAPABILITY_UNAVAILABLE }
        assertEquals(ScheduleOutcomeCode.CAPABILITY_UNAVAILABLE, firstResult.outcome?.code)
        val oneTime = oneTimeDraft().copy(timing = ScheduleTiming(oneTimeEpochMilliseconds = NOW + 2L * DAY + 60_000L))
        assertTrue(preferences.saveSchedule(oneTime, schedule.id, schedule.revision, NOW))
        val afterEdit = preferences.loadScheduleExecutions().entries.single()
        assertEquals(first.localDate, afterEdit.lastResolvedLocalDate)
        assertEquals(firstResult.outcome, afterEdit.lastOutcome)
        assertNull(afterEdit.pending)
        schedule = preferences.loadSchedules().schedules.single()
        val once = pending(schedule)
        val onceResult = preferences.dispatchSchedule(once, once.epochMilliseconds) { _, _ -> ScheduleOutcomeCode.CAPABILITY_UNAVAILABLE }
        schedule = preferences.loadSchedules().schedules.single()
        assertTrue(preferences.saveSchedule(repeatingDraft(), schedule.id, schedule.revision, NOW))
        val preserved = preferences.loadScheduleExecutions().entries.single()
        assertEquals(first.localDate, preserved.lastResolvedLocalDate)
        assertEquals(once.epochMilliseconds, preserved.lastResolvedOneTimeEpochMilliseconds)
        assertEquals(onceResult.outcome, preserved.lastOutcome)
        val next = pending(preferences.loadSchedules().schedules.single(), NOW - DAY)
        assertEquals("2026-09-04", next.localDate)
        assertIgnored(first)
        assertIgnored(once)
    }

    @Test
    fun recoveryAfterTodaysStartCoversTheStoppedIntervalAndPreventsWestwardReplay() {
        seedReadyConfiguration()
        val septemberFirst = NOW - 2L * DAY
        val dailyNine = repeatingDraft().copy(timing = ScheduleTiming(
            repeatWeekdays = PortableWeekday.entries.toSet(), hour = 9, minute = 0,
        ))
        assertTrue(preferences.saveSchedule(dailyNine, nowEpochMilliseconds = septemberFirst))
        val schedule = preferences.loadSchedules().schedules.single()
        val oldPending = pending(schedule, septemberFirst)
        assertEquals("2026-09-01", oldPending.localDate)
        assertTrue(preferences.suspendSchedules())
        val recoveryTime = NOW + 10L * 3_600_000L // September 3, 10:00 UTC.
        var startValidationCalls = 0
        val oldResult = preferences.dispatchSchedule(oldPending, recoveryTime) { _, _ ->
            startValidationCalls += 1
            ScheduleOutcomeCode.RECOVERY_REQUIRED
        }
        assertEquals(ScheduleOutcomeCode.MISSED, oldResult.outcome?.code)
        assertFalse(oldResult.started)
        assertFalse(oldResult.persistenceFailed)
        assertEquals(0, startValidationCalls)
        assertEquals("2026-09-01", preferences.loadScheduleExecutions().entries.single().lastResolvedLocalDate)

        assertTrue(preferences.acknowledgeScheduleRecovery(recoveryTime, "UTC"))
        val recovered = preferences.loadScheduleExecutions().entries.single()
        assertEquals("2026-09-03", recovered.lastResolvedLocalDate)
        assertEquals(oldResult.outcome, recovered.lastOutcome)
        assertNull(recovered.pending)
        assertFalse(preferences.areSchedulesSuspended())
        assertEquals(recoveryTime, preferences.scheduleRecoveryAcknowledgedAt())

        // At 10:00 UTC New York is still before 09:00, but today's nominal start is resolved.
        val westward = preferences.reconcileSchedulePlan(recoveryTime, "America/New_York")
        assertFalse(westward.error)
        val next = requireNotNull(westward.entries.single().pending)
        assertEquals("2026-09-04", next.localDate)
        assertEquals(NOW + DAY + 13L * 3_600_000L, next.epochMilliseconds)
        for (rolledBackTime in listOf(NOW - DAY, NOW)) {
            val rollback = preferences.reconcileSchedulePlan(rolledBackTime, "UTC")
            assertFalse(rollback.error)
            assertEquals("2026-09-03", rollback.entries.single().lastResolvedLocalDate)
            assertEquals("2026-09-04", rollback.entries.single().pending?.localDate)
        }
        assertIgnored(oldPending, recoveryTime)
        assertFalse(preferences.snapshot().desiredActive)
        assertEquals(0, preferences.loadHistory().totalCount)
    }

    @Test
    fun recoveryBeforeTodaysStartPreservesItAndFailedOrRevokedAcknowledgmentCannotResume() {
        seedReadyConfiguration()
        val dailyNine = repeatingDraft().copy(timing = ScheduleTiming(
            repeatWeekdays = PortableWeekday.entries.toSet(), hour = 9, minute = 0,
        ))
        val todayPending = pending(create(dailyNine))
        assertEquals("2026-09-03", todayPending.localDate)
        assertEquals(NOW + 9L * 3_600_000L, todayPending.epochMilliseconds)
        assertTrue(preferences.suspendSchedules())
        val recoveryTime = NOW + 8L * 3_600_000L
        val originalExecutions = preferences.loadScheduleExecutions()
        val originalState = raw().all
        for (invalidTime in listOf(-1L, PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS + 1L)) {
            assertFalse(preferences.acknowledgeScheduleRecovery(invalidTime, "UTC"))
            assertEquals(originalState, raw().all)
        }

        val failingContext = FailingContext(context)
        val failing = Gate0Preferences(failingContext)
        failingContext.failNextCommit = true
        assertFalse(failing.acknowledgeScheduleRecovery(recoveryTime, "UTC"))
        assertFalse(failingContext.failNextCommit)
        assertTrue(preferences.areSchedulesSuspended())
        assertEquals(0L, preferences.scheduleRecoveryAcknowledgedAt())
        assertEquals(originalExecutions, failing.loadScheduleExecutions())
        assertFalse(preferences.snapshot().desiredActive)

        assertTrue(preferences.acknowledgeScheduleRecovery(recoveryTime, "UTC"))
        val recovered = preferences.loadScheduleExecutions().entries.single()
        assertEquals("2026-09-02", recovered.lastResolvedLocalDate)
        assertEquals(todayPending, recovered.pending)
        assertNull(recovered.lastOutcome)
        assertFalse(preferences.areSchedulesSuspended())
        assertEquals(todayPending, pending(preferences.loadSchedules().schedules.single(), recoveryTime))

        // Revoked current consent must neither clear suspension nor recreate absent execution state.
        assertTrue(preferences.suspendSchedules())
        assertTrue(raw().edit().remove("accepted_disclosure_version")
            .remove("accepted_disclosure_epoch_millis").remove("schedule_executions_v1").commit())
        val revokedState = raw().all
        assertFalse(preferences.acknowledgeScheduleRecovery(recoveryTime, "UTC"))
        assertEquals(revokedState, raw().all)
        assertTrue(preferences.areSchedulesSuspended())
        assertFalse(raw().contains("schedule_executions_v1"))
        assertTrue(preferences.clearForWithdrawal())
        val withdrawnState = raw().all
        assertFalse(preferences.acknowledgeScheduleRecovery(recoveryTime, "UTC"))
        assertEquals(withdrawnState, raw().all)
        assertFalse(raw().contains("schedule_executions_v1"))
        assertFalse(raw().contains("schedule_recovery_acknowledged_at"))
    }

    @Test
    fun failedAtomicStartCommitRestoresPendingReceiptConfigurationAndInactiveState() {
        seedReadyConfiguration()
        val schedule = createOneTime()
        val candidate = pending(schedule)
        val before = preferences.snapshot()
        val execution = preferences.loadScheduleExecutions()
        val failingContext = FailingContext(context)
        val failing = Gate0Preferences(failingContext)
        failingContext.failNextCommit = true

        val result = failing.dispatchSchedule(candidate, candidate.epochMilliseconds) { _, _ -> null }

        assertTrue(result.persistenceFailed)
        assertFalse(result.started)
        assertNull(result.outcome)
        assertFalse(failingContext.failNextCommit)
        assertEquals(before.copy(recoveryRequired = true), preferences.snapshot())
        assertEquals(execution, failing.loadScheduleExecutions())
        assertEquals(schedule, preferences.loadSchedules().schedules.single())
        assertNull(preferences.snapshot().activeSessionId)
        assertFalse(raw().contains("pending_session_history"))
        assertFalse(raw().contains("history_eligible"))
        assertEquals(0, preferences.loadHistory().totalCount)
        assertEquals(candidate, preferences.loadScheduleExecutions().entries.single().pending)
        assertNull(preferences.loadScheduleExecutions().entries.single().lastOutcome)
    }

    @Test
    fun failedPlanCommitCannotLeaveAnUnconfirmedPendingCandidate() {
        val schedule = createOneTime()
        val before = preferences.loadScheduleExecutions()
        val failingContext = FailingContext(context)
        val failing = Gate0Preferences(failingContext)
        failingContext.failNextCommit = true
        assertTrue(failing.reconcileSchedulePlan(NOW, "UTC").error)
        assertEquals(before, failing.loadScheduleExecutions())
        assertFalse(preferences.snapshot().desiredActive)
        val retried = failing.reconcileSchedulePlan(NOW, "UTC")
        assertFalse(retried.error)
        assertEquals(schedule.id, retried.entries.single().pending?.scheduleId)
        assertNull(retried.entries.single().lastOutcome)
    }

    @Test
    fun withdrawalClearsExecutionAndLateCallbacksCannotResurrectSessionsOrHistory() {
        seedReadyConfiguration()
        val original = pending(createOneTime())
        assertTrue(dispatch(original).started)
        assertTrue(preferences.clearForWithdrawal())
        val cleared = preferences.snapshot()
        assertFalse(cleared.desiredActive)
        assertNull(cleared.acceptedDisclosureVersion)
        assertNull(cleared.zapDigest)
        assertFalse(raw().contains("schedules_v1"))
        assertFalse(raw().contains("schedule_executions_v1"))
        assertIgnored(original, original.epochMilliseconds + 1L)
        assertTrue(preferences.reconcileSchedulePlan(original.epochMilliseconds + 1L, "UTC").entries.isEmpty())
        assertEquals(cleared, preferences.snapshot())
        assertEquals(0, preferences.loadHistory().totalCount)
        seedReadyConfiguration()
        val replacement = pending(createOneTime())
        assertNotEquals(original.scheduleId, replacement.scheduleId)
        assertIgnored(original)
        assertEquals(replacement, preferences.loadScheduleExecutions().entries.single().pending)
        assertFalse(preferences.snapshot().desiredActive)
    }

    private fun seedReadyConfiguration() {
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, NOW - 1L))
        assertTrue(preferences.setSelectedPackages(TARGETS))
        assertTrue(preferences.setZapDigest("synthetic-dispatch-test-digest"))
        // A null test callback substitutes readiness; it never enables a real service.
    }

    private fun createOneTime(): StoredSchedule = create(oneTimeDraft())
    private fun createRepeating(): StoredSchedule = create(repeatingDraft())
    private fun create(draft: ScheduleDraft): StoredSchedule {
        assertTrue(preferences.saveSchedule(draft, nowEpochMilliseconds = NOW))
        val snapshot = preferences.loadSchedules()
        assertFalse(snapshot.error)
        return snapshot.schedules.last()
    }

    private fun pending(schedule: StoredSchedule, now: Long = NOW): ScheduledOccurrence {
        val snapshot = preferences.reconcileSchedulePlan(now, "UTC")
        assertFalse(snapshot.error)
        val pending = snapshot.entries.single { it.scheduleId == schedule.id }.pending
        assertNotNull(pending)
        return requireNotNull(pending)
    }

    private fun dispatch(candidate: ScheduledOccurrence, now: Long = candidate.epochMilliseconds): ScheduleDispatchResult =
        preferences.dispatchSchedule(candidate, now) { _, _ -> null }

    private fun assertIgnored(candidate: ScheduledOccurrence, now: Long = candidate.epochMilliseconds) {
        var calls = 0
        val result = preferences.dispatchSchedule(candidate, now) { _, _ -> calls += 1; null }
        assertNull(result.outcome)
        assertFalse(result.started)
        assertFalse(result.persistenceFailed)
        assertEquals(0, calls)
    }

    private fun oneTimeDraft() = ScheduleDraft("Synthetic once", true,
        ScheduleTiming(oneTimeEpochMilliseconds = NOW + 60_000L), "UTC")

    private fun repeatingDraft() = ScheduleDraft("Synthetic repeat", true,
        ScheduleTiming(repeatWeekdays = PortableWeekday.entries.toSet(), hour = 0, minute = 1), "UTC")

    private fun raw(): SharedPreferences = context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)

    private class FailingContext(base: Context) : ContextWrapper(base) {
        var failNextCommit = false
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val storage = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by storage {
                override fun edit(): SharedPreferences.Editor = FailingEditor(storage.edit()) {
                    failNextCommit.also { failNextCommit = false }
                }
            }
        }
    }

    private class FailingEditor(
        private val editor: SharedPreferences.Editor,
        private val shouldFail: () -> Boolean,
    ) : SharedPreferences.Editor by editor {
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
            editor.apply() // Reproduce a visible in-memory change despite commit(false).
            return false
        }
    }

    private companion object {
        const val NOW = 1_788_393_600_000L // 2026-09-03T00:00:00Z
        const val DAY = 86_400_000L
        val TARGETS = setOf("synthetic.dispatch.first", "synthetic.dispatch.second")
    }
}
