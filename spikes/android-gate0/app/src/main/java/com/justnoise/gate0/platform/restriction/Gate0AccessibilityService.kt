package com.justnoise.gate0.platform.restriction

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.platform.discovery.ProtectedPackagePolicy
import com.justnoise.gate0.platform.discovery.ProtectedPackageResolver

class Gate0AccessibilityService : AccessibilityService() {
    private lateinit var preferences: Gate0Preferences
    private lateinit var overlayController: OverlayController
    private lateinit var protectedPackageResolver: ProtectedPackageResolver
    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_WITHDRAW -> {
                    overlayController.hide()
                    preferences.markServiceDisconnected()
                    disableSelf()
                }

                ACTION_STATE_CHANGED -> {
                    // Any config or session revision invalidates the current overlay. A fresh,
                    // exact target-package event is required before it can be shown again.
                    overlayController.hide()
                }
            }
        }
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            val stored = preferences.snapshot()
            if (
                stored.acceptedDisclosureVersion != Gate0Preferences.CURRENT_DISCLOSURE_VERSION ||
                stored.acceptedDisclosureEpochMilliseconds <= 0L
            ) {
                overlayController.hide()
                preferences.markServiceDisconnected()
                disableSelf()
                return
            }
            preferences.updateServiceHeartbeat(SystemClock.elapsedRealtime())
            if (!stored.desiredActive || stored.recoveryRequired) overlayController.hide()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MILLISECONDS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferences = Gate0Preferences(this)
        overlayController = OverlayController(this)
        protectedPackageResolver = ProtectedPackageResolver(this)

        val stored = preferences.snapshot()
        if (
            stored.acceptedDisclosureVersion != Gate0Preferences.CURRENT_DISCLOSURE_VERSION ||
            stored.acceptedDisclosureEpochMilliseconds <= 0L
        ) {
            preferences.markServiceDisconnected()
            disableSelf()
            return
        }

        registerStateReceiver()
        handler.removeCallbacks(heartbeat)
        heartbeat.run()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (::overlayController.isInitialized) overlayController.hide()
            return
        }

        val foregroundPackage = event.packageName?.toString()?.takeIf(String::isNotBlank)
        if (foregroundPackage == null) {
            overlayController.hide()
            return
        }
        val stored = preferences.snapshot()
        val selectedPackage = stored.selectedPackage
        val protectedPackages = protectedPackageResolver.resolve()
        val mayIntercept = stored.acceptedDisclosureVersion ==
            Gate0Preferences.CURRENT_DISCLOSURE_VERSION &&
            stored.acceptedDisclosureEpochMilliseconds > 0L &&
            stored.desiredActive &&
            !stored.recoveryRequired &&
            selectedPackage != null &&
            !ProtectedPackagePolicy.isProtected(selectedPackage, protectedPackages) &&
            foregroundPackage == selectedPackage

        if (mayIntercept) {
            overlayController.show(resolveAppLabel(selectedPackage))
        } else {
            overlayController.hide()
        }
    }

    private fun resolveAppLabel(packageName: String): String? = try {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(applicationInfo).toString()
    } catch (_: Exception) {
        null
    }

    override fun onInterrupt() {
        shutdownServiceState()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdownServiceState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdownServiceState()
        super.onDestroy()
    }

    private fun registerStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_STATE_CHANGED)
            addAction(ACTION_WITHDRAW)
        }
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun shutdownServiceState() {
        handler.removeCallbacks(heartbeat)
        if (::overlayController.isInitialized) overlayController.hide()
        if (::preferences.isInitialized) preferences.markServiceDisconnected()
        if (receiverRegistered) {
            try {
                unregisterReceiver(stateReceiver)
            } catch (_: IllegalArgumentException) {
                // Already removed by the system.
            }
            receiverRegistered = false
        }
    }

    companion object {
        const val ACTION_STATE_CHANGED = "com.justnoise.gate0.action.STATE_CHANGED"
        const val ACTION_WITHDRAW = "com.justnoise.gate0.action.WITHDRAW"
        private const val HEARTBEAT_INTERVAL_MILLISECONDS = 2_000L
    }
}
