package com.justnoise.integrated

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.justnoise.gate0.data.SelectionEditResult
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.feature.AppSelectionChoice
import com.justnoise.gate0.feature.AppSelectionManagement
import com.justnoise.gate0.feature.AppSelectionSnapshot
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class IntegratedSelectionProblem {
    LOAD_FAILED, SAVE_FAILED, CONFLICT, ACTIVE_SESSION, RECOVERY_REQUIRED, INVALID_SELECTION, REMOVAL_ONLY,
}

data class IntegratedSelectionState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val choices: List<AppSelectionChoice> = emptyList(),
    val draft: Set<String> = emptySet(),
    val original: Set<String> = emptySet(),
    val token: SelectionEditToken? = null,
    val problem: IntegratedSelectionProblem? = null,
    val reloadRequired: Boolean = false,
    val savedVersion: Long = 0L,
) {
    val canEdit: Boolean get() = visible && !loading && !saving && token != null && !reloadRequired
    val canSave: Boolean get() = canEdit
    val hasChanges: Boolean get() = draft != original

    /** Immutable draft only. Eligibility is checked again by the existing manager at Save. */
    fun withToggle(packageName: String, selected: Boolean): IntegratedSelectionState {
        if (!canEdit) return this
        val choice = choices.firstOrNull { it.packageName == packageName } ?: return this
        if (selected && !choice.available) return copy(problem = IntegratedSelectionProblem.REMOVAL_ONLY)
        val changed = if (selected) draft + packageName else draft - packageName
        return copy(draft = changed, problem = null)
    }
}

/**
 * Presentation owner for the existing selection manager. No draft/token is serialized and no
 * Activity is retained. A confirmed Save may settle once after closing; it is never replayed.
 */
class IntegratedSelectionViewModel internal constructor(
    application: Application,
    private val management: AppSelectionManagement,
    private val worker: ExecutorService,
    private val main: Handler,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application, AppSelectionManagement(application), Executors.newSingleThreadExecutor(),
        Handler(Looper.getMainLooper()),
    )

    private var generation = 0L
    private var closed = false

    var state by mutableStateOf(IntegratedSelectionState())
        private set

    @MainThread
    fun open() {
        if (closed || state.visible || state.saving) return
        loadFresh()
    }

    /** Explicit reload discards a draft; the surface confirms when there are unsaved changes. */
    @MainThread
    fun reload() {
        if (closed || !state.visible || state.loading || state.saving) return
        loadFresh()
    }

    private fun loadFresh() {
        val request = ++generation
        state = IntegratedSelectionState(visible = true, loading = true, savedVersion = state.savedVersion)
        worker.execute {
            val snapshot = management.load()
            main.post {
                if (!closed && request == generation && state.visible) applyLoaded(snapshot)
            }
        }
    }

    private fun applyLoaded(snapshot: AppSelectionSnapshot) {
        val edit = snapshot.edit
        val problem = when {
            snapshot.error || edit == null || edit.error -> IntegratedSelectionProblem.LOAD_FAILED
            edit.sessionRequested -> IntegratedSelectionProblem.ACTIVE_SESSION
            edit.recoveryRequired || edit.token == null -> IntegratedSelectionProblem.RECOVERY_REQUIRED
            else -> null
        }
        state = state.copy(
            loading = false,
            choices = snapshot.choices.toList(),
            draft = edit?.selectedPackages.orEmpty().toSet(),
            original = edit?.selectedPackages.orEmpty().toSet(),
            token = if (problem == null) edit?.token else null,
            problem = problem,
            reloadRequired = problem != null,
        )
    }

    @MainThread
    fun toggle(packageName: String, selected: Boolean) {
        if (!closed) state = state.withToggle(packageName, selected)
    }

    @MainThread
    fun save() {
        if (closed || !state.canSave) return
        val token = state.token ?: return
        val draft = state.draft.toSet()
        val request = generation
        state = state.copy(saving = true, problem = null)
        worker.execute {
            val result = management.save(token, draft)
            main.post {
                if (closed) return@post
                val saved = result == SelectionEditResult.SAVED || result == SelectionEditResult.UNCHANGED
                val savedVersion = state.savedVersion + if (saved) 1L else 0L
                if (saved || request != generation || !state.visible) {
                    // A late completion cannot reopen a dismissed sheet or restore its old draft.
                    state = IntegratedSelectionState(savedVersion = savedVersion)
                } else {
                    val problem = when (result) {
                        SelectionEditResult.CONFLICT -> IntegratedSelectionProblem.CONFLICT
                        SelectionEditResult.ACTIVE_SESSION -> IntegratedSelectionProblem.ACTIVE_SESSION
                        SelectionEditResult.RECOVERY_REQUIRED -> IntegratedSelectionProblem.RECOVERY_REQUIRED
                        SelectionEditResult.INVALID_SELECTION -> IntegratedSelectionProblem.INVALID_SELECTION
                        else -> IntegratedSelectionProblem.SAVE_FAILED
                    }
                    state = state.copy(
                        saving = false, problem = problem,
                        // Only known pre-write validation permits another edited Save. Storage failure
                        // and stale authority need an explicit fresh read, never an automatic retry.
                        reloadRequired = problem != IntegratedSelectionProblem.INVALID_SELECTION,
                    )
                }
            }
        }
    }

    @MainThread
    fun cancel() {
        if (closed || state.saving) return
        discardPresentation()
    }

    /** Host lifecycle/safety wins over modal dismissal restrictions, including during Save. */
    @MainThread
    fun onHostPaused() {
        if (!closed) discardPresentation()
    }

    private fun discardPresentation() {
        generation += 1
        state = IntegratedSelectionState(saving = state.saving, savedVersion = state.savedVersion)
    }

    override fun onCleared() {
        closed = true
        generation += 1
        worker.shutdown()
        super.onCleared()
    }
}
