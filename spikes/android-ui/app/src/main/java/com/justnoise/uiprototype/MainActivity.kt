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
                else -> JustnoisePrototypeApp()
            }
        }
    }
}
