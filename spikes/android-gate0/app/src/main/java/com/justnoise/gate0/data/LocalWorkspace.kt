package com.justnoise.gate0.data

import org.json.JSONObject
import java.util.UUID

/** Local storage identity only; a UUID here is not authentication/authorization. */
class LocalWorkspaceId private constructor(val value: String) {
    val historyDatabaseName: String get() = if (this == LOCAL) "session_history.db" else "session_history_$value.db"
    override fun equals(other: Any?) = other is LocalWorkspaceId && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = "LocalWorkspaceId([redacted])"
    companion object {
        val LOCAL = LocalWorkspaceId("local")
        fun parse(value: String): LocalWorkspaceId {
            if (value == "local") return LOCAL
            require(value.length == 36 &&
                runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
                "Invalid local workspace identity"
            }
            return LocalWorkspaceId(value)
        }
    }
}

data class WorkspaceEditToken(val workspace: LocalWorkspaceId, val state: SelectionEditToken)
data class WorkspaceReadToken(val workspace: LocalWorkspaceId, val epoch: String?, val process: String, val generation: Long)
enum class WorkspaceSwitchResult { SWITCHED_PENDING, UNCHANGED, ACTIVE_SESSION, RECOVERY_REQUIRED, CONFLICT, STORAGE_FAILED }

/** Only product configuration is archived; never pairing, credentials or live service state. */
internal data class WorkspaceProfile(val modes: ModeCatalog, val schedules: List<StoredSchedule>, val executions: List<ScheduleExecution>) {
    fun encode(): String = JSONObject().apply {
        put("version", 1)
        put("modes", ModeStoreCodec.encode(modes))
        put("schedules", ScheduleStoreCodec.encode(schedules))
        put("executions", ScheduleExecutionCodec.encode(executions))
    }.toString().also { require(it.length <= MAX_ENCODED_CHARACTERS) }

    companion object {
        private const val MAX_ENCODED_CHARACTERS = 3_145_728
        fun decode(encoded: String): WorkspaceProfile {
            require(encoded.length <= MAX_ENCODED_CHARACTERS)
            return JSONObject(encoded).let {
                require(it.getInt("version") == 1)
                WorkspaceProfile(ModeStoreCodec.decode(it.getString("modes")),
                    ScheduleStoreCodec.decode(it.getString("schedules")),
                    ScheduleExecutionCodec.decode(it.getString("executions")))
            }
        }
        fun empty() = WorkspaceProfile(ModeRules.migrateLegacy(null), emptyList(), emptyList())
    }
}
