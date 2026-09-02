package com.justnoise.gate0

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.ScheduleDraft
import com.justnoise.gate0.data.ScheduleOutcomeCode
import com.justnoise.gate0.domain.ScheduleTiming
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.feature.ScheduleRuntime
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import com.justnoise.gate0.platform.restriction.ScheduledEnforcement
import com.justnoise.gate0.platform.restriction.ScheduledEnforcementEndpoint
import com.justnoise.gate0.platform.schedule.ScheduleAlarmReceiver
import com.justnoise.gate0.platform.schedule.ScheduleAlarmScheduler
import com.justnoise.gate0.platform.schedule.ScheduleStartupGuard
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable emulator only. Shell-granted alarm access is not evidence of the production consent flow. */
@RunWith(AndroidJUnit4::class)
class ScheduleRuntimeInstrumentationTest {
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences
    private var endpoint: TestEndpoint? = null

    @Before
    fun prepare() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        context = ApplicationProvider.getApplicationContext()
        preferences = Gate0Preferences(context)
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(ScheduleAlarmScheduler(context).cancel())
    }

    @After
    fun clean() {
        endpoint?.let(ScheduledEnforcement::disconnected)
        if (::context.isInitialized) {
            assertTrue(ScheduleAlarmScheduler(context).cancel())
            assertTrue(preferences.clearForWithdrawal())
        }
    }

    @Test
    fun readinessRequiresMainThreadAcknowledgmentAndAnUnchangedBoundGeneration() {
        assertNull(ScheduledEnforcement.prepareStart())
        val fake = TestEndpoint().also { endpoint = it }
        ScheduledEnforcement.connected(fake)
        val ready = requireNotNull(ScheduledEnforcement.prepareStart())
        assertTrue(fake.checkedOnMain)
        assertTrue(ready.isStillReady())
        fake.generation += 1L
        assertFalse(ready.isStillReady())
        fake.known = false
        assertNull(ScheduledEnforcement.prepareStart())
        ScheduledEnforcement.disconnected(fake)
        assertNull(ScheduledEnforcement.prepareStart())
    }

    @Test
    fun actualAlarmReceiverRecordsUnavailableWithoutManufacturingBoundServiceReadiness() {
        grantAlarmAccessOnEmulator()
        val now = System.currentTimeMillis()
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, now))
        val target = LauncherAppRepository(context).load().first().packageName
        assertTrue(preferences.setSelectedPackages(setOf(target)))
        assertTrue(preferences.setZapDigest("synthetic-runtime-test-digest"))
        ScheduleStartupGuard.initialize(context, preferences)
        assertTrue(ScheduleStartupGuard.acknowledgeForegroundRecovery(preferences, now))
        val at = System.currentTimeMillis() + 2_000L
        assertTrue(preferences.saveSchedule(
            ScheduleDraft("Alarm test", true, ScheduleTiming(oneTimeEpochMilliseconds = at), "UTC"),
            nowEpochMilliseconds = now,
        ))
        // Deliberately fresh persisted liveness is still not a live system-bound endpoint.
        assertTrue(preferences.updateServiceHeartbeat(SystemClock.elapsedRealtime()))
        ScheduleRuntime(context).onAlarm()
        val until = SystemClock.elapsedRealtime() + 9_000L
        while (preferences.loadScheduleExecutions().entries.single().lastOutcome == null &&
            SystemClock.elapsedRealtime() < until
        ) SystemClock.sleep(25L)
        val outcome = requireNotNull(preferences.loadScheduleExecutions().entries.single().lastOutcome)
        assertEquals(ScheduleOutcomeCode.CAPABILITY_UNAVAILABLE, outcome.code)
        assertFalse(preferences.snapshot().desiredActive)
        assertNull(preferences.snapshot().activeSessionId)
        assertFalse(preferences.loadSchedules().schedules.single().enabled)
        assertEquals(0, preferences.loadHistory().totalCount)
    }

    @Test
    fun withdrawalCancelsTheNativeTokenAndLateRuntimeCannotRecreateConfiguration() {
        grantAlarmAccessOnEmulator()
        val now = System.currentTimeMillis()
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, now))
        ScheduleStartupGuard.initialize(context, preferences)
        assertTrue(ScheduleStartupGuard.acknowledgeForegroundRecovery(preferences, now))
        assertTrue(preferences.saveSchedule(
            ScheduleDraft("Future", true, ScheduleTiming(oneTimeEpochMilliseconds = now + 60_000L), "UTC"),
            nowEpochMilliseconds = now,
        ))
        ScheduleRuntime(context).onAlarm()
        assertNotNull(alarmToken())
        assertTrue(Gate0Controller(context).withdrawAndClear())
        assertNull(alarmToken())
        ScheduleRuntime(context).onAlarm()
        assertNull(alarmToken())
        assertTrue(preferences.loadSchedules().schedules.isEmpty())
        assertTrue(preferences.loadScheduleExecutions().entries.isEmpty())
        assertFalse(preferences.snapshot().desiredActive)
        assertFalse(context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)
            .contains("schedule_recovery_acknowledged_at"))
    }

    private fun alarmToken(): PendingIntent? = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_ALARM).setPackage(context.packageName),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun grantAlarmAccessOnEmulator() {
        require(context.packageName == "com.justnoise.gate0")
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cmd appops set com.justnoise.gate0 SCHEDULE_EXACT_ALARM allow",
        )
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        assertTrue(ScheduleAlarmScheduler(context).hasExactAlarmAccess())
    }

    private class TestEndpoint : ScheduledEnforcementEndpoint {
        @Volatile var generation = 1L
        @Volatile var known = true
        @Volatile var checkedOnMain = false
        override fun prepareScheduleStart(): Long? {
            checkedOnMain = Looper.myLooper() == Looper.getMainLooper()
            return if (known) generation else null
        }
        override fun isScheduleReadinessCurrent(generation: Long): Boolean = known && generation == this.generation
        override fun onScheduledStateChanged() = Unit
    }
}
