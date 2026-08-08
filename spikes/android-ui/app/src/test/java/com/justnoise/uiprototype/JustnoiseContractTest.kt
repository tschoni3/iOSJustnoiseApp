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
}
