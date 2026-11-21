package com.justnoise.gate0.platform.restriction

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.justnoise.gate0.app.DirectNfcActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.platform.discovery.ProtectedPackagePolicy
import com.justnoise.gate0.platform.discovery.ProtectedPackageResolver
import com.justnoise.gate0.platform.notification.SessionNotifications
import com.justnoise.gate0.platform.restriction.ScheduledEnforcement.ReadinessStatus
import java.io.FileDescriptor
import java.io.PrintWriter

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
                    SessionNotifications.cancel(this@Gate0AccessibilityService)
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
                    SessionNotifications.refresh(this@Gate0AccessibilityService)
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
                SessionNotifications.cancel(this@Gate0AccessibilityService)
                disableSelf()
                return
            }
            if (stored.desiredActive && stored.activeScheduleId != null && hasUnknownInteractiveForeground()) {
                preferences.markRecoveryRequired()
                overlayController.hide()
            }
            preferences.updateServiceHeartbeat(SystemClock.elapsedRealtime())
            if (!stored.desiredActive || stored.recoveryRequired) overlayController.hide()
            SessionNotifications.refresh(this@Gate0AccessibilityService)
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MILLISECONDS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferences = Gate0Preferences(this)
        overlayController = OverlayController(this) {
            preferences.markRecoveryRequired()
            SessionNotifications.refresh(this)
        }
        protectedPackageResolver = ProtectedPackageResolver(this)

        val stored = preferences.snapshot()
        if (
            stored.acceptedDisclosureVersion != Gate0Preferences.CURRENT_DISCLOSURE_VERSION ||
            stored.acceptedDisclosureEpochMilliseconds <= 0L
        ) {
            preferences.markServiceDisconnected()
            SessionNotifications.cancel(this)
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
        if (windowChange && AccessibilitySetupReturn.isPending()) {
            val stored = preferences.snapshot()
            AccessibilitySetupReturn.onWindow(this, foregroundPackage,
                stored.acceptedDisclosureVersion == Gate0Preferences.CURRENT_DISCLOSURE_VERSION &&
                    stored.acceptedDisclosureEpochMilliseconds > 0L &&
                    !stored.desiredActive && !stored.recoveryRequired &&
                    getSystemService(PowerManager::class.java).isInteractive &&
                    !getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        }
        val eventClass = if (windowChange) event?.className?.toString() else null
        val ownActivityWindow = windowChange && foregroundPackage == packageName &&
            isVisibleOwnActivity(eventClass)
        // SoftInputWindow belongs to the keyboard process, not the app beneath it.
        // Class metadata also distinguishes our declared UI activities from our overlay.
        // No event content,
        // node, window list or typed text is read or retained.
        val observedPackage = if (windowChange) {
            ForegroundWindowRules.observedPackage(
                previousPackage = lastForegroundPackage,
                eventPackage = foregroundPackage,
                eventClass = eventClass,
                currentInputMethodPackage = if (eventClass == ForegroundWindowRules.SOFT_INPUT_WINDOW)
                    currentInputMethodPackage() else null,
                eventPackageProtected = foregroundPackage == null ||
                    (eventClass == ForegroundWindowRules.SOFT_INPUT_WINDOW &&
                        ProtectedPackagePolicy.isProtected(foregroundPackage, protectedPackageResolver.resolve())),
            )
        } else foregroundPackage
        if (windowChange && !(overlayController.isShowing && foregroundPackage == packageName &&
                !ownActivityWindow)) {
            foregroundGeneration += 1L
            lastForegroundPackage = observedPackage
        }
        applyObservedPackage(observedPackage, windowChange, ownActivityWindow)
    }

    private fun isVisibleOwnActivity(eventClass: String?): Boolean {
        // The transparent NFC trampoline is not an app UI transition: ignoring it
        // must not briefly expose a blocked target while a tag is being validated.
        if (eventClass.isNullOrBlank() || eventClass == DirectNfcActivity::class.java.name) return false
        return try {
            val activity = packageManager.getActivityInfo(ComponentName(packageName, eventClass), 0)
            activity.packageName == packageName && activity.name == eventClass
        } catch (_: Exception) {
            false
        }
    }

    private fun currentInputMethodPackage(): String? = try {
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)?.packageName
    } catch (_: RuntimeException) {
        null // Unknown metadata must not invent a foreground app or bypass an escape.
    }

    private fun applyObservedPackage(
        foregroundPackage: String?,
        windowChange: Boolean,
        ownActivityWindow: Boolean = false,
    ) {
        val stored = preferences.snapshot()
        if (stored.desiredActive && stored.activeScheduleId != null && hasUnknownInteractiveForeground()) {
            preferences.markRecoveryRequired()
            overlayController.hide()
            SessionNotifications.refresh(this)
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
                isOwnActivityWindow = ownActivityWindow,
            )
        ) {
            OverlayEventDecision.SHOW ->
                overlayController.show(resolveAppLabel(requireNotNull(foregroundPackage)))

            OverlayEventDecision.HIDE -> overlayController.hide()
            OverlayEventDecision.IGNORE -> Unit
        }
    }

    override fun prepareScheduleStart(): ScheduledEnforcement.Preparation {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!boundForScheduling) return rejectSchedulePreparation(ReadinessStatus.NO_BOUND_SERVICE)
        if (!AccessibilityServiceStatus.isEnabled(this))
            return rejectSchedulePreparation(ReadinessStatus.SERVICE_DISABLED)
        val stored = preferences.snapshot()
        if (stored.acceptedDisclosureVersion != Gate0Preferences.CURRENT_DISCLOSURE_VERSION ||
            stored.acceptedDisclosureEpochMilliseconds <= 0L
        ) return rejectSchedulePreparation(ReadinessStatus.CONSENT_REQUIRED)
        if (stored.recoveryRequired) return rejectSchedulePreparation(ReadinessStatus.RECOVERY_REQUIRED)
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        // A cold/unknown foreground cannot be presented as an already-intercepted app.
        if (interactive && !locked && lastForegroundPackage == null)
            return rejectSchedulePreparation(ReadinessStatus.FOREGROUND_UNKNOWN)
        return if (preferences.updateServiceHeartbeat(SystemClock.elapsedRealtime()))
            ScheduledEnforcement.Preparation.Ready(foregroundGeneration)
            else rejectSchedulePreparation(ReadinessStatus.HEARTBEAT_WRITE_FAILED)
    }

    private fun rejectSchedulePreparation(status: ReadinessStatus) =
        ScheduledEnforcement.Preparation.Rejected(status)

    /** Read-only Android service diagnostics; deliberately omit all product/event data. */
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        writer.write("Justnoise schedule readiness: ${ScheduledEnforcement.lastReadinessStatus.name}\n")
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
                SessionNotifications.refresh(this)
                return@post
            }
            if (hasUnknownInteractiveForeground()) {
                preferences.markRecoveryRequired()
                overlayController.hide()
                SessionNotifications.refresh(this)
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
            SessionNotifications.refresh(this)
        }
    }

    private fun resolveAppLabel(packageName: String): String? = try {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(applicationInfo).toString()
    } catch (_: Exception) {
        null
    }

    override fun onInterrupt() {
        AccessibilitySetupReturn.cancel()
        // Feedback interruption does not unbind the service. Keep stop/withdrawal delivery
        // and heartbeat reconciliation alive, but suspend enforcement until recovery.
        if (::overlayController.isInitialized) overlayController.hide()
        foregroundGeneration += 1L
        lastForegroundPackage = null
        if (::preferences.isInitialized) preferences.markRecoveryRequired()
        SessionNotifications.refresh(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::overlayController.isInitialized) overlayController.refreshBounds()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilitySetupReturn.cancel()
        shutdownServiceState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilitySetupReturn.cancel()
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
        SessionNotifications.cancel(this)
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
