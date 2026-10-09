package com.justnoise.gate0.feature

import com.justnoise.gate0.data.ModeMutationResult
import com.justnoise.gate0.data.ModeSnapshot
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.data.StoredMode
import com.justnoise.gate0.platform.discovery.LaunchableApp
import org.junit.Assert.*
import org.junit.Test

/** Adapter checks; transaction guards are covered separately by ModePersistenceTest. */
class ModeManagementTest {
    private val token = SelectionEditToken("mode-test-process", 1L, 3L, null, 0L)
    private val mode = StoredMode("11111111-1111-4111-8111-111111111111", 1L, "Work", setOf("present"))

    @Test
    fun loadRetainsUnavailableSavedTargetsAsRemovalOnlyWithoutWriting() {
        val harness = Harness().apply {
            snapshot = snapshot.copy(modes = listOf(mode.copy(selectedPackages = setOf("present", "missing", "protected"))))
            apps = listOf(LaunchableApp("Present", "present"), LaunchableApp("Duplicate", "present"))
        }
        val loaded = harness.management.load()
        assertFalse(loaded.error)
        assertEquals(listOf("present", "missing", "protected"), loaded.choices.map { it.packageName })
        assertEquals(listOf(true, false, false), loaded.choices.map { it.available })
        assertTrue(loaded.choices.drop(1).all { it.label == null })
        assertEquals(setOf("present", "missing", "protected"), loaded.catalog.modes.single().selectedPackages)
        harness.assertNoCommit()
    }

    @Test
    fun saveRechecksEligibilityAfterLoadForRemovedOrNewlyProtectedTargets() {
        for (removed in listOf("missing-after-load", "newly-protected")) {
            val harness = Harness().apply { apps = listOf(LaunchableApp("App", removed)) }
            assertTrue(harness.management.load().choices.single { it.packageName == removed }.available)
            // The platform discovery adapter now excludes the uninstalled/protected app.
            harness.apps = emptyList()
            assertEquals(ModeMutationResult.INVALID_SELECTION,
                harness.management.save(token, mode.id, "Work", setOf(removed)))
            harness.assertNoCommit()
        }
    }

    @Test
    fun saveRejectsInvalidNameAndEmptyTargetsBeforeDiscoveryOrCommit() {
        val harness = Harness()
        assertEquals(ModeMutationResult.INVALID_NAME,
            harness.management.save(token, null, "   ", setOf("present")))
        assertEquals(ModeMutationResult.INVALID_SELECTION,
            harness.management.save(token, null, "Work", emptySet()))
        assertEquals(0, harness.discoveryCalls)
        harness.assertNoCommit()
    }

    @Test
    fun saveCopiesDraftAndForwardsFrozenAuthorityWithoutSelecting() {
        val harness = Harness()
        val draft = mutableSetOf("present")
        assertEquals(ModeMutationResult.SAVED,
            harness.management.save(token, mode.id, "Work", draft))
        assertSame(token, harness.savedToken)
        assertEquals(mode.id, harness.savedId)
        assertEquals("Work", harness.savedName)
        assertNotSame(draft, harness.savedPackages)
        draft.clear()
        assertEquals(setOf("present"), harness.savedPackages)
        assertEquals(1, harness.saveCalls)
        assertEquals(0, harness.selectCalls)
        assertEquals(1, harness.changedCalls)
        assertEquals(mode.id, harness.snapshot.recentModeId)
    }

    @Test
    fun selectRejectsStaleTokenWithoutDiscoveryOrCommit() {
        val harness = Harness().apply {
            snapshot = snapshot.copy(token = SelectionEditToken("mode-test-process", 1L, 4L, null, 0L))
        }
        assertEquals(ModeMutationResult.CONFLICT, harness.management.select(token, mode.id))
        assertEquals(0, harness.discoveryCalls)
        harness.assertNoCommit()
    }

    @Test
    fun selectRejectsActiveRecoveryAndQuarantinedStateBeforeDiscovery() {
        val cases = listOf(
            ModeSnapshot(modes = listOf(mode), token = token, sessionRequested = true) to ModeMutationResult.ACTIVE_SESSION,
            ModeSnapshot(modes = listOf(mode), token = token, recoveryRequired = true) to ModeMutationResult.RECOVERY_REQUIRED,
            ModeSnapshot(modes = listOf(mode), token = token, error = true) to ModeMutationResult.STORAGE_FAILED,
        )
        for ((state, expected) in cases) {
            val harness = Harness().apply { snapshot = state }
            assertEquals(expected, harness.management.select(token, mode.id))
            assertEquals(0, harness.discoveryCalls)
            // A storage failure may request reconciliation; none can commit a selection.
            assertEquals(0, harness.saveCalls)
            assertEquals(0, harness.selectCalls)
            assertEquals(0, harness.deleteCalls)
        }
    }

    @Test
    fun selectRejectsMissingEmptyOrNoLongerEligibleModeWithoutCommit() {
        val missing = Harness()
        assertEquals(ModeMutationResult.MODE_UNAVAILABLE, missing.management.select(token, "missing"))
        missing.assertNoCommit()
        val empty = Harness().apply { snapshot = snapshot.copy(modes = listOf(mode.copy(selectedPackages = emptySet()))) }
        assertEquals(ModeMutationResult.INVALID_SELECTION, empty.management.select(token, mode.id))
        empty.assertNoCommit()
        val unavailable = Harness().apply { apps = emptyList() }
        assertEquals(ModeMutationResult.INVALID_SELECTION, unavailable.management.select(token, mode.id))
        unavailable.assertNoCommit()
    }

    @Test
    fun validSelectionForwardsTokenAndIdOnceAfterFreshEligibilityCheck() {
        val harness = Harness()
        assertEquals(ModeMutationResult.SAVED, harness.management.select(token, mode.id))
        assertSame(token, harness.selectedToken)
        assertEquals(mode.id, harness.selectedId)
        assertEquals(1, harness.discoveryCalls)
        assertEquals(1, harness.selectCalls)
        assertEquals(0, harness.saveCalls)
        assertEquals(1, harness.changedCalls)
    }

    @Test
    fun discoveryFailureCannotCommitOrReportSuccessfulSaveOrSelection() {
        val harness = Harness().apply { discoveryFails = true }
        assertEquals(ModeMutationResult.STORAGE_FAILED,
            harness.management.save(token, mode.id, "Work", setOf("present")))
        assertEquals(ModeMutationResult.STORAGE_FAILED, harness.management.select(token, mode.id))
        harness.assertNoCommit()
    }

    @Test
    fun postCommitRefreshFailureDoesNotChangeSuccessOrReplaySave() {
        val harness = Harness().apply { changedFails = true }
        assertEquals(ModeMutationResult.SAVED,
            harness.management.save(token, mode.id, "Work", setOf("present")))
        assertEquals(1, harness.saveCalls)
        assertEquals(0, harness.selectCalls)
        assertEquals(1, harness.changedCalls)
    }

    private inner class Harness {
        var snapshot = ModeSnapshot(modes = listOf(mode), recentModeId = mode.id, token = token)
        var apps = listOf(LaunchableApp("Present", "present"))
        var discoveryFails = false
        var changedFails = false
        var discoveryCalls = 0
        var saveCalls = 0
        var selectCalls = 0
        var deleteCalls = 0
        var changedCalls = 0
        var savedToken: SelectionEditToken? = null
        var savedId: String? = null
        var savedName: String? = null
        var savedPackages: Set<String>? = null
        var selectedToken: SelectionEditToken? = null
        var selectedId: String? = null
        val management = ModeManagement(
            read = { snapshot },
            discover = { discoveryCalls++; check(!discoveryFails); apps },
            saveMode = { received, id, name, packages ->
                saveCalls++; savedToken = received; savedId = id; savedName = name; savedPackages = packages
                ModeMutationResult.SAVED
            },
            selectMode = { received, id ->
                selectCalls++; selectedToken = received; selectedId = id
                ModeMutationResult.SAVED
            },
            deleteMode = { _, _ -> deleteCalls++; ModeMutationResult.SAVED },
            changed = { changedCalls++; check(!changedFails) },
        )

        fun assertNoCommit() {
            assertEquals(0, saveCalls)
            assertEquals(0, selectCalls)
            assertEquals(0, deleteCalls)
            assertEquals(0, changedCalls)
        }
    }
}
