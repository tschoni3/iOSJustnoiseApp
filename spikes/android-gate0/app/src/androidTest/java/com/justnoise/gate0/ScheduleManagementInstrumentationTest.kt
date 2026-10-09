package com.justnoise.gate0

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.ScheduleDraft
import com.justnoise.gate0.data.ModeMutationResult
import com.justnoise.gate0.domain.ScheduleTiming
import com.justnoise.gate0.feature.ScheduleManagement
import com.justnoise.gate0.feature.ScheduleMutationFailure
import com.justnoise.gate0.feature.ScheduleMutationResult
import com.justnoise.gate0.platform.restriction.AccessibilityServiceStatus
import com.justnoise.gate0.platform.schedule.ScheduleAlarmReceiver
import com.justnoise.gate0.platform.schedule.ScheduleAlarmScheduler
import com.justnoise.gate0.platform.schedule.ScheduleStartupGuard
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
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

/** Disposable emulator preferences only; no permission grants, service enablement or physical NFC. */
@RunWith(AndroidJUnit4::class)
class ScheduleManagementInstrumentationTest {
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences

    @Before
    fun prepare() {
        assumeTrue("Only run on a disposable emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        context = ApplicationProvider.getApplicationContext()
        preferences = Gate0Preferences(context)
        assertTrue(ScheduleAlarmScheduler(context).cancel())
        assertTrue(preferences.clearForWithdrawal())
    }

    @After
    fun clean() {
        if (::context.isInitialized) {
            assertTrue(ScheduleAlarmScheduler(context).cancel())
            assertTrue(Gate0Preferences(context).clearForWithdrawal())
        }
    }

    @Test
    fun loadUsesStoredConfigurationExecutionsAndActualSetupMetadata() {
        val manager = ScheduleManagement(context)
        val empty = manager.load()
        assertFalse(empty.error)
        assertFalse(empty.consentAccepted)
        assertFalse(empty.zapPaired)
        assertTrue(empty.modes.isEmpty())
        assertNull(empty.recentModeId)
        assertTrue(empty.schedules.isEmpty())
        acceptConsent()
        assertTrue(preferences.setZapDigest("synthetic-management-digest"))
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.one", "synthetic.two")))
        assertTrue(preferences.saveSchedule(draft()))
        assertFalse(preferences.reconcileSchedulePlan(System.currentTimeMillis(), "UTC").error)
        assertTrue(preferences.suspendSchedules())

        val loaded = manager.load()
        assertFalse(loaded.error)
        assertTrue(loaded.consentAccepted)
        assertEquals(preferences.snapshot().acceptedDisclosureEpochMilliseconds, loaded.consentEpochMilliseconds)
        assertTrue(loaded.zapPaired)
        assertEquals(2, loaded.modeForReference(loaded.schedules.single().modeReference)?.selectedPackages?.size)
        assertEquals(preferences.loadSchedules().schedules, loaded.schedules)
        assertEquals(preferences.loadScheduleExecutions().entries, loaded.executions)
        assertEquals(ScheduleAlarmScheduler(context).hasExactAlarmAccess(), loaded.exactAlarmAccess)
        assertEquals(AccessibilityServiceStatus.isEnabled(context), loaded.accessibilityEnabled)
        assertEquals(preferences.snapshot().recoveryRequired, loaded.recoveryRequired)
        assertEquals(ScheduleStartupGuard.needsForegroundRecovery(preferences), loaded.suspended)
        assertTrue(loaded.suspended)
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun missingExactAccessRetainsEnabledIntentAndReportsSetupWithoutStartingSession() {
        acceptConsent()
        var rearms = 0
        val manager = ScheduleManagement(context, preferences, exactAlarmAccess = { false }, rearm = { rearms++ })
        val result = manager.save(draft())
        assertTrue(result.saved)
        assertTrue(result.setupRequired)
        assertNull(result.failure)
        assertTrue(preferences.loadSchedules().schedules.single().enabled)
        assertEquals(1, rearms)
        assertFalse(preferences.snapshot().desiredActive)
        assertEquals(0, preferences.loadHistory().totalCount)
    }

    @Test
    fun createEditEnableDisableDeletePreserveManualSessionAndIncrementRevisions() {
        acceptConsent()
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.one", "synthetic.two")))
        assertTrue(preferences.setZapDigest("synthetic-management-digest"))
        assertTrue(preferences.startSession(System.currentTimeMillis()))
        val active = preferences.snapshot()
        val manager = quietManager()
        assertTrue(manager.save(draft(false).copy(name = "  Work  ")).saved)
        val first = preferences.loadSchedules().schedules.single()
        assertEquals("Work", first.name)
        assertEquals(1L, first.revision)
        assertTrue(manager.save(draft(false).copy(name = "Morning"), first.id, 1L).saved)
        assertTrue(manager.setEnabled(first.id, 2L, true).saved)
        assertTrue(manager.setEnabled(first.id, 3L, false).saved)
        val disabled = preferences.loadSchedules().schedules.single()
        assertEquals(4L, disabled.revision)
        assertFalse(disabled.enabled)
        val deletion = manager.delete(first.id, 4L)
        assertTrue(deletion.saved)
        assertFalse(deletion.setupRequired)
        assertTrue(preferences.loadSchedules().schedules.isEmpty())
        assertTrue(preferences.loadScheduleExecutions().entries.isEmpty())
        assertEquals(active, preferences.snapshot())
        assertEquals(0, preferences.loadHistory().totalCount)
    }

    @Test
    fun staleOrMissingRevisionCannotOverwriteToggleOrDeleteAndDoesNotRearm() {
        acceptConsent()
        var rearms = 0
        val manager = quietManager { rearms++ }
        assertTrue(manager.save(draft()).saved)
        val first = preferences.loadSchedules().schedules.single()
        assertTrue(manager.save(draft().copy(name = "Newer"), first.id, first.revision).saved)
        val current = preferences.loadSchedules()
        val beforeRearms = rearms
        listOf(
            manager.save(draft(), first.id, first.revision),
            manager.save(draft(), first.id),
            manager.save(draft(), expectedRevision = 1L),
            manager.setEnabled(first.id, first.revision, false),
            manager.delete(first.id, first.revision),
            manager.delete("missing", 1L),
        ).forEach { assertFailure(ScheduleMutationFailure.CONFLICT, it) }
        assertEquals(current, preferences.loadSchedules())
        assertEquals(beforeRearms, rearms)
    }

    @Test
    fun invalidDraftOrCorruptStorageFailsWithoutOverwritingOrRearming() {
        acceptConsent()
        var rearms = 0
        val manager = quietManager { rearms++ }
        listOf(
            draft().copy(name = "  "),
            draft().copy(creationZoneId = "Not/AZone"),
            draft().copy(timing = ScheduleTiming(oneTimeEpochMilliseconds = 1L)),
        ).forEach { assertFailure(ScheduleMutationFailure.INVALID_OR_FAILED_WRITE, manager.save(it)) }
        assertTrue(preferences.loadSchedules().schedules.isEmpty())
        for (key in listOf("schedules_v1", "schedule_executions_v1")) {
            assertTrue(raw().edit().putString(key, "{").commit())
            assertTrue(manager.load().error)
            assertFailure(ScheduleMutationFailure.INVALID_OR_FAILED_WRITE, manager.save(draft()))
            assertEquals("{", raw().getString(key, null))
            assertTrue(raw().edit().remove(key).commit())
        }
        assertEquals(0, rearms)
    }

    @Test
    fun oldConsentAndLateWritesAfterWithdrawalCannotRecreateConfiguration() {
        var rearms = 0
        val manager = quietManager { rearms++ }
        assertFailure(ScheduleMutationFailure.CONSENT_REQUIRED, manager.save(draft()))
        assertTrue(preferences.acceptDisclosure("gate0-accessibility-v3"))
        assertFailure(ScheduleMutationFailure.CONSENT_REQUIRED, manager.save(draft()))
        acceptConsent()
        assertTrue(manager.save(draft()).saved)
        val saved = preferences.loadSchedules().schedules.single()
        assertTrue(preferences.clearForWithdrawal())
        listOf(
            manager.save(draft()),
            manager.save(draft(), saved.id, saved.revision),
            manager.setEnabled(saved.id, saved.revision, false),
            manager.delete(saved.id, saved.revision),
        ).forEach { assertFailure(ScheduleMutationFailure.CONSENT_REQUIRED, it) }
        assertFalse(raw().contains("schedules_v1"))
        assertFalse(raw().contains("schedule_executions_v1"))
        assertTrue(preferences.loadSchedules().schedules.isEmpty())
        assertEquals(1, rearms)
    }

    @Test
    fun waitingMutationRechecksConsentInsideTheCommitLock() {
        acceptConsent()
        val result = AtomicReference<ScheduleMutationResult?>()
        val entering = CountDownLatch(1)
        lateinit var worker: Thread
        preferences.withScheduleLock {
            worker = thread(name = "schedule-withdrawal-test") {
                entering.countDown()
                result.set(quietManager().save(draft()))
            }
            assertTrue(entering.await(2, TimeUnit.SECONDS))
            assertTrue(preferences.clearForWithdrawal())
        }
        worker.join(2_000L)
        assertFalse(worker.isAlive)
        assertFailure(ScheduleMutationFailure.CONSENT_REQUIRED, requireNotNull(result.get()))
        assertFalse(raw().contains("schedules_v1"))
        assertFalse(raw().contains("schedule_executions_v1"))
    }

    @Test
    fun frozenConsentEpochRejectsQueuedCreateAfterWithdrawalAndNewConsent() {
        acceptConsent()
        var rearms = 0
        val manager = quietManager { rearms++ }
        val oldEpoch = manager.load().consentEpochMilliseconds
        val queuedDraft = draft()
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, oldEpoch + 1L))
        ensureMode()
        assertFailure(
            ScheduleMutationFailure.CONSENT_REQUIRED,
            manager.save(queuedDraft, expectedConsentEpochMilliseconds = oldEpoch),
        )
        assertFalse(raw().contains("schedules_v1"))
        assertFalse(raw().contains("schedule_executions_v1"))
        assertEquals(0, rearms)
        assertTrue(manager.save(queuedDraft, expectedConsentEpochMilliseconds = oldEpoch + 1L).saved)
        val saved = preferences.loadSchedules().schedules.single()
        assertFailure(
            ScheduleMutationFailure.CONSENT_REQUIRED,
            manager.setEnabled(saved.id, saved.revision, false, oldEpoch),
        )
        assertFailure(
            ScheduleMutationFailure.CONSENT_REQUIRED,
            manager.delete(saved.id, saved.revision, oldEpoch),
        )
        assertEquals(saved, preferences.loadSchedules().schedules.single())
        assertEquals(1, rearms)
    }

    @Test
    fun failedCommitRollsBackRevisionAndDoesNotRearmThenRetryCanSave() {
        acceptConsent()
        assertTrue(preferences.saveSchedule(draft(false)))
        val first = preferences.loadSchedules().schedules.single()
        val failingContext = FailingContext(context)
        val failingPreferences = Gate0Preferences(failingContext)
        var rearms = 0
        val manager = ScheduleManagement(context, failingPreferences, { false }, { rearms++ })
        failingContext.failNextCommit = true
        assertFailure(
            ScheduleMutationFailure.INVALID_OR_FAILED_WRITE,
            manager.save(draft(false).copy(name = "Changed"), first.id, first.revision),
        )
        assertEquals(first, preferences.loadSchedules().schedules.single())
        assertTrue(preferences.snapshot().recoveryRequired)
        assertEquals(0, rearms)
        assertTrue(manager.save(draft(false).copy(name = "Changed"), first.id, first.revision).saved)
        assertEquals(first.revision + 1L, preferences.loadSchedules().schedules.single().revision)
        assertEquals(1, rearms)
    }

    @Test
    fun runtimeFailureIsReportedAsSavedButNotReadyWithoutRollingBackConfiguration() {
        acceptConsent()
        val manager = quietManager { throw IllegalStateException("Synthetic runtime failure") }
        val result = manager.save(draft())
        assertTrue(result.saved)
        assertTrue(result.setupRequired)
        assertEquals(ScheduleMutationFailure.REARM_FAILED, result.failure)
        assertEquals(1L, preferences.loadSchedules().schedules.single().revision)
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun explicitRefreshRetriesRearmButPollingDoesNotAndFailurePreservesStoredData() {
        acceptConsent()
        assertTrue(preferences.saveSchedule(draft(false)))
        val saved = preferences.loadSchedules().schedules
        var rearms = 0
        var fail = false
        val manager = quietManager {
            rearms++
            if (fail) throw IllegalStateException("Synthetic retry failure")
        }
        assertFalse(manager.load().error)
        assertEquals(0, rearms)
        assertFalse(manager.refresh().error)
        assertEquals(1, rearms)
        fail = true
        val failed = manager.refresh()
        assertTrue(failed.error)
        assertEquals(saved, failed.schedules)
        assertEquals(2, rearms)
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun rearmRunsAfterReleasingThePreferencesLock() {
        acceptConsent()
        val obtainedLock = CountDownLatch(1)
        var releasedBeforeRearm = false
        lateinit var worker: Thread
        val manager = quietManager {
            worker = thread(name = "schedule-rearm-lock-test") {
                preferences.withScheduleLock { obtainedLock.countDown() }
            }
            releasedBeforeRearm = obtainedLock.await(2, TimeUnit.SECONDS)
        }
        assertTrue(manager.save(draft(false)).saved)
        worker.join(2_000L)
        assertFalse(worker.isAlive)
        assertTrue("Rearm must not retain the preferences lock", releasedBeforeRearm)
    }

    @Test
    fun disablingLastScheduleCancelsTheActualAlarmTokenWithoutStartingSession() {
        acceptConsent()
        assertTrue(preferences.saveSchedule(draft()))
        val saved = preferences.loadSchedules().schedules.single()
        assertNotNull(alarmToken(PendingIntent.FLAG_UPDATE_CURRENT))
        assertNotNull(alarmToken(PendingIntent.FLAG_NO_CREATE))
        val result = ScheduleManagement(context).setEnabled(saved.id, saved.revision, false)
        assertTrue(result.saved)
        assertFalse(result.setupRequired)
        assertFalse(preferences.loadSchedules().schedules.single().enabled)
        assertNull(alarmToken(PendingIntent.FLAG_NO_CREATE))
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun deletingLastScheduleCancelsTheActualAlarmTokenAndClearsItsExecution() {
        acceptConsent()
        assertTrue(preferences.saveSchedule(draft()))
        assertFalse(preferences.reconcileSchedulePlan(System.currentTimeMillis(), "UTC").error)
        val saved = preferences.loadSchedules().schedules.single()
        assertNotNull(preferences.loadScheduleExecutions().entries.single().pending)
        assertNotNull(alarmToken(PendingIntent.FLAG_UPDATE_CURRENT))
        val result = ScheduleManagement(context).delete(saved.id, saved.revision)
        assertTrue(result.saved)
        assertFalse(result.setupRequired)
        assertTrue(preferences.loadSchedules().schedules.isEmpty())
        assertTrue(preferences.loadScheduleExecutions().entries.isEmpty())
        assertNull(alarmToken(PendingIntent.FLAG_NO_CREATE))
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun selectedModeFailuresDoNotFallbackAndAnExistingEmptyModeScheduleCanStillBeDisabled() {
        acceptConsent()
        val manager = quietManager()
        assertFailure(ScheduleMutationFailure.MODE_UNAVAILABLE, manager.save(draft().copy(modeReference = "missing")))
        assertTrue(preferences.loadSchedules().schedules.isEmpty())
        assertTrue(manager.save(draft()).saved)
        val saved = preferences.loadSchedules().schedules.single()
        assertTrue(preferences.setSelectedPackages(emptySet()))
        assertFailure(ScheduleMutationFailure.EMPTY_SELECTION, manager.save(draft(false), saved.id, saved.revision))
        assertTrue(manager.setEnabled(saved.id, saved.revision, false).saved)
        val disabled = preferences.loadSchedules().schedules.single()
        assertFalse(disabled.enabled)
        assertFailure(ScheduleMutationFailure.EMPTY_SELECTION, manager.setEnabled(disabled.id, disabled.revision, true))
        assertEquals(disabled, preferences.loadSchedules().schedules.single())
    }

    private fun acceptConsent() {
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION))
        ensureMode()
    }

    private fun ensureMode() {
        val modes = preferences.loadModes()
        if (modes.modes.isNotEmpty()) return
        assertEquals(ModeMutationResult.SAVED,
            preferences.saveMode(requireNotNull(modes.token), null, "Focus", setOf("synthetic.one")))
        val created = preferences.loadModes()
        assertEquals(ModeMutationResult.SAVED,
            preferences.selectMode(requireNotNull(created.token), created.modes.single().id))
    }

    private fun quietManager(rearm: () -> Unit = {}) = ScheduleManagement(context, preferences, { false }, rearm)

    private fun draft(enabled: Boolean = true) = ScheduleDraft(
        "Morning", enabled,
        ScheduleTiming(oneTimeEpochMilliseconds = System.currentTimeMillis() + 86_400_000L), "UTC",
    )

    private fun assertFailure(expected: ScheduleMutationFailure, result: ScheduleMutationResult) {
        assertFalse(result.saved)
        assertEquals(expected, result.failure)
    }

    private fun raw(): SharedPreferences = context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)

    // A token, not a granted permission or delivered alarm. Cancellation goes through the production scheduler.
    private fun alarmToken(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_ALARM).setPackage(context.packageName),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private class FailingContext(base: Context) : ContextWrapper(base) {
        var failNextCommit = false
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val storage = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by storage {
                override fun edit(): SharedPreferences.Editor = FailingEditor(storage.edit()) {
                    failNextCommit.also { failNextCommit = false }
                }
            }
        }
    }

    private class FailingEditor(
        private val editor: SharedPreferences.Editor,
        private val shouldFail: () -> Boolean,
    ) : SharedPreferences.Editor by editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { editor.putString(key, value) }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { editor.putStringSet(key, values) }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { editor.putInt(key, value) }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { editor.putLong(key, value) }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { editor.putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { editor.putBoolean(key, value) }
        override fun remove(key: String): SharedPreferences.Editor = apply { editor.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { editor.clear() }
        override fun commit(): Boolean {
            if (!shouldFail()) return editor.commit()
            editor.apply() // Reproduce the visible in-memory state despite commit(false).
            return false
        }
    }
}
