package com.justnoise.integrated

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.justnoise.gate0.data.StoredSchedule
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.feature.ScheduleManagement
import com.justnoise.gate0.feature.ScheduleManagementSnapshot
import com.justnoise.gate0.feature.ScheduleMutationFailure
import com.justnoise.gate0.feature.ScheduleMutationResult
import java.time.ZoneId
import java.util.concurrent.Executors

data class IntegratedScheduleState(
    val snapshot: ScheduleManagementSnapshot? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val editor: IntegratedScheduleEditor? = null,
    val editorError: String? = null,
    val mutationUncertain: Boolean = false,
) {
    val canEditDraft: Boolean
        get() = editor != null && !loading && !saving && !mutationUncertain

    fun withEditorChange(changed: IntegratedScheduleEditor): IntegratedScheduleState {
        val current = editor ?: return this
        if (!canEditDraft || current.id != changed.id || current.expectedRevision != changed.expectedRevision) return this
        return copy(editor = changed, editorError = null)
    }
}

/** Retains the one in-flight mutation across rotation; no Activity or NFC reader is retained. */
class IntegratedScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val management = ScheduleManagement(application)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var initialized = false
    private var closed = false
    private var loadGeneration = 0

    var state by mutableStateOf(IntegratedScheduleState())
        private set

    fun initialize(saved: Bundle?) {
        if (initialized) return
        initialized = true
        val interruptedMutation = saved?.getBoolean("mutation_running") == true
        state = state.copy(
            editor = saved?.getBundle("editor")?.let(::restoreEditor),
            mutationUncertain = interruptedMutation,
            editorError = if (interruptedMutation) UNCERTAIN_SAVE else null,
            message = if (interruptedMutation) UNCERTAIN_SAVE else null,
        )
    }

    fun saveInstanceState(): Bundle = Bundle().apply {
        putBoolean("mutation_running", state.saving || state.mutationUncertain)
        state.editor?.let { putBundle("editor", storeEditor(it)) }
    }

    fun load() = load(refreshRuntime = false)

    /** A deliberate refresh may settle an interrupted list mutation, never repeat it. */
    fun refresh() = load(refreshRuntime = true)

    private fun load(refreshRuntime: Boolean) {
        if (closed || state.loading || state.saving) return
        val generation = ++loadGeneration
        state = state.copy(loading = true)
        worker.execute {
            val snapshot = if (refreshRuntime) {
                try { management.refresh() } catch (_: Exception) { ScheduleManagementSnapshot(error = true) }
            } else readSnapshot()
            main.post {
                if (!closed && generation == loadGeneration) {
                    val revoked = !snapshot.error && !snapshot.consentAccepted
                    val reviewedUncertainMutation = refreshRuntime && !snapshot.error && state.editor == null &&
                        state.mutationUncertain
                    state = state.copy(
                        snapshot = snapshot,
                        loading = false,
                        editor = if (revoked) null else state.editor,
                        editorError = if (revoked) null else state.editorError,
                        mutationUncertain = !revoked && !reviewedUncertainMutation && state.mutationUncertain,
                        message = if (reviewedUncertainMutation) {
                            "Saved schedules refreshed. The previous change was not repeated."
                        } else state.message,
                    )
                }
            }
        }
    }

    fun newSchedule() {
        if (state.saving || state.snapshot?.consentAccepted != true || state.snapshot?.error == true) return
        state = state.copy(
            editor = IntegratedScheduleEditor.new(System.currentTimeMillis(), ZoneId.systemDefault().id),
            editorError = null,
            message = null,
            mutationUncertain = false,
        )
    }

    fun edit(schedule: StoredSchedule) {
        if (state.saving) return
        state = state.copy(
            editor = IntegratedScheduleEditor.fromStored(
                schedule, System.currentTimeMillis(), ZoneId.systemDefault().id,
            ),
            editorError = null,
            message = null,
            mutationUncertain = false,
        )
    }

    fun changeEditor(editor: IntegratedScheduleEditor) {
        // Reject callbacks during reload and after cancellation, including delayed pickers.
        state = state.withEditorChange(editor)
    }

    fun cancelEditor() {
        if (state.saving) return
        state = state.copy(editor = null, editorError = null, mutationUncertain = false)
        load()
    }

    /** Explicit reload discards edits; the surface asks first. */
    fun reloadEditor() {
        val previous = state.editor ?: return
        if (state.saving || state.loading) return
        val id = previous.id ?: run { cancelEditor(); return }
        val generation = ++loadGeneration
        state = state.copy(loading = true)
        worker.execute {
            val snapshot = readSnapshot()
            main.post {
                if (!closed && generation == loadGeneration) {
                    if (state.editor?.id != id) {
                        val revoked = !snapshot.error && !snapshot.consentAccepted
                        state = state.copy(
                            snapshot = snapshot, loading = false,
                            editor = if (revoked) null else state.editor,
                            editorError = if (revoked) null else state.editorError,
                            mutationUncertain = !revoked && state.mutationUncertain,
                        )
                        return@post
                    }
                    val latest = snapshot.schedules.find { it.id == id }
                    state = when {
                        snapshot.error -> state.copy(
                            snapshot = snapshot, loading = false,
                            editorError = "Couldn’t reload. Your draft is still here; check storage and retry.",
                        )
                        !snapshot.consentAccepted -> state.copy(
                            snapshot = snapshot, loading = false, editor = null,
                            editorError = null, mutationUncertain = false,
                        )
                        latest == null -> state.copy(
                            snapshot = snapshot, loading = false,
                            editorError = "This schedule was deleted. Go back to the list; this draft was not saved.",
                        )
                        else -> state.copy(
                            snapshot = snapshot, loading = false,
                            editor = IntegratedScheduleEditor.fromStored(
                                latest, System.currentTimeMillis(), ZoneId.systemDefault().id,
                            ),
                            editorError = null, mutationUncertain = false,
                        )
                    }
                }
            }
        }
    }

    fun save() {
        val editor = state.editor ?: return
        if (state.saving || state.mutationUncertain) return
        when (val result = editor.toDraft(System.currentTimeMillis(), ZoneId.systemDefault().id)) {
            is IntegratedScheduleEditorResult.Invalid -> state = state.copy(editorError = result.message)
            is IntegratedScheduleEditorResult.Valid -> mutate("Schedule saved.") { consentEpoch ->
                management.save(result.draft, editor.id, editor.expectedRevision, consentEpoch)
            }
        }
    }

    fun setEnabled(schedule: StoredSchedule, enabled: Boolean) = mutate(
        if (enabled) "Automatic start requested." else "Schedule disabled. Any active session keeps running.",
    ) { consentEpoch -> management.setEnabled(schedule.id, schedule.revision, enabled, consentEpoch) }

    fun delete(schedule: StoredSchedule) = mutate(
        "Schedule deleted. Any active session keeps running.",
    ) { consentEpoch -> management.delete(schedule.id, schedule.revision, consentEpoch) }

    fun showMessage(message: String) { state = state.copy(message = message) }

    private fun mutate(successMessage: String, action: (Long) -> ScheduleMutationResult) {
        if (closed || state.saving) return
        val consentEpoch = state.snapshot?.consentEpochMilliseconds ?: 0L
        loadGeneration += 1
        state = state.copy(saving = true, loading = false, message = null, editorError = null)
        worker.execute {
            val result = try {
                action(consentEpoch)
            } catch (_: Exception) {
                ScheduleMutationResult(saved = false, failure = ScheduleMutationFailure.INVALID_OR_FAILED_WRITE)
            }
            val snapshot = readSnapshot()
            main.post {
                if (!closed) {
                    val message = when {
                        !snapshot.error && !snapshot.consentAccepted -> "Review Setup before using schedules."
                        result.saved && result.failure == ScheduleMutationFailure.REARM_FAILED ->
                            "Saved, but scheduling could not refresh. Check setup and retry Refresh."
                        result.saved && result.setupRequired -> "$successMessage Setup is still required for automatic starts."
                        result.saved -> successMessage
                        result.failure == ScheduleMutationFailure.CONSENT_REQUIRED ->
                            "Review the current disclosure in Setup before saving schedules."
                        result.failure == ScheduleMutationFailure.CONFLICT ->
                            "This schedule changed. Reload it before trying again; your edits were not saved."
                        else -> "Couldn’t save the change. Check the date, time and free storage, then refresh or retry."
                    }
                    val revoked = !snapshot.error && !snapshot.consentAccepted
                    state = state.copy(
                        snapshot = snapshot, saving = false, loading = false, message = message,
                        editor = if (result.saved || revoked) null else state.editor,
                        editorError = if (result.saved || revoked) null else message,
                        mutationUncertain = false,
                    )
                }
            }
        }
    }

    private fun readSnapshot(): ScheduleManagementSnapshot = try {
        management.load()
    } catch (_: Exception) {
        ScheduleManagementSnapshot(error = true)
    }

    override fun onCleared() {
        closed = true
        loadGeneration += 1
        worker.shutdown()
        super.onCleared()
    }

    private fun storeEditor(editor: IntegratedScheduleEditor) = Bundle().apply {
        putString("id", editor.id)
        editor.expectedRevision?.let { putLong("revision", it) }
        putString("name", editor.name)
        putBoolean("repeating", editor.isRepeating)
        putString("date", editor.localDate)
        putInt("hour", editor.hour)
        putInt("minute", editor.minute)
        putStringArrayList("weekdays", ArrayList(editor.weekdays.map { it.name }))
        putBoolean("enabled", editor.enabled)
        putString("zone", editor.zoneId)
        editor.originalOneTimeEpochMilliseconds?.let { putLong("original_instant", it) }
        putString("original_zone", editor.originalOneTimeZoneId)
    }

    private fun restoreEditor(bundle: Bundle): IntegratedScheduleEditor? = try {
        IntegratedScheduleEditor(
            id = bundle.getString("id"),
            expectedRevision = if (bundle.containsKey("revision")) bundle.getLong("revision") else null,
            name = bundle.getString("name") ?: "",
            isRepeating = bundle.getBoolean("repeating"),
            localDate = requireNotNull(bundle.getString("date")),
            hour = bundle.getInt("hour"), minute = bundle.getInt("minute"),
            weekdays = bundle.getStringArrayList("weekdays").orEmpty().map(PortableWeekday::valueOf).toSet(),
            enabled = bundle.getBoolean("enabled"), zoneId = requireNotNull(bundle.getString("zone")),
            originalOneTimeEpochMilliseconds =
                if (bundle.containsKey("original_instant")) bundle.getLong("original_instant") else null,
            originalOneTimeZoneId = bundle.getString("original_zone"),
        )
    } catch (_: Exception) { null }

    private companion object {
        const val UNCERTAIN_SAVE = "A previous save may have completed before the app closed. " +
            "Review the schedule list before creating another; nothing will be saved again automatically."
    }
}
