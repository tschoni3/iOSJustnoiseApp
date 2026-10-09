package com.justnoise.gate0.feature

import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.data.StoredGate0State
import com.justnoise.gate0.domain.SessionStateWriter

/** Binds asynchronous NFC preflight to one state. A lost race is not a storage failure. */
internal class ValidatedSessionWriter(
    private val preferences: Gate0Preferences,
    private val expected: StoredGate0State,
    private val token: SelectionEditToken?,
    private val capabilityReady: (StoredGate0State) -> Boolean,
    private val completed: (SessionHistoryEntry?) -> Unit,
) : SessionStateWriter {
    private var mayRequireRecovery = false
    var persistenceFailed: Boolean = false
        private set

    override fun startSession(epochMilliseconds: Long): Boolean = preferences.withScheduleLock {
        val current = preferences.snapshot()
        if (!preferences.accountAllowsSessionStart() || current.desiredActive || current.recoveryRequired || current.modeStorageError || token == null ||
            preferences.readSelectionEdit().token != token ||
            current.sessionRevision != expected.sessionRevision || current.recentModeId != expected.recentModeId ||
            current.selectedPackages != expected.selectedPackages || current.zapDigest != expected.zapDigest ||
            !capabilityReady(current)
        ) return@withScheduleLock false
        mayRequireRecovery = true
        preferences.startSession(epochMilliseconds, expected, token).also { persistenceFailed = !it }
    }

    override fun stopSession(epochMilliseconds: Long, expectedSessionId: String?): Boolean = preferences.withScheduleLock {
        val current = preferences.snapshot()
        if (current.sessionRevision != expected.sessionRevision || current.activeSessionId != expected.activeSessionId) {
            return@withScheduleLock false
        }
        mayRequireRecovery = true
        preferences.stopSessionWithCompletion(epochMilliseconds, expectedSessionId).also {
            completed(it.completion)
            persistenceFailed = !it.succeeded
        }.succeeded
    }

    override fun markRecoveryRequired() = preferences.withScheduleLock {
        val current = preferences.snapshot()
        // Never quarantine a mode selection or a different session which won after preflight.
        if (mayRequireRecovery && current.sessionRevision == expected.sessionRevision &&
            current.activeSessionId == expected.activeSessionId
        ) preferences.markRecoveryRequired()
    }
}
