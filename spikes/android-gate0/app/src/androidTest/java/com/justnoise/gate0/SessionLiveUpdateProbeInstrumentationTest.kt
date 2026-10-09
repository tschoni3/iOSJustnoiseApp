package com.justnoise.gate0

import android.app.Notification
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.feature.SessionNotificationProjection
import com.justnoise.gate0.platform.notification.SessionNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Construction only: no notification posts, permissions, service changes, or session storage. */
@RunWith(AndroidJUnit4::class)
class SessionLiveUpdateProbeInstrumentationTest {
    private lateinit var context: Context

    @Before fun guardBeforeCreatingPendingIntents() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val candidate = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(candidate.packageName == "com.justnoise.gate0")
        assertTrue(candidate.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        context = candidate
    }

    @Test fun ordinaryConstructionDoesNotRequestPromotion() {
        val notification = build()
        assertFalse(NotificationCompat.isRequestPromotedOngoing(notification))
        assertFalse(notification.extras.containsKey(NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertFalse(notification.extras.getBoolean(SessionNotifications.PROBE_REQUESTED))
        assertFalse(notification.extras.getBoolean(SessionNotifications.PROBE_PROMOTABLE))
        assertEquals("standard", notification.extras.getString(SessionNotifications.PROBE_TEMPLATE))
        assertSafeSessionSurface(notification)
    }

    @Test fun attentionNeverRequestsPromotionEvenWhenExplicitlyRequested() {
        val notification = build(SessionNotificationProjection.Attention(SESSION_ID), requested = true)
        assertFalse(NotificationCompat.isRequestPromotedOngoing(notification))
        assertFalse(notification.extras.getBoolean(SessionNotifications.PROBE_REQUESTED))
        assertFalse(notification.extras.getBoolean(SessionNotifications.PROBE_PROMOTABLE))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals("Blocking needs attention", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(0L, notification.`when`)
        assertEquals("standard", notification.extras.getString(SessionNotifications.PROBE_TEMPLATE))
        assertStandardTemplate(notification)
    }

    @Test fun requestedSessionRetainsTheSameTimerSafeIntentsAndOrdinaryFallback() {
        val ordinary = build()
        val requested = build(requested = true)
        assertSafeSessionSurface(requested)
        assertEquals(ordinary.`when`, requested.`when`)
        assertEquals(ordinary.channelId, requested.channelId)
        assertEquals(ordinary.contentIntent, requested.contentIntent)
        assertEquals(ordinary.deleteIntent, requested.deleteIntent)
        val requestedBit = NotificationCompat.isRequestPromotedOngoing(requested)
        val allows = SessionNotifications.canRequestPromotion(context)
        assertEquals(allows, requested.extras.getBoolean(SessionNotifications.PROBE_SYSTEM_ALLOWS))
        assertEquals(requestedBit, requested.extras.getBoolean(SessionNotifications.PROBE_REQUESTED))
        assertEquals(allows && requested.extras.getBoolean(SessionNotifications.PROBE_PROMOTABLE), requestedBit)
        assertEquals(if (requestedBit) "big_text" else "standard",
            requested.extras.getString(SessionNotifications.PROBE_TEMPLATE))
        if (requestedBit) {
            assertTrue(Build.VERSION.SDK_INT >= 36)
            if (Build.VERSION.SDK_INT >= 36) {
                assertTrue(Build.VERSION.SDK_INT_FULL >= 3_600_001)
                assertTrue(requested.hasPromotableCharacteristics())
            }
        }
        if (Build.VERSION.SDK_INT < 36 || Build.VERSION.SDK_INT_FULL < 3_600_001) {
            // A base API 36 emulator must fall back; do not force legacy colorized semantics.
            assertFalse(allows)
            assertFalse(requestedBit)
        }
        // This is an app-built request, never evidence that Android/OEM actually promoted it.
        assertEquals(0, requested.flags and Notification.FLAG_PROMOTED_ONGOING)
    }

    @Test fun invalidElapsedMappingFallsBackToAttentionWithoutPromotion() {
        val notification = build(SessionNotificationProjection.SessionTime(SESSION_ID, 200_000L), requested = true)
        assertFalse(NotificationCompat.isRequestPromotedOngoing(notification))
        assertFalse(notification.extras.getBoolean(SessionNotifications.PROBE_REQUESTED))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals("Blocking needs attention", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("standard", notification.extras.getString(SessionNotifications.PROBE_TEMPLATE))
        assertStandardTemplate(notification)
    }

    @Test fun nonDebugApplicationUsesTheSameCapabilityButCannotExposeProbeDiagnostics() {
        // A local Context seam tests the guard, not the separate release-manifest/package audit.
        val nonDebug = object : ContextWrapper(context) {
            override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
                flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            }
        }
        val notification = requireNotNull(SessionNotifications.buildNotification(nonDebug,
            SessionNotificationProjection.SessionTime(SESSION_ID, 50_000L),
            101_000L, 1_000_000_000L, requestPromotion = true))
        assertEquals(SessionNotifications.canRequestPromotion(context), SessionNotifications.canRequestPromotion(nonDebug))
        for (key in listOf(SessionNotifications.PROBE_REQUESTED,
            SessionNotifications.PROBE_SYSTEM_ALLOWS, SessionNotifications.PROBE_PROMOTABLE,
            SessionNotifications.PROBE_TEMPLATE)) {
            assertFalse(notification.extras.containsKey(key))
        }
        assertSafeSessionSurface(notification)
    }

    @Test fun publicBigTextConstructionPreservesTheSessionTimerWithoutRequestingPromotion() {
        val ordinary = build()
        // Exercise the public style combination on base API 36 too. This is not the guarded
        // production promotion branch, and it cannot establish actual Samsung Now Bar rendering.
        val styled = NotificationCompat.Builder(context, ordinary)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(ordinary.extras.getCharSequence(Notification.EXTRA_TEXT)))
            .build()
        assertSafeSessionContentAndTimer(styled)
        assertBigTextTemplate(styled)
        assertFalse(NotificationCompat.isRequestPromotedOngoing(styled))
        assertEquals(0, styled.flags and Notification.FLAG_PROMOTED_ONGOING)
        assertEquals(ordinary.`when`, styled.`when`)
        assertEquals(ordinary.channelId, styled.channelId)
        assertEquals(ordinary.contentIntent, styled.contentIntent)
        assertEquals(ordinary.deleteIntent, styled.deleteIntent)
        assertStandardTemplate(ordinary)
    }

    private fun build(
        projection: SessionNotificationProjection = SessionNotificationProjection.SessionTime(SESSION_ID, 50_000L),
        requested: Boolean = false,
    ): Notification = requireNotNull(SessionNotifications.buildNotification(context, projection,
        101_000L, 1_000_000_000L, requestPromotion = requested))

    private fun assertSafeSessionSurface(notification: Notification) {
        assertSafeSessionContentAndTimer(notification)
        val requested = NotificationCompat.isRequestPromotedOngoing(notification)
        if (requested) assertBigTextTemplate(notification) else assertStandardTemplate(notification)
        if (notification.extras.containsKey(SessionNotifications.PROBE_TEMPLATE)) {
            assertEquals(if (requested) "big_text" else "standard",
                notification.extras.getString(SessionNotifications.PROBE_TEMPLATE))
        }
    }

    private fun assertSafeSessionContentAndTimer(notification: Notification) {
        assertEquals("LOCKED IN FOR", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Session time · Justnoise",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(999_949_000L, notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertTrue(notification.contentIntent.isActivity)
        assertTrue(notification.contentIntent.isImmutable)
        assertTrue(notification.deleteIntent.isBroadcast)
        assertTrue(notification.deleteIntent.isImmutable)
        assertTrue(notification.actions.isNullOrEmpty())
        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(0, notification.flags and Notification.FLAG_FOREGROUND_SERVICE)
        assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(0, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertNull(notification.contentView)
        assertNull(notification.bigContentView)
        assertNull(notification.headsUpContentView)
        assertEquals(0L, notification.timeoutAfter)
    }

    private fun assertBigTextTemplate(notification: Notification) {
        assertEquals(Notification.BigTextStyle::class.java.name,
            notification.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
            notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
    }

    private fun assertStandardTemplate(notification: Notification) {
        assertNull(notification.extras.getString(Notification.EXTRA_TEMPLATE))
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
    }

    companion object { private const val SESSION_ID = "synthetic-live-update-probe" }
}
