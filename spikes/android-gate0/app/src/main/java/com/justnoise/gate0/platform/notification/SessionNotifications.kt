package com.justnoise.gate0.platform.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.justnoise.gate0.R
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.feature.SessionNotificationProjection
import com.justnoise.gate0.feature.SessionNotificationRules
import com.justnoise.gate0.feature.SessionLiveUpdateRules
import com.justnoise.gate0.feature.SessionLiveUpdateStatus
import com.justnoise.gate0.platform.restriction.AccessibilityServiceStatus

/** Optional presentation of the persisted session. Does not start, stop or keep a service alive. */
object SessionNotifications {
    internal const val CHANNEL_ID = "zap_session"
    internal const val NOTIFICATION_ID = 7101
    internal const val ACTION_DISMISSED = "com.justnoise.action.SESSION_NOTIFICATION_DISMISSED"
    internal const val EXTRA_SESSION_ID = "session_id"
    private val lock = Any()
    private var lastProjection: Triple<String, SessionNotificationProjection, Boolean>? = null
    private var lastPostedClockOffset: Long? = null
    private var postGeneration = 0L
    private var promotionRejected = false
    private var postedAtElapsed = 0L
    // Android 16 QPR2 / 36.1 introduced the request-based promotion contract.
    // Use the public compatibility bridge, not reflection or older colorized semantics.
    private const val PROMOTED_PERMISSION = "android.permission.POST_PROMOTED_NOTIFICATIONS"
    internal const val PROBE_REQUESTED = "com.justnoise.debug.live_update_requested"
    internal const val PROBE_SYSTEM_ALLOWS = "com.justnoise.debug.live_update_system_allows"
    internal const val PROBE_PROMOTABLE = "com.justnoise.debug.live_update_promotable"
    internal const val PROBE_TEMPLATE = "com.justnoise.debug.live_update_template"

    fun ensureChannel(context: Context) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.session_notification_channel),
                        NotificationManager.IMPORTANCE_LOW).apply {
                        description = context.getString(R.string.session_notification_channel_description)
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                    },
                )
            }
        } catch (_: RuntimeException) {
            // Optional notifications must never obstruct NFC, blocking, or recovery.
        }
    }

    fun areEnabled(context: Context): Boolean = try {
        val manager = context.getSystemService(NotificationManager::class.java)
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    } catch (_: RuntimeException) {
        false
    }

    /** Serialize fresh observations, not snapshots captured by asynchronous callers. */
    @SuppressLint("MissingPermission") // areEnabled checks permission; a revocation race is caught below.
    fun refresh(context: Context) = synchronized(lock) {
        val app = context.applicationContext
        try {
            val stored = Gate0Preferences(app).snapshot()
            val enabled = AccessibilityServiceStatus.isEnabled(app)
            // Read the clock after the snapshot, matching the readiness reconciler's ordering.
            val nowElapsed = SystemClock.elapsedRealtime()
            val nowEpoch = System.currentTimeMillis()
            val clockOffset = nowEpoch - nowElapsed
            val projection = if (areEnabled(app)) {
                SessionNotificationRules.project(stored, enabled, nowElapsed)
            } else SessionNotificationProjection.Hidden
            val requestPromotion = projection is SessionNotificationProjection.SessionTime && canRequestPromotion(app)
            val key = Triple(app.packageName, projection, requestPromotion)
            val clockChanged = projection is SessionNotificationProjection.SessionTime &&
                SessionNotificationRules.clockOffsetChanged(lastPostedClockOffset, clockOffset)
            if (lastProjection == key && !clockChanged) {
                if (requestPromotion && nowElapsed - postedAtElapsed >= 1_000L) {
                    removeOrdinaryFallback(app.getSystemService(NotificationManager::class.java))
                }
                return@synchronized
            }
            val generation = ++postGeneration
            promotionRejected = false
            val manager = app.getSystemService(NotificationManager::class.java)
            if (!requestPromotion) {
                // Owner choice: Live Update only. Recovery stays in the app, not a fallback notification.
                manager.cancel(NOTIFICATION_ID)
            } else {
                ensureChannel(app)
                val notification = buildNotification(app, projection, nowElapsed, nowEpoch, requestPromotion)
                val eligible = Build.VERSION.SDK_INT >= 36 && notification != null &&
                    SessionLiveUpdateRules.shouldPost(projection, requestPromotion,
                        NotificationCompat.isRequestPromotedOngoing(notification),
                        notification.hasPromotableCharacteristics())
                if (!eligible) {
                    manager.cancel(NOTIFICATION_ID)
                    promotionRejected = true
                } else {
                    manager.notify(NOTIFICATION_ID, requireNotNull(notification))
                    postedAtElapsed = nowElapsed
                    // OEMs may reject a valid request. Remove a demoted ordinary item once
                    // Android has processed the post; never repost it on every heartbeat.
                    Handler(Looper.getMainLooper()).postDelayed({
                        synchronized(lock) verification@ {
                            if (postGeneration != generation) return@verification
                            removeOrdinaryFallback(manager)
                        }
                    }, 1_000L)
                }
            }
            // Normal ticking/jitter is not a state change. A clock adjustment rebuilds once.
            lastProjection = key
            lastPostedClockOffset = clockOffset.takeIf { projection is SessionNotificationProjection.SessionTime }
        } catch (_: RuntimeException) {
            cancelLocked(app)
        }
    }

    fun cancel(context: Context) = synchronized(lock) { cancelLocked(context.applicationContext) }

    /** Called under lock after posting settles, and by existing refreshes after user demotion. */
    private fun removeOrdinaryFallback(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < 36 || promotionRejected) return
        try {
            val posted = manager.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID && it.tag == null }
            if (posted != null && posted.notification.flags and Notification.FLAG_PROMOTED_ONGOING == 0) {
                manager.cancel(NOTIFICATION_ID)
                promotionRejected = true
            }
        } catch (_: RuntimeException) {
            runCatching { manager.cancel(NOTIFICATION_ID) }
            promotionRejected = true
        }
    }

    fun supportsLiveUpdates(context: Context): Boolean = try {
        SessionLiveUpdateRules.supported(Build.VERSION.SDK_INT,
            if (Build.VERSION.SDK_INT >= 36) Build.VERSION.SDK_INT_FULL else 0,
            ContextCompat.checkSelfPermission(context, PROMOTED_PERMISSION) == PackageManager.PERMISSION_GRANTED)
    } catch (_: RuntimeException) { false }

    /** Release and debug use the same public API; the manifest defines the capability. */
    internal fun canRequestPromotion(context: Context): Boolean = try {
        Build.VERSION.SDK_INT >= 36 && supportsLiveUpdates(context) &&
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
    } catch (_: RuntimeException) {
        false
    }

    /** Read only this app's notification flags; never request notification-listener access. */
    fun liveUpdateStatus(context: Context): SessionLiveUpdateStatus {
        val supported = supportsLiveUpdates(context)
        val enabled = areEnabled(context)
        val allowed = canRequestPromotion(context)
        val promoted = if (Build.VERSION.SDK_INT >= 36 && supported && enabled && allowed) try {
            context.getSystemService(NotificationManager::class.java).activeNotifications.any {
                it.id == NOTIFICATION_ID && it.tag == null &&
                    it.notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0
            }
        } catch (_: RuntimeException) { false } else false
        return synchronized(lock) {
            SessionLiveUpdateRules.status(supported, enabled, allowed, promoted, promotionRejected)
        }
    }

    private fun cancelLocked(context: Context) {
        lastProjection = null
        lastPostedClockOffset = null
        postGeneration++
        promotionRejected = false
        try {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        } catch (_: RuntimeException) {
            // Platform notification failure cannot change the authoritative session state.
        }
    }

    internal fun buildNotification(
        context: Context,
        projection: SessionNotificationProjection,
        nowElapsedMilliseconds: Long,
        nowEpochMilliseconds: Long,
        requestPromotion: Boolean = false,
    ): Notification? {
        val sessionId = when (projection) {
            SessionNotificationProjection.Hidden -> return null
            is SessionNotificationProjection.SessionTime -> projection.sessionId
            is SessionNotificationProjection.Attention -> projection.sessionId
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        // Explicit launcher Activity only: never NFC, a receiver trampoline, or a stop action.
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss = PendingIntent.getBroadcast(context, NOTIFICATION_ID,
            Intent(context, SessionNotificationDismissReceiver::class.java)
                .setAction(ACTION_DISMISSED)
                .setData(Uri.Builder().scheme("justnoise-session").authority(context.packageName)
                    .appendPath(sessionId).build())
                .putExtra(EXTRA_SESSION_ID, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val whenEpoch = (projection as? SessionNotificationProjection.SessionTime)?.let {
            SessionNotificationRules.chronometerWhen(it.startElapsedMilliseconds,
                nowElapsedMilliseconds, nowEpochMilliseconds)
        }
        val attention = whenEpoch == null
        val sessionText = (projection as? SessionNotificationProjection.SessionTime)?.modeName?.let {
            context.getString(R.string.session_notification_mode_body, it)
        } ?: context.getString(R.string.session_notification_body)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_session_notification)
            .setContentTitle(context.getString(if (attention) R.string.session_notification_attention_title
                else R.string.session_notification_title))
            .setContentText(if (attention) context.getString(R.string.session_notification_attention_body) else sessionText)
            // iOS Live Activity accent. Android controls whether/how the tint is rendered.
            .setColor(android.graphics.Color.rgb(215, 250, 0))
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setShowWhen(!attention)
            .setUsesChronometer(!attention)
            .setChronometerCountDown(false)
            .setWhen(whenEpoch ?: 0L)
            .setContentIntent(open)
            .setDeleteIntent(dismiss)
        val systemAllows = canRequestPromotion(context)
        var promotable = false
        var requested = false
        var notification = builder.build()
        val ordinaryNotification = notification.clone()
        if (requestPromotion && !attention && systemAllows && Build.VERSION.SDK_INT >= 36) {
            try {
                // One native template and OS timer; never custom views or fake progress.
                val candidate = builder
                    .setLargeIcon(android.graphics.drawable.Icon.createWithResource(context,
                        R.drawable.session_notification_artwork))
                    .setStyle(NotificationCompat.BigTextStyle()
                        .bigText(sessionText))
                    .setRequestPromotedOngoing(true)
                    .build()
                promotable = candidate.hasPromotableCharacteristics()
                if (promotable) {
                    notification = candidate
                    requested = true
                } else {
                    notification = ordinaryNotification
                }
            } catch (_: RuntimeException) {
                // A promotion-only failure must retain the useful ordinary notification.
                notification = ordinaryNotification
            }
        }
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Own-notification diagnostics only: no IDs, tag data, history or logging.
            notification.extras.putBoolean(PROBE_SYSTEM_ALLOWS, systemAllows)
            notification.extras.putBoolean(PROBE_PROMOTABLE, promotable)
            notification.extras.putBoolean(PROBE_REQUESTED, requested)
            notification.extras.putString(PROBE_TEMPLATE, if (requested) "big_text" else "standard")
        }
        return notification
    }
}

/** Android 14+ may dismiss ongoing notifications; dismissal hides this session, never ends it. */
class SessionNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SessionNotifications.ACTION_DISMISSED) return
        val sessionId = intent.getStringExtra(SessionNotifications.EXTRA_SESSION_ID)
            ?.takeIf(String::isNotBlank) ?: return
        try {
            if (Gate0Preferences(context).dismissSessionNotification(sessionId)) {
                SessionNotifications.refresh(context)
            }
        } catch (_: RuntimeException) {
            // Never turn notification delivery into a session/storage recovery mutation.
        }
    }
}
