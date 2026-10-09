package com.justnoise.integrated

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsetsController
import android.view.inspector.WindowInspector
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.feature.Gate0Truth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual modal-window flags, not just Activity flags or synthetic semantics. No stored fixtures. */
@RunWith(AndroidJUnit4::class)
class IntegratedModalSystemBarInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var selectionVisible by mutableStateOf(false)
    private var completionVisible by mutableStateOf(false)

    @Before fun guardBeforeLaunchingAnyFixtureHost() {
        assumeTrue("Modal fixtures must never replace the owner's physical phone UI",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context: Context = ApplicationProvider.getApplicationContext()
        assumeTrue("Only the disposable integrated app is allowed", context.packageName == "com.justnoise.androiddev")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario?.onActivity { activity ->
            activity.setContent {
                JustnoiseIntegratedTheme {
                    Box {
                        IntegratedZapSurface(
                            IntegratedZapState(truth = Gate0Truth.IDLE), {}, {}, {}, {}, {}, {},
                        )
                        IntegratedSelectionSheet(
                            IntegratedSelectionState(visible = selectionVisible),
                            { _, _ -> }, {}, {}, { selectionVisible = false },
                        )
                        if (completionVisible) IntegratedCompletionSurface(
                            SessionHistoryEntry("modal-render-only", 0L, 1_500_000L, 1_500_000L, 2, false),
                            onDismiss = { completionVisible = false },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun closeOnlyTheHostLaunchedAfterTheGuard() {
        scenario?.close()
        scenario = null
    }

    @Test fun selectionUsesIndependentStatusAndNavigationContrastAndPreservesHostOnCancel() {
        compose.runOnIdle { selectionVisible = true }
        compose.onNodeWithTag("selection-sheet").assertIsDisplayed()
        assertModalAndHostAppearance()
        captureZapEvidence("fixture-selection-modal-system-bars")
        compose.onNodeWithTag("selection-cancel").performClick()
        compose.onNodeWithTag("selection-sheet").assertDoesNotExist()
        assertHostAfterDismissal()
    }

    @Test fun completionUsesIndependentStatusAndNavigationContrastAndPreservesHostOnDone() {
        compose.runOnIdle { completionVisible = true }
        compose.onNodeWithTag("completion-sheet").assertIsDisplayed()
        assertModalAndHostAppearance()
        captureZapEvidence("fixture-completion-modal-system-bars")
        val done = compose.onNodeWithTag("completion-done")
        try { done.assertIsDisplayed() } catch (_: AssertionError) { done.performScrollTo() }
        done.performClick()
        compose.onNodeWithTag("completion-sheet").assertDoesNotExist()
        assertHostAfterDismissal()
    }

    private fun assertModalAndHostAppearance() {
        compose.runOnIdle {
            requireNotNull(scenario).onActivity { activity ->
                val modal = dialogWindows().single()
                assertNotSame("Regression must inspect the sheet's own window", activity.window, modal)
                assertAppearance(modal, lightStatus = false, lightNavigation = true)
                assertAppearance(activity.window, lightStatus = false, lightNavigation = false)
            }
        }
    }

    private fun assertHostAfterDismissal() {
        compose.runOnIdle {
            assertEquals("Dismissal must remove the modal window", emptyList<Window>(), dialogWindows())
            requireNotNull(scenario).onActivity { activity ->
                assertAppearance(activity.window, lightStatus = false, lightNavigation = false)
            }
        }
    }

    private fun assertAppearance(window: Window, lightStatus: Boolean, lightNavigation: Boolean) {
        val appearance = requireNotNull(window.insetsController).systemBarsAppearance
        assertEquals("Status icons must stay light above dark idle Zap", lightStatus,
            appearance and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS != 0)
        assertEquals("Navigation icons must match the surface in their own window", lightNavigation,
            appearance and WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS != 0)
    }

    private fun dialogWindows(): List<Window> {
        val windows = mutableSetOf<Window>()
        fun inspect(view: View) {
            if (view is DialogWindowProvider) windows += view.window
            if (view is ViewGroup) for (index in 0 until view.childCount) inspect(view.getChildAt(index))
        }
        WindowInspector.getGlobalWindowViews().forEach(::inspect)
        return windows.toList()
    }
}
