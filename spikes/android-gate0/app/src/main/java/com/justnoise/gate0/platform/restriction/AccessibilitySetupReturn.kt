package com.justnoise.gate0.platform.restriction

import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Process-only, short-lived intent from an explicit setup action; never persisted across restarts. */
internal class AccessibilityReturnGate {
    private var requestedAt: Long? = null

    @Synchronized fun arm(now: Long) { requestedAt = now }
    @Synchronized fun cancel() { requestedAt = null }
    @Synchronized fun isPending(): Boolean = requestedAt != null

    @Synchronized fun take(now: Long, foregroundPackage: String?, eligible: Boolean): Boolean {
        val requested = requestedAt ?: return false
        // First post-grant window consumes the request, including refusal/expiry/other apps.
        requestedAt = null
        return eligible && now >= requested && now - requested <= 120_000L &&
            foregroundPackage in setOf("com.android.settings", "com.samsung.accessibility")
    }
}

object AccessibilitySetupReturn {
    private val gate = AccessibilityReturnGate()

    fun arm() = gate.arm(SystemClock.elapsedRealtime())
    fun cancel() = gate.cancel()
    fun isPending(): Boolean = gate.isPending()

    /** Called only by the connected service, after consent, on a Settings window event. */
    fun onWindow(context: Context, foregroundPackage: String?, eligible: Boolean) {
        if (!gate.take(SystemClock.elapsedRealtime(), foregroundPackage, eligible)) return
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        if (intent.component?.packageName != context.packageName) return
        // No automated Back/Allow taps, new access, Settings fragments or repeated attempts.
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        // Some OEMs silently deny background launch. Normal manual return remains usable.
    }
}
