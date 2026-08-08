package com.justnoise.uiprototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        val prototypeScene = intent.getStringExtra("prototype_scene")
        setContent {
            when (prototypeScene) {
                "noise-rewind-ready" -> JustnoiseTheme {
                    NoiseRewindSceneHost(startWithFlow = false)
                }
                "noise-rewind-flow" -> JustnoiseTheme {
                    NoiseRewindSceneHost(startWithFlow = true)
                }
                "blocked-surface" -> JustnoiseTheme {
                    BlockedSurfacePrototype(displayName = "Instagram")
                }
                "blocked-surface-long-name" -> JustnoiseTheme {
                    BlockedSurfacePrototype(
                        displayName = "A Very Long Social Media Application Name",
                    )
                }
                "capability-permission-required" -> JustnoiseTheme {
                    CapabilityRecoveryScene(RestrictionCapabilityPreview.PermissionRequired)
                }
                "capability-permission-denied" -> JustnoiseTheme {
                    CapabilityRecoveryScene(RestrictionCapabilityPreview.PermissionDenied)
                }
                "capability-temporarily-unavailable" -> JustnoiseTheme {
                    CapabilityRecoveryScene(RestrictionCapabilityPreview.TemporarilyUnavailable)
                }
                "capability-unsupported" -> JustnoiseTheme {
                    CapabilityRecoveryScene(RestrictionCapabilityPreview.Unsupported)
                }
                "startup-loading" -> JustnoiseTheme {
                    StartupBoundaryScene(StartupPreviewPhase.AppHydration)
                }
                "startup-cleanup" -> JustnoiseTheme {
                    StartupBoundaryScene(StartupPreviewPhase.AccountCleanup)
                }
                "startup-cleanup-blocked" -> JustnoiseTheme {
                    StartupBoundaryScene(StartupPreviewPhase.AccountCleanupBlocked)
                }
                "auth-resolving" -> JustnoiseTheme {
                    StartupBoundaryScene(StartupPreviewPhase.AuthResolution)
                }
                "password-update" -> JustnoiseTheme {
                    PasswordUpdateScene()
                }
                "password-update-success" -> JustnoiseTheme {
                    PasswordUpdateScene(
                        PasswordUpdateUiState(
                            newPassword = "focus123",
                            confirmPassword = "focus123",
                            visualState = PasswordUpdateVisualState.Success,
                            statusMessage = "Your password has been updated successfully.",
                        ),
                    )
                }
                "coach-tour" -> JustnoiseTheme {
                    CoachTourScene()
                }
                "schedules-empty" -> EmptySchedulesScene()
                else -> JustnoisePrototypeApp()
            }
        }
    }
}
