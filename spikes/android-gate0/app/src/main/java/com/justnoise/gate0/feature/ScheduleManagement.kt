package com.justnoise.gate0.feature

import android.content.Context
import com.justnoise.gate0.data.DEFAULT_SCHEDULE_MODE_REFERENCE
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.ScheduleDraft
import com.justnoise.gate0.data.ScheduleExecution
import com.justnoise.gate0.data.StoredGate0State
import com.justnoise.gate0.data.StoredSchedule
import com.justnoise.gate0.platform.restriction.AccessibilityServiceStatus
import com.justnoise.gate0.platform.schedule.ScheduleAlarmScheduler
import com.justnoise.gate0.platform.schedule.ScheduleStartupGuard

data class ScheduleManagementSnapshot(
    val schedules: List<StoredSchedule> = emptyList(),
    val executions: List<ScheduleExecution> = emptyList(),
    val exactAlarmAccess: Boolean = false,
    val consentAccepted: Boolean = false,
    val consentEpochMilliseconds: Long = 0L,
    val zapPaired: Boolean = false,
    val selectedAppCount: Int = 0,
    val accessibilityEnabled: Boolean = false,
    val recoveryRequired: Boolean = false,
    val suspended: Boolean = false,
    val error: Boolean = false,
)

enum class ScheduleMutationFailure { CONSENT_REQUIRED, CONFLICT, INVALID_OR_FAILED_WRITE, REARM_FAILED }

data class ScheduleMutationResult(
    val saved: Boolean,
    val setupRequired: Boolean = false,
    val failure: ScheduleMutationFailure? = null,
)

/** Native UI bridge only. No settings launch, permission grant, separate storage or session toggle. */
class ScheduleManagement(
    context: Context,
    private val preferences: Gate0Preferences = Gate0Preferences(context),
    private val exactAlarmAccess: () -> Boolean = { ScheduleAlarmScheduler(context).hasExactAlarmAccess() },
    private val rearm: () -> Unit = { ScheduleRuntime(context).onConfigurationChanged() },
) {
    private val appContext = context.applicationContext

    fun load(): ScheduleManagementSnapshot = try {
        preferences.withScheduleLock {
            val schedules = preferences.loadSchedules()
            val executions = preferences.loadScheduleExecutions()
            val state = preferences.snapshot()
            ScheduleManagementSnapshot(
                schedules = schedules.schedules,
                executions = executions.entries,
                exactAlarmAccess = exactAlarmAccess(),
                consentAccepted = state.hasCurrentConsent(),
                consentEpochMilliseconds = state.acceptedDisclosureEpochMilliseconds,
                zapPaired = !state.zapDigest.isNullOrBlank(),
                selectedAppCount = state.selectedPackages.size,
                accessibilityEnabled = AccessibilityServiceStatus.isEnabled(appContext),
                recoveryRequired = state.recoveryRequired,
                suspended = ScheduleStartupGuard.needsForegroundRecovery(preferences),
                error = schedules.error || executions.error,
            )
        }
    } catch (_: Exception) {
        ScheduleManagementSnapshot(error = true)
    }

    /** Explicit worker-thread retry; polling load() never rearms or acknowledges recovery. */
    fun refresh(): ScheduleManagementSnapshot = try {
        rearm()
        load()
    } catch (_: Exception) {
        load().copy(error = true)
    }

    fun save(
        draft: ScheduleDraft,
        id: String? = null,
        expectedRevision: Long? = null,
        expectedConsentEpochMilliseconds: Long? = null,
    ): ScheduleMutationResult = mutate(id, expectedRevision, expectedConsentEpochMilliseconds, creating = id == null) {
        preferences.saveSchedule(draft, id, expectedRevision)
    }

    fun setEnabled(
        id: String, revision: Long, enabled: Boolean, expectedConsentEpochMilliseconds: Long? = null,
    ): ScheduleMutationResult = mutate(id, revision, expectedConsentEpochMilliseconds) {
        preferences.setScheduleEnabled(id, revision, enabled)
    }

    fun delete(
        id: String, revision: Long, expectedConsentEpochMilliseconds: Long? = null,
    ): ScheduleMutationResult = mutate(id, revision, expectedConsentEpochMilliseconds) {
        preferences.deleteSchedule(id, revision)
    }

    private fun mutate(
        id: String?,
        expectedRevision: Long?,
        expectedConsentEpochMilliseconds: Long?,
        creating: Boolean = false,
        write: () -> Boolean,
    ): ScheduleMutationResult {
        // UI callers freeze this when accepting the action, before queueing worker-thread work.
        val consentEpoch = expectedConsentEpochMilliseconds ?: try {
            preferences.snapshot().acceptedDisclosureEpochMilliseconds
        } catch (_: Exception) {
            return failed(ScheduleMutationFailure.INVALID_OR_FAILED_WRITE)
        }
        val committed = try {
            preferences.withScheduleLock {
                // Validate consent inside the same lock as the CAS write, including cleanup retries.
                val schedules = preferences.loadSchedules()
                val executions = preferences.loadScheduleExecutions()
                val state = preferences.snapshot()
                if (!state.hasCurrentConsent() || state.acceptedDisclosureEpochMilliseconds != consentEpoch) {
                    return@withScheduleLock failed(ScheduleMutationFailure.CONSENT_REQUIRED)
                }
                if (schedules.error || executions.error) {
                    return@withScheduleLock failed(ScheduleMutationFailure.INVALID_OR_FAILED_WRITE)
                }
                val current = schedules.schedules.find { it.id == id }
                if ((creating && expectedRevision != null) ||
                    (!creating && (current == null || current.revision != expectedRevision))
                ) return@withScheduleLock failed(ScheduleMutationFailure.CONFLICT)
                if (write()) ScheduleMutationResult(saved = true)
                else failed(ScheduleMutationFailure.INVALID_OR_FAILED_WRITE)
            }
        } catch (_: Exception) {
            failed(ScheduleMutationFailure.INVALID_OR_FAILED_WRITE)
        }
        if (!committed.saved) return committed

        // The runtime can wait for the service's main-thread acknowledgment. Never retain the prefs lock here.
        try {
            rearm()
        } catch (_: Exception) {
            return ScheduleMutationResult(saved = true, setupRequired = true, failure = ScheduleMutationFailure.REARM_FAILED)
        }
        val after = load()
        return ScheduleMutationResult(saved = true, setupRequired = after.hasSetupGap())
    }

    private fun StoredGate0State.hasCurrentConsent(): Boolean =
        acceptedDisclosureVersion == Gate0Preferences.CURRENT_DISCLOSURE_VERSION &&
            acceptedDisclosureEpochMilliseconds > 0L

    private fun ScheduleManagementSnapshot.hasSetupGap(): Boolean = error ||
        (schedules.any { it.enabled } && (!exactAlarmAccess || !consentAccepted || !zapPaired ||
            selectedAppCount == 0 || !accessibilityEnabled || recoveryRequired || suspended ||
            schedules.any { it.enabled && it.modeReference != DEFAULT_SCHEDULE_MODE_REFERENCE }))

    private fun failed(reason: ScheduleMutationFailure) = ScheduleMutationResult(saved = false, failure = reason)
}
