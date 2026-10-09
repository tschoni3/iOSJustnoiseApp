package com.justnoise.integrated

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.platform.nfc.NdefReadFailure
import com.justnoise.gate0.platform.nfc.NdefReadResult
import com.justnoise.gate0.platform.nfc.OneShotScanCoordinator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Activity/controller/outbox using synthetic emulator input; not physical NFC evidence. */
@RunWith(AndroidJUnit4::class)
class IntegratedCompletionActivityInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var guarded = false
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences
    private var scenario: ActivityScenario<MainActivity>? = null
    private val payload get() = "synthetic-completion-only-zap".toByteArray()

    @Before fun setupDisposableEmulator() {
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        context = ApplicationProvider.getApplicationContext()
        assumeTrue(context.packageName == "com.justnoise.androiddev")
        guarded = true
        preferences = Gate0Preferences(context)
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(HmacZapStore(preferences).clear())
        val bytes = payload
        try { assertTrue(HmacZapStore(preferences).pairInitial(bytes)) } finally { bytes.fill(0) }
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION))
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.completion.target")))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("zap-main").assertIsDisplayed()
        // No twenty-minute wait or readiness bypass: characterize interrupted cross-process timing.
        assertTrue(preferences.startSession(System.currentTimeMillis() - 25 * 60_000L))
        assertTrue(context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE).edit()
            .putString("active_process_id", "synthetic-prior-process").commit())
        // Respect the shared three-second process input gate between instrumented cases.
        SystemClock.sleep(3_010L)
    }

    @After fun cleanupDisposableEmulatorOnly() {
        if (!guarded) return
        scenario?.close()
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(HmacZapStore(preferences).clear())
    }

    @Test fun foregroundStopShowsExactCommittedEntryThenDismissAndRecreateDoNotReplay() {
        val activeId = preferences.snapshot().activeSessionId
        deliver(claim(), NdefReadResult.Accepted(payload, 1))
        compose.onNodeWithTag("completion-sheet").assertIsDisplayed()
        val row = preferences.loadHistory().entries.single()
        assertEquals(activeId, row.id)
        assertTrue(row.interrupted)
        assertFalse(preferences.snapshot().desiredActive)
        requireNotNull(scenario).onActivity { activity ->
            val state = MainActivity::class.java.getDeclaredMethod("getCompletionState")
                .apply { isAccessible = true }.invoke(activity) as IntegratedCompletionState
            assertEquals(row, state.visibleEntry)
        }
        captureZapEvidence("real-foreground-completion-interrupted-emulator")
        compose.onNodeWithTag("completion-done").performScrollTo().performClick()
        noSheet()
        requireNotNull(scenario).recreate()
        noSheet()
        assertEquals(listOf(row), preferences.loadHistory().entries)
    }

    @Test fun canceledClaimCannotStopOrPresentAndSensitiveBytesAreCleared() {
        val token = claim()
        requireNotNull(scenario).onActivity { activity ->
            MainActivity::class.java.getDeclaredMethod("cancelReaderMode").apply { isAccessible = true }.invoke(activity)
        }
        val bytes = payload
        deliver(token, NdefReadResult.Accepted(bytes, 1))
        assertTrue(bytes.all { it == 0.toByte() })
        assertTrue(preferences.snapshot().desiredActive)
        assertEquals(0, preferences.loadHistory().totalCount)
        noSheet()
    }

    @Test fun pauseInvalidatesInFlightReadAndResumingNeverReplaysIt() {
        val token = claim()
        requireNotNull(scenario).moveToState(Lifecycle.State.CREATED)
        requireNotNull(scenario).moveToState(Lifecycle.State.RESUMED)
        val bytes = payload
        deliver(token, NdefReadResult.Accepted(bytes, 1))
        assertTrue(bytes.all { it == 0.toByte() })
        assertTrue(preferences.snapshot().desiredActive)
        assertEquals(0, preferences.loadHistory().totalCount)
        noSheet()
    }

    @Test fun backgroundControllerStopDoesNotCreateACompletionPopupOnReturn() {
        requireNotNull(scenario).moveToState(Lifecycle.State.CREATED)
        val bytes = payload
        try {
            val interaction = Gate0Controller(context).handleZap(bytes, false)
            assertFalse(interaction.persistenceFailed)
            assertNotNull(interaction.completedSession)
        } finally { bytes.fill(0) }
        requireNotNull(scenario).moveToState(Lifecycle.State.RESUMED)
        assertFalse(preferences.snapshot().desiredActive)
        assertEquals(1, preferences.loadHistory().totalCount)
        noSheet()
        requireNotNull(scenario).recreate()
        noSheet()
    }

    @Test fun rejectedAndUnauthorizedResultsDoNotStopOrShowCompletion() {
        deliver(claim(), NdefReadResult.Rejected(NdefReadFailure.INVALID_TEXT_RECORD))
        noSheet()
        val bytes = "synthetic-different-unpaired-zap".toByteArray()
        deliver(claim(), NdefReadResult.Accepted(bytes, 1))
        assertTrue(bytes.all { it == 0.toByte() })
        assertTrue(preferences.snapshot().desiredActive)
        assertEquals(0, preferences.loadHistory().totalCount)
        noSheet()
    }

    private fun claim(): OneShotScanCoordinator.Token<IntegratedScanPurpose> {
        lateinit var token: OneShotScanCoordinator.Token<IntegratedScanPurpose>
        requireNotNull(scenario).onActivity { activity ->
            @Suppress("UNCHECKED_CAST")
            val coordinator = MainActivity::class.java.getDeclaredField("scanCoordinator")
                .apply { isAccessible = true }.get(activity) as OneShotScanCoordinator<IntegratedScanPurpose>
            token = coordinator.begin(IntegratedScanPurpose.TOGGLE_SESSION)
            assertTrue(coordinator.claim(token))
        }
        return token
    }

    private fun deliver(token: OneShotScanCoordinator.Token<IntegratedScanPurpose>, result: NdefReadResult) {
        requireNotNull(scenario).onActivity { activity ->
            MainActivity::class.java.getDeclaredMethod("completeOneShotScan",
                OneShotScanCoordinator.Token::class.java, NdefReadResult::class.java)
                .apply { isAccessible = true }.invoke(activity, token, result)
        }
        compose.waitForIdle()
    }

    private fun noSheet() {
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("completion-sheet").fetchSemanticsNodes().isEmpty())
    }
}
