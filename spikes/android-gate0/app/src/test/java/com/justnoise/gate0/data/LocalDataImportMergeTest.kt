package com.justnoise.gate0.data

import com.justnoise.gate0.domain.ScheduleTiming
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LocalDataImportMergeTest {
    private val mode = StoredMode("local-default", 1, "Local", setOf("synthetic.local"))
    private val schedule = StoredSchedule("44444444-4444-4444-4444-444444444444", 2, "Sleep", true,
        ScheduleTiming(oneTimeEpochMilliseconds = 500_000), "UTC", mode.id)
    private val source = WorkspaceProfile(ModeCatalog(listOf(mode), mode.id), listOf(schedule), emptyList())

    @Test fun contractCoverageIsExplicitAndAndroidOnly() {
        val text = javaClass.classLoader!!.getResource("android-local-import.v1.json")!!.readText()
        val contract = JSONObject(text)
        assertEquals(1, contract.getInt("schemaVersion"))
        val cases = contract.getJSONArray("cases")
        assertEquals(setOf("empty", "defer", "merge", "id-collision", "repeat", "other-account", "history-conflict",
            "interrupted-retry", "active-session", "stale-consent", "schedule-disabled", "capacity"),
            (0 until cases.length()).map { cases.getString(it) }.toSet())
    }

    @Test fun conflictingModeAndScheduleIdsAreRemappedWithReferencesAndExistingDataUntouched() {
        val accountMode = mode.copy(name = "Account", selectedPackages = setOf("synthetic.account"))
        val accountSchedule = schedule.copy(name = "Work")
        val destination = WorkspaceProfile(ModeCatalog(listOf(accountMode), accountMode.id), listOf(accountSchedule), emptyList())
        val result = LocalDataImportMerge.merge(source, destination)
        assertEquals(accountMode, result.modes.modes.first())
        assertEquals(accountSchedule, result.schedules.first())
        val importedMode = result.modes.modes.last()
        assertNotEquals(mode.id, importedMode.id)
        assertNotEquals(schedule.id, result.schedules.last().id)
        assertEquals(importedMode.id, result.schedules.last().modeReference)
        assertFalse(result.schedules.last().enabled)
        assertEquals(accountMode.id, result.modes.recentModeId)
        assertEquals(result, LocalDataImportMerge.merge(source, destination))
    }

    @Test fun repeatedMergeAndExactSemanticDuplicatesDoNotCreateDuplicates() {
        val first = LocalDataImportMerge.merge(source, WorkspaceProfile.empty())
        assertEquals(first, LocalDataImportMerge.merge(source, first))
        val equivalent = mode.copy(id = UUID.randomUUID().toString(), revision = 3)
        val result = LocalDataImportMerge.merge(source, WorkspaceProfile(ModeCatalog(listOf(equivalent)), emptyList(), emptyList()))
        assertEquals(listOf(equivalent), result.modes.modes)
        assertEquals(equivalent.id, result.schedules.single().modeReference)
    }

    @Test fun historyReceiptsAreRetainedButPendingAlarmsAreNotImported() {
        val occurrence = ScheduledOccurrence(schedule.id, 2, "oneTime:500000", 500_000, null, "UTC")
        val receipt = ScheduleExecution(schedule.id, occurrence, lastResolvedOneTimeEpochMilliseconds = 500_000,
            lastOutcome = ScheduleOutcome(ScheduleOutcomeCode.MISSED, occurrence, 900_000))
        val result = LocalDataImportMerge.merge(source.copy(executions = listOf(receipt)), WorkspaceProfile.empty())
        assertNull(result.executions.single().pending)
        assertEquals(receipt.lastOutcome, result.executions.single().lastOutcome)
        assertEquals(receipt.lastResolvedOneTimeEpochMilliseconds, result.executions.single().lastResolvedOneTimeEpochMilliseconds)
        assertFalse(result.schedules.single().enabled)
    }

    @Test fun orphanDoesNotAccidentallyResolveToDestinationModeAndNoFirstModeFallback() {
        val emptyLocal = source.copy(modes = ModeCatalog())
        val result = LocalDataImportMerge.merge(emptyLocal, WorkspaceProfile(ModeCatalog(listOf(mode)), emptyList(), emptyList()))
        assertNotEquals(mode.id, result.schedules.single().modeReference)
        assertNull(result.modes.recentModeId)
    }

    @Test fun capacityFailureDoesNotDropExistingOrImportedRecords() {
        val full = (1..ModeRules.MAX_MODES).map { mode.copy(id = UUID.randomUUID().toString(), name = "Account $it") }
        val destination = WorkspaceProfile(ModeCatalog(full), emptyList(), emptyList())
        assertThrows(IllegalArgumentException::class.java) { LocalDataImportMerge.merge(source, destination) }
        assertEquals(full, destination.modes.modes)
    }
}
