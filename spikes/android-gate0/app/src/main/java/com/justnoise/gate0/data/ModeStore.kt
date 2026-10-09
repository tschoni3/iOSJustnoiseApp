package com.justnoise.gate0.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

data class StoredMode(
    val id: String,
    val revision: Long,
    val name: String,
    val selectedPackages: Set<String>,
)

data class ModeCatalog(val modes: List<StoredMode> = emptyList(), val recentModeId: String? = null) {
    val recentMode: StoredMode? get() = modes.find { it.id == recentModeId }
}

data class ModeSnapshot(
    val modes: List<StoredMode> = emptyList(),
    val recentModeId: String? = null,
    val sessionRequested: Boolean = false,
    val recoveryRequired: Boolean = false,
    val error: Boolean = false,
    val token: SelectionEditToken? = null,
) {
    val recentMode: StoredMode? get() = modes.find { it.id == recentModeId }
}

enum class ModeMutationResult {
    SAVED, UNCHANGED, ACTIVE_SESSION, RECOVERY_REQUIRED, CONFLICT, INVALID_NAME,
    INVALID_SELECTION, MODE_UNAVAILABLE, REFERENCED_BY_SCHEDULE, STORAGE_FAILED, LIMIT_REACHED,
}

/** Product rules only; installed-app eligibility remains in the Android adapter. */
object ModeRules {
    const val MAX_MODES = 30
    const val MAX_NAME_LENGTH = 60
    const val MAX_TARGETS = 500

    fun validName(name: String): Boolean = name.trim().let {
        it.length in 1..MAX_NAME_LENGTH && it.none(Char::isISOControl)
    }

    fun validTargets(packages: Set<String>, allowEmpty: Boolean = false): Boolean =
        (allowEmpty || packages.isNotEmpty()) && packages.size <= MAX_TARGETS &&
            packages.all { it.length in 1..255 && it == it.trim() && it.none(Char::isWhitespace) && it.none(Char::isISOControl) }

    fun canDelete(modeId: String, scheduleModeIds: Set<String>): Boolean = modeId !in scheduleModeIds

    /** No first-mode fallback. A removed reference is a recoverable setup state. */
    fun modeForStart(catalog: ModeCatalog, modeId: String? = catalog.recentModeId): StoredMode? =
        catalog.modes.find { it.id == modeId }?.takeIf { validTargets(it.selectedPackages) }

    fun migrateLegacy(packages: Set<String>?): ModeCatalog = if (packages == null) ModeCatalog() else
        ModeCatalog(listOf(StoredMode(DEFAULT_SCHEDULE_MODE_REFERENCE, 1L, "Noise", packages.toSet())),
            DEFAULT_SCHEDULE_MODE_REFERENCE)
}

/** Bounded local configuration; an empty legacy selection remains explicitly incomplete. */
object ModeStoreCodec {
    private const val MAX_CHARACTERS = 262_144

    fun encode(catalog: ModeCatalog): String {
        validate(catalog)
        return JSONObject().put("schemaVersion", 1).put("recentModeId", catalog.recentModeId ?: JSONObject.NULL)
            .put("modes", JSONArray(catalog.modes.map { mode ->
                JSONObject().put("id", mode.id).put("revision", mode.revision).put("name", mode.name)
                    .put("selectedPackages", JSONArray(mode.selectedPackages.sorted()))
            })).toString().also { require(it.length <= MAX_CHARACTERS) }
    }

    fun decode(encoded: String): ModeCatalog {
        require(encoded.length <= MAX_CHARACTERS)
        val tokens = JSONTokener(encoded)
        val root = tokens.nextValue() as? JSONObject ?: invalid()
        require(tokens.nextClean() == '\u0000')
        root.fields(setOf("schemaVersion", "recentModeId", "modes"))
        require(integer(root.get("schemaVersion")) == 1L)
        val records = root.get("modes") as? JSONArray ?: invalid()
        require(records.length() <= ModeRules.MAX_MODES)
        val catalog = ModeCatalog(
            modes = (0 until records.length()).map { index ->
                val record = records.get(index) as? JSONObject ?: invalid()
                record.fields(setOf("id", "revision", "name", "selectedPackages"))
                val targets = record.get("selectedPackages") as? JSONArray ?: invalid()
                require(targets.length() <= ModeRules.MAX_TARGETS)
                val packages = (0 until targets.length()).map { targets.get(it) as? String ?: invalid() }
                require(packages.size == packages.toSet().size)
                StoredMode(record.get("id") as? String ?: invalid(), integer(record.get("revision")),
                    record.get("name") as? String ?: invalid(), packages.toSet())
            },
            recentModeId = root.get("recentModeId").let { if (it == JSONObject.NULL) null else it as? String ?: invalid() },
        )
        validate(catalog)
        return catalog
    }

    private fun validate(catalog: ModeCatalog) {
        require(catalog.modes.size <= ModeRules.MAX_MODES)
        require(catalog.modes.map { it.id }.distinct().size == catalog.modes.size)
        // A dangling recent ID is preserved, not silently repaired into another mode.
        require(catalog.recentModeId == null || validId(catalog.recentModeId))
        catalog.modes.forEach {
            require(validId(it.id) && it.revision > 0L)
            require(ModeRules.validName(it.name) && it.name == it.name.trim())
            require(ModeRules.validTargets(it.selectedPackages, allowEmpty = true))
        }
    }

    private fun validId(id: String): Boolean = id == DEFAULT_SCHEDULE_MODE_REFERENCE ||
        (id.length == 36 && runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))

    private fun JSONObject.fields(expected: Set<String>) { require(keys().asSequence().toSet() == expected) }
    private fun integer(value: Any): Long = when (value) { is Int -> value.toLong(); is Long -> value; else -> invalid() }
    private fun invalid(): Nothing = throw IllegalArgumentException("Invalid mode configuration")
}
