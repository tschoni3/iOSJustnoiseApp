package com.justnoise.gate0.feature

import com.justnoise.gate0.data.SelectionEditResult
import com.justnoise.gate0.data.SelectionEditSnapshot
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.platform.discovery.LaunchableApp
import org.junit.Assert.*
import org.junit.Test

class AppSelectionManagementTest {
    private val token = SelectionEditToken("test-process", 1L, 3L, null, 0L)

    @Test
    fun loadingKeepsSavedUnavailableRowsRemovalOnlyAndDoesNotWrite() {
        val edit = SelectionEditSnapshot(selectedPackages = setOf("present", "missing", "protected"), token = token)
        val management = AppSelectionManagement(
            readEdit = { edit },
            discover = { listOf(LaunchableApp("Present", "present"), LaunchableApp("Other", "other")) },
            commit = { _, _ -> error("Loading must not mutate selection") },
        )
        val loaded = management.load()
        assertFalse(loaded.error)
        assertSame(edit, loaded.edit)
        assertEquals(listOf("present", "other", "missing", "protected"), loaded.choices.map { it.packageName })
        assertEquals(listOf(true, true, false, false), loaded.choices.map { it.available })
        assertNull(loaded.choices.last().label)
        assertEquals(setOf("present", "missing", "protected"), edit.selectedPackages)
    }

    @Test
    fun editAuthorityIsReadAfterPotentiallySlowDiscovery() {
        var discovered = false
        val management = AppSelectionManagement(
            readEdit = { assertTrue(discovered); SelectionEditSnapshot(token = token) },
            discover = { discovered = true; emptyList() },
            commit = { _, _ -> error("No write") },
        )
        assertSame(token, management.load().edit?.token)
    }

    @Test
    fun catalogFailureAndStorageQuarantineAreNotReportedAsEmptySuccessfulLists() {
        val failedCatalog = AppSelectionManagement(
            readEdit = { error("Do not freeze authority from a failed discovery") },
            discover = { error("Package manager unavailable") },
            commit = { _, _ -> error("No write") },
        )
        assertTrue(failedCatalog.load().error)
        val quarantined = AppSelectionManagement(
            readEdit = { SelectionEditSnapshot(selectedPackages = setOf("old"), error = true) },
            discover = { listOf(LaunchableApp("App", "app")) },
            commit = { _, _ -> error("No write") },
        ).load()
        assertTrue(quarantined.error)
        assertNull(quarantined.edit)
        assertTrue(quarantined.choices.isEmpty())
    }

    @Test
    fun duplicateAndBlankDiscoveryEntriesAreNotSelectable() {
        val loaded = AppSelectionManagement(
            readEdit = { SelectionEditSnapshot(token = token) },
            discover = { listOf(LaunchableApp("First", "one"), LaunchableApp("Second", "one"), LaunchableApp("Blank", "")) },
            commit = { _, _ -> error("No write") },
        ).load()
        assertEquals(listOf(AppSelectionChoice("one", "First", true)), loaded.choices)
    }

    @Test
    fun saveUsesFrozenAuthorityAndCopiesMutableDraftWithoutInventingSuccess() {
        val draft = mutableSetOf("one", "two")
        val outcomes = SelectionEditResult.entries
        outcomes.forEach { expected ->
            val management = AppSelectionManagement(
                readEdit = { error("Save must not replace the user's frozen token") },
                discover = { error("Commit adapter owns fresh eligibility validation") },
                commit = { received, selection ->
                    assertSame(token, received)
                    assertNotSame(draft, selection)
                    assertEquals(setOf("one", "two"), selection)
                    expected
                },
            )
            assertEquals(expected, management.save(token, draft))
        }
        assertEquals(setOf("one", "two"), draft)
    }

    @Test
    fun saveExceptionReportsFailureAndDoesNotRetry() {
        var attempts = 0
        val management = AppSelectionManagement(
            readEdit = { error("No reload") }, discover = { error("No discovery") },
            commit = { _, _ -> attempts++; error("Write unavailable") },
        )
        assertEquals(SelectionEditResult.STORAGE_FAILED, management.save(token, emptySet()))
        assertEquals(1, attempts)
    }
}
