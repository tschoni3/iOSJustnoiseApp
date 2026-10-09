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
    val activeSinceElapsedMilliseconds: Long = 0L,
    val activeClockMatchesProcess: Boolean = false,
    val dismissedNotificationSessionId: String? = null,
    val recentModeId: String? = null,
    val recentModeName: String? = null,
    val activeModeId: String? = null,
    val activeModeName: String? = null,
    val modeStorageError: Boolean = false,
)

data class ScheduleDispatchResult(
    val outcome: ScheduleOutcome? = null,
    val persistenceFailed: Boolean = false,
) {
    val started: Boolean get() = outcome?.code == ScheduleOutcomeCode.STARTED
}

class Gate0Preferences internal constructor(
    private val preferences: SharedPreferences,
    private val historyStorage: SessionHistoryStorage,
    private val startAccess: SessionStartAccess = SessionStartAccess(),
    private val elapsedRealtime: () -> Long,
) : SessionStateWriter {
    constructor(context: Context, historyStorage: SessionHistoryStorage = SqliteSessionHistoryStorage(context)) : this(
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE), historyStorage,
        (context.applicationContext as? SessionStartAccessHost)?.sessionStartAccess ?: SessionStartAccess(),
        SystemClock::elapsedRealtime,
    )
    private var cachedModeEncoding: String? = null
    private var cachedModes: ModeCatalog? = null

    /** Short native arm/revoke section only; callers must never wait for a main-thread callback here. */
    fun <T> withScheduleLock(action: () -> T): T = synchronized(STATE_LOCK, action)

    fun currentWorkspace(): LocalWorkspaceId = synchronized(STATE_LOCK) { readWorkspace() }

    /** Account worker closes this BEFORE journal changes; stop paths never consult it. */
    fun closeAccountSessionStarts() = synchronized(STATE_LOCK) {
        startAccess.authorizedOwner = null
        startAccess.resolved = !startAccess.accountRequired
    }

    fun denyAccountSessionStarts() = synchronized(STATE_LOCK) {
        startAccess.authorizedOwner = null
        startAccess.resolved = true
    }

    fun accountAllowsSessionStart(): Boolean = synchronized(STATE_LOCK) {
        runCatching { startAccess.allows(readWorkspace()) }.getOrDefault(false)
    }

    fun awaitingAccountStartRestoration(): Boolean = synchronized(STATE_LOCK) {
        startAccess.accountRequired && !startAccess.resolved &&
            !preferences.getBoolean(KEY_DESIRED_ACTIVE, false)
    }

    /** Publish only after durable auth and the exact product owner have reconciled. */
    fun authorizeAccountSessionStarts(owner: LocalWorkspaceId): Boolean = synchronized(STATE_LOCK) {
        if (owner == LocalWorkspaceId.LOCAL || readWorkspace() != owner ||
            isWorkspacePending() || unconfirmedWrite) false
        else { startAccess.authorizedOwner = owner; startAccess.resolved = true; true }
    }

    fun workspaceTransitionPending(): Boolean = synchronized(STATE_LOCK) { isWorkspacePending() }

    /** Called by the authenticated coordinator only; this method does not establish identity. */
    fun localDataImportOffer(owner: LocalWorkspaceId): LocalDataImportOffer = synchronized(STATE_LOCK) {
        try {
            if (owner == LocalWorkspaceId.LOCAL || owner != readWorkspace() || !hasHistoryConsent())
                return LocalDataImportOffer()
            val claimed = preferences.getString(KEY_LOCAL_IMPORT_OWNER, null)
            if (claimed != null) return LocalDataImportOffer(when {
                claimed != owner.value -> LocalDataImportStatus.UNAVAILABLE
                preferences.contains(KEY_LOCAL_IMPORT_PLAN) -> LocalDataImportStatus.PENDING
                else -> LocalDataImportStatus.COMPLETE
            })
            if (unconfirmedWrite || isWorkspacePending()) return LocalDataImportOffer(LocalDataImportStatus.ERROR)
            val source = preferences.getString(workspaceKey(LocalWorkspaceId.LOCAL), null)
                ?.let(WorkspaceProfile::decode) ?: WorkspaceProfile.empty()
            val history = historyStorage.forWorkspace(LocalWorkspaceId.LOCAL).load(1)
            check(!history.error)
            if (source.modes.modes.isEmpty() && source.schedules.isEmpty() && history.totalCount == 0)
                return LocalDataImportOffer()
            LocalDataImportOffer(LocalDataImportStatus.AVAILABLE, source.modes.modes.size,
                source.schedules.size, history.totalCount,
                preferences.getBoolean(KEY_LOCAL_IMPORT_DEFERRED, false),
                WorkspaceEditToken(owner, selectionEditToken()))
        } catch (_: Exception) { LocalDataImportOffer(LocalDataImportStatus.ERROR) }
    }

    fun deferLocalDataImport(expected: WorkspaceEditToken): Boolean = synchronized(STATE_LOCK) {
        if (readWorkspace() != expected.workspace || !matchesSelectionEditToken(expected.state) ||
            isWorkspacePending() || unconfirmedWrite || preferences.contains(KEY_LOCAL_IMPORT_OWNER)) false
        else commitState(preferences.edit().putBoolean(KEY_LOCAL_IMPORT_DEFERRED, true))
    }

    fun importLocalData(expected: WorkspaceEditToken): LocalDataImportResult = synchronized(STATE_LOCK) {
        try {
            if (readWorkspace() != expected.workspace || expected.workspace == LocalWorkspaceId.LOCAL ||
                !matchesSelectionEditToken(expected.state)) return LocalDataImportResult.CONFLICT
            if (preferences.getBoolean(KEY_DESIRED_ACTIVE, false)) return LocalDataImportResult.ACTIVE_SESSION
            if (unconfirmedWrite || isWorkspacePending() || preferences.getBoolean(KEY_RECOVERY_REQUIRED, false))
                return LocalDataImportResult.RECOVERY_REQUIRED
            if (!hasHistoryConsent() || preferences.contains(KEY_LOCAL_IMPORT_OWNER)) return LocalDataImportResult.UNAVAILABLE
            if (!drainPendingHistory()) return LocalDataImportResult.STORAGE_FAILED
            val source = preferences.getString(workspaceKey(LocalWorkspaceId.LOCAL), null)
                ?.let(WorkspaceProfile::decode) ?: WorkspaceProfile.empty()
            val destination = WorkspaceProfile(readModes(), readSchedules(), readScheduleExecutions())
            val merged = try { LocalDataImportMerge.merge(source, destination) }
                catch (_: IllegalArgumentException) { return LocalDataImportResult.CAPACITY }
            if (!historyStorage.forWorkspace(expected.workspace).canMergeFrom(historyStorage.forWorkspace(LocalWorkspaceId.LOCAL)))
                return LocalDataImportResult.HISTORY_CONFLICT
            // Claim and complete plan precede all cross-database work. A second account can
            // never claim these records, even after deletion of the original destination.
            if (!commitState(preferences.edit().putString(KEY_LOCAL_IMPORT_OWNER, expected.workspace.value)
                    .putString(KEY_LOCAL_IMPORT_PLAN, merged.encode()))) return LocalDataImportResult.STORAGE_FAILED
            if (resumeLocalDataImport(expected.workspace)) LocalDataImportResult.IMPORTED else LocalDataImportResult.STORAGE_FAILED
        } catch (_: Exception) { LocalDataImportResult.STORAGE_FAILED }
    }

    /** Retry only for the durable signed-in owner, before releasing product hydration. */
    fun resumeLocalDataImport(owner: LocalWorkspaceId): Boolean = synchronized(STATE_LOCK) {
        try {
            val encoded = preferences.getString(KEY_LOCAL_IMPORT_PLAN, null) ?: return true
            if (owner == LocalWorkspaceId.LOCAL || readWorkspace() != owner ||
                preferences.getString(KEY_LOCAL_IMPORT_OWNER, null) != owner.value ||
                preferences.getBoolean(KEY_DESIRED_ACTIVE, false) ||
                preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false) ||
                preferences.contains(KEY_ACCOUNT_ERASURE_PENDING) || !hasHistoryConsent() ||
                !confirmFailedWrite(allowWorkspacePending = true)) return false
            val merged = WorkspaceProfile.decode(encoded)
            val source = historyStorage.forWorkspace(LocalWorkspaceId.LOCAL)
            historyStorage.forWorkspace(owner).mergeFrom(source)
            // Destination transaction is durable. A failure from here on keeps the claim
            // and plan; retries accept identical History UUIDs, even after source clear.
            source.clear()
            if (!commitState(preferences.edit()
                    .putString(KEY_MODES, ModeStoreCodec.encode(merged.modes))
                    .putString(KEY_SCHEDULES, ScheduleStoreCodec.encode(merged.schedules))
                    .putString(KEY_SCHEDULE_EXECUTIONS, ScheduleExecutionCodec.encode(merged.executions))
                    .remove(workspaceKey(LocalWorkspaceId.LOCAL))
                    .remove(KEY_LOCAL_IMPORT_PLAN).remove(KEY_LOCAL_IMPORT_DEFERRED)
                    .putString(KEY_WORKSPACE_EPOCH, UUID.randomUUID().toString()))) return false
            selectionEditGeneration += 1L
            workspaceReadGeneration += 1L
            cachedModeEncoding = null; cachedModes = null
            true
        } catch (_: Exception) { false }
    }

    /** Stable across ordinary edits; account boundaries invalidate it. Consent has its own guard. */
    fun workspaceReadToken(): WorkspaceReadToken? = synchronized(STATE_LOCK) {
        if (unconfirmedWrite || isWorkspacePending() || preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false)) null
        else runCatching { WorkspaceReadToken(readWorkspace(), preferences.getString(KEY_WORKSPACE_EPOCH, null),
            PROCESS_ID, workspaceReadGeneration) }.getOrNull()
    }

    fun isWorkspaceCurrent(expected: WorkspaceReadToken?): Boolean = synchronized(STATE_LOCK) {
        expected != null && expected == workspaceReadToken()
    }

    fun prepareWorkspaceSwitch(): WorkspaceEditToken? = synchronized(STATE_LOCK) {
        if (unconfirmedWrite || isWorkspacePending() || preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false) ||
            preferences.getBoolean(KEY_DESIRED_ACTIVE, false) || preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)
        ) null else WorkspaceEditToken(readWorkspace(), selectionEditToken())
    }

    /** Native account coordinator only. No remote identity or provider callback is trusted here. */
    fun switchWorkspace(expected: WorkspaceEditToken, target: LocalWorkspaceId, forceHandoff: Boolean = false): WorkspaceSwitchResult = synchronized(STATE_LOCK) {
        try {
            if (unconfirmedWrite || preferences.contains(KEY_LOCAL_IMPORT_PLAN) || preferences.contains(KEY_ACCOUNT_ERASURE_PENDING) ||
                preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false))
                return WorkspaceSwitchResult.STORAGE_FAILED
            if (preferences.getBoolean(KEY_DESIRED_ACTIVE, false)) return WorkspaceSwitchResult.ACTIVE_SESSION
            if (preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)) return WorkspaceSwitchResult.RECOVERY_REQUIRED
            if (readWorkspace() != expected.workspace || !matchesSelectionEditToken(expected.state)) return WorkspaceSwitchResult.CONFLICT
            if (target == expected.workspace && !forceHandoff) return WorkspaceSwitchResult.UNCHANGED
            // A startup coordinator may retarget an unfinished handoff to the durable auth
            // owner without ever opening the wrong workspace. Read only for archival here.
            val outgoing = WorkspaceProfile(
                preferences.getString(KEY_MODES, null)?.let(ModeStoreCodec::decode)
                    ?: ModeRules.migrateLegacy(if (preferences.contains(KEY_SELECTED_PACKAGE) ||
                        preferences.contains(KEY_SELECTED_PACKAGES)) readLegacySelectedPackages() else null),
                preferences.getString(KEY_SCHEDULES, null)?.let(ScheduleStoreCodec::decode).orEmpty(),
                readScheduleExecutions(),
            )
            val incoming = if (target == expected.workspace) outgoing else
                preferences.getString(workspaceKey(target), null)?.let(WorkspaceProfile::decode) ?: WorkspaceProfile.empty()
            historyStorage.forWorkspace(target) // Reject an adapter that cannot isolate the destination.
            // Complete old-account delivery before the current History destination changes.
            if (!drainPendingHistory()) return WorkspaceSwitchResult.STORAGE_FAILED
            val editor = preferences.edit().putString(workspaceKey(expected.workspace), outgoing.encode())
            WORKSPACE_TRANSIENT_KEYS.forEach(editor::remove)
            editor.putString(KEY_MODES, ModeStoreCodec.encode(incoming.modes))
                .putString(KEY_SCHEDULES, ScheduleStoreCodec.encode(incoming.schedules))
                .putString(KEY_SCHEDULE_EXECUTIONS, ScheduleExecutionCodec.encode(incoming.executions))
                .putString(KEY_WORKSPACE, target.value)
                .putString(KEY_WORKSPACE_EPOCH, UUID.randomUUID().toString())
                .putBoolean(KEY_WORKSPACE_PENDING, true)
                .putBoolean(KEY_SCHEDULE_SUSPENDED, true)
                .putLong(KEY_SESSION_REVISION, Math.addExact(expected.state.sessionRevision, 1L))
            if (!commitState(editor)) return WorkspaceSwitchResult.STORAGE_FAILED
            selectionEditGeneration += 1L
            workspaceReadGeneration += 1L
            zapPairingGeneration += 1L
            dismissedNotificationSessionId = null
            cachedModeEncoding = null
            cachedModes = null
            WorkspaceSwitchResult.SWITCHED_PENDING
        } catch (_: Exception) { WorkspaceSwitchResult.STORAGE_FAILED }
    }

    /** Read by startup reconciliation; does not itself authorize releasing the handoff barrier. */
    fun pendingWorkspaceSwitch(): WorkspaceEditToken? = synchronized(STATE_LOCK) {
        if (!isWorkspacePending() || !confirmFailedWrite(allowWorkspacePending = true)) null
        else WorkspaceEditToken(readWorkspace(), selectionEditToken())
    }

    /** Call only after the durable auth outcome and analytics boundary match this exact owner. */
    fun completeWorkspaceSwitch(expected: WorkspaceEditToken): Boolean = synchronized(STATE_LOCK) {
        if (!isWorkspacePending() || unconfirmedWrite || preferences.contains(KEY_LOCAL_IMPORT_PLAN) || preferences.contains(KEY_ACCOUNT_ERASURE_PENDING) ||
            readWorkspace() != expected.workspace ||
            !matchesSelectionEditToken(expected.state) || preferences.getBoolean(KEY_DESIRED_ACTIVE, false)
        ) false else commitState(preferences.edit().remove(KEY_WORKSPACE_PENDING))
    }

    /**
     * Only after the auth journal confirms remote deletion. The coordinator first moves
     * to LOCAL with its handoff still closed; this method cannot erase an open workspace.
     * A durable owner marker permits retries without reviving the removed profile.
     */
    fun eraseAccountWorkspace(expected: WorkspaceEditToken, owner: LocalWorkspaceId): Boolean = synchronized(STATE_LOCK) {
        try {
            if (owner == LocalWorkspaceId.LOCAL || expected.workspace != LocalWorkspaceId.LOCAL ||
                readWorkspace() != LocalWorkspaceId.LOCAL || !isWorkspacePending() ||
                !matchesSelectionEditToken(expected.state) || preferences.getBoolean(KEY_DESIRED_ACTIVE, false) ||
                preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false)
            ) return false
            val pending = preferences.getString(KEY_ACCOUNT_ERASURE_PENDING, null)
            if (pending != null && LocalWorkspaceId.parse(pending) != owner) return false
            if (!confirmFailedWrite(allowWorkspacePending = true)) return false
            // Remove the profile and persist its cleanup owner together, BEFORE touching
            // SQLite. Neither a crash nor a failed disk write may reopen the deleted data.
            if (!commitState(preferences.edit().remove(workspaceKey(owner))
                    .putString(KEY_ACCOUNT_ERASURE_PENDING, owner.value))) return false
            historyStorage.forWorkspace(owner).eraseAccountData()
            commitState(preferences.edit().remove(KEY_ACCOUNT_ERASURE_PENDING))
        } catch (_: Exception) { false }
    }

    private fun readWorkspace() = LocalWorkspaceId.parse(preferences.getString(KEY_WORKSPACE, null) ?: "local")
    private fun workspaceKey(workspace: LocalWorkspaceId) = WORKSPACE_PROFILE_PREFIX + workspace.value
    private fun isWorkspacePending() = preferences.getBoolean(KEY_WORKSPACE_PENDING, false) || preferences.contains(KEY_LOCAL_IMPORT_PLAN)
    private fun workspaceHistory() = historyStorage.forWorkspace(readWorkspace())

    fun snapshot(): StoredGate0State = synchronized(STATE_LOCK) {
        val modes = runCatching { readModes() }
        val selectedPackages = modes.getOrNull()?.recentMode?.selectedPackages.orEmpty()
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
                readLegacySelectedPackages()
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
            activeSinceElapsedMilliseconds = preferences.getLong(KEY_ACTIVE_SINCE_ELAPSED, 0L),
            activeClockMatchesProcess = preferences.getString(KEY_ACTIVE_PROCESS, null) == PROCESS_ID,
            dismissedNotificationSessionId = dismissedNotificationSessionId
                ?.takeIf { it == preferences.getString(KEY_ACTIVE_SESSION_ID, null) }
                ?: preferences.getString(KEY_DISMISSED_NOTIFICATION_SESSION_ID, null),
            recentModeId = modes.getOrNull()?.recentModeId,
            recentModeName = modes.getOrNull()?.recentMode?.name,
            activeModeId = preferences.getString(KEY_ACTIVE_MODE_ID, null),
            activeModeName = preferences.getString(KEY_ACTIVE_MODE_NAME, null),
            modeStorageError = modes.isFailure,
        )
    }

    /** Local migration shares the session transaction lock and never rewrites History/schedules. */
    fun loadModes(): ModeSnapshot = synchronized(STATE_LOCK) {
        try {
            val active = preferences.getBoolean(KEY_DESIRED_ACTIVE, false)
            val recovery = preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)
            if (unconfirmedWrite || preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false)) {
                return ModeSnapshot(sessionRequested = active, recoveryRequired = recovery, error = true)
            }
            val catalog = readModes()
            // Freeze legacy data once, including an explicitly empty catalog. Never regenerate
            // defaults after deletion, and never replace unknown/corrupt versioned data.
            if (!preferences.contains(KEY_MODES) && catalog.modes.isNotEmpty() && !commitState(modeEditor(catalog))) {
                return ModeSnapshot(sessionRequested = active, recoveryRequired = true, error = true)
            }
            ModeSnapshot(catalog.modes, catalog.recentModeId, active, recovery,
                token = if (active || recovery) null else selectionEditToken())
        } catch (_: Exception) {
            ModeSnapshot(error = true)
        }
    }

    fun modeForReference(id: String): StoredMode? = synchronized(STATE_LOCK) {
        runCatching { readModes().modes.find { it.id == id } }.getOrNull()
    }

    fun saveMode(token: SelectionEditToken, id: String?, name: String, packages: Set<String>): ModeMutationResult =
        mutateModes(token) { catalog ->
            if (!ModeRules.validName(name)) return@mutateModes ModeMutationResult.INVALID_NAME to null
            if (!ModeRules.validTargets(packages)) return@mutateModes ModeMutationResult.INVALID_SELECTION to null
            val previous = catalog.modes.find { it.id == id }
            if (id != null && previous == null) return@mutateModes ModeMutationResult.MODE_UNAVAILABLE to null
            if (previous == null && catalog.modes.size >= ModeRules.MAX_MODES) {
                return@mutateModes ModeMutationResult.LIMIT_REACHED to null
            }
            if (previous?.name == name.trim() && previous.selectedPackages == packages) {
                return@mutateModes ModeMutationResult.UNCHANGED to null
            }
            val mode = StoredMode(previous?.id ?: UUID.randomUUID().toString(),
                Math.addExact(previous?.revision ?: 0L, 1L), name.trim(), packages.toSet())
            ModeMutationResult.SAVED to catalog.copy(modes = if (previous == null) catalog.modes + mode else
                catalog.modes.map { if (it.id == id) mode else it })
        }

    fun selectMode(token: SelectionEditToken, id: String): ModeMutationResult = mutateModes(token) { catalog ->
        val mode = catalog.modes.find { it.id == id }
            ?: return@mutateModes ModeMutationResult.MODE_UNAVAILABLE to null
        if (!ModeRules.validTargets(mode.selectedPackages)) return@mutateModes ModeMutationResult.INVALID_SELECTION to null
        if (catalog.recentModeId == id) ModeMutationResult.UNCHANGED to null else
            ModeMutationResult.SAVED to catalog.copy(recentModeId = id)
    }

    fun deleteMode(token: SelectionEditToken, id: String): ModeMutationResult = mutateModes(token) { catalog ->
        if (catalog.modes.none { it.id == id }) return@mutateModes ModeMutationResult.MODE_UNAVAILABLE to null
        if (!ModeRules.canDelete(id, readSchedules().mapTo(mutableSetOf()) { it.modeReference })) {
            return@mutateModes ModeMutationResult.REFERENCED_BY_SCHEDULE to null
        }
        ModeMutationResult.SAVED to catalog.copy(modes = catalog.modes.filterNot { it.id == id },
            recentModeId = catalog.recentModeId.takeUnless { it == id })
    }

    private fun mutateModes(
        token: SelectionEditToken,
        change: (ModeCatalog) -> Pair<ModeMutationResult, ModeCatalog?>,
    ): ModeMutationResult = synchronized(STATE_LOCK) {
        try {
            if (unconfirmedWrite || preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false)) {
                return ModeMutationResult.STORAGE_FAILED
            }
            if (preferences.getBoolean(KEY_DESIRED_ACTIVE, false)) return ModeMutationResult.ACTIVE_SESSION
            if (preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)) return ModeMutationResult.RECOVERY_REQUIRED
            if (!matchesSelectionEditToken(token)) return ModeMutationResult.CONFLICT
            val (result, updated) = change(readModes())
            if (updated == null) return result
            if (commitState(modeEditor(updated).putLong(KEY_SESSION_REVISION,
                    Math.addExact(token.sessionRevision, 1L)))) result else ModeMutationResult.STORAGE_FAILED
        } catch (_: Exception) {
            ModeMutationResult.STORAGE_FAILED
        }
    }

    /** Presentation only: a late dismissal cannot hide a newer session or change its revision. */
    fun dismissSessionNotification(sessionId: String): Boolean = synchronized(STATE_LOCK) {
        if (sessionId.isBlank() || !preferences.getBoolean(KEY_DESIRED_ACTIVE, false) ||
            preferences.getString(KEY_ACTIVE_SESSION_ID, null) != sessionId
        ) return false
        dismissedNotificationSessionId = sessionId
        try {
            // Best-effort durability, with the process marker as fallback. Optional presentation
            // must not quarantine the session engine if this preference cannot be written.
            preferences.edit().putString(KEY_DISMISSED_NOTIFICATION_SESSION_ID, sessionId).commit()
        } catch (_: RuntimeException) {
            // The in-process dismissal remains effective.
        }
        true
    }

    fun acceptDisclosure(
        version: String,
        epochMilliseconds: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(STATE_LOCK) {
        selectionEditGeneration += 1L
        if (!finishPendingDeletion()) return false
        preferences.edit()
            .putString(KEY_DISCLOSURE_VERSION, version)
            .putLong(KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS, epochMilliseconds)
            .commit()
    }

    fun hasCompletedOnboarding(): Boolean = synchronized(STATE_LOCK) {
        try {
            !unconfirmedWrite && !preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false) &&
                preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        } catch (_: Exception) {
            false
        }
    }

    /** Completing the introduction is not disclosure acceptance, pairing, or a session start. */
    fun completeOnboarding(): Boolean = synchronized(STATE_LOCK) {
        try {
            if (!confirmFailedWrite() || !finishPendingDeletion()) return false
            if (preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)) return true
            commitState(preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true))
        } catch (_: Exception) {
            false
        }
    }

    fun readSelectionEdit(): SelectionEditSnapshot = synchronized(STATE_LOCK) {
        try {
            val recovery = preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)
            val error = unconfirmedWrite || preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false)
            SelectionEditSnapshot(
                selectedPackages = readSelectedPackages(),
                sessionRequested = preferences.getBoolean(KEY_DESIRED_ACTIVE, false),
                recoveryRequired = recovery,
                error = error,
                token = if (error || recovery) null else selectionEditToken(),
            )
        } catch (_: Exception) {
            SelectionEditSnapshot(
                recoveryRequired = runCatching { preferences.getBoolean(KEY_RECOVERY_REQUIRED, false) }.getOrDefault(false),
                error = true,
            )
        }
    }

    fun commitSelectionEdit(expectedToken: SelectionEditToken, packageNames: Set<String>): SelectionEditResult =
        synchronized(STATE_LOCK) {
            try {
                if (unconfirmedWrite || preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false)) {
                    return SelectionEditResult.STORAGE_FAILED
                }
                if (preferences.getBoolean(KEY_DESIRED_ACTIVE, false)) return SelectionEditResult.ACTIVE_SESSION
                if (preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)) return SelectionEditResult.RECOVERY_REQUIRED
                if (!matchesSelectionEditToken(expectedToken)) return SelectionEditResult.CONFLICT
                if (!ModeRules.validTargets(packageNames, allowEmpty = true)) return SelectionEditResult.INVALID_SELECTION
                val selection = packageNames.toSet()
                if (readSelectedPackages() == selection) return SelectionEditResult.UNCHANGED
                val catalog = readModes()
                val previous = catalog.recentMode
                // Compatibility setup may create the first Noise mode. Once a versioned
                // catalog has existed, a missing recent choice must be selected explicitly.
                if (previous == null && preferences.contains(KEY_MODES)) return SelectionEditResult.CONFLICT
                val mode = StoredMode(previous?.id ?: DEFAULT_SCHEDULE_MODE_REFERENCE,
                    Math.addExact(previous?.revision ?: 0L, 1L), previous?.name ?: "Noise", selection)
                val updated = catalog.copy(
                    modes = if (previous == null) catalog.modes + mode else catalog.modes.map { if (it.id == mode.id) mode else it },
                    recentModeId = mode.id,
                )
                // No platform adapter or callback is invoked while holding the shared state lock.
                val editor = modeEditor(updated)
                    .putLong(KEY_SESSION_REVISION, Math.addExact(expectedToken.sessionRevision, 1L))
                if (commitState(editor)) SelectionEditResult.SAVED else SelectionEditResult.STORAGE_FAILED
            } catch (_: Exception) {
                SelectionEditResult.STORAGE_FAILED
            }
        }

    /** Legacy immediate call. Keep its active identical-selection no-op, but never use it for a queued editor save. */
    fun setSelectedPackages(packageNames: Set<String>): Boolean = synchronized(STATE_LOCK) {
        try {
            if (packageNames.any(String::isBlank) || unconfirmedWrite ||
                preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false)
            ) return false
            if (readSelectedPackages() == packageNames) return true
            if (preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)) return false
            commitSelectionEdit(selectionEditToken(), packageNames) == SelectionEditResult.SAVED
        } catch (_: Exception) {
            false
        }
    }

    private fun selectionEditToken(): SelectionEditToken = SelectionEditToken(
        SELECTION_PROCESS_NONCE, selectionEditGeneration,
        preferences.getLong(KEY_SESSION_REVISION, 0L),
        preferences.getString(KEY_DISCLOSURE_VERSION, null),
        preferences.getLong(KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS, 0L),
    )

    private fun matchesSelectionEditToken(token: SelectionEditToken): Boolean =
        token.processNonce == SELECTION_PROCESS_NONCE && token.consentGeneration == selectionEditGeneration &&
            token.sessionRevision == preferences.getLong(KEY_SESSION_REVISION, 0L) &&
            token.consentVersion == preferences.getString(KEY_DISCLOSURE_VERSION, null) &&
            token.consentEpochMilliseconds == preferences.getLong(KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS, 0L)

    /** Compatibility for initial provisioning only; replacement requires a frozen intent. */
    fun setZapDigest(encodedDigest: String): Boolean = setInitialZapDigest(encodedDigest)

    fun prepareZapPairing(): ZapPairingToken? = synchronized(STATE_LOCK) {
        try {
            if (!canPairZap()) return null
            ZapPairingToken(
                stateToken = selectionEditToken(),
                pairingGeneration = zapPairingGeneration,
                expectedDigest = preferences.getString(KEY_ZAP_DIGEST, null),
                replacesExistingZap = preferences.contains(KEY_ZAP_DIGEST),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Cheap preflight only. Token validity is rechecked under STATE_LOCK at the final write. */
    internal fun canPairZap(expectedToken: ZapPairingToken): Boolean = synchronized(STATE_LOCK) {
        try {
            canPairZap() && matchesSelectionEditToken(expectedToken.stateToken) &&
                expectedToken.pairingGeneration == zapPairingGeneration &&
                expectedToken.replacesExistingZap == preferences.contains(KEY_ZAP_DIGEST) &&
                expectedToken.expectedDigest == preferences.getString(KEY_ZAP_DIGEST, null)
        } catch (_: Exception) {
            false
        }
    }

    internal fun commitZapPairing(expectedToken: ZapPairingToken, encodedDigest: String): Boolean =
        synchronized(STATE_LOCK) {
            try {
                if (encodedDigest.isBlank() || !canPairZap(expectedToken)) return false
                // Consume even a same-digest write or failed commit: the confirmation is one-shot.
                zapPairingGeneration += 1L
                commitState(preferences.edit().putString(KEY_ZAP_DIGEST, encodedDigest))
            } catch (_: Exception) {
                false
            }
        }

    private fun canPairZap(): Boolean =
        !unconfirmedWrite && !isWorkspacePending() && !preferences.getBoolean(KEY_HISTORY_DELETION_PENDING, false) &&
            !preferences.getBoolean(KEY_DESIRED_ACTIVE, false) &&
            !preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)

    /** Cheap preflight only; the same conditions are checked atomically at the final write. */
    internal fun canPairInitialZap(): Boolean = synchronized(STATE_LOCK) {
        try {
            canPairZap() && !preferences.contains(KEY_ZAP_DIGEST)
        } catch (_: Exception) {
            false
        }
    }

    internal fun setInitialZapDigest(encodedDigest: String): Boolean = synchronized(STATE_LOCK) {
        try {
            if (encodedDigest.isBlank() || !canPairInitialZap()) return false
            // This guard and commit share STATE_LOCK and happen after HMAC computation. A stale
            // first-run screen cannot replace an existing pairing or pair into session recovery.
            zapPairingGeneration += 1L
            commitState(preferences.edit().putString(KEY_ZAP_DIGEST, encodedDigest))
        } catch (_: Exception) {
            false
        }
    }

    fun clearZapDigest(): Boolean = synchronized(STATE_LOCK) {
        zapPairingGeneration += 1L
        commitState(preferences.edit().remove(KEY_ZAP_DIGEST))
    }

    override fun startSession(epochMilliseconds: Long): Boolean = startSession(epochMilliseconds, expectedState = null)

    /** CAS binds the already-validated targets/consent to the exact mode that will be started. */
    fun startSession(
        epochMilliseconds: Long,
        expectedState: StoredGate0State?,
        expectedToken: SelectionEditToken? = null,
    ): Boolean = synchronized(STATE_LOCK) {
        if (!accountAllowsSessionStart()) return false
        if (!confirmFailedWrite() || !finishPendingDeletion()) return false
        if (preferences.getBoolean(KEY_DESIRED_ACTIVE, false) || epochMilliseconds < 0L) return false
        if (preferences.getBoolean(KEY_RECOVERY_REQUIRED, false)) return false
        if (expectedToken != null && !matchesSelectionEditToken(expectedToken)) return false
        val current = snapshot()
        if (expectedState != null && (current.sessionRevision != expectedState.sessionRevision ||
                current.recentModeId != expectedState.recentModeId ||
                current.selectedPackages != expectedState.selectedPackages ||
                current.acceptedDisclosureVersion != expectedState.acceptedDisclosureVersion ||
                current.acceptedDisclosureEpochMilliseconds != expectedState.acceptedDisclosureEpochMilliseconds ||
                current.zapDigest != expectedState.zapDigest)) return false
        // Never overwrite a completed record whose durable delivery is still pending.
        if (!drainPendingHistory()) return false
        val catalog = runCatching { readModes() }.getOrNull() ?: return false
        val mode = ModeRules.modeForStart(catalog) ?: return false
        commitState(sessionStartEditor(epochMilliseconds, UUID.randomUUID().toString(), mode))
    }

    private fun sessionStartEditor(epochMilliseconds: Long, sessionId: String, mode: StoredMode): SharedPreferences.Editor {
        return modeEditor(readModes())
            .putStringSet(KEY_ACTIVE_PACKAGES, mode.selectedPackages)
            .putString(KEY_ACTIVE_MODE_ID, mode.id)
            .putString(KEY_ACTIVE_MODE_NAME, mode.name)
            .putString(KEY_ACTIVE_SESSION_ID, sessionId)
            .remove(KEY_ACTIVE_SCHEDULE_ID)
            .putLong(KEY_ACTIVE_SINCE_ELAPSED, elapsedRealtime())
            .putString(KEY_ACTIVE_PROCESS, PROCESS_ID)
            .putBoolean(KEY_HISTORY_ELIGIBLE, hasHistoryConsent())
            .remove(KEY_HISTORY_INTERRUPTED)
            .putBoolean(KEY_DESIRED_ACTIVE, true)
            .putLong(KEY_ACTIVE_SINCE_EPOCH_MILLIS, epochMilliseconds)
            .putLong(KEY_SESSION_REVISION, nextSessionRevision())
            .remove(KEY_RECOVERY_REQUIRED)
    }

    override fun stopSession(epochMilliseconds: Long, expectedSessionId: String?): Boolean =
        stopSessionWithCompletion(epochMilliseconds, expectedSessionId).succeeded

    /** Return the exact outbox value only when this call newly commits the stop. */
    fun stopSessionWithCompletion(
        epochMilliseconds: Long,
        expectedSessionId: String? = null,
    ): SessionStopResult = synchronized(STATE_LOCK) {
        if (!confirmFailedWrite(allowWorkspacePending = true)) return SessionStopResult(false)
        val stored = snapshot()
        if (!stored.desiredActive) {
            return SessionStopResult(expectedSessionId == null ||
                preferences.getString(KEY_LAST_FINISHED_ID, null) == expectedSessionId)
        }
        if (expectedSessionId != null && stored.activeSessionId != expectedSessionId) return SessionStopResult(false)
        // A v1/v2 session has no History consent at its start: stop it, but never backfill it.
        val entry = if (preferences.getBoolean(KEY_HISTORY_ELIGIBLE, false) &&
            stored.activeSessionId != null && stored.activePackages.isNotEmpty()
        ) {
            val sameProcess = preferences.getString(KEY_ACTIVE_PROCESS, null) == PROCESS_ID
            val nowElapsed = elapsedRealtime()
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
        if (preferences.contains(KEY_PENDING_HISTORY)) return SessionStopResult(false)
        val editor = preferences.edit()
            .putBoolean(KEY_DESIRED_ACTIVE, false)
            .remove(KEY_ACTIVE_PACKAGES)
            .remove(KEY_ACTIVE_SINCE_EPOCH_MILLIS)
            .remove(KEY_ACTIVE_SESSION_ID)
            .remove(KEY_ACTIVE_SCHEDULE_ID)
            .remove(KEY_ACTIVE_MODE_ID)
            .remove(KEY_ACTIVE_MODE_NAME)
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
        if (commitState(editor)) SessionStopResult(true, entry) else SessionStopResult(false)
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
            workspaceHistory().load(limit.coerceAtLeast(1))
        } catch (_: Exception) {
            SessionHistorySnapshot(error = true)
        }
    }

    /** Worker-thread only. Serialize with withdrawal and the outbox, like the paged History read. */
    fun loadHistoryTimeline(range: com.justnoise.gate0.domain.HistoryDateRange): SessionHistoryTimelineSnapshot = synchronized(STATE_LOCK) {
        try {
            if (!confirmFailedWrite() || !finishPendingDeletion() || !drainPendingHistory()) {
                return SessionHistoryTimelineSnapshot(range, error = true)
            }
            workspaceHistory().loadTimeline(range)
        } catch (_: Exception) {
            SessionHistoryTimelineSnapshot(range, error = true)
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
        modeForReference(modeReference) != null
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
        isWorkspacePending() || preferences.getBoolean(KEY_SCHEDULE_SUSPENDED, false)
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
            val scheduledMode = modeForReference(schedule.modeReference)
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
                    // A cold process restores encrypted auth asynchronously. Do not consume
                    // a valid occurrence as signed-out before that local decision exists.
                    if (startAccess.accountRequired && !startAccess.resolved) return ScheduleDispatchResult()
                    when {
                        !startAccess.allows(readWorkspace()) -> ScheduleOutcomeCode.ACCOUNT_REQUIRED
                        scheduledMode == null -> ScheduleOutcomeCode.MODE_UNAVAILABLE
                        scheduledMode.selectedPackages.isEmpty() -> ScheduleOutcomeCode.EMPTY_SELECTION
                        else -> startBlocker(stored.copy(selectedPackages = scheduledMode.selectedPackages), schedule)
                            ?: ScheduleOutcomeCode.STARTED
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
            val editor = if (sessionId == null) preferences.edit() else sessionStartEditor(dispatchNow, sessionId,
                requireNotNull(scheduledMode))
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
                modeReference = draft.modeReference ?: previous?.modeReference ?: readModes().recentModeId ?: return false,
            )
            val mode = modeForReference(updated.modeReference) ?: return false
            if (!ModeRules.validTargets(mode.selectedPackages)) return false
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
            // Disabling must remain available even if its referenced data is unavailable.
            if (!enabled) {
                val updated = current.copy(enabled = false, revision = Math.addExact(current.revision, 1L))
                return commitState(preferences.edit().putString(KEY_SCHEDULES, ScheduleStoreCodec.encode(
                    readSchedules().map { if (it.id == id) updated else it },
                )).putString(KEY_SCHEDULE_EXECUTIONS, ScheduleExecutionCodec.encode(
                    readScheduleExecutions().map { if (it.scheduleId == id) it.copy(pending = null) else it },
                )))
            }
            saveSchedule(
                ScheduleDraft(current.name, enabled, current.timing, current.creationZoneId, current.modeReference),
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
        selectionEditGeneration += 1L
        dismissedNotificationSessionId = null
        val outgoingWorkspace = readWorkspace()
        // A local-only withdrawal/re-consent is not an account change. Keep that UI bridge
        // usable; its existing frozen consent-epoch guard still rejects old queued writes.
        val localEpoch = if (outgoingWorkspace == LocalWorkspaceId.LOCAL) {
            preferences.getString(KEY_WORKSPACE_EPOCH, null)
        } else UUID.randomUUID().toString()
        if (outgoingWorkspace != LocalWorkspaceId.LOCAL) workspaceReadGeneration += 1L
        val owners = preferences.getStringSet(KEY_HISTORY_DELETE_WORKSPACES, emptySet()).orEmpty() +
            listOfNotNull(preferences.getString(KEY_ACCOUNT_ERASURE_PENDING, null)) +
            outgoingWorkspace.value + "local" + preferences.all.keys.filter { it.startsWith(WORKSPACE_PROFILE_PREFIX) }
                .map { LocalWorkspaceId.parse(it.removePrefix(WORKSPACE_PROFILE_PREFIX)).value }
        // Revoke and discard outbox first. A crash cannot replay a record being deleted.
        val revoked = preferences.edit().clear().putBoolean(KEY_HISTORY_DELETION_PENDING, true)
            .putString(KEY_WORKSPACE_EPOCH, localEpoch)
            .putStringSet(KEY_HISTORY_DELETE_WORKSPACES, owners).commit()
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
            val owners = preferences.getStringSet(KEY_HISTORY_DELETE_WORKSPACES, setOf("local")).orEmpty()
            check("local" in owners) { "Incomplete workspace cleanup inventory" }
            owners.forEach {
                historyStorage.forWorkspace(LocalWorkspaceId.parse(it)).clear()
            }
            commitState(preferences.edit().remove(KEY_HISTORY_DELETION_PENDING).remove(KEY_HISTORY_DELETE_WORKSPACES)).also {
                if (it) unconfirmedWrite = false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun drainPendingHistory(): Boolean {
        val pending = preferences.getString(KEY_PENDING_HISTORY, null) ?: return true
        return try {
            workspaceHistory().insert(SessionHistoryEntry.decode(pending))
            commitState(preferences.edit().remove(KEY_PENDING_HISTORY))
        } catch (_: Exception) {
            false
        }
    }

    private fun readSchedules(): List<StoredSchedule> {
        check(!isWorkspacePending()) { "Workspace handoff pending" }
        readWorkspace()
        return preferences.getString(KEY_SCHEDULES, null)?.let(ScheduleStoreCodec::decode).orEmpty()
    }

    private fun readScheduleExecutions(): List<ScheduleExecution> =
        preferences.getString(KEY_SCHEDULE_EXECUTIONS, null)?.let(ScheduleExecutionCodec::decode).orEmpty()

    private fun confirmFailedWrite(allowWorkspacePending: Boolean = false): Boolean {
        if (!allowWorkspacePending && isWorkspacePending()) return false
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

    private fun readSelectedPackages(): Set<String> = readModes().recentMode?.selectedPackages.orEmpty()

    /** Decode only when the single configuration value changes, never once per overlay event. */
    private fun readModes(): ModeCatalog {
        check(!isWorkspacePending()) { "Workspace handoff pending" }
        readWorkspace()
        val encoded = preferences.getString(KEY_MODES, null)
        if (encoded == null) {
            val hasLegacy = preferences.contains(KEY_SELECTED_PACKAGES) || preferences.contains(KEY_SELECTED_PACKAGE)
            return ModeRules.migrateLegacy(if (hasLegacy) readLegacySelectedPackages() else null)
        }
        if (cachedModeEncoding == encoded) return requireNotNull(cachedModes)
        return ModeStoreCodec.decode(encoded).also { cachedModeEncoding = encoded; cachedModes = it }
    }

    private fun modeEditor(catalog: ModeCatalog): SharedPreferences.Editor {
        val editor = preferences.edit().putString(KEY_MODES, ModeStoreCodec.encode(catalog))
        if (preferences.getBoolean(KEY_DESIRED_ACTIVE, false) && !preferences.contains(KEY_ACTIVE_PACKAGES) &&
            !preferences.contains(KEY_SELECTED_PACKAGES)
        ) editor.putStringSet(KEY_ACTIVE_PACKAGES, readLegacySelectedPackages())
        return editor.remove(KEY_SELECTED_PACKAGES).remove(KEY_SELECTED_PACKAGE)
    }

    private fun readLegacySelectedPackages(): Set<String> =
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
        private val SELECTION_PROCESS_NONCE = UUID.randomUUID().toString()
        private var selectionEditGeneration = 0L
        private var workspaceReadGeneration = 0L
        private var zapPairingGeneration = 0L
        private var unconfirmedWrite = false
        private var dismissedNotificationSessionId: String? = null

        private const val FILE_NAME = "gate0_local_state"
        private const val KEY_DISCLOSURE_VERSION = "accepted_disclosure_version"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed_v1"
        private const val KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS =
            "accepted_disclosure_epoch_millis"
        private const val KEY_SELECTED_PACKAGE = "selected_package"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_MODES = "modes_v1"
        private const val KEY_ACTIVE_MODE_ID = "active_mode_id"
        private const val KEY_ACTIVE_MODE_NAME = "active_mode_name"
        private const val KEY_ACTIVE_PACKAGES = "active_packages"
        private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        private const val KEY_ACTIVE_SCHEDULE_ID = "active_schedule_id"
        private const val KEY_ACTIVE_SINCE_ELAPSED = "active_since_elapsed_millis"
        private const val KEY_ACTIVE_PROCESS = "active_process_id"
        private const val KEY_DISMISSED_NOTIFICATION_SESSION_ID = "dismissed_notification_session_id"
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
        private const val KEY_LOCAL_IMPORT_OWNER = "local_import_owner_v1"
        private const val KEY_LOCAL_IMPORT_PLAN = "local_import_plan_v1"
        private const val KEY_LOCAL_IMPORT_DEFERRED = "local_import_deferred_v1"
        private const val KEY_WORKSPACE = "workspace_owner_v1"
        private const val KEY_WORKSPACE_EPOCH = "workspace_epoch_v1"
        private const val KEY_WORKSPACE_PENDING = "workspace_handoff_pending_v1"
        private const val WORKSPACE_PROFILE_PREFIX = "workspace_profile_v1_"
        private const val KEY_HISTORY_DELETE_WORKSPACES = "history_delete_workspaces_v1"
        private const val KEY_ACCOUNT_ERASURE_PENDING = "account_erasure_pending_v1"
        private val WORKSPACE_TRANSIENT_KEYS = setOf(
            KEY_SELECTED_PACKAGE, KEY_SELECTED_PACKAGES, KEY_ACTIVE_MODE_ID, KEY_ACTIVE_MODE_NAME,
            KEY_ACTIVE_PACKAGES, KEY_ACTIVE_SESSION_ID, KEY_ACTIVE_SCHEDULE_ID, KEY_ACTIVE_SINCE_ELAPSED,
            KEY_ACTIVE_PROCESS, KEY_DISMISSED_NOTIFICATION_SESSION_ID, KEY_HISTORY_ELIGIBLE,
            KEY_HISTORY_INTERRUPTED, KEY_LAST_FINISHED_ID, KEY_ACTIVE_SINCE_EPOCH_MILLIS,
            KEY_DESIRED_ACTIVE, KEY_RECOVERY_REQUIRED, KEY_SCHEDULE_RECOVERY_ACKNOWLEDGED,
        )
    }
}
