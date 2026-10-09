package com.justnoise.integrated

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.androidcontract.JustnoiseContract
import com.justnoise.gate0.R
import com.justnoise.gate0.feature.Gate0Truth
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pure rendered-surface fixtures, not NFC, persistence, or Activity-routing evidence.
 * The real Activity is only the resource/window host; its Compose content is replaced
 * with the fixture after an emulator/package guard. No test Activity enters the manifest.
 */
@RunWith(AndroidJUnit4::class)
class IntegratedZapUiInstrumentationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var context: Context
    private var scenario: ActivityScenario<MainActivity>? = null
    private var renderedState by mutableStateOf(IntegratedZapState(truth = Gate0Truth.IDLE))
    private var compactEnlarged by mutableStateOf(false)
    private val events = mutableListOf<String>()

    @Before
    fun guardEmulatorBeforeLaunchingTheFixtureHost() {
        assumeTrue("Rendered fixtures must never replace UI on the owner's physical phone",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        context = ApplicationProvider.getApplicationContext()
        assumeTrue("Only the disposable integrated app is allowed", context.packageName == "com.justnoise.androiddev")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario?.onActivity { activity ->
            activity.setContent {
                val systemDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides if (compactEnlarged) {
                    Density(systemDensity.density, fontScale = 2f)
                } else systemDensity) {
                    JustnoiseIntegratedTheme {
                        Box(Modifier.fillMaxSize()) {
                            Box(if (compactEnlarged) {
                                Modifier.widthIn(max = 360.dp).heightIn(max = 640.dp)
                            } else Modifier.fillMaxSize()) {
                                IntegratedZapSurface(
                                    state = renderedState,
                                    onZap = { events += "zap" },
                                    onOpenSetup = { events += "setup" },
                                    onOpenHistory = { events += "history" },
                                    onOpenSchedules = { events += "schedules" },
                                    onOpenSelection = { events += "selection" },
                                    onCancelScan = { events += "cancel-scan" },
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun closeOnlyTheHostLaunchedAfterTheGuard() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun idleExposesNamedNavigationSelectionAndOneScanActionWithoutDevelopmentChrome() {
        render(IntegratedZapState(truth = Gate0Truth.IDLE, selectedAppName = "2 apps"))
        compose.onNodeWithTag("zap-main").assertIsDisplayed()
        compose.onNodeWithText(JustnoiseContract.text("zap.idle.title")).assertIsDisplayed()
        compose.onNodeWithText("ANDROID DEV").assertDoesNotExist()
        compose.onNodeWithText("SETUP").assertDoesNotExist()
        compose.onNodeWithText(JustnoiseContract.text("zap.active.action.stop")).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.zap_schedules_accessibility))
            .assertHasClickAction().performClick()
        compose.onNodeWithContentDescription(text(R.string.zap_history_accessibility))
            .assertHasClickAction().performClick()
        compose.onNodeWithTag("zap-selection").inView().assertIsEnabled().performClick()
        compose.onNodeWithTag("zap-artwork").inView()
            .assertContentDescriptionEquals("Scan Zap to start")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertEvents("schedules", "history", "selection", "zap")
        captureZapEvidence("fixture-zap-idle")
    }

    @Test
    fun activeShowsElapsedTimeAndStopWithHelpButNoEditableSelectionOrIdleNavigation() {
        render(activeState())
        compose.onNodeWithText(JustnoiseContract.text("zap.active.title")).assertIsDisplayed()
        compose.onNodeWithText(ELAPSED).assertIsDisplayed()
        compose.onNodeWithTag("zap-selection").assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.zap_schedules_accessibility)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.zap_history_accessibility)).assertDoesNotExist()
        compose.onNodeWithText(JustnoiseContract.text("zap.active.action.stop"))
            .inView().assertIsEnabled().performClick()
        compose.onNodeWithText(text(R.string.zap_session_help))
            .inView().assertHasClickAction().performClick()
        assertEvents("zap", "setup")
        captureZapEvidence("fixture-zap-active")
    }

    @Test
    fun recoveryWithRequestedSessionOffersOnlyStopAndHelpWithoutActivePresentation() {
        render(activeState().copy(truth = Gate0Truth.RECOVERY_REQUIRED))
        assertNoActivePresentation()
        compose.onNodeWithText(text(R.string.zap_recovery_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.zap_recovery_subtitle)).assertIsDisplayed()
        compose.onNodeWithTag("zap-selection").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.zap_recovery_needed)).inView()
        compose.onNodeWithText("Scan Zap to stop").inView().performClick()
        compose.onNodeWithText(text(R.string.zap_session_help)).inView().performClick()
        assertEvents("zap", "setup")
        captureZapEvidence("fixture-zap-recovery-requested-lower-actions")
    }

    @Test
    fun idleRecoveryExplainsUncertaintyAndOffersSetupInsteadOfAStart() {
        render(IntegratedZapState(truth = Gate0Truth.RECOVERY_REQUIRED, elapsedText = ELAPSED))
        assertNoActivePresentation()
        compose.onNodeWithTag("zap-artwork").assertContentDescriptionEquals("Open setup")
        compose.onNodeWithText("Scan Zap to start").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.zap_recovery_needed)).inView()
        compose.onAllNodesWithText(text(R.string.zap_open_setup))[0].inView().performClick()
        assertEvents("setup")
    }

    @Test
    fun everySetupGapRetainsItsExplanationAndCannotPresentProtectionWithAnOldDesiredFlag() {
        val notices = mapOf(
            Gate0Truth.CONSENT_REQUIRED to R.string.zap_consent_needed,
            Gate0Truth.TARGET_REQUIRED to R.string.zap_selection_needed,
            Gate0Truth.ZAP_REQUIRED to R.string.zap_pair_needed,
            Gate0Truth.PERMISSION_REQUIRED to R.string.zap_permission_needed,
        )
        notices.forEach { (truth, notice) ->
            listOf(false, true).forEach { desired ->
                render(IntegratedZapState(truth = truth, desiredActive = desired, elapsedText = ELAPSED))
                assertNoActivePresentation()
                compose.onNodeWithTag("zap-artwork").assertContentDescriptionEquals("Open setup")
                compose.onNodeWithText(text(notice)).inView()
                val help = if (desired) R.string.zap_session_help else R.string.zap_open_setup
                compose.onAllNodesWithText(text(help))[0].inView().performClick()
                assertEvents("setup")
            }
        }
    }

    @Test
    fun scanningDisablesArtworkAndProvidesAnAccessibleCancelWithoutLosingTheResultNotice() {
        listOf(
            IntegratedZapState(truth = Gate0Truth.IDLE),
            activeState(),
            activeState().copy(truth = Gate0Truth.RECOVERY_REQUIRED),
        ).forEach { state ->
            render(state.copy(isScanning = true, transientStatus = SCAN_NOTICE))
            compose.onNodeWithTag("zap-artwork").inView()
                .assertContentDescriptionEquals("Hold Zap near phone").assertIsNotEnabled()
            compose.onNodeWithText(SCAN_NOTICE).inView()
            compose.onNodeWithText(text(R.string.zap_cancel_scan)).inView()
                .assertHasClickAction().assertIsEnabled().performClick()
            compose.onNodeWithText(JustnoiseContract.text("zap.active.action.stop")).assertDoesNotExist()
            assertEvents("cancel-scan")
        }
    }

    @Test
    fun compactTwoHundredPercentTextKeepsRecoveryHelpAndScanCancellationReachable() {
        compose.runOnIdle { compactEnlarged = true }
        render(activeState().copy(truth = Gate0Truth.RECOVERY_REQUIRED))
        assertNoActivePresentation()
        compose.onNodeWithText("Scan Zap to stop").inView().performClick()
        compose.onNodeWithText(text(R.string.zap_session_help)).inView().performClick()
        assertEvents("zap", "setup")
        captureZapEvidence("fixture-zap-compact-360x640-font200-recovery-lower-actions")

        render(activeState().copy(isScanning = true, transientStatus = SCAN_NOTICE))
        compose.onNodeWithText(text(R.string.zap_cancel_scan)).inView()
            .assertIsDisplayed().assertIsEnabled().performClick()
        assertEvents("cancel-scan")
    }

    private fun render(state: IntegratedZapState) {
        compose.runOnIdle { events.clear(); renderedState = state }
        compose.waitForIdle()
    }

    private fun assertNoActivePresentation() {
        compose.onNodeWithText(JustnoiseContract.text("zap.active.title")).assertDoesNotExist()
        compose.onNodeWithText(JustnoiseContract.text("zap.active.action.stop")).assertDoesNotExist()
        compose.onNodeWithText(ELAPSED).assertDoesNotExist()
    }

    private fun assertEvents(vararg expected: String) {
        compose.runOnIdle { assertEquals(expected.toList(), events.toList()) }
    }

    /** Normal-sized Zap has no scroll ancestor; compact/attention layouts do. */
    private fun SemanticsNodeInteraction.inView(): SemanticsNodeInteraction {
        try {
            assertIsDisplayed()
        } catch (_: AssertionError) {
            performScrollTo()
        }
        return assertIsDisplayed()
    }

    private fun activeState() = IntegratedZapState(truth = Gate0Truth.ACTIVE,
        selectedAppName = "2 apps", elapsedText = ELAPSED, desiredActive = true)

    private fun text(resource: Int): String = context.getString(resource)

    private companion object {
        const val ELAPSED = "00:25:00"
        const val SCAN_NOTICE = "Hold the Zap near your phone to finish this scan."
    }
}

/**
 * Opt-in, unmodified full-display evidence. Call after Compose assertions have settled.
 * Shared with real-route tests, whose filenames must distinguish them from fixture captures.
 * Nothing is captured or written unless the explicit instrumentation argument is enabled.
 */
internal fun captureZapEvidence(name: String) {
    if (InstrumentationRegistry.getArguments().getString("captureZapEvidence") != "true") return
    check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") {
        "Zap UI evidence capture is limited to the emulator"
    }
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    check(context.packageName == "com.justnoise.androiddev") {
        "Zap UI evidence capture is limited to the disposable integrated app"
    }
    require(name.matches(Regex("[a-z0-9][a-z0-9_-]*"))) { "Use a safe descriptive evidence name" }
    val directory = checkNotNull(context.getExternalFilesDir("zap-ui-evidence")) {
        "Zap UI evidence storage is unavailable"
    }
    check(directory.isDirectory || directory.mkdirs()) { "Could not create Zap UI evidence directory" }
    instrumentation.waitForIdleSync()
    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
        "Android could not capture the Zap UI evidence"
    }
    try {
        File(directory, "$name.png").outputStream().use { output ->
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Android could not save the Zap UI evidence"
            }
        }
    } finally {
        screenshot.recycle()
    }
}
