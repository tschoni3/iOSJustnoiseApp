package com.justnoise.gate0.feature

import android.content.Context
import androidx.annotation.WorkerThread
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.SelectionEditResult
import com.justnoise.gate0.data.SelectionEditSnapshot
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.platform.discovery.LaunchableApp
import com.justnoise.gate0.platform.discovery.LauncherAppRepository

data class AppSelectionChoice(
    val packageName: String,
    val label: String?,
    val available: Boolean,
)

data class AppSelectionSnapshot(
    val edit: SelectionEditSnapshot? = null,
    val choices: List<AppSelectionChoice> = emptyList(),
    val error: Boolean = false,
)

/** Shared by native setup and the future Compose sheet; no Activity, draft or permission ownership. */
class AppSelectionManagement internal constructor(
    private val readEdit: () -> SelectionEditSnapshot,
    private val discover: () -> List<LaunchableApp>,
    private val commit: (SelectionEditToken, Set<String>) -> SelectionEditResult,
) {
    constructor(context: Context, preferences: Gate0Preferences = Gate0Preferences(context)) : this(
        readEdit = preferences::readSelectionEdit,
        discover = LauncherAppRepository(context.applicationContext)::load,
        commit = Gate0Controller(context.applicationContext, preferences)::commitTargetSelection,
    )

    @WorkerThread
    fun load(): AppSelectionSnapshot = try {
        val apps = discover().filter { it.packageName.isNotBlank() }.distinctBy { it.packageName }
        // Freeze edit authority after discovery. The UI never refreshes this token under a draft.
        val edit = readEdit()
        if (edit.error) AppSelectionSnapshot(error = true) else {
            val available = apps.mapTo(mutableSetOf()) { it.packageName }
            AppSelectionSnapshot(
                edit = edit,
                choices = apps.map { AppSelectionChoice(it.packageName, it.label, available = true) } +
                    (edit.selectedPackages - available).sorted().map {
                        // Missing/newly protected saved targets remain visible, but removal-only.
                        AppSelectionChoice(it, label = null, available = false)
                    },
            )
        }
    } catch (_: Exception) {
        AppSelectionSnapshot(error = true)
    }

    @WorkerThread
    fun save(token: SelectionEditToken, draft: Set<String>): SelectionEditResult = try {
        commit(token, draft.toSet())
    } catch (_: Exception) {
        SelectionEditResult.STORAGE_FAILED
    }
}
