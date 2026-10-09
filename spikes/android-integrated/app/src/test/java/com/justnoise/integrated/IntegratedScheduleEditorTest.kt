package com.justnoise.integrated

import com.justnoise.gate0.data.ScheduleDraft
import com.justnoise.gate0.data.StoredSchedule
import com.justnoise.gate0.data.StoredMode
import com.justnoise.gate0.feature.ScheduleManagementSnapshot
import com.justnoise.gate0.domain.PortableScheduleTimingRules
import com.justnoise.gate0.domain.PortableWeekday
import com.justnoise.gate0.domain.ScheduleTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class IntegratedScheduleEditorTest {
    private val now = epoch("2026-09-03T10:15:40Z")
    private val zone = "Europe/Berlin"

    @Test
    fun confirmedWithdrawalClearsStaleScheduleSuccessMessage() {
        val cleared = IntegratedScheduleState(
            snapshot = ScheduleManagementSnapshot(consentAccepted = false),
            message = "Schedule saved.",
        ).withoutRevokedConsentMessage()
        assertNull(cleared.message)
        assertTrue(requireNotNull(cleared.snapshot).schedules.isEmpty())
    }

    @Test
    fun failedOrPendingReadDoesNotSilentlyDiscardSaveWarnings() {
        for (snapshot in listOf(null, ScheduleManagementSnapshot(error = true))) {
            val state = IntegratedScheduleState(
                snapshot = snapshot, message = "A previous save may have completed.",
                editor = namedEditor(), editorError = "Check storage and retry.",
                mutationUncertain = true,
            )
            assertSame(state, state.withoutRevokedConsentMessage())
        }
    }

    @Test
    fun acceptedConsentPreservesSuccessAndRecoveryNotices() {
        for (message in listOf("Schedule saved.", "Saved, but scheduling could not refresh.")) {
            val state = IntegratedScheduleState(
                snapshot = ScheduleManagementSnapshot(consentAccepted = true, recoveryRequired = true),
                message = message,
            )
            assertSame(state, state.withoutRevokedConsentMessage())
        }
    }

    @Test
    fun editableDraftAcceptsChangesAndClearsItsValidationError() {
        val editor = namedEditor()
        val state = IntegratedScheduleState(editor = editor, editorError = "Previous validation error")
        val changed = editor.copy(name = "Updated")
        assertTrue(state.canEditDraft)
        val updated = state.withEditorChange(changed)
        assertEquals(changed, updated.editor)
        assertNull(updated.editorError)
    }

    @Test
    fun reloadSaveAndUncertainMutationRejectLateEditorCallbacks() {
        val editor = namedEditor()
        val initial = IntegratedScheduleState(editor = editor, editorError = "Retained error")
        for (blocked in listOf(
            initial.copy(loading = true),
            initial.copy(saving = true),
            initial.copy(mutationUncertain = true),
        )) {
            assertFalse(blocked.canEditDraft)
            assertSame(blocked, blocked.withEditorChange(editor.copy(name = "Must not replace draft")))
        }
    }

    @Test
    fun latePickerCallbackCannotReopenACancelledNewDraft() {
        val cancelled = IntegratedScheduleState()
        assertFalse(cancelled.canEditDraft)
        assertSame(cancelled, cancelled.withEditorChange(namedEditor().copy(hour = 15)))
    }

    @Test
    fun editorCallbacksCannotReplaceAnotherScheduleOrRevision() {
        val editor = namedEditor().copy(id = "schedule-one", expectedRevision = 2)
        val state = IntegratedScheduleState(editor = editor)
        assertSame(state, state.withEditorChange(editor.copy(id = "schedule-two")))
        assertSame(state, state.withEditorChange(editor.copy(expectedRevision = 1)))
    }

    @Test
    fun newEditorDefaultsAboutOneHourAheadInNamedZone() {
        val editor = IntegratedScheduleEditor.new(now, zone, "mode-focus")
        assertNull(editor.id)
        assertNull(editor.expectedRevision)
        assertEquals("2026-09-03", editor.localDate)
        assertEquals(13, editor.hour)
        assertEquals(15, editor.minute)
        assertEquals(zone, editor.zoneId)
        assertFalse(editor.isRepeating)
        assertTrue(editor.enabled)
        assertTrue(editor.weekdays.isEmpty())
        assertEquals(epoch("2026-09-03T11:15:00Z"), valid(editor.copy(name = "Focus")).timing.oneTimeEpochMilliseconds)
    }

    @Test
    fun newDefaultMayCrossMidnightButUserPastDateIsNeverRolledForward() {
        val late = epoch("2026-09-03T23:40:00Z")
        val editor = IntegratedScheduleEditor.new(late, "UTC", "mode-focus").copy(name = "Focus")
        assertEquals("2026-09-04", editor.localDate)
        assertEquals(0, editor.hour)
        assertEquals(40, editor.minute)
        assertEquals(epoch("2026-09-04T00:40:00Z"), valid(editor, late, "UTC").timing.oneTimeEpochMilliseconds)
        invalid(editor.copy(localDate = "2026-09-03"), late, "UTC")
        assertEquals("2026-09-03", editor.copy(localDate = "2026-09-03").localDate)
    }

    @Test
    fun namesAreTrimmedAndLimitedToEightyCharacters() {
        val editor = namedEditor()
        assertEquals("Focus", valid(editor.copy(name = "  Focus\n")).name)
        assertEquals("x".repeat(80), valid(editor.copy(name = " ${"x".repeat(80)} ")).name)
        invalid(editor.copy(name = " \n "))
        invalid(editor.copy(name = "x".repeat(81)))
    }

    @Test
    fun invalidDatesTimesAndUnsupportedZonesAreRejected() {
        val editor = namedEditor()
        listOf("2026-02-30", "2026-2-03", "10000-01-01", "1500-02-29", "").forEach {
            invalid(editor.copy(localDate = it))
        }
        invalid(editor.copy(hour = 24))
        invalid(editor.copy(hour = -1))
        invalid(editor.copy(minute = 60))
        invalid(editor.copy(zoneId = "+02:00"))
        invalid(editor.copy(zoneId = "Not/AZone"))
        invalid(editor, -1L)
        invalid(editor, PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS + 1L)
    }

    @Test
    fun newOneTimeMustBeStrictlyFutureEvenWhenDisabled() {
        val minuteNow = epoch("2026-09-03T10:15:00Z")
        val editor = namedEditor().copy(localDate = "2026-09-03", hour = 12, minute = 15)
        invalid(editor, minuteNow)
        invalid(editor.copy(enabled = false), minuteNow)
        invalid(editor.copy(localDate = "2026-09-02", enabled = false), minuteNow)
    }

    @Test
    fun oneTimeGapIsRejectedAndOverlapUsesOnlyFirstOccurrence() {
        val beforeSpring = epoch("2026-03-01T00:00:00Z")
        val editor = namedEditor().copy(localDate = "2026-03-29", hour = 2, minute = 30)
        invalid(editor, beforeSpring)
        val beforeAutumn = epoch("2026-10-01T00:00:00Z")
        val overlap = editor.copy(localDate = "2026-10-25")
        assertEquals(epoch("2026-10-25T00:30:00Z"), valid(overlap, beforeAutumn).timing.oneTimeEpochMilliseconds)
        invalid(overlap, epoch("2026-10-25T00:30:00Z"))
        invalid(overlap, epoch("2026-10-25T01:00:00Z"))
    }

    @Test
    fun defaultDoesNotOfferTheSecondOverlapAsAFutureOneTime() {
        val duringOverlap = epoch("2026-10-25T00:30:00Z")
        val editor = IntegratedScheduleEditor.new(duringOverlap, zone, "mode-focus").copy(name = "Focus")
        assertEquals(3, editor.hour)
        assertEquals(30, editor.minute)
        assertEquals(epoch("2026-10-25T02:30:00Z"), valid(editor, duringOverlap).timing.oneTimeEpochMilliseconds)
    }

    @Test
    fun repeatingRequiresWeekdaysAndSavesTheCurrentDeviceZone() {
        val editor = namedEditor().copy(isRepeating = true)
        invalid(editor)
        val days = setOf(PortableWeekday.MONDAY, PortableWeekday.FRIDAY)
        val draft = valid(editor.copy(weekdays = days), deviceZone = "America/New_York")
        assertEquals("America/New_York", draft.creationZoneId)
        assertEquals(days, draft.timing.repeatWeekdays)
        assertEquals(editor.hour, draft.timing.hour)
        assertEquals(editor.minute, draft.timing.minute)
        assertNull(draft.timing.oneTimeEpochMilliseconds)
        invalid(editor.copy(weekdays = days), deviceZone = "+02:00")
    }

    @Test
    fun repeatingSkipsAGapDayUsingSharedRules() {
        val beforeGap = epoch("2026-03-28T12:00:00Z")
        val editor = namedEditor().copy(
            isRepeating = true, weekdays = setOf(PortableWeekday.SUNDAY), hour = 2, minute = 30,
        )
        val draft = valid(editor, beforeGap)
        assertEquals(
            epoch("2026-04-05T00:30:00Z"),
            PortableScheduleTimingRules.nextOccurrence(draft.timing, beforeGap, zone)?.epochMilliseconds,
        )
    }

    @Test
    fun storedOneTimePreservesRevisionZoneAndExactInstantAfterTravel() {
        val original = epoch("2026-09-04T09:30:45.123Z")
        val stored = stored(ScheduleTiming(oneTimeEpochMilliseconds = original))
        val editor = IntegratedScheduleEditor.fromStored(stored, now, "America/New_York")
        assertEquals(stored.id, editor.id)
        assertEquals(7L, editor.expectedRevision)
        assertEquals(zone, editor.zoneId)
        assertEquals(zone, editor.originalOneTimeZoneId)
        assertEquals("2026-09-04", editor.localDate)
        assertEquals(11, editor.hour)
        assertEquals(30, editor.minute)
        val draft = valid(editor.copy(name = "Renamed"), deviceZone = "Asia/Tokyo")
        assertEquals(original, draft.timing.oneTimeEpochMilliseconds)
        assertEquals(zone, draft.creationZoneId)
        assertEquals(stored.id, editor.copy(name = "Renamed").id)
        assertEquals(7L, editor.copy(name = "Renamed").expectedRevision)
    }

    @Test
    fun changingAStoredOneTimeOrItsChosenZoneResolvesTheNewInput() {
        val original = epoch("2026-09-04T09:30:45.123Z")
        val editor = IntegratedScheduleEditor.fromStored(stored(ScheduleTiming(oneTimeEpochMilliseconds = original)), now, zone)
        assertEquals(epoch("2026-09-04T09:31:00Z"), valid(editor.copy(minute = 31)).timing.oneTimeEpochMilliseconds)
        assertEquals(epoch("2026-09-04T11:30:00Z"), valid(editor.copy(zoneId = "UTC")).timing.oneTimeEpochMilliseconds)
    }

    @Test
    fun pastStoredOneTimeMayRemainDisabledButCannotBeReenabledOrChangedToAnotherPastTime() {
        val original = epoch("2026-09-02T09:30:45.123Z")
        val editor = IntegratedScheduleEditor.fromStored(
            stored(ScheduleTiming(oneTimeEpochMilliseconds = original), enabled = false), now, zone,
        )
        assertEquals(original, valid(editor.copy(name = "Past focus")).timing.oneTimeEpochMilliseconds)
        invalid(editor.copy(enabled = true))
        invalid(editor.copy(minute = 31))
        invalid(editor.copy(zoneId = "UTC"))
        invalid(editor.copy(id = null, expectedRevision = null))
        invalid(editor.copy(expectedRevision = 0L))
        assertEquals("2026-09-02", editor.localDate)
    }

    @Test
    fun storedRepeatingRetainsIdentityAndClockButFollowsDeviceZone() {
        val timing = ScheduleTiming(repeatWeekdays = setOf(PortableWeekday.TUESDAY), hour = 8, minute = 5)
        val stored = stored(timing, enabled = false)
        val editor = IntegratedScheduleEditor.fromStored(stored, now, "Asia/Tokyo")
        assertEquals(stored.id, editor.id)
        assertEquals(stored.revision, editor.expectedRevision)
        assertTrue(editor.isRepeating)
        assertFalse(editor.enabled)
        assertEquals("Asia/Tokyo", editor.zoneId)
        assertEquals(timing, valid(editor, deviceZone = "US/Pacific").timing)
        assertEquals("US/Pacific", valid(editor, deviceZone = "US/Pacific").creationZoneId)
    }

    @Test
    fun supportedEpochEndDoesNotOverflowOrCreateAnUnavailableOccurrence() {
        val maximum = PortableScheduleTimingRules.MAXIMUM_EPOCH_MILLISECONDS
        val editor = IntegratedScheduleEditor.new(maximum, "UTC", "mode-focus").copy(name = "Focus")
        invalid(editor, maximum, "UTC")
        invalid(editor.copy(isRepeating = true, weekdays = setOf(PortableWeekday.FRIDAY)), maximum, "UTC")
        invalid(IntegratedScheduleEditor.new(Long.MAX_VALUE, "UTC", "mode-focus").copy(name = "Focus"), Long.MAX_VALUE, "UTC")
        val stored = stored(ScheduleTiming(oneTimeEpochMilliseconds = maximum), enabled = false).copy(creationZoneId = "UTC")
        val existing = IntegratedScheduleEditor.fromStored(stored, maximum, "UTC")
        assertEquals(maximum, valid(existing, maximum, "UTC").timing.oneTimeEpochMilliseconds)
    }

    @Test
    fun newDraftUsesOnlyAnExplicitRecentModeAndOtherwiseRequiresChoice() {
        val empty = IntegratedScheduleEditor.new(now, zone).copy(name = "Focus")
        assertNull(empty.modeReference)
        invalid(empty)
        assertEquals(IntegratedScheduleModeProblem.REQUIRED, empty.modeProblem(listOf(mode("mode-other"))))
        val chosen = IntegratedScheduleEditor.new(now, zone, "mode-focus").copy(name = "Focus")
        assertEquals("mode-focus", valid(chosen).modeReference)
        invalid(chosen.copy(modeReference = " "))
    }

    @Test
    fun editingAndSavingRetainTheSchedulesOwnModeIndependentlyOfTheRecentMode() {
        val stored = stored(ScheduleTiming(oneTimeEpochMilliseconds = now + 86_400_000L))
            .copy(modeReference = "mode-schedule")
        val editor = IntegratedScheduleEditor.fromStored(stored, now, zone)
        assertEquals("mode-schedule", editor.modeReference)
        val snapshot = ScheduleManagementSnapshot(modes = listOf(mode("mode-recent"), mode("mode-schedule")),
            recentModeId = "mode-recent")
        assertNull(editor.modeProblem(snapshot.modes))
        assertEquals("mode-schedule", valid(editor.copy(name = "Renamed")).modeReference)
        val changed = IntegratedScheduleState(editor = editor).withEditorChange(editor.copy(modeReference = "mode-recent"))
        assertEquals("mode-recent", valid(requireNotNull(changed.editor)).modeReference)
        assertEquals("mode-schedule", editor.modeReference)
    }

    @Test
    fun missingAndEmptyModesNeverResolveToAnotherAvailableMode() {
        val editor = namedEditor()
        assertEquals(IntegratedScheduleModeProblem.UNAVAILABLE, editor.modeProblem(listOf(mode("mode-other"))))
        assertEquals(IntegratedScheduleModeProblem.EMPTY,
            editor.modeProblem(listOf(mode("mode-other"), mode("mode-focus").copy(selectedPackages = emptySet()))))
        assertNull(editor.modeProblem(listOf(mode("mode-focus"))))
        assertEquals("mode-focus", editor.modeReference)
    }

    @Test
    fun setupChecksEachSchedulesModeNotRecentModeOrAnotherSchedulesTargets() {
        val snapshot = ScheduleManagementSnapshot(
            exactAlarmAccess = true, consentAccepted = true, zapPaired = true, accessibilityEnabled = true,
            modes = listOf(mode("ready"), mode("empty").copy(selectedPackages = emptySet())), recentModeId = "empty",
        )
        assertFalse(snapshot.hasSetupGap("ready"))
        assertTrue(snapshot.hasSetupGap("empty"))
        assertTrue(snapshot.hasSetupGap("missing"))
        assertTrue(snapshot.hasSetupGap(null))
        assertFalse(snapshot.copy(recentModeId = null).hasSetupGap("ready"))
        assertTrue(snapshot.copy(exactAlarmAccess = false).hasSetupGap("ready"))
    }

    @Test
    fun lateModeChoiceCannotReplacePendingOrCancelledDraft() {
        val editor = namedEditor()
        val pending = IntegratedScheduleState(editor = editor, saving = true)
        assertSame(pending, pending.withEditorChange(editor.copy(modeReference = "mode-other")))
        val cancelled = IntegratedScheduleState()
        assertSame(cancelled, cancelled.withEditorChange(editor.copy(modeReference = "mode-other")))
    }

    private fun mode(id: String) = StoredMode(id, 1L, id, setOf("synthetic.one"))

    private fun namedEditor() = IntegratedScheduleEditor.new(now, zone, "mode-focus").copy(name = "Focus")

    private fun stored(timing: ScheduleTiming, enabled: Boolean = true) = StoredSchedule(
        id = "98bce997-1391-4cb8-8203-b36b76e993b8", revision = 7L,
        name = "Focus", enabled = enabled, timing = timing, creationZoneId = zone,
    )

    private fun valid(
        editor: IntegratedScheduleEditor,
        nowMilliseconds: Long = now,
        deviceZone: String = zone,
    ): ScheduleDraft {
        val result = editor.toDraft(nowMilliseconds, deviceZone)
        assertTrue("Expected a valid draft, got $result", result is IntegratedScheduleEditorResult.Valid)
        return (result as IntegratedScheduleEditorResult.Valid).draft
    }

    private fun invalid(
        editor: IntegratedScheduleEditor,
        nowMilliseconds: Long = now,
        deviceZone: String = zone,
    ) {
        val result = editor.toDraft(nowMilliseconds, deviceZone)
        assertTrue("Expected invalid input, got $result", result is IntegratedScheduleEditorResult.Invalid)
        assertTrue((result as IntegratedScheduleEditorResult.Invalid).message.isNotBlank())
    }

    private fun epoch(value: String): Long = Instant.parse(value).toEpochMilli()
}
