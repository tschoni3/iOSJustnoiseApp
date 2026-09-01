package com.justnoise.integrated

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.justnoise.gate0.app.MainActivity as Gate0SetupActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.feature.Gate0Truth
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var controller: Gate0Controller
    private lateinit var preferences: Gate0Preferences
    private val handler = Handler(Looper.getMainLooper())
    private var screenState by mutableStateOf(IntegratedZapState())

    private val refresh = object : Runnable {
        override fun run() {
            refreshTruth()
            handler.postDelayed(this, REFRESH_INTERVAL_MILLISECONDS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = Gate0Controller(this)
        preferences = Gate0Preferences(this)
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
        setContent {
            JustnoiseIntegratedTheme {
                IntegratedZapSurface(
                    state = screenState,
                    onOpenSetup = ::openSetup,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        refresh.run()
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun openSetup() {
        startActivity(Intent(this, Gate0SetupActivity::class.java))
    }

    private fun refreshTruth() {
        val truth = controller.truth()
        val stored = preferences.snapshot()
        screenState = IntegratedZapState(
            truth = truth,
            selectedAppName = resolveAppLabel(controller.selectedPackage()),
            elapsedText = if (truth == Gate0Truth.ACTIVE) {
                formatElapsed(
                    (System.currentTimeMillis() - stored.activeSinceEpochMilliseconds)
                        .coerceAtLeast(0L),
                )
            } else {
                "00:00:00"
            },
        )
    }

    private fun resolveAppLabel(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun formatElapsed(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = totalSeconds % 3_600L / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private companion object {
        const val REFRESH_INTERVAL_MILLISECONDS = 1_000L
    }
}
