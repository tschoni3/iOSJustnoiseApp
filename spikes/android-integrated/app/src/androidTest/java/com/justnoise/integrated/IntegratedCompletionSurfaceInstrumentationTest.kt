package com.justnoise.integrated

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.gate0.data.SessionHistoryEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic rendered receipts only; these tests never stop a session or seed History. */
@RunWith(AndroidJUnit4::class)
class IntegratedCompletionSurfaceInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var emulatorGuardPassed = false
    private var scenario: ActivityScenario<MainActivity>? = null
    private var state by mutableStateOf(IntegratedCompletionState())
    private var boundedViewport by mutableStateOf(false)
    private var dismissals = 0

    @Before fun guardBeforeLaunchingAnyFixtureHost() {
        assumeTrue("Completion fixtures must never replace the owner's physical phone UI",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context: Context = ApplicationProvider.getApplicationContext()
        assumeTrue("Only the disposable integrated app is allowed", context.packageName == "com.justnoise.androiddev")
        emulatorGuardPassed = true
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario?.onActivity { activity ->
            activity.setContent {
                JustnoiseIntegratedTheme {
                    Box(Modifier.fillMaxSize()) {
                        Text("Completion presentation fixture")
                        state.visibleEntry?.let { entry ->
                            IntegratedCompletionSurface(entry, onDismiss = {
                                dismissals += 1
                                state = state.dismiss()
                            }, modifier = if (boundedViewport) {
                                // Bounds content only. Modal sheets use their own window density;
                                // enlarged text requires the host emulator's actual font setting.
                                Modifier.widthIn(max = 360.dp).heightIn(max = 440.dp)
                            } else Modifier)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun closeOnlyTheHostLaunchedAfterTheGuard() {
        if (!emulatorGuardPassed) return
        scenario?.close()
        scenario = null
    }

    @Test fun showsExactDurationAndTargetCountWithoutSignalOrProtectionClaims() {
        present(receipt("actual-shape", duration = 5_400_000L, targets = 7))
        compose.onNodeWithTag("completion-sheet").assertIsDisplayed()
        compose.onNodeWithTag("completion-targets").assertTextEquals("7 selected apps")
        compose.onNodeWithTag("completion-duration").assertTextEquals("1h 30m")
        compose.onNodeWithText("Your focus was protected.").assertDoesNotExist()
        compose.onNodeWithText("Signal Strengthened").assertDoesNotExist()
        compose.onNodeWithText("Reflect").assertDoesNotExist()
        compose.onNodeWithTag("completion-interrupted").assertDoesNotExist()
        compose.onNodeWithTag("completion-timing-uncertain").assertDoesNotExist()
        captureZapEvidence("fixture-completion-exact-record")
    }

    @Test fun doneDismissesOnceAndDuplicateUuidCannotReopen() {
        val receipt = receipt("once")
        present(receipt)
        compose.onNodeWithTag("completion-done").inView().assertHasClickAction().performClick()
        compose.onNodeWithTag("completion-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissals); state = state.receive(receipt) }
        compose.onNodeWithTag("completion-sheet").assertDoesNotExist()
    }

    @Test fun nativeBackDismissesTheSheetWithoutAnotherAction() {
        present(receipt("back"))
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("completion-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun historicalWarningsDoNotAddHelperCopyToCompletion() {
        val interruptedOnly = receipt("interrupted", interrupted = true)
        present(interruptedOnly)
        compose.onNodeWithTag("completion-interrupted").assertDoesNotExist()
        compose.onNodeWithTag("completion-timing-uncertain").assertDoesNotExist()
        compose.onNodeWithTag("completion-done").inView().performClick()

        val uncertainOnly = receipt("clock").let { it.copy(endedAtEpochMilliseconds = it.endedAtEpochMilliseconds + 1_001L) }
        present(uncertainOnly)
        compose.onNodeWithTag("completion-interrupted").assertDoesNotExist()
        compose.onNodeWithTag("completion-timing-uncertain").assertDoesNotExist()
    }

    @Test fun boundedViewportKeepsSummaryAndDoneReachableWithoutHelperCopy() {
        compose.runOnIdle { boundedViewport = true }
        val receipt = receipt("large", interrupted = true).copy(endedAtEpochMilliseconds = 0L)
        present(receipt)
        compose.onNodeWithTag("completion-targets").inView().assertTextEquals("2 selected apps")
        compose.onNodeWithTag("completion-duration").inView().assertTextEquals("25m")
        compose.onNodeWithTag("completion-interrupted").assertDoesNotExist()
        compose.onNodeWithTag("completion-timing-uncertain").assertDoesNotExist()
        compose.onNodeWithTag("completion-done").inView().assertHasClickAction()
        captureZapEvidence("fixture-completion-bounded-content-lower-actions")
        compose.onNodeWithTag("completion-done").performClick()
        compose.onNodeWithTag("completion-sheet").assertDoesNotExist()
    }

    @Test fun belowThresholdAndBackgroundClearingDoNotPresentAReceipt() {
        present(receipt("short", duration = 1_199_999L))
        compose.onNodeWithTag("completion-sheet").assertDoesNotExist()
        present(receipt("background"))
        compose.onNodeWithTag("completion-sheet").assertIsDisplayed()
        compose.runOnIdle { state = state.onBackground() }
        compose.onNodeWithTag("completion-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, dismissals) }
    }

    private fun present(receipt: SessionHistoryEntry) {
        compose.runOnIdle { state = state.receive(receipt) }
        compose.waitForIdle()
    }

    private fun receipt(id: String, duration: Long = 1_500_000L, targets: Int = 2, interrupted: Boolean = false) =
        SessionHistoryEntry(id, 1_000L, 1_000L + duration, duration, targets, interrupted)

    private fun SemanticsNodeInteraction.inView(): SemanticsNodeInteraction {
        try { assertIsDisplayed() } catch (_: AssertionError) { performScrollTo() }
        return assertIsDisplayed()
    }
}
