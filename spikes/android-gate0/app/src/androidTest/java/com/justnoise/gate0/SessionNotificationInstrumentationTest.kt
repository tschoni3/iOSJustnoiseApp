package com.justnoise.gate0

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.feature.SessionNotificationProjection
import com.justnoise.gate0.platform.notification.SessionNotificationDismissReceiver
import com.justnoise.gate0.platform.notification.SessionNotifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Guard precedes every mutation: disposable emulator Gate 0 package only, never owner Androiddev. */
@RunWith(AndroidJUnit4::class)
class SessionNotificationInstrumentationTest {
    private var guardedContext: Context? = null
    private val context: Context get() = requireNotNull(guardedContext)

    @Before fun guardAndPrepare() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val candidate = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(candidate.packageName == "com.justnoise.gate0")
        guardedContext = candidate
        SessionNotifications.cancel(context)
        assertTrue(Gate0Preferences(context).clearForWithdrawal())
    }

    @After fun cleanup() {
        guardedContext?.let {
            SessionNotifications.cancel(it)
            assertTrue(Gate0Preferences(it).clearForWithdrawal())
        }
    }

    @Test fun platformNotificationHasRealChronometerAndNoStopOrForegroundService() {
        val notification = requireNotNull(SessionNotifications.buildNotification(context,
            SessionNotificationProjection.SessionTime("synthetic-session", 50_000L),
            101_000L, 1_000_000_000L))
        assertEquals(999_949_000L, notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(0, notification.flags and Notification.FLAG_FOREGROUND_SERVICE)
        assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
        assertEquals(0L, notification.timeoutAfter)
        assertTrue(notification.contentIntent.isActivity)
        assertTrue(notification.deleteIntent.isBroadcast)
        assertTrue(notification.actions.isNullOrEmpty())
        assertEquals("LOCKED IN FOR", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Session time · Justnoise",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test fun attentionNotificationNeverShowsRunningTimeAndHiddenHasNoNotification() {
        val notification = requireNotNull(SessionNotifications.buildNotification(context,
            SessionNotificationProjection.Attention("synthetic-session"), 101_000L, 1_000_000_000L))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertEquals("Blocking needs attention", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(SessionNotifications.buildNotification(context, SessionNotificationProjection.Hidden, 1L, 1L))
    }

    @Test fun dismissalPersistsForOnlyTheCurrentUuidWithoutStoppingOrChangingRevision() {
        val preferences = Gate0Preferences(context)
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION))
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.notification.target")))
        assertTrue(preferences.startSession(System.currentTimeMillis()))
        val before = preferences.snapshot()
        val sessionId = requireNotNull(before.activeSessionId)
        val receiver = SessionNotificationDismissReceiver()
        receiver.onReceive(context, dismissal("older-session"))
        assertEquals(before, preferences.snapshot())
        receiver.onReceive(context, dismissal(sessionId))
        val after = Gate0Preferences(context).snapshot()
        assertTrue(after.desiredActive)
        assertEquals(before.sessionRevision, after.sessionRevision)
        assertEquals(before.activeSessionId, after.activeSessionId)
        assertEquals(before.activeSinceElapsedMilliseconds, after.activeSinceElapsedMilliseconds)
        assertEquals(sessionId, after.dismissedNotificationSessionId)
        assertEquals(0, preferences.loadHistory().totalCount)
    }

    @Test fun dismissalReceiverIsNotExportedAndIgnoresUnrelatedActions() {
        @Suppress("DEPRECATION")
        val receiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, SessionNotificationDismissReceiver::class.java), 0)
        assertFalse(receiverInfo.exported)
        assertNotNull(receiverInfo)
        val before = Gate0Preferences(context).snapshot()
        SessionNotificationDismissReceiver().onReceive(context, Intent("synthetic.unrelated"))
        assertEquals(before, Gate0Preferences(context).snapshot())
    }

    /**
     * Harness requirement: on the explicitly verified disposable Gate 0 emulator, revoke
     * android.permission.POST_NOTIFICATIONS with pm BEFORE starting instrumentation, then run
     * only Class#systemNotificationDenialDoesNotPreventOrMutateThePersistedSession. Revoking
     * the running target's permission can kill its instrumentation process. Run the other seven
     * cases separately afterward; their grant helper is never called from this denial case.
     */
    @Test fun systemNotificationDenialDoesNotPreventOrMutateThePersistedSession() {
        assertTrue("Runtime notification denial requires API 33 or newer", Build.VERSION.SDK_INT >= 33)
        assertEquals("Harness must revoke POST_NOTIFICATIONS before this test starts",
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        val preferences = startSyntheticSession()
        val before = preferences.snapshot()
        assertFalse(SessionNotifications.areEnabled(context))
        SessionNotifications.refresh(context)
        awaitNotification(present = false)
        assertEquals(before, preferences.snapshot())
        assertTrue(preferences.snapshot().desiredActive)
    }

    @Test fun permissionWithoutHealthySessionDoesNotPostOrdinaryFallbackAndStopStillWorks() {
        grantNotificationAccess()
        val preferences = startSyntheticSession()
        val before = preferences.snapshot()
        SessionNotifications.refresh(context)
        // No Accessibility grant: owner now wants Live Updates only, not a recovery notification.
        awaitNotification(present = false)
        assertEquals(before, preferences.snapshot())
        SystemClock.sleep(100L)
        repeat(10) { SessionNotifications.refresh(context) }
        assertNull(currentNotification())
        assertTrue(preferences.stopSession(System.currentTimeMillis(), before.activeSessionId))
        SessionNotifications.refresh(context)
        awaitNotification(present = false)
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test fun committedWithdrawalKeepsLiveUpdateHidden() {
        grantNotificationAccess()
        val preferences = startSyntheticSession()
        SessionNotifications.refresh(context)
        awaitNotification(present = false)
        assertTrue(preferences.clearForWithdrawal())
        SessionNotifications.refresh(context)
        awaitNotification(present = false)
        assertFalse(preferences.snapshot().desiredActive)
        assertNull(preferences.snapshot().acceptedDisclosureVersion)
    }

    @Test fun deliveredDeleteIntentHidesCurrentSessionButCannotHideTheNextSession() {
        grantNotificationAccess()
        val preferences = startSyntheticSession()
        val first = preferences.snapshot()
        SessionNotifications.refresh(context)
        awaitNotification(present = false)
        val built = requireNotNull(SessionNotifications.buildNotification(context,
            SessionNotificationProjection.SessionTime(requireNotNull(first.activeSessionId), 50_000L),
            101_000L, 1_000_000_000L))
        // Exercise the real immutable PendingIntent/manifest receiver, not a pretend NFC read.
        val oldDelete = built.deleteIntent
        oldDelete.send()
        awaitNotification(present = false)
        repeat(10) { SessionNotifications.refresh(context) }
        assertNull(currentNotification())
        val dismissed = Gate0Preferences(context).snapshot()
        assertTrue(dismissed.desiredActive)
        assertEquals(first.activeSessionId, dismissed.activeSessionId)
        assertEquals(first.sessionRevision, dismissed.sessionRevision)
        assertEquals(first.activeSessionId, dismissed.dismissedNotificationSessionId)

        assertTrue(preferences.stopSession(System.currentTimeMillis(), first.activeSessionId))
        assertTrue(preferences.startSession(System.currentTimeMillis()))
        val second = preferences.snapshot()
        assertTrue(first.activeSessionId != second.activeSessionId)
        SessionNotifications.refresh(context)
        awaitNotification(present = false)
        oldDelete.send()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(100L)
        SessionNotifications.refresh(context)
        assertEquals(second, Gate0Preferences(context).snapshot())
        assertNull(currentNotification())
    }

    private fun startSyntheticSession(): Gate0Preferences = Gate0Preferences(context).also {
        assertTrue(it.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION))
        assertTrue(it.setSelectedPackages(setOf("synthetic.notification.target")))
        assertTrue(it.startSession(System.currentTimeMillis()))
    }

    /** Emulator/package guards also wrap the permission grant, not just @Before. */
    private fun grantNotificationAccess() {
        require(Build.HARDWARE in setOf("ranchu", "goldfish"))
        require(context.packageName == "com.justnoise.gate0")
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        SessionNotifications.ensureChannel(context)
        assertTrue(SessionNotifications.areEnabled(context))
    }

    private fun currentNotification(): StatusBarNotification? =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.id == SessionNotifications.NOTIFICATION_ID }

    private fun awaitNotification(present: Boolean): StatusBarNotification? {
        val deadline = SystemClock.elapsedRealtime() + 3_000L
        do {
            val current = currentNotification()
            if ((current != null) == present) return current
            SystemClock.sleep(30L)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Session notification present=$present was not observed")
    }

    private fun dismissal(sessionId: String) = Intent(context, SessionNotificationDismissReceiver::class.java)
        .setAction(SessionNotifications.ACTION_DISMISSED)
        .putExtra(SessionNotifications.EXTRA_SESSION_ID, sessionId)
}
