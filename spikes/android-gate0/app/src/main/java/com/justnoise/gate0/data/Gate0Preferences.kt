package com.justnoise.gate0.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.justnoise.gate0.domain.PortableScheduleTimingRules
import com.justnoise.gate0.domain.ScheduleDeliveryDecision
import com.justnoise.gate0.domain.ScheduleDeliveryRules
import com.justnoise.gate0.domain.SessionHistoryTiming
import com.justnoise.gate0.domain.SessionStateWriter
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class StoredGate0State(
    val acceptedDisclosureVersion: String?,
    val acceptedDisclosureEpochMilliseconds: Long,
    val selectedPackages: Set<String>,
    val activePackages: Set<String>,
    val zapDigest: String?,
    val desiredActive: Boolean,
    val activeSinceEpochMilliseconds: Long,
    val sessionRevision: Long,
    val recoveryRequired: Boolean,
    val serviceConnected: Boolean,
    val serviceHeartbeatElapsedMilliseconds: Long,
    val activeSessionId: String? = null,
    val activeScheduleId: String? = null,
)

data class ScheduleDispatchResult(
    val outcome: ScheduleOutcome? = null,
    val persistenceFailed: Boolean = false,
) {
    val started: Boolean get() = outcome?.code == ScheduleOutcomeCode.STARTED
}

class Gate0Preferences(
    context: Context,
    private val historyStorage: SessionHistoryStorage = SqliteSessionHistoryStorage(context),
) : SessionStateWriter {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    /** Short native arm/revoke section only; callers must never wait for a main-thread callback here. */
    fun <T> withScheduleLock(action: () -> T): T = synchronized(STATE_LOCK, action)

    fun snapshot(): StoredGate0State = synchronized(STATE_LOCK) {
        val selectedPackages = readSelectedPackages()
        val desiredActive = preferences.getBoolean(KEY_DESIRED_ACTIVE, false)
        StoredGate0State(
            acceptedDisclosureVersion = preferences.getString(KEY_DISCLOSURE_VERSION, null),
            acceptedDisclosureEpochMilliseconds = preferences.getLong(
                KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS,
                0L,
            ),
            selectedPackages = selectedPackages,
            activePackages = if (preferences.contains(KEY_ACTIVE_PACKAGES)) {
                preferences.getStringSet(KEY_ACTIVE_PACKAGES, emptySet()).orEmpty().toSet()
            } else if (desiredActive && !preferences.contains(KEY_SELECTED_PACKAGES)) {
                // Upgrade an existing single-target session without discarding its intent.
                selectedPackages
            } else {
                emptySet()
            },
            zapDigest = preferences.getString(KEY_ZAP_DIGEST, null),
            desiredActive = desiredActive,
            activeSinceEpochMilliseconds = preferences.getLong(KEY_ACTIVE_SINCE_EPOCH_MILLIS, 0L),
            sessionRevision = preferences.getLong(KEY_SESSION_REVISION, 0L),
            recoveryRequired = preferences.getBoolean(KEY_RECOVERY_REQUIRED, false),
            serviceConnected = preferences.getBoolean(KEY_SERVICE_CONNECTED, false),
            serviceHeartbeatElapsedMilliseconds = preferences.getLong(
                KEY_SERVICE_HEARTBEAT_ELAPSED_MILLIS,
                0L,
            ),
            activeSessionId = preferences.getString(KEY_ACTIVE_SESSION_ID, null),
            activeScheduleId = preferences.getString(KEY_ACTIVE_SCHEDULE_ID, null),
        )
    }

    fun acceptDisclosure(
        version: String,
        epochMilliseconds: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(STATE_LOCK) {
        if (!finishPendingDeletion()) return false
        preferences.edit()
            .putString(KEY_DISCLOSURE_VERSION, version)
            .putLong(KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS, epochMilliseconds)
            .commit()
    }

    fun setSelectedPackages(packageNames: Set<String>): Boolean = synchronized(STATE_LOCK) {
        if (packageNames.any(String::isBlank)) return false
        val selection = packageNames.toSet()
        if (readSelectedPackages() == selection) return true
        // A target edit must never end a session or alter its enforced snapshot.
        if (preferences.getBoolean(KEY_DESIRED_ACTIVE, false)) return false

        preferences.edit()
            .putStringSet(KEY_SELECTED_PACKAGES, selection)
            .remove(KEY_SELECTED_PACKAGE)
            .putLong(KEY_SESSION_REVISION, nextSessionRevision())
            .commit()
    }

    fun setZapDigest(encodedDigest: String): Boolean = synchronized(STATE_LOCK) {
        preferences.edit().putString(KEY_ZAP_DIGEST, encodedDigest).commit()
    }

    fun clearZapDigest(): Boolean = synchronized(STATE_LOCK) {
        preferences.edit().remove(KEY_ZAP_DIGEST).commit()
    }

    override fun startSession(epochMilliseconds: Long): Boolean = synchronized(STATE_LOCK) {
        if (!confirmFailedWrite() || !finishPendingDeletion()) return false
        if (preferences.getBoolean(KEY_DESIRED_ACTIVE, false) || epochMilliseconds < 0L) return false
        // Never overwrite a completed record whose durable delivery is still pending.
        if (!drainPendingHistory()) return false
        val selection = readSelectedPackages()
        if (selection.isEmpty()) return false
        commitState(sessionStartEditor(epochMilliseconds, UUID.randomUUID().toString()))
    }

    private fun sessionStartEditor(epochMilliseconds: Long, sessionId: String): SharedPreferences.Editor {
        val selection = readSelectedPackages()
        return preferences.edit()
            .putStringSet(KEY_SELECTED_PACKAGES, selection)
            .remove(KEY_SELECTED_PACKAGE)
            .putStringSet(KEY_ACTIVE_PACKAGES, selection)
            .putString(KEY_ACTIVE_SESSION_ID, sessionId)
            .remove(KEY_ACTIVE_SCHEDULE_ID)
            .putLong(KEY_ACTIVE_SINCE_ELAPSED, SystemClock.elapsedRealtime())
            .putString(KEY_ACTIVE_PROCESS, PROCESS_ID)
            .putBoolean(KEY_HISTORY_ELIGIBLE, hasHistoryConsent())
            .remove(KEY_HISTORY_INTERRUPTED)
            .putBoolean(KEY_DESIRED_ACTIVE, true)
            .putLong(KEY_ACTIVE_SINCE_EPOCH_MILLIS, epochMilliseconds)
            .putLong(KEY_SESSION_REVISION, nextSessionRevision())
            .remove(KEY_RECOVERY_REQUIRED)
    }

    override fun stopSession(epochMilliseconds: Long, expectedSessionId: String?): Boolean = synchronized(STATE_LOCK) {
        if (!confirmFailedWrite()) return false
        val stored = snapshot()
        if (!stored.desiredActive) {
            return expectedSessionId == null ||
                preferences.getString(KEY_LAST_FINISHED_ID, null) == expectedSessionId
        }
        if (expectedSessionId != null && stored.activeSessionId != expectedSessionId) return false
        // A v1/v2 session has no History consent at its start: stop it, but never backfill it.
        val entry = if (preferences.getBoolean(KEY_HISTORY_ELIGIBLE, false) &&
            stored.activeSessionId != null && stored.activePackages.isNotEmpty()
        ) {
            val sameProcess = preferences.getString(KEY_ACTIVE_PROCESS, null) == PROCESS_ID
            val nowElapsed = SystemClock.elapsedRealtime()
            val startElapsed = preferences.getLong(KEY_ACTIVE_SINCE_ELAPSED, -1L)
            val continuousClock = sameProcess && startElapsed >= 0L && nowElapsed >= startElapsed
            val heartbeatAge = nowElapsed - stored.serviceHeartbeatElapsedMilliseconds
            SessionHistoryEntry(
                stored.activeSessionId,
                stored.activeSinceEpochMilliseconds.coerceAtLeast(0L),
                epochMilliseconds.coerceAtLeast(0L),
                SessionHistoryTiming.durationMilliseconds(
                    stored.activeSinceEpochMilliseconds, epochMilliseconds,
                    startElapsed, nowElapsed, sameProcess,
                ),
                stored.activePackages.size,
                interrupted = !continuousClock || stored.recoveryRequired || !stored.serviceConnected ||
                    preferences.getBoolean(KEY_HISTORY_INTERRUPTED, false) ||
                    heartbeatAge !in 0..HEARTBEAT_STALE_AFTER_MILLISECONDS,
            )
        } else null
        if (preferences.contains(KEY_PENDING_HISTORY)) return false
        val editor = preferences.edit()
            .putBoolean(KEY_DESIRED_ACTIVE, false)
            .remove(KEY_ACTIVE_PACKAGES)
            .remove(KEY_ACTIVE_SINCE_EPOCH_MILLIS)
            .remove(KEY_ACTIVE_SESSION_ID)
            .remove(KEY_ACTIVE_SCHEDULE_ID)
            .remove(KEY_ACTIVE_SINCE_ELAPSED)
            .remove(KEY_ACTIVE_PROCESS)
            .remove(KEY_HISTORY_ELIGIBLE)
            .remove(KEY_HISTORY_INTERRUPTED)
            .putString(KEY_LAST_FINISHED_ID, stored.activeSessionId)
            .putLong(KEY_SESSION_REVISION, nextSessionRevision())
            .remove(KEY_RECOVERY_REQUIRED)
        if (entry != null) editor.putString(KEY_PENDING_HISTORY, entry.encode())
        // This is the commit point: cleared active state and completion travel together.
        // Database delivery is deferred; a DB problem must never undo a successful stop.
        commitState(editor)
    }

    override fun markRecoveryRequired() = synchronized(STATE_LOCK) {
        // apply() updates the process-local map immediately even when a preceding durable
        // commit failed. Both UI and service must fail closed for the rest of this process.
        preferences.edit().putBoolean(KEY_RECOVERY_REQUIRED, true)
            .also { if (snapshot().desiredActive) it.putBoolean(KEY_HISTORY_INTERRUPTED, true) }
            .apply()
    }

    fun markHistoryInterrupted() = synchronized(STATE_LOCK) {
        if (preferences.getBoolean(KEY_DESIRED_ACTIVE, false) &&
            !preferences.getBoolean(KEY_HISTORY_INTERRUPTED, false)
        ) {
            preferences.edit().putBoolean(KEY_HISTORY_INTERRUPTED, true).apply()
        }
    }

    fun updateServiceHeartbeat(elapsedMilliseconds: Long): Boolean = synchronized(STATE_LOCK) {
        val previous = preferences.getLong(KEY_SERVICE_HEARTBEAT_ELAPSED_MILLIS, 0L)
        if (elapsedMilliseconds - previous !in 0..HEARTBEAT_STALE_AFTER_MILLISECONDS) {
            markHistoryInterrupted()
        }
        preferences.edit()
            .putBoolean(KEY_SERVICE_CONNECTED, true)
            .putLong(KEY_SERVICE_HEARTBEAT_ELAPSED_MILLIS, elapsedMilliseconds)
            .commit()
    }

    fun markServiceDisconnected(): Boolean = synchronized(STATE_LOCK) {
        markHistoryInterrupted()
        preferences.edit()
            .remove(KEY_SERVICE_CONNECTED)
            .remove(KEY_SERVICE_HEARTBEAT_ELAPSED_MILLIS)
            .commit()
    }

    fun loadHistory(limit: Int = 100): SessionHistorySnapshot = synchronized(STATE_LOCK) {
        try {
            if (!confirmFailedWrite() || !finishPendingDeletion() || !drainPendingHistory()) {
                return SessionHistorySnapshot(error = true)
            }
            historyStorage.load(limit.coerceAtLeast(1))
        } catch (_: Exception) {
            SessionHistorySnapshot(error = true)
        }
    }

    fun loadSchedules(): ScheduleSnapshot = synchronized(STATE_LOCK) {
        try {
            if (!confirmFailedWrite() || !finishPendingDeletion()) {
                return ScheduleSnapshot(error = true)
            }
            ScheduleSnapshot(schedules = readSchedules())
        } catch (_: Exception) {
            // Corrupt or newer storage is not an empty list and must not be overwritten.
            ScheduleSnapshot(error = true)
        }
    }

    fun isScheduleSelectionResolvable(modeReference: String): Boolean = synchronized(STATE_LOCK) {
        modeReference == DEFAULT_SCHEDULE_MODE_REFERENCE &&
            (preferences.contains(KEY_SELECTED_PACKAGES) || preferences.contains(KEY_SELECTED_PACKAGE))
    }

    fun loadScheduleExecutions(): ScheduleExecutionSnapshot = synchronized(STATE_LOCK) {
        try {
            if (!confirmFailedWrite() || !finishPendingDeletion()) {
                return ScheduleExecutionSnapshot(error = true)
            }
            ScheduleExecutionSnapshot(readScheduleExecutions())
        } catch (_: Exception) {
            ScheduleExecutionSnapshot(error = true)
        }
    }

    fun areSchedulesSuspended(): Boolean = synchronized(STATE_LOCK) {
        preferences.getBoolean(KEY_SCHEDULE_SUSPENDED, false)
    }

    fun suspendSchedules(): Boolean = synchronized(STATE_LOCK) {
        if (!preferences.contains(KEY_SCHEDULES) && !preferences.contains(KEY_DISCLOSURE_VERSION)) return true
        commitState(preferences.edit().putBoolean(KEY_SCHEDULE_SUSPENDED, true))
    }

    fun scheduleRecoveryAcknowledgedAt(): Long = synchronized(STATE_LOCK) {
        preferences.getLong(KEY_SCHEDULE_RECOVERY_ACKNOWLEDGED, 0L)
    }

    fun acknowledgeScheduleRecovery(
        epochMilliseconds: Long,
        zoneId: String = ZoneId.systemDefault().id,
    ): Boolean = synchronized(STATE_LOCK) {
        try {
            if (epochMilliseconds !in 0L..PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS ||
                zoneId !in ZoneId.getAvailableZoneIds() + "UTC"
            ) return false
            if (!confirmFailedWrite() || !finishPendingDeletion() || !hasHistoryConsent()) return false
            val schedules = readSchedules().associateBy { it.id }
            val today = Instant.ofEpochMilli(epochMilliseconds).atZone(ZoneId.of(zoneId)).toLocalDate()
            val executions = readScheduleExecutions().map { execution ->
                val schedule = schedules[execution.scheduleId] ?: return@map execution
                if (schedule.timing.repeatWeekdays.isEmpty()) return@map execution
                val firstToday = PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds(
                    today.toString(), requireNotNull(schedule.timing.hour), requireNotNull(schedule.timing.minute),
                    zoneId, 0L,
                )
                // Cover the whole stopped interval, not merely its oldest pending day.
                // This also prevents westward travel from replaying an intervening day.
                val through = (if (firstToday == null || firstToday <= epochMilliseconds) today else today.minusDays(1L))
                    .toString()
                val highWater = maxOf(execution.lastResolvedLocalDate ?: through, through)
                execution.copy(
                    lastResolvedLocalDate = highWater,
                    pending = execution.pending?.takeUnless { it.localDate != null && it.localDate <= highWater },
                )
            }
            commitState(preferences.edit().remove(KEY_SCHEDULE_SUSPENDED)
                .putLong(KEY_SCHEDULE_RECOVERY_ACKNOWLEDGED, epochMilliseconds)
                .putString(KEY_SCHEDULE_EXECUTIONS, ScheduleExecutionCodec.encode(executions)))
        } catch (_: Exception) {
            false
        }
    }

    fun reconcileSchedulePlan(now: Long, zoneId: String): ScheduleExecutionSnapshot = synchronized(STATE_LOCK) {
        try {
            if (!confirmFailedWrite() || !finishPendingDeletion()) {
                return ScheduleExecutionSnapshot(error = true)
            }
            val previous = readScheduleExecutions()
            val updated = SchedulePlanner.reconcile(readSchedules(), previous, now, zoneId)
            if (updated != previous && !commitState(preferences.edit().putString(
                    KEY_SCHEDULE_EXECUTIONS, ScheduleExecutionCodec.encode(updated),
                ))
            ) return ScheduleExecutionSnapshot(error = true)
            ScheduleExecutionSnapshot(updated)
        } catch (_: Exception) {
            ScheduleExecutionSnapshot(error = true)
        }
    }

    /** Validate the exact stored candidate and commit its receipt with the session, never a toggle. */
    fun dispatchSchedule(
        occurrence: ScheduledOccurrence,
        now: Long? = null,
        startBlocker: (StoredGate0State, StoredSchedule) -> ScheduleOutcomeCode?,
    ): ScheduleDispatchResult = synchronized(STATE_LOCK) {
        try {
            if (!confirmFailedWrite() || !finishPendingDeletion()) {
                return ScheduleDispatchResult(persistenceFailed = true)
            }
            val schedules = readSchedules()
            val schedule = schedules.find { it.id == occurrence.scheduleId } ?: return ScheduleDispatchResult()
            val executions = readScheduleExecutions()
            val execution = executions.find { it.scheduleId == schedule.id } ?: return ScheduleDispatchResult()
            if (execution.pending != occurrence || !SchedulePlanner.matches(schedule, execution, occurrence)) {
                return ScheduleDispatchResult()
            }
            // Production time is sampled inside the lock, after persistence recovery.
            // Explicit times are for deterministic tests and the fixed recovery cutoff.
            val dispatchNow = now ?: System.currentTimeMillis()
            val stored = snapshot()
            val decision = ScheduleDeliveryRules.evaluate(
                currentRevisionEnabled = true,
                alreadyHandled = false, // Exact pending identity + high-water guards above own this check.
                sessionRequested = stored.desiredActive,
                scheduledAtEpochMilliseconds = occurrence.epochMilliseconds,
                nowEpochMilliseconds = dispatchNow,
            )
            val code = when (decision) {
                ScheduleDeliveryDecision.MISSED -> ScheduleOutcomeCode.MISSED
                ScheduleDeliveryDecision.SKIP_ACTIVE -> ScheduleOutcomeCode.ACTIVE_SESSION
                ScheduleDeliveryDecision.START_CANDIDATE -> {
                    when {
                        !isScheduleSelectionResolvable(schedule.modeReference) -> ScheduleOutcomeCode.MODE_UNAVAILABLE
                        stored.selectedPackages.isEmpty() -> ScheduleOutcomeCode.EMPTY_SELECTION
                        else -> startBlocker(stored, schedule) ?: ScheduleOutcomeCode.STARTED
                    }
                }
                else -> return ScheduleDispatchResult()
            }
            if (code == ScheduleOutcomeCode.STARTED && !drainPendingHistory()) {
                markRecoveryRequired()
                return ScheduleDispatchResult(persistenceFailed = true)
            }
            val sessionId = if (code == ScheduleOutcomeCode.STARTED) UUID.randomUUID().toString() else null
            val outcome = ScheduleOutcome(code, occurrence, dispatchNow, sessionId)
            val resolved = execution.copy(
                pending = null,
                lastResolvedLocalDate = occurrence.localDate ?: execution.lastResolvedLocalDate,
                lastResolvedOneTimeEpochMilliseconds = if (occurrence.localDate == null) {
                    occurrence.epochMilliseconds
                } else execution.lastResolvedOneTimeEpochMilliseconds,
                lastOutcome = outcome,
            )
            val updatedSchedules = if (occurrence.localDate == null) {
                schedules.map { if (it.id == schedule.id) it.copy(enabled = false) else it }
            } else schedules
            val editor = if (sessionId == null) preferences.edit() else sessionStartEditor(dispatchNow, sessionId)
                .putString(KEY_ACTIVE_SCHEDULE_ID, schedule.id)
            val committed = commitState(editor
                .putString(KEY_SCHEDULES, ScheduleStoreCodec.encode(updatedSchedules))
                .putString(KEY_SCHEDULE_EXECUTIONS, ScheduleExecutionCodec.encode(
                    executions.map { if (it.scheduleId == schedule.id) resolved else it },
                )))
            if (committed) ScheduleDispatchResult(outcome) else ScheduleDispatchResult(persistenceFailed = true)
        } catch (_: Exception) {
            markRecoveryRequired()
            ScheduleDispatchResult(persistenceFailed = true)
        }
    }

    /** Configuration only: no alarm registration or session mutation occurs here. */
    fun saveSchedule(
        draft: ScheduleDraft,
        id: String? = null,
        expectedRevision: Long? = null,
        nowEpochMilliseconds: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(STATE_LOCK) {
        try {
            if (!confirmFailedWrite() || !finishPendingDeletion()) return false
            val schedules = readSchedules()
            val previousExecutions = readScheduleExecutions()
            val previous = schedules.find { it.id == id }
            if (id == null) {
                if (expectedRevision != null) return false
            } else if (previous == null || previous.revision != expectedRevision) {
                return false
            }
            if (previous?.revision == Long.MAX_VALUE) return false
            val updated = StoredSchedule(
                id = previous?.id ?: UUID.randomUUID().toString(),
                revision = (previous?.revision ?: 0L) + 1L,
                name = draft.name.trim(),
                enabled = draft.enabled,
                timing = draft.timing.copy(repeatWeekdays = draft.timing.repeatWeekdays.toSet()),
                creationZoneId = draft.creationZoneId,
                modeReference = previous?.modeReference ?: DEFAULT_SCHEDULE_MODE_REFERENCE,
            )
            val replayGuard = previousExecutions.find { it.scheduleId == updated.id }
            if (updated.enabled && updated.timing.oneTimeEpochMilliseconds?.let { instant ->
                    replayGuard?.lastResolvedOneTimeEpochMilliseconds?.let { instant <= it }
                } == true
            ) return false
            // Past one-time drafts may remain disabled, but cannot be armed silently.
            if (updated.enabled && PortableScheduleTimingRules.nextOccurrence(
                    updated.timing, nowEpochMilliseconds, updated.creationZoneId, replayGuard?.lastResolvedLocalDate,
                ) == null
            ) return false
            val replacement = if (previous == null) schedules + updated else
                schedules.map { if (it.id == previous.id) updated else it }
            val executions = previousExecutions.map {
                if (it.scheduleId == updated.id) it.copy(pending = null) else it
            }
            commitState(preferences.edit().putString(KEY_SCHEDULES, ScheduleStoreCodec.encode(replacement))
                .putString(KEY_SCHEDULE_EXECUTIONS, ScheduleExecutionCodec.encode(executions)))
        } catch (_: Exception) {
            false
        }
    }

    fun setScheduleEnabled(
        id: String,
        expectedRevision: Long,
        enabled: Boolean,
        nowEpochMilliseconds: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(STATE_LOCK) {
        try {
            if (!confirmFailedWrite() || !finishPendingDeletion()) return false
            val current = readSchedules().find { it.id == id } ?: return false
            if (current.revision != expectedRevision) return false
            if (current.enabled == enabled) return true
            saveSchedule(
                ScheduleDraft(current.name, enabled, current.timing, current.creationZoneId),
                id, expectedRevision, nowEpochMilliseconds,
            )
        } catch (_: Exception) {
            false
        }
    }

    fun deleteSchedule(id: String, expectedRevision: Long): Boolean = synchronized(STATE_LOCK) {
        try {
            if (!confirmFailedWrite() || !finishPendingDeletion()) return false
            val schedules = readSchedules()
            val current = schedules.find { it.id == id } ?: return false
            if (current.revision != expectedRevision) return false
            commitState(preferences.edit().putString(
                KEY_SCHEDULES, ScheduleStoreCodec.encode(schedules.filterNot { it.id == id }),
            ).putString(KEY_SCHEDULE_EXECUTIONS, ScheduleExecutionCodec.encode(
                readScheduleExecutions().filterNot { it.scheduleId == id },
            )))
        } catch (_: Exception) {
            false
        }
    }

    fun clearForWithdrawal(): Boolean = synchronized(STATE_LOCK) {
        // Revoke and discard outbox first. A crash cannot replay a record being deleted.
        val revoked = preferences.edit().clear().putBoolean(KEY_HISTORY_DELETION_PENDING, true).commit()
        unconfirmedWrite = !revoked
        val historyCleared = finishPendingDeletion()
        revoked && historyCleared
    }

    private fun hasHistoryConsent(): Boolean =
        preferences.getString(KEY_DISCLOSURE_VERSION, null) == CURRENT_DISCLOSURE_VERSION &&
            preferences.getLong(KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS, 0L) > 0L

    private fun finishPendingDeletion(): Boolean {
        if (!preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false)) return true
        // Recommit the revocation before touching SQLite (commit(false) changes memory too).
        if (!preferences.edit().putBoolean(KEY_HISTORY_DELETION_PENDING, true).commit()) return false
        return try {
            historyStorage.clear()
            commitState(preferences.edit().remove(KEY_HISTORY_DELETION_PENDING)).also {
                if (it) unconfirmedWrite = false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun drainPendingHistory(): Boolean {
        val pending = preferences.getString(KEY_PENDING_HISTORY, null) ?: return true
        return try {
            historyStorage.insert(SessionHistoryEntry.decode(pending))
            commitState(preferences.edit().remove(KEY_PENDING_HISTORY))
        } catch (_: Exception) {
            false
        }
    }

    private fun readSchedules(): List<StoredSchedule> =
        preferences.getString(KEY_SCHEDULES, null)?.let(ScheduleStoreCodec::decode).orEmpty()

    private fun readScheduleExecutions(): List<ScheduleExecution> =
        preferences.getString(KEY_SCHEDULE_EXECUTIONS, null)?.let(ScheduleExecutionCodec::decode).orEmpty()

    private fun confirmFailedWrite(): Boolean {
        if (!unconfirmedWrite) return true
        // Failed session writes were restored in memory; confirm that rollback durably
        // before any database delivery or new session can proceed.
        val confirmed = preferences.edit().putBoolean(KEY_RECOVERY_REQUIRED, true).commit()
        if (confirmed) unconfirmedWrite = false
        return confirmed
    }

    private fun commitState(editor: SharedPreferences.Editor): Boolean {
        val before = preferences.all
        if (editor.commit()) return true
        // SharedPreferences updates memory even when commit fails. Restore the old map
        // immediately, then quarantine database delivery until its durable confirmation.
        val restore = preferences.edit().clear()
        for ((key, value) in before) {
            when (value) {
                is String -> restore.putString(key, value)
                is Long -> restore.putLong(key, value)
                is Int -> restore.putInt(key, value)
                is Boolean -> restore.putBoolean(key, value)
                is Float -> restore.putFloat(key, value)
                is Set<*> -> restore.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        unconfirmedWrite = true
        restore.putBoolean(KEY_RECOVERY_REQUIRED, true).apply()
        return false
    }

    private fun nextSessionRevision(): Long =
        preferences.getLong(KEY_SESSION_REVISION, 0L) + 1L

    private fun readSelectedPackages(): Set<String> =
        if (preferences.contains(KEY_SELECTED_PACKAGES)) {
            // getStringSet exposes the preferences' collection; never return or mutate it.
            preferences.getStringSet(KEY_SELECTED_PACKAGES, emptySet()).orEmpty().toSet()
        } else {
            setOfNotNull(preferences.getString(KEY_SELECTED_PACKAGE, null)?.takeIf(String::isNotBlank))
        }

    companion object {
        const val CURRENT_DISCLOSURE_VERSION = "gate0-accessibility-v4"
        const val HEARTBEAT_STALE_AFTER_MILLISECONDS = 8_000L

        private val STATE_LOCK = Any()
        private val PROCESS_ID = UUID.randomUUID().toString()
        private var unconfirmedWrite = false

        private const val FILE_NAME = "gate0_local_state"
        private const val KEY_DISCLOSURE_VERSION = "accepted_disclosure_version"
        private const val KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS =
            "accepted_disclosure_epoch_millis"
        private const val KEY_SELECTED_PACKAGE = "selected_package"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_ACTIVE_PACKAGES = "active_packages"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        private const val KEY_ACTIVE_SCHEDULE_ID = "active_schedule_id"
        private const val KEY_ACTIVE_SINCE_ELAPSED = "active_since_elapsed_millis"
        private const val KEY_ACTIVE_PROCESS = "active_process_id"
        private const val KEY_HISTORY_ELIGIBLE = "history_eligible"
        private const val KEY_HISTORY_INTERRUPTED = "history_interrupted"
        private const val KEY_PENDING_HISTORY = "pending_session_history"
        private const val KEY_SCHEDULES = "schedules_v1"
        private const val KEY_SCHEDULE_EXECUTIONS = "schedule_executions_v1"
        private const val KEY_SCHEDULE_SUSPENDED = "schedule_suspended"
        private const val KEY_SCHEDULE_RECOVERY_ACKNOWLEDGED = "schedule_recovery_acknowledged_at"
        private const val KEY_LAST_FINISHED_ID = "last_finished_session_id"
        private const val KEY_HISTORY_DELETION_PENDING = "history_deletion_pending"
        private const val KEY_ZAP_DIGEST = "zap_hmac_digest"
        private const val KEY_DESIRED_ACTIVE = "desired_active"
        private const val KEY_ACTIVE_SINCE_EPOCH_MILLIS = "active_since_epoch_millis"
        private const val KEY_SESSION_REVISION = "session_revision"
        private const val KEY_RECOVERY_REQUIRED = "recovery_required"
        private const val KEY_SERVICE_CONNECTED = "service_connected"
        private const val KEY_SERVICE_HEARTBEAT_ELAPSED_MILLIS =
            "service_heartbeat_elapsed_millis"
    }
}
