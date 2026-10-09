package com.justnoise.gate0.feature

import android.content.Context
import androidx.annotation.WorkerThread
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.ModeMutationResult
import com.justnoise.gate0.data.ModeRules
import com.justnoise.gate0.data.ModeSnapshot
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.platform.discovery.LaunchableApp
import com.justnoise.gate0.platform.discovery.LauncherAppRepository

data class ModeManagementSnapshot(
    val catalog: ModeSnapshot = ModeSnapshot(),
    val choices: List<AppSelectionChoice> = emptyList(),
    val error: Boolean = false,
)

/** Drafts belong to UI; discovery and package eligibility stay outside the storage lock. */
class ModeManagement internal constructor(
    private val read: () -> ModeSnapshot,
    private val discover: () -> List<LaunchableApp>,
    private val saveMode: (SelectionEditToken, String?, String, Set<String>) -> ModeMutationResult,
    private val selectMode: (SelectionEditToken, String) -> ModeMutationResult,
    private val deleteMode: (SelectionEditToken, String) -> ModeMutationResult,
    private val changed: () -> Unit,
) {
    constructor(context: Context, preferences: Gate0Preferences = Gate0Preferences(context)) : this(
        read = preferences::loadModes,
        discover = LauncherAppRepository(context.applicationContext)::load,
        saveMode = preferences::saveMode,
        selectMode = preferences::selectMode,
        deleteMode = preferences::deleteMode,
        changed = {
            // Edits are idle-only: they do not invalidate any active overlay. In particular,
            // don't send a delayed hide signal if a scheduled session wins after the edit.
            // Mode names never enter optional notifications. Alarms retain their own references.
            ScheduleRuntime(context.applicationContext).onConfigurationChanged()
        },
    )

    @WorkerThread
    fun load(): ModeManagementSnapshot = try {
        val apps = discover().distinctBy { it.packageName }
        val catalog = read() // Freeze write authority after app discovery.
        val available = apps.mapTo(mutableSetOf()) { it.packageName }
        val unavailable = catalog.modes.flatMap { it.selectedPackages }.toSet() - available
        ModeManagementSnapshot(catalog, apps.map { AppSelectionChoice(it.packageName, it.label, true) } +
            unavailable.sorted().map { AppSelectionChoice(it, null, false) }, error = catalog.error)
    } catch (_: Exception) { ModeManagementSnapshot(error = true) }

    @WorkerThread
    fun save(token: SelectionEditToken, id: String?, name: String, packages: Set<String>): ModeMutationResult = attempt {
        if (!ModeRules.validName(name)) return@attempt ModeMutationResult.INVALID_NAME
        if (!ModeRules.validTargets(packages)) return@attempt ModeMutationResult.INVALID_SELECTION
        val eligible = discover().mapTo(mutableSetOf()) { it.packageName }
        if (!eligible.containsAll(packages)) return@attempt ModeMutationResult.INVALID_SELECTION
        saveMode(token, id, name, packages.toSet())
    }

    @WorkerThread
    fun select(token: SelectionEditToken, id: String): ModeMutationResult = attempt {
        val current = read()
        if (current.error) return@attempt ModeMutationResult.STORAGE_FAILED
        if (current.sessionRequested) return@attempt ModeMutationResult.ACTIVE_SESSION
        if (current.recoveryRequired) return@attempt ModeMutationResult.RECOVERY_REQUIRED
        if (current.token != token) return@attempt ModeMutationResult.CONFLICT
        val mode = current.modes.find { it.id == id } ?: return@attempt ModeMutationResult.MODE_UNAVAILABLE
        val eligible = discover().mapTo(mutableSetOf()) { it.packageName }
        if (!ModeRules.validTargets(mode.selectedPackages) || !eligible.containsAll(mode.selectedPackages)) {
            return@attempt ModeMutationResult.INVALID_SELECTION
        }
        selectMode(token, id)
    }

    @WorkerThread
    fun delete(token: SelectionEditToken, id: String): ModeMutationResult = attempt { deleteMode(token, id) }

    private fun attempt(action: () -> ModeMutationResult): ModeMutationResult = try {
        val result = action()
        if (result == ModeMutationResult.SAVED || result == ModeMutationResult.STORAGE_FAILED) {
            // A post-commit rearm failure must not invite replay of a successful mode write.
            runCatching { changed() }
        }
        result
    } catch (_: Exception) { ModeMutationResult.STORAGE_FAILED }
}
