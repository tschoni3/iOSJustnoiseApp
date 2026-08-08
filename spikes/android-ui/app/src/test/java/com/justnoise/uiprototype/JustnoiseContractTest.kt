package com.justnoise.uiprototype

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JustnoiseContractTest {
    private val repositoryRoot = File(
        checkNotNull(System.getProperty("justnoise.repositoryRoot")),
    )

    @Test
    fun nativeCopyMappingMatchesSharedContract() {
        val json = JSONObject(
            repositoryRoot.resolve("product/copy/en.v1.json").readText(),
        ).getJSONObject("strings")

        assertEquals(json.length(), JustnoiseContract.copy.size)
        JustnoiseContract.copy.forEach { (key, value) ->
            assertTrue("Shared copy is missing $key", json.has(key))
            assertEquals("Copy drift for $key", json.getString(key), value)
        }
    }

    @Test
    fun nativeColorMappingMatchesSharedContract() {
        val json = JSONObject(
            repositoryRoot.resolve("product/design/tokens.v1.json").readText(),
        ).getJSONObject("colors")

        assertEquals(json.length(), JustnoiseContract.colors.size)
        JustnoiseContract.colors.forEach { (key, value) ->
            assertTrue("Shared colors are missing $key", json.has(key))
            assertEquals("Color drift for $key", json.getString(key), value)
        }
    }

    @Test
    fun parserMapsTrailingAlphaToComposeArgb() {
        assertEquals(0xE6FFFF00L, parseContractColor("#FFFF00E6"))
        assertEquals(0xFF151515L, parseContractColor("#151515"))
        assertThrows(IllegalArgumentException::class.java) {
            parseContractColor("#ABC")
        }
    }

    @Test
    fun previewReducerChangesOnlyRequestedState() {
        val initial = ZapUiState()
        val active = ZapPreviewReducer.reduce(initial, ZapPreviewAction.ToggleSession)
        val study = PreviewModes.first { it.id == "study" }
        val selected = ZapPreviewReducer.reduce(active, ZapPreviewAction.SelectMode(study))

        assertTrue(active.isActive)
        assertEquals(initial.selectedMode, active.selectedMode)
        assertFalse(active.isCompletionVisible)
        assertTrue(selected.isActive)
        assertEquals(study, selected.selectedMode)
        val stopped = ZapPreviewReducer.reduce(selected, ZapPreviewAction.ToggleSession)
        assertFalse(stopped.isActive)
        assertTrue(stopped.isCompletionVisible)

        val dismissed = ZapPreviewReducer.reduce(
            stopped,
            ZapPreviewAction.DismissCompletion,
        )
        assertFalse(dismissed.isCompletionVisible)
        assertFalse(dismissed.isActive)
        assertEquals(study, dismissed.selectedMode)
    }

    @Test
    fun prototypeSourceDeclaresNoUserPermissionOrService() {
        val manifest = repositoryRoot.resolve(
            "spikes/android-ui/app/src/main/AndroidManifest.xml",
        ).readText()

        assertFalse(manifest.contains("<uses-permission"))
        assertFalse(manifest.contains("<service"))
    }

    @Test
    fun previewLaunchFlowMatchesReleaseSequence() {
        var state = PrototypeFlowState()
        assertEquals(PrototypeRoute.Onboarding, state.route)
        assertEquals(0, state.onboardingPage)

        state = PrototypeFlowReducer.reduce(
            state,
            PrototypeFlowAction.AdvanceOnboarding,
        )
        assertEquals(PrototypeRoute.Onboarding, state.route)
        assertEquals(1, state.onboardingPage)

        state = PrototypeFlowReducer.reduce(
            state,
            PrototypeFlowAction.AdvanceOnboarding,
        )
        assertEquals(PrototypeRoute.Activation, state.route)

        state = PrototypeFlowReducer.reduce(
            state,
            PrototypeFlowAction.CompleteActivationPreview,
        )
        assertEquals(PrototypeRoute.SignIn, state.route)

        state = PrototypeFlowReducer.reduce(
            state,
            PrototypeFlowAction.OpenSignUp,
        )
        assertEquals(PrototypeRoute.SignUp, state.route)

        state = PrototypeFlowReducer.reduce(
            state,
            PrototypeFlowAction.OpenSignIn,
        )
        state = PrototypeFlowReducer.reduce(
            state,
            PrototypeFlowAction.PreviewAuthenticated,
        )
        assertEquals(PrototypeRoute.Zap, state.route)
    }

    @Test
    fun previewManagementRoutesReturnToExpectedParent() {
        val zap = PrototypeFlowState(route = PrototypeRoute.Zap)

        val modes = PrototypeFlowReducer.reduce(zap, PrototypeFlowAction.OpenModes)
        assertEquals(PrototypeRoute.Modes, modes.route)
        assertEquals(
            PrototypeRoute.Zap,
            PrototypeFlowReducer.reduce(modes, PrototypeFlowAction.Back).route,
        )

        val schedules = PrototypeFlowReducer.reduce(zap, PrototypeFlowAction.OpenSchedules)
        assertEquals(PrototypeRoute.Schedules, schedules.route)
        assertEquals(
            PrototypeRoute.Zap,
            PrototypeFlowReducer.reduce(schedules, PrototypeFlowAction.Back).route,
        )

        val history = PrototypeFlowReducer.reduce(zap, PrototypeFlowAction.OpenHistory)
        val settings = PrototypeFlowReducer.reduce(history, PrototypeFlowAction.OpenSettings)
        assertEquals(PrototypeRoute.Settings, settings.route)
        assertEquals(
            PrototypeRoute.History,
            PrototypeFlowReducer.reduce(settings, PrototypeFlowAction.Back).route,
        )
    }

    @Test
    fun coachTourPreservesTheThreeStepReleaseSequence() {
        assertEquals(
            listOf(CoachTarget.Zap, CoachTarget.Mode, CoachTarget.Schedule),
            BasicCoachMarks.map { it.target },
        )

        var state = CoachTourUiState()
        assertTrue(state.isVisible)
        assertEquals(0, state.step)

        state = CoachTourReducer.reduce(state, CoachTourAction.Next)
        assertTrue(state.isVisible)
        assertEquals(1, state.step)
        state = CoachTourReducer.reduce(state, CoachTourAction.Next)
        assertTrue(state.isVisible)
        assertEquals(2, state.step)
        state = CoachTourReducer.reduce(state, CoachTourAction.Next)
        assertFalse(state.isVisible)
        assertEquals(2, state.step)

        val skipped = CoachTourReducer.reduce(CoachTourUiState(), CoachTourAction.Skip)
        assertFalse(skipped.isVisible)
        assertEquals(0, skipped.step)
    }

    @Test
    fun noiseRewindPreviewPreservesSevenPageSequenceAndDraft() {
        var state = NoiseRewindUiState()
        assertEquals(0, state.page)
        assertEquals(
            0,
            NoiseRewindReducer.reduce(state, NoiseRewindAction.Back).page,
        )

        state = NoiseRewindReducer.reduce(state, NoiseRewindAction.Begin)
        assertEquals(1, state.page)
        repeat(5) {
            state = NoiseRewindReducer.reduce(state, NoiseRewindAction.Next)
        }
        assertEquals(6, state.page)
        assertEquals(
            6,
            NoiseRewindReducer.reduce(state, NoiseRewindAction.Next).page,
        )

        state = NoiseRewindReducer.reduce(
            state,
            NoiseRewindAction.ChangeReflection("Morning mode helped."),
        )
        state = NoiseRewindReducer.reduce(state, NoiseRewindAction.Back)
        assertEquals(5, state.page)
        assertEquals("Morning mode helped.", state.reflectionDraft)

        assertEquals(
            6,
            NoiseRewindReducer.reduce(state, NoiseRewindAction.ShowPage(99)).page,
        )
        assertEquals(
            0,
            NoiseRewindReducer.reduce(state, NoiseRewindAction.ShowPage(-4)).page,
        )
        assertEquals(
            NoiseRewindUiState(),
            NoiseRewindReducer.reduce(state, NoiseRewindAction.Reset),
        )
    }

    @Test
    fun previewShieldRendererConsumesEverySharedSubstitutionFixture() {
        val cases = JSONObject(
            repositoryRoot.resolve("product/behavior/portable-fixtures.v1.json").readText(),
        ).getJSONArray("shieldMessageRendering")

        assertEquals(3, cases.length())
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val targetKind = when (case.getString("fallbackCopyKey")) {
                "shield.application.fallbackName" -> ShieldTargetKind.Application
                "shield.website.fallbackName" -> ShieldTargetKind.Website
                else -> error("Unknown shield fallback key")
            }
            val rendered = renderShieldMessage(
                displayName = case.getString("displayName"),
                targetKind = targetKind,
            )

            assertEquals(case.getString("id"), case.getString("expectedMessage"), rendered)
            assertFalse(case.getString("id"), rendered.contains("{appName}"))
        }
    }

    @Test
    fun recoveryPreviewNeverTurnsAnUnavailableCapabilityIntoAvailable() {
        val expectedRequests = mapOf(
            RestrictionCapabilityPreview.PermissionRequired to RecoveryRequest.OpenSettings,
            RestrictionCapabilityPreview.PermissionDenied to RecoveryRequest.OpenSettings,
            RestrictionCapabilityPreview.TemporarilyUnavailable to RecoveryRequest.Retry,
            RestrictionCapabilityPreview.Unsupported to RecoveryRequest.None,
        )

        expectedRequests.forEach { (capability, expectedRequest) ->
            val initial = CapabilityRecoveryUiState(capability)
            val afterPrimary = CapabilityRecoveryReducer.reduce(
                initial,
                CapabilityRecoveryAction.Primary,
            )

            assertEquals(capability.contractValue, capability, afterPrimary.capability)
            assertEquals(capability.contractValue, expectedRequest, afterPrimary.request)
            assertEquals(
                capability.contractValue,
                capability == RestrictionCapabilityPreview.Unsupported,
                afterPrimary.isDismissed,
            )
        }

        val denied = CapabilityRecoveryUiState(
            RestrictionCapabilityPreview.PermissionDenied,
            request = RecoveryRequest.OpenSettings,
        )
        val handled = CapabilityRecoveryReducer.reduce(
            denied,
            CapabilityRecoveryAction.RequestHandled,
        )
        assertEquals(RestrictionCapabilityPreview.PermissionDenied, handled.capability)
        assertEquals(RecoveryRequest.None, handled.request)
        assertFalse(handled.isDismissed)
    }

    @Test
    fun startupCleanupRetryKeepsTheHydrationGateClosed() {
        val blocked = StartupBoundaryUiState(StartupPreviewPhase.AccountCleanupBlocked)
        val retrying = StartupBoundaryReducer.reduce(
            blocked,
            StartupBoundaryAction.RetryAccountCleanup,
        )

        assertEquals(StartupPreviewPhase.AccountCleanupBlocked, retrying.phase)
        assertEquals(StartupPreviewRequest.RetryAccountCleanup, retrying.request)

        val loading = StartupBoundaryUiState(StartupPreviewPhase.AppHydration)
        assertEquals(
            loading,
            StartupBoundaryReducer.reduce(
                loading,
                StartupBoundaryAction.RetryAccountCleanup,
            ),
        )
    }

    @Test
    fun passwordUpdatePreviewValidatesBeforeRequestingAnAdapterMutation() {
        fun submit(state: PasswordUpdateUiState): PasswordUpdateUiState =
            PasswordUpdateReducer.reduce(state, PasswordUpdateAction.Submit)

        assertEquals("Please fill in all fields.", submit(PasswordUpdateUiState()).statusMessage)
        assertEquals(
            "Passwords do not match.",
            submit(
                PasswordUpdateUiState(
                    newPassword = "focus1",
                    confirmPassword = "focus2",
                ),
            ).statusMessage,
        )
        assertEquals(
            "Password is too weak (min 6 chars).",
            submit(
                PasswordUpdateUiState(
                    newPassword = "short",
                    confirmPassword = "short",
                ),
            ).statusMessage,
        )
        assertEquals(
            "Invalid reset token.",
            submit(
                PasswordUpdateUiState(
                    newPassword = "focus123",
                    confirmPassword = "focus123",
                    hasResetToken = false,
                ),
            ).statusMessage,
        )

        val submitting = submit(
            PasswordUpdateUiState(
                newPassword = "focus123",
                confirmPassword = "focus123",
            ),
        )
        assertEquals(PasswordUpdateVisualState.Submitting, submitting.visualState)
        assertEquals(PasswordUpdatePreviewRequest.SubmitPassword, submitting.request)
        assertFalse(submitting.visualState == PasswordUpdateVisualState.Success)

        val succeeded = PasswordUpdateReducer.reduce(
            submitting,
            PasswordUpdateAction.ResolveSuccess,
        )
        assertEquals(PasswordUpdateVisualState.Success, succeeded.visualState)
        assertEquals("Your password has been updated successfully.", succeeded.statusMessage)

        assertEquals(
            PasswordUpdateUiState(),
            PasswordUpdateReducer.reduce(
                PasswordUpdateUiState(),
                PasswordUpdateAction.ResolveSuccess,
            ),
        )
    }
}
