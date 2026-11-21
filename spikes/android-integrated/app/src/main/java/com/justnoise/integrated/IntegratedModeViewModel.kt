package com.justnoise.integrated

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.justnoise.gate0.data.ModeMutationResult
import com.justnoise.gate0.data.ModeRules
import com.justnoise.gate0.data.ModeSnapshot
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.feature.AppSelectionChoice
import com.justnoise.gate0.feature.ModeManagement
import com.justnoise.gate0.feature.ModeManagementSnapshot
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class IntegratedModeProblem {
    LOAD_FAILED, STORAGE_FAILED, ACTIVE_SESSION, RECOVERY_REQUIRED, CONFLICT,
    INVALID_NAME, INVALID_SELECTION, MODE_UNAVAILABLE, REFERENCED_BY_SCHEDULE,
    LIMIT_REACHED, REMOVAL_ONLY,
}

data class IntegratedModeEditor(
    val id: String? = null,
    val name: String = "",
    val packages: Set<String> = emptySet(),
    val originalName: String = "",
    val originalPackages: Set<String> = emptySet(),
) {
    val hasChanges: Boolean get() = name != originalName || packages != originalPackages
    val valid: Boolean get() = ModeRules.validName(name) && ModeRules.validTargets(packages)
}

data class IntegratedModeState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val catalog: ModeSnapshot? = null,
    val choices: List<AppSelectionChoice> = emptyList(),
    val editor: IntegratedModeEditor? = null,
    val problem: IntegratedModeProblem? = null,
    val reloadRequired: Boolean = false,
    val savedVersion: Long = 0L,
) {
    val canMutate: Boolean get() = visible && !loading && !saving && !reloadRequired &&
        catalog?.let { !it.error && !it.sessionRequested && !it.recoveryRequired && it.token != null } == true
    val canSave: Boolean get() = canMutate && editor?.valid == true

    fun withName(name: String): IntegratedModeState = if (!canMutate || editor == null) this
        else copy(editor = editor.copy(name = name), problem = null)

    fun withToggle(packageName: String, selected: Boolean): IntegratedModeState {
        if (!canMutate || editor == null) return this
        val choice = choices.firstOrNull { it.packageName == packageName } ?: return this
        if (selected && !choice.available) return copy(problem = IntegratedModeProblem.REMOVAL_ONLY)
        return copy(
            editor = editor.copy(packages = if (selected) editor.packages + packageName else editor.packages - packageName),
            problem = null,
        )
    }

    fun withoutDraft(): IntegratedModeState = IntegratedModeState(saving = saving, savedVersion = savedVersion)
}

/** One worker owns real catalog operations. No draft/token or queued write is serialized. */
class IntegratedModeViewModel internal constructor(
    application: Application,
    private val management: ModeManagement,
    private val worker: ExecutorService,
    private val main: Handler,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application, ModeManagement(application), Executors.newSingleThreadExecutor(), Handler(Looper.getMainLooper()),
    )

    private var generation = 0L
    private var closed = false
    var state by mutableStateOf(IntegratedModeState())
        private set

    @MainThread
    fun open() {
        if (!closed && !state.visible && !state.saving) loadFresh()
    }

    @MainThread
    fun reload() {
        if (!closed && state.visible && !state.loading && !state.saving) loadFresh()
    }

    private fun loadFresh() {
        val request = ++generation
        state = IntegratedModeState(visible = true, loading = true, savedVersion = state.savedVersion)
        worker.execute {
            val snapshot = runCatching { management.load() }.getOrNull()
            main.post {
                if (!closed && request == generation && state.visible) applyLoaded(snapshot)
            }
        }
    }

    private fun applyLoaded(snapshot: ModeManagementSnapshot?) {
        val catalog = snapshot?.catalog
        val problem = when {
            snapshot == null || snapshot.error || catalog == null || catalog.error -> IntegratedModeProblem.LOAD_FAILED
            catalog.sessionRequested -> IntegratedModeProblem.ACTIVE_SESSION
            catalog.recoveryRequired || catalog.token == null -> IntegratedModeProblem.RECOVERY_REQUIRED
            else -> null
        }
        state = state.copy(
            loading = false, catalog = catalog, choices = snapshot?.choices.orEmpty().toList(),
            problem = problem, reloadRequired = problem != null,
        )
    }

    @MainThread
    fun newMode() {
        if (!closed && state.canMutate && state.editor == null) {
            state = state.copy(editor = IntegratedModeEditor(), problem = null)
        }
    }

    @MainThread
    fun edit(id: String) {
        if (closed || !state.canMutate || state.editor != null) return
        val mode = state.catalog?.modes?.firstOrNull { it.id == id } ?: return
        state = state.copy(
            editor = IntegratedModeEditor(mode.id, mode.name, mode.selectedPackages.toSet(),
                mode.name, mode.selectedPackages.toSet()),
            problem = null,
        )
    }

    @MainThread
    fun changeName(name: String) { if (!closed) state = state.withName(name) }

    @MainThread
    fun toggle(packageName: String, selected: Boolean) {
        if (!closed) state = state.withToggle(packageName, selected)
    }

    @MainThread
    fun save() {
        if (closed || !state.canSave) return
        val editor = state.editor ?: return
        val packages = editor.packages.toSet()
        mutate { token -> management.save(token, editor.id, editor.name, packages) }
    }

    /** Rows select existing modes. Creating a mode selects it in the same durable save. */
    @MainThread
    fun select(id: String) {
        if (closed || !state.canMutate || state.editor != null) return
        mutate { token -> management.select(token, id) }
    }

    @MainThread
    fun delete() {
        if (closed || !state.canMutate) return
        val id = state.editor?.id ?: return
        mutate { token -> management.delete(token, id) }
    }

    private fun mutate(write: (SelectionEditToken) -> ModeMutationResult) {
        val token = state.catalog?.token ?: return
        val request = generation
        state = state.copy(saving = true, problem = null)
        worker.execute {
            val result = runCatching { write(token) }.getOrDefault(ModeMutationResult.STORAGE_FAILED)
            main.post {
                if (closed) return@post
                val saved = result == ModeMutationResult.SAVED || result == ModeMutationResult.UNCHANGED
                val savedVersion = state.savedVersion + if (saved) 1L else 0L
                if (request != generation || !state.visible) {
                    // A confirmed write may settle after pause, but never reopens or replays a draft.
                    state = IntegratedModeState(savedVersion = savedVersion)
                } else if (saved) {
                    state = state.copy(saving = false, savedVersion = savedVersion)
                    loadFresh()
                } else {
                    val problem = result.problem()
                    state = state.copy(saving = false, problem = problem,
                        reloadRequired = problem !in setOf(IntegratedModeProblem.INVALID_NAME,
                            IntegratedModeProblem.INVALID_SELECTION, IntegratedModeProblem.REFERENCED_BY_SCHEDULE))
                }
            }
        }
    }

    @MainThread
    fun cancelEditor() {
        if (!closed && state.visible && !state.saving && state.editor != null) loadFresh()
    }

    @MainThread
    fun dismiss() {
        if (!closed && !state.saving) onHostPaused()
    }

    @MainThread
    fun onHostPaused() {
        if (closed) return
        generation += 1L
        state = state.withoutDraft()
    }

    override fun onCleared() {
        closed = true
        generation += 1L
        worker.shutdown()
        super.onCleared()
    }
}

private fun ModeMutationResult.problem(): IntegratedModeProblem = when (this) {
    ModeMutationResult.ACTIVE_SESSION -> IntegratedModeProblem.ACTIVE_SESSION
    ModeMutationResult.RECOVERY_REQUIRED -> IntegratedModeProblem.RECOVERY_REQUIRED
    ModeMutationResult.CONFLICT -> IntegratedModeProblem.CONFLICT
    ModeMutationResult.INVALID_NAME -> IntegratedModeProblem.INVALID_NAME
    ModeMutationResult.INVALID_SELECTION -> IntegratedModeProblem.INVALID_SELECTION
    ModeMutationResult.MODE_UNAVAILABLE -> IntegratedModeProblem.MODE_UNAVAILABLE
    ModeMutationResult.REFERENCED_BY_SCHEDULE -> IntegratedModeProblem.REFERENCED_BY_SCHEDULE
    ModeMutationResult.LIMIT_REACHED -> IntegratedModeProblem.LIMIT_REACHED
    else -> IntegratedModeProblem.STORAGE_FAILED
}
