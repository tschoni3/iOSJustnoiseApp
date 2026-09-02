package com.justnoise.gate0

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.domain.RestrictionAction
import com.justnoise.gate0.domain.SessionState
import com.justnoise.gate0.domain.SessionTransition
import com.justnoise.gate0.domain.ZapReadOutcome
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import com.justnoise.gate0.platform.restriction.Gate0AccessibilityService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic emulator-only controller tests; no physical tag or Accessibility enablement. */
@RunWith(AndroidJUnit4::class)
class NoOpZapInstrumentationTest {
    private lateinit var context: RecordingContext
    private lateinit var preferences: Gate0Preferences
    private lateinit var zapStore: HmacZapStore
    private lateinit var controller: Gate0Controller
    private lateinit var pairedRecord: ByteArray

    @Before
    fun prepareSyntheticActiveSession() {
        context = RecordingContext(ApplicationProvider.getApplicationContext())
        preferences = Gate0Preferences(context)
        zapStore = HmacZapStore(preferences)
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(zapStore.clear())
        pairedRecord = byteArrayOf(0x01, 0x54, 0x02, 0x65, 0x6E) +
            "synthetic-noop-zap-test".toByteArray(Charsets.UTF_8)

        val targets = LauncherAppRepository(context).load().take(1).map { it.packageName }.toSet()
        assertEquals("Emulator fixture requires one eligible launcher app", 1, targets.size)
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertTrue(preferences.setSelectedPackages(targets))
        assertTrue(zapStore.pair(pairedRecord))
        assertTrue(preferences.startSession(START_EPOCH_MILLISECONDS))
        controller = Gate0Controller(context, preferences, zapStore)
        assertTrue(context.broadcasts.isEmpty())
    }

    @After
    fun clearSyntheticState() {
        if (::pairedRecord.isInitialized) pairedRecord.fill(0)
        if (::preferences.isInitialized) assertTrue(preferences.clearForWithdrawal())
        if (::zapStore.isInitialized) assertTrue(zapStore.clear())
    }

    @Test
    fun authorizedTooEarlyStopPreservesSessionWithoutBroadcastOrCompletion() {
        val before = preferences.snapshot()
        assertNotNull(before.activeSessionId)

        val interaction = controller.handleZap(
            pairedRecord,
            acceptedReadAlreadyHandled = false,
            nowEpochMilliseconds = START_EPOCH_MILLISECONDS + 2_999L,
        )

        assertEquals(ZapReadOutcome.ACCEPTED, interaction.readDecision.outcome)
        assertEquals(SessionTransition.STOP_REJECTED, interaction.sessionDecision?.transition)
        assertEquals(RestrictionAction.NONE, interaction.sessionDecision?.restrictionAction)
        assertFalse(interaction.persistenceFailed)
        // Includes immutable ID, revision, start time and active/saved target snapshots.
        assertEquals(before, preferences.snapshot())
        assertTrue("A rejected stop must not dismiss the overlay through a broadcast", context.broadcasts.isEmpty())
        assertFalse(rawPreferences().contains("pending_session_history"))
        val history = preferences.loadHistory()
        assertFalse(history.error)
        assertEquals(0, history.totalCount)
    }

    @Test
    fun unauthorizedAndDuplicateReadsPreserveSessionWithoutBroadcastOrCompletion() {
        val before = preferences.snapshot()
        val unauthorizedRecord = byteArrayOf(0x01, 0x54, 0x02, 0x65, 0x6E) +
            "synthetic-unpaired-noop-test".toByteArray(Charsets.UTF_8)
        try {
            val unauthorized = controller.handleZap(
                unauthorizedRecord,
                acceptedReadAlreadyHandled = false,
                nowEpochMilliseconds = START_EPOCH_MILLISECONDS + 5_000L,
            )
            assertEquals(ZapReadOutcome.UNAUTHORIZED, unauthorized.readDecision.outcome)
            assertNull(unauthorized.sessionDecision)

            val duplicate = controller.handleZap(
                pairedRecord,
                acceptedReadAlreadyHandled = true,
                nowEpochMilliseconds = START_EPOCH_MILLISECONDS + 5_000L,
            )
            assertEquals(ZapReadOutcome.DUPLICATE_IGNORED, duplicate.readDecision.outcome)
            assertNull(duplicate.sessionDecision)
            assertEquals(before, preferences.snapshot())
            assertTrue(context.broadcasts.isEmpty())
            assertFalse(rawPreferences().contains("pending_session_history"))
            val history = preferences.loadHistory()
            assertFalse(history.error)
            assertEquals(0, history.totalCount)
        } finally {
            unauthorizedRecord.fill(0)
        }
    }

    @Test
    fun acceptedStopBroadcastsOnceAndDuplicateCallbackCannotAddAnotherCompletion() {
        val before = preferences.snapshot()
        val id = requireNotNull(before.activeSessionId)

        val stopped = controller.handleZap(
            pairedRecord,
            acceptedReadAlreadyHandled = false,
            nowEpochMilliseconds = START_EPOCH_MILLISECONDS + 3_000L,
        )

        assertEquals(SessionTransition.STOP, stopped.sessionDecision?.transition)
        assertEquals(RestrictionAction.CLEAR, stopped.sessionDecision?.restrictionAction)
        assertFalse(stopped.persistenceFailed)
        assertEquals(1, context.broadcasts.size)
        assertEquals(Gate0AccessibilityService.ACTION_STATE_CHANGED, context.broadcasts.single().action)
        assertEquals(context.packageName, context.broadcasts.single().`package`)
        val after = preferences.snapshot()
        assertFalse(after.desiredActive)
        assertNull(after.activeSessionId)
        assertTrue(after.activePackages.isEmpty())
        assertEquals(before.selectedPackages, after.selectedPackages)
        assertEquals(before.sessionRevision + 1L, after.sessionRevision)
        val pending = rawPreferences().getString("pending_session_history", null)
        assertNotNull("A v3 stop must atomically queue its completion", pending)

        val duplicate = controller.handleZap(
            pairedRecord,
            acceptedReadAlreadyHandled = true,
            nowEpochMilliseconds = START_EPOCH_MILLISECONDS + 4_000L,
        )
        assertEquals(ZapReadOutcome.DUPLICATE_IGNORED, duplicate.readDecision.outcome)
        assertEquals(after, preferences.snapshot())
        assertEquals(1, context.broadcasts.size)
        assertEquals(pending, rawPreferences().getString("pending_session_history", null))

        val history = preferences.loadHistory()
        assertFalse(history.error)
        assertEquals(1, history.totalCount)
        assertEquals(id, history.entries.single().id)
        assertEquals(before.activePackages.size, history.entries.single().targetCount)
        assertFalse(rawPreferences().contains("pending_session_history"))
        assertEquals(history, preferences.loadHistory())
    }

    @Test
    fun pendingOutboxRefusalPreservesSessionAndBroadcastsRecoveryWithoutOverwritingReceipt() {
        val before = preferences.snapshot()
        val pending = """{"id":"00000000-0000-0000-0000-000000000099","start":1000,"end":2000,"duration":1000,"targets":1,"interrupted":false}"""
        assertTrue(rawPreferences().edit().putString("pending_session_history", pending).commit())

        val refused = controller.handleZap(
            pairedRecord,
            acceptedReadAlreadyHandled = false,
            nowEpochMilliseconds = START_EPOCH_MILLISECONDS + 3_000L,
        )

        assertEquals(ZapReadOutcome.ACCEPTED, refused.readDecision.outcome)
        assertTrue(refused.persistenceFailed)
        assertEquals(SessionTransition.NO_CHANGE, refused.sessionDecision?.transition)
        assertEquals(SessionState.FAILED, refused.sessionDecision?.state)
        assertEquals(RestrictionAction.NONE, refused.sessionDecision?.restrictionAction)
        assertEquals(before.copy(recoveryRequired = true), preferences.snapshot())
        assertEquals(pending, rawPreferences().getString("pending_session_history", null))
        assertEquals(1, context.broadcasts.size)
        assertEquals(Gate0AccessibilityService.ACTION_STATE_CHANGED, context.broadcasts.single().action)
        assertEquals(context.packageName, context.broadcasts.single().`package`)
    }

    private fun rawPreferences() = context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val broadcasts = mutableListOf<Intent>()

        override fun getApplicationContext(): Context = this

        override fun sendBroadcast(intent: Intent) {
            // Capture the actual controller side effect without dispatching to any service.
            broadcasts += Intent(intent)
        }
    }

    private companion object {
        const val START_EPOCH_MILLISECONDS = 100_000L
    }
}
