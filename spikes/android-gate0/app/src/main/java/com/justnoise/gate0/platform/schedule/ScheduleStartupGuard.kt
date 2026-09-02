package com.justnoise.gate0.platform.schedule

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.domain.PortableScheduleTimingRules

/** Startup evidence is not permission to resume after a user-controlled stop. */
object ScheduleStartupGuard {
    private val state = ScheduleStartupState()

    /** Called once from Application, before any receiver or service may schedule work. */
    @Synchronized
    fun initialize(context: Context, preferences: Gate0Preferences) {
        if (state.isInitialized) return
        val evidence = try {
            val manager = context.getSystemService(ActivityManager::class.java)
            if (manager == null) {
                ScheduleStartupEvidence.UNKNOWN
            } else if (Build.VERSION.SDK_INT >= 35) {
                currentProcessEvidence(manager)
            } else {
                val processName = Application.getProcessName()
                val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 0)
                    .filter { it.processName == processName }
                    .map { ScheduleProcessExit(it.timestamp, it.reason == ApplicationExitInfo.REASON_USER_REQUESTED) }
                legacyScheduleStartupEvidence(
                    exits,
                    preferences.scheduleRecoveryAcknowledgedAt(),
                    System.currentTimeMillis(),
                )
            }
        } catch (_: Exception) {
            ScheduleStartupEvidence.UNKNOWN
        }
        // Keep the process blocked even if persisting suspension fails. A later
        // foreground acknowledgment must succeed before either guard is cleared.
        state.initialize(evidence)
        if (evidence != ScheduleStartupEvidence.CLEAR) {
            try {
                preferences.suspendSchedules()
            } catch (_: Exception) {
                // The in-memory UNKNOWN/RECOVERY_REQUIRED state remains blocked.
            }
        }
    }

    fun allowBackgroundScheduling(preferences: Gate0Preferences): Boolean =
        try {
            state.allowsBackground(preferences.areSchedulesSuspended())
        } catch (_: Exception) {
            false
        }

    fun needsForegroundRecovery(preferences: Gate0Preferences): Boolean =
        !allowBackgroundScheduling(preferences)

    /**
     * Foreground-only caller must hold the runtime lock and durably discard all
     * due occurrences first. Neither this method nor Application ever rearms work.
     */
    @Synchronized
    fun acknowledgeForegroundRecovery(preferences: Gate0Preferences, now: Long): Boolean {
        if (!state.isInitialized || now !in 0..PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS) {
            return false
        }
        val acknowledged = try {
            preferences.acknowledgeScheduleRecovery(now)
        } catch (_: Exception) {
            false
        }
        return state.acknowledge(acknowledged)
    }

    @RequiresApi(35)
    private fun currentProcessEvidence(manager: ActivityManager): ScheduleStartupEvidence {
        // History includes in-progress starts. Inspect every retained record for
        // this process: a warm Activity start must not hide its first stopped start.
        val starts = manager.getHistoricalProcessStartReasons(0).map {
            ScheduleProcessStart(it.pid, it.processName, it.wasForceStopped())
        }
        return currentScheduleStartupEvidence(starts, Process.myPid(), Application.getProcessName())
    }
}

internal enum class ScheduleStartupEvidence { CLEAR, RECOVERY_REQUIRED, UNKNOWN }

internal data class ScheduleProcessStart(val pid: Int, val processName: String, val wasForceStopped: Boolean)
internal data class ScheduleProcessExit(val timestamp: Long, val userRequested: Boolean)

internal fun currentScheduleStartupEvidence(
    starts: List<ScheduleProcessStart>,
    pid: Int,
    processName: String,
): ScheduleStartupEvidence {
    val current = starts.filter { it.pid == pid && it.processName == processName }
    return when {
        pid <= 0 || processName.isBlank() || current.isEmpty() -> ScheduleStartupEvidence.UNKNOWN
        current.any { it.wasForceStopped } -> ScheduleStartupEvidence.RECOVERY_REQUIRED
        else -> ScheduleStartupEvidence.CLEAR
    }
}

internal fun legacyScheduleStartupEvidence(
    exits: List<ScheduleProcessExit>,
    acknowledgedAt: Long,
    now: Long,
): ScheduleStartupEvidence {
    val range = 0..PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS
    return when {
        acknowledgedAt !in range || now !in range || acknowledgedAt > now -> ScheduleStartupEvidence.UNKNOWN
        exits.any { it.userRequested && it.timestamp > acknowledgedAt } -> ScheduleStartupEvidence.RECOVERY_REQUIRED
        exits.isEmpty() || exits.any { it.timestamp !in range || it.timestamp > now } -> ScheduleStartupEvidence.UNKNOWN
        // API 31–34 only offers conservative evidence: USER_REQUESTED also
        // includes Recents removal and, before API 34, some package changes.
        else -> ScheduleStartupEvidence.CLEAR
    }
}

/** Small process latch; persistence failures must never become background consent. */
internal class ScheduleStartupState {
    @Volatile private var evidence: ScheduleStartupEvidence? = null
    val isInitialized: Boolean get() = evidence != null

    fun initialize(value: ScheduleStartupEvidence) {
        if (!isInitialized) evidence = value
    }

    fun allowsBackground(persistedSuspension: Boolean): Boolean =
        evidence == ScheduleStartupEvidence.CLEAR && !persistedSuspension

    fun acknowledge(persisted: Boolean): Boolean {
        if (!isInitialized) return false
        if (!persisted) {
            evidence = ScheduleStartupEvidence.RECOVERY_REQUIRED
            return false
        }
        evidence = ScheduleStartupEvidence.CLEAR
        return true
    }
}
