package com.justnoise.gate0.data

import java.util.UUID

enum class LocalDataImportStatus { UNAVAILABLE, AVAILABLE, PENDING, COMPLETE, ERROR }
data class LocalDataImportOffer(
    val status: LocalDataImportStatus = LocalDataImportStatus.UNAVAILABLE,
    val modeCount: Int = 0,
    val scheduleCount: Int = 0,
    val sessionCount: Int = 0,
    val deferred: Boolean = false,
    val token: WorkspaceEditToken? = null,
) {
    val shouldPrompt get() = status == LocalDataImportStatus.AVAILABLE && !deferred
    override fun toString() = "LocalDataImportOffer($status, [redacted])"
}
enum class LocalDataImportResult { IMPORTED, UNAVAILABLE, CONFLICT, HISTORY_CONFLICT, ACTIVE_SESSION, RECOVERY_REQUIRED, CAPACITY, STORAGE_FAILED }

/** Pure merge: input profiles remain untouched; the persisted plan makes retries deterministic. */
internal object LocalDataImportMerge {
    fun merge(source: WorkspaceProfile, destination: WorkspaceProfile): WorkspaceProfile {
        val modes = destination.modes.modes.toMutableList()
        val modeIds = mutableMapOf<String, String>()
        source.modes.modes.forEach { local ->
            val same = modes.find { it.name == local.name && it.selectedPackages == local.selectedPackages }
            val id = same?.id ?: freeId("mode", local.id, modes.map { it.id }.toSet())
            modeIds[local.id] = id
            if (same == null) modes.add(local.copy(id = id))
        }
        require(modes.size <= ModeRules.MAX_MODES) { "Import mode capacity" }
        val schedules = destination.schedules.toMutableList()
        val executions = destination.executions.toMutableList()
        source.schedules.forEach { local ->
            // An orphan remains an orphan: never bind it accidentally to an account mode.
            val mode = modeIds[local.modeReference] ?: freeId("missing-mode", local.modeReference, modes.map { it.id }.toSet())
            val same = schedules.find { it.name == local.name && it.timing == local.timing &&
                it.creationZoneId == local.creationZoneId && it.modeReference == mode }
            if (same == null) {
                val id = freeId("schedule", local.id, schedules.map { it.id }.toSet())
                schedules.add(local.copy(id = id, modeReference = mode, enabled = false))
                source.executions.find { it.scheduleId == local.id }?.let { execution ->
                    executions.add(execution.copy(scheduleId = id, pending = null,
                        lastOutcome = execution.lastOutcome?.let { it.copy(occurrence = it.occurrence.copy(scheduleId = id)) }))
                }
            }
        }
        require(schedules.size <= ScheduleStoreCodec.MAX_SCHEDULES) { "Import schedule capacity" }
        val recent = destination.modes.recentModeId ?: source.modes.recentModeId?.let { modeIds[it] }
        return WorkspaceProfile(ModeCatalog(modes, recent), schedules, executions).also { it.encode() }
    }

    private fun freeId(kind: String, original: String, occupied: Set<String>): String {
        if (original !in occupied && kind != "missing-mode") return original
        var n = 0
        while (true) {
            val id = UUID.nameUUIDFromBytes("justnoise.local-import.v1:$kind:$original:${n++}".toByteArray(Charsets.UTF_8)).toString()
            if (id !in occupied) return id
        }
    }
}
