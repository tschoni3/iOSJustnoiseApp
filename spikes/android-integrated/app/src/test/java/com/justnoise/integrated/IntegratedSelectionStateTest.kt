package com.justnoise.integrated

import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.feature.AppSelectionChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedSelectionStateTest {
    private val editable = IntegratedSelectionState(
        visible = true,
        choices = listOf(
            AppSelectionChoice("synthetic.first", "First", true),
            AppSelectionChoice("synthetic.second", "Second", true),
            AppSelectionChoice("synthetic.unavailable", null, false),
        ),
        draft = setOf("synthetic.first", "synthetic.unavailable"),
        original = setOf("synthetic.first", "synthetic.unavailable"),
        token = SelectionEditToken("synthetic-process", 0L, 0L, null, 0L),
    )

    @Test
    fun togglingCopiesDraftWithoutChangingOriginalOrPriorState() {
        val changed = editable.withToggle("synthetic.second", true)
        assertTrue(changed.hasChanges)
        assertTrue("synthetic.second" in changed.draft)
        assertFalse("synthetic.second" in editable.draft)
        assertEquals(editable.original, changed.original)
        assertSame(editable.token, changed.token)
    }

    @Test
    fun unavailableSavedTargetCanBeRemovedButNotReadded() {
        val removed = editable.withToggle("synthetic.unavailable", false)
        val rejected = removed.withToggle("synthetic.unavailable", true)
        assertFalse("synthetic.unavailable" in rejected.draft)
        assertEquals(IntegratedSelectionProblem.REMOVAL_ONLY, rejected.problem)
        assertTrue(rejected.canEdit)
        assertEquals(editable.original, rejected.original)
    }

    @Test
    fun hiddenLoadingSavingStaleAndTokenlessStatesRejectLateTogglesAndSave() {
        listOf(
            editable.copy(visible = false), editable.copy(loading = true), editable.copy(saving = true),
            editable.copy(reloadRequired = true), editable.copy(token = null),
        ).forEach { guarded ->
            assertFalse(guarded.canSave)
            assertSame(guarded, guarded.withToggle("synthetic.second", true))
        }
    }

    @Test
    fun unknownPackageCannotEnterDraft() {
        assertSame(editable, editable.withToggle("synthetic.unknown", true))
    }

    @Test
    fun clearingIdleDraftRemainsExplicitlySaveableButDoesNotInventASelection() {
        val empty = editable.withToggle("synthetic.first", false).withToggle("synthetic.unavailable", false)
        assertTrue(empty.draft.isEmpty())
        assertTrue(empty.canSave)
        assertEquals(editable.original, empty.original)
        assertTrue(empty.hasChanges)
    }
}
