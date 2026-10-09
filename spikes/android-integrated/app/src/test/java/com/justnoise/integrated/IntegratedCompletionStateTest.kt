package com.justnoise.integrated

import com.justnoise.gate0.data.SessionHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedCompletionStateTest {
    @Test fun sourceThresholdIsInclusiveWithoutChangingTheReceipt() {
        val below = entry("below", 1_199_999L)
        assertNull(IntegratedCompletionState().receive(below).visibleEntry)
        val at = entry("at", 1_200_000L)
        assertSame(at, IntegratedCompletionState().receive(at).visibleEntry)
        val above = entry("above", 1_200_001L)
        assertSame(above, IntegratedCompletionState().receive(above).visibleEntry)
    }

    @Test fun duplicateUuidCannotReopenAfterDismissalEvenAfterAnotherCompletion() {
        val first = entry("first")
        val second = entry("second")
        val closed = IntegratedCompletionState().receive(first).dismiss()
        assertNull(closed.receive(first).visibleEntry)
        val secondShown = closed.receive(second)
        assertEquals(second, secondShown.receive(first).visibleEntry)
        assertNull(secondShown.dismiss().receive(second).visibleEntry)
    }

    @Test fun aNewShortReceiptClearsAnOlderVisibleCardWithoutInventingDuration() {
        val prior = IntegratedCompletionState().receive(entry("first"))
        assertNull(prior.receive(entry("short", 42L)).visibleEntry)
    }

    @Test fun backgroundAndNewSessionClearVisibleReceiptAndSuppressItsReplay() {
        val receipt = entry("one")
        val visible = IntegratedCompletionState().receive(receipt)
        listOf(visible.onBackground(), visible.onNewSession()).forEach {
            assertNull(it.visibleEntry)
            assertNull(it.receive(receipt).visibleEntry)
        }
    }

    @Test fun withdrawalDropsAllTransientSessionIdentifiersAsWellAsTheVisibleEntry() {
        val state = IntegratedCompletionState().receive(entry("first")).receive(entry("second"))
        assertEquals(IntegratedCompletionState(), state.onWithdrawal())
    }

    @Test fun aFreshHolderHasNoHistoryLookupOrPersistedReplay() {
        val discarded = IntegratedCompletionState().receive(entry("old"))
        assertTrue(discarded.visibleEntry != null)
        assertNull(IntegratedCompletionState().visibleEntry)
    }

    @Test fun exactRecordIdentityTargetCountAndInterruptionArePreserved() {
        val receipt = entry("exact", interrupted = true).copy(targetCount = 7)
        val state = IntegratedCompletionState().receive(receipt)
        assertSame(receipt, state.visibleEntry)
        assertEquals(7, state.visibleEntry!!.targetCount)
        assertTrue(state.visibleEntry.interrupted)
        assertFalse(state.visibleEntry.completionTimingUncertain)
        assertFalse(state.toString().contains("exact"))
    }

    @Test fun clockUncertaintyUsesExistingToleranceAndIsIndependentOfInterruption() {
        val at = entry("sampling").let { it.copy(endedAtEpochMilliseconds = it.endedAtEpochMilliseconds + 1_000) }
        assertFalse(at.completionTimingUncertain)
        val beyond = at.copy(endedAtEpochMilliseconds = at.endedAtEpochMilliseconds + 1)
        assertTrue(beyond.completionTimingUncertain)
        assertFalse(beyond.interrupted)
        val interruptedOnly = entry("interrupted", interrupted = true)
        assertTrue(interruptedOnly.interrupted)
        assertFalse(interruptedOnly.completionTimingUncertain)
    }

    @Test fun malformedWallOrderKeepsElapsedDurationAndReportsUncertainty() {
        val receipt = entry("clock").copy(endedAtEpochMilliseconds = 0L)
        assertTrue(receipt.completionTimingUncertain)
        assertEquals(1_500_000L, IntegratedCompletionState().receive(receipt).visibleEntry!!.durationMilliseconds)
    }

    private fun entry(id: String, duration: Long = 1_500_000L, interrupted: Boolean = false) =
        SessionHistoryEntry(id, 1_000L, 1_000L + duration, duration, 2, interrupted)
}
