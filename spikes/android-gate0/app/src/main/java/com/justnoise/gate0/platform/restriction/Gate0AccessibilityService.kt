package com.justnoise.gate0.platform.restriction

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.platform.discovery.ProtectedPackagePolicy
import com.justnoise.gate0.platform.discovery.ProtectedPackageResolver

class Gate0AccessibilityService : AccessibilityService(), ScheduledEnforcementEndpoint {
    private lateinit var preferences: Gate0Preferences
    private lateinit var overlayController: OverlayController
    private lateinit var protectedPackageResolver: ProtectedPackageResolver
    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    @Volatile private var boundForScheduling = false
    @Volatile private var foregroundGeneration = 0L
    // One current observation only, never a log or a persisted package history.
    private var lastForegroundPackage: String? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    foregroundGeneration += 1L
                    lastForegroundPackage = null
                    overlayController.hide()
                }
                ACTION_WITHDRAW -> {
                    foregroundGeneration += 1L
                    boundForScheduling = false
                    ScheduledEnforcement.disconnected(this@Gate0AccessibilityService)
                    lastForegroundPackage = null
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
                boundForScheduling = false
                ScheduledEnforcement.disconnected(this@Gate0AccessibilityService)
                lastForegroundPackage = null
                preferences.markServiceDisconnected()
                disableSelf()
                return
            }
            if (stored.desiredActive && stored.activeScheduleId != null && hasUnknownInteractiveForeground()) {
                preferences.markRecoveryRequired()
                overlayController.hide()
            }
            preferences.updateServiceHeartbeat(SystemClock.elapsedRealtime())
            if (!stored.desiredActive || stored.recoveryRequired) overlayController.hide()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MILLISECONDS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferences = Gate0Preferences(this)
        overlayController = OverlayController(this, preferences::markRecoveryRequired)
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
        boundForScheduling = true
        foregroundGeneration += 1L
        lastForegroundPackage = null
        ScheduledEnforcement.connected(this)
        handler.removeCallbacks(heartbeat)
        heartbeat.run()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val foregroundPackage = event?.packageName?.toString()?.takeIf(String::isNotBlank)
        val windowChange = event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (windowChange && !(overlayController.isShowing && foregroundPackage == packageName)) {
            foregroundGeneration += 1L
            lastForegroundPackage = foregroundPackage
        }
        applyObservedPackage(foregroundPackage, windowChange)
    }

    private fun applyObservedPackage(foregroundPackage: String?, windowChange: Boolean) {
        val stored = preferences.snapshot()
        if (stored.desiredActive && stored.activeScheduleId != null && hasUnknownInteractiveForeground()) {
            preferences.markRecoveryRequired()
            overlayController.hide()
            return
        }
        val activePackages = stored.activePackages
        val protectedPackages = protectedPackageResolver.resolve()
        val enforcementEligible = stored.acceptedDisclosureVersion ==
            Gate0Preferences.CURRENT_DISCLOSURE_VERSION &&
            stored.acceptedDisclosureEpochMilliseconds > 0L &&
            stored.desiredActive &&
            !stored.recoveryRequired &&
            activePackages.isNotEmpty() &&
            foregroundPackage != null &&
            !ProtectedPackagePolicy.isProtected(foregroundPackage, protectedPackages)

        when (
            OverlayEventRules.decide(
                isWindowStateChange = windowChange,
                foregroundPackage = foregroundPackage,
                justnoisePackage = packageName,
                selectedPackages = activePackages,
                enforcementEligible = enforcementEligible,
                overlayIsShowing = overlayController.isShowing,
            )
        ) {
            OverlayEventDecision.SHOW ->
                overlayController.show(resolveAppLabel(requireNotNull(foregroundPackage)))

            OverlayEventDecision.HIDE -> overlayController.hide()
            OverlayEventDecision.IGNORE -> Unit
        }
    }

    override fun prepareScheduleStart(): Long? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!boundForScheduling || !AccessibilityServiceStatus.isEnabled(this)) return null
        val stored = preferences.snapshot()
        if (stored.acceptedDisclosureVersion != Gate0Preferences.CURRENT_DISCLOSURE_VERSION ||
            stored.acceptedDisclosureEpochMilliseconds <= 0L || stored.recoveryRequired
        ) return null
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        // A cold/unknown foreground cannot be presented as an already-intercepted app.
        if (interactive && !locked && lastForegroundPackage == null) return null
        return if (preferences.updateServiceHeartbeat(SystemClock.elapsedRealtime())) foregroundGeneration else null
    }

    override fun isScheduleReadinessCurrent(generation: Long): Boolean =
        boundForScheduling && generation == foregroundGeneration

    private fun hasUnknownInteractiveForeground(): Boolean = lastForegroundPackage == null &&
        getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    override fun onScheduledStateChanged() {
        handler.post {
            if (!boundForScheduling) return@post
            val stored = preferences.snapshot()
            if (!stored.desiredActive || stored.recoveryRequired) {
                overlayController.hide()
                return@post
            }
            if (hasUnknownInteractiveForeground()) {
                preferences.markRecoveryRequired()
                overlayController.hide()
                return@post
            }
            val interactive = getSystemService(PowerManager::class.java).isInteractive
            val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if (!interactive || locked) {
                lastForegroundPackage = null
                overlayController.hide()
            } else {
                applyObservedPackage(lastForegroundPackage, windowChange = true)
            }
        }
    }

    private fun resolveAppLabel(packageName: String): String? = try {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(applicationInfo).toString()
    } catch (_: Exception) {
        null
    }

    override fun onInterrupt() {
        // Feedback interruption does not unbind the service. Keep stop/withdrawal delivery
        // and heartbeat reconciliation alive, but suspend enforcement until recovery.
        if (::overlayController.isInitialized) overlayController.hide()
        foregroundGeneration += 1L
        lastForegroundPackage = null
        if (::preferences.isInitialized) preferences.markRecoveryRequired()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::overlayController.isInitialized) overlayController.refreshBounds()
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
            addAction(Intent.ACTION_SCREEN_OFF)
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
        foregroundGeneration += 1L
        boundForScheduling = false
        ScheduledEnforcement.disconnected(this)
        lastForegroundPackage = null
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
