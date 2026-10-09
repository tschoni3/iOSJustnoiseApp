package com.justnoise.integrated

import com.justnoise.gate0.data.ModeRules
import com.justnoise.gate0.data.ModeSnapshot
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.data.StoredMode
import com.justnoise.gate0.feature.AppSelectionChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedModeStateTest {
    private val saved = StoredMode("synthetic-mode", 1L, "Sleep", setOf("synthetic.first"))
    private val editable = IntegratedModeState(
        visible = true,
        catalog = ModeSnapshot(modes = listOf(saved), recentModeId = saved.id,
            token = SelectionEditToken("synthetic-process", 0L, 0L, null, 0L)),
        editor = IntegratedModeEditor(saved.id, saved.name, saved.selectedPackages,
            saved.name, saved.selectedPackages),
        choices = listOf(
            AppSelectionChoice("synthetic.first", "First", true),
            AppSelectionChoice("synthetic.second", "Second", true),
            AppSelectionChoice("synthetic.unavailable", null, false),
        ),
    )

    @Test
    fun editingNameAndTargetsNeverMutatesSavedModeOrRecentSelection() {
        val changed = editable.withName("Focus").withToggle("synthetic.second", true)
        assertEquals("Focus", changed.editor?.name)
        assertEquals(setOf("synthetic.first", "synthetic.second"), changed.editor?.packages)
        assertEquals(saved, changed.catalog?.recentMode)
        assertEquals(editable.catalog, changed.catalog)
        assertEquals(saved.name, editable.editor?.name)
        assertEquals(saved.selectedPackages, editable.editor?.packages)
        assertTrue(changed.editor?.hasChanges == true)
    }

    @Test
    fun creationHasNoAutomaticSelectedMode() {
        val creating = editable.copy(catalog = editable.catalog?.copy(recentModeId = null),
            editor = IntegratedModeEditor(name = "Focus", packages = setOf("synthetic.first")))
        assertTrue(creating.canSave)
        assertNull(creating.catalog?.recentMode)
        assertNull(creating.editor?.id)
    }

    @Test
    fun blankLongControlNamesAndEmptySelectionCannotSave() {
        listOf("", "   ", "A".repeat(ModeRules.MAX_NAME_LENGTH + 1), "Sleep\nFocus").forEach { name ->
            assertFalse(editable.withName(name).canSave)
        }
        assertTrue(editable.withName("A".repeat(ModeRules.MAX_NAME_LENGTH)).canSave)
        assertTrue(editable.withName("  Sleep  ").canSave)
        assertFalse(editable.withToggle("synthetic.first", false).canSave)
    }

    @Test
    fun missingTargetCanBeRemovedButNotReadded() {
        val retained = editable.copy(editor = editable.editor?.copy(
            packages = setOf("synthetic.first", "synthetic.unavailable")))
        val removed = retained.withToggle("synthetic.unavailable", false)
        val rejected = removed.withToggle("synthetic.unavailable", true)
        assertEquals(setOf("synthetic.first"), rejected.editor?.packages)
        assertEquals(IntegratedModeProblem.REMOVAL_ONLY, rejected.problem)
        assertTrue(rejected.canMutate)
        assertEquals(saved, rejected.catalog?.recentMode)
    }

    @Test
    fun unknownTargetsNeverEnterTheDraft() {
        assertSame(editable, editable.withToggle("synthetic.unknown", true))
    }

    @Test
    fun hiddenBusyStaleAndReadOnlyCatalogsRejectAllDraftChanges() {
        listOf(
            editable.copy(visible = false), editable.copy(loading = true), editable.copy(saving = true),
            editable.copy(reloadRequired = true), editable.copy(catalog = null),
            editable.copy(catalog = editable.catalog?.copy(token = null)),
            editable.copy(catalog = editable.catalog?.copy(error = true)),
            editable.copy(catalog = editable.catalog?.copy(sessionRequested = true)),
            editable.copy(catalog = editable.catalog?.copy(recoveryRequired = true)),
        ).forEach { guarded ->
            assertFalse(guarded.canMutate)
            assertFalse(guarded.canSave)
            assertSame(guarded, guarded.withName("Changed"))
            assertSame(guarded, guarded.withToggle("synthetic.second", true))
        }
    }

    @Test
    fun closingDiscardsDraftAndAuthorityButPreservesInFlightWriteBoundary() {
        val busy = editable.withName("Unsaved").copy(saving = true, savedVersion = 4L)
        val hidden = busy.withoutDraft()
        assertFalse(hidden.visible)
        assertTrue(hidden.saving)
        assertFalse(hidden.canMutate)
        assertNull(hidden.editor)
        assertNull(hidden.catalog)
        assertTrue(hidden.choices.isEmpty())
        assertEquals(4L, hidden.savedVersion)
    }

    @Test
    fun missingRememberedModeNeverFallsBackToFirstSavedMode() {
        val missing = editable.copy(catalog = editable.catalog?.copy(recentModeId = "synthetic-removed"))
        assertTrue(missing.catalog?.modes?.isNotEmpty() == true)
        assertNull(missing.catalog?.recentMode)
        assertEquals("synthetic-removed", missing.catalog?.recentModeId)
    }
}
