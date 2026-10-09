package com.justnoise.gate0

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.feature.Gate0Controller
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable emulator-only tests. Never install/run against the owner's paired application. */
@RunWith(AndroidJUnit4::class)
class FirstRunPersistenceInstrumentationTest {
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences
    private lateinit var store: HmacZapStore
    private var emulatorCleanupAllowed = false

    @Before
    fun prepareSyntheticStorage() {
        assumeTrue("Synthetic destructive storage tests require the Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        emulatorCleanupAllowed = true
        context = ApplicationProvider.getApplicationContext()
        preferences = Gate0Preferences(context)
        store = HmacZapStore(preferences)
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(store.clear())
    }

    @After
    fun clearSyntheticStorage() {
        if (!emulatorCleanupAllowed || !::preferences.isInitialized || !::store.isInitialized) return
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(store.clear())
    }

    @Test
    fun introductionCompletionDefaultsFalsePersistsAndDoesNotGrantConsentOrAlterExistingSetup() {
        assertFalse(preferences.hasCompletedOnboarding())
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.saved.target")))
        assertTrue(preferences.setZapDigest("synthetic-existing-digest"))
        val before = preferences.snapshot()
        assertTrue(preferences.completeOnboarding())
        assertTrue(preferences.completeOnboarding())
        assertTrue(Gate0Preferences(context).hasCompletedOnboarding())
        assertEquals(before, preferences.snapshot())
        assertNull(preferences.snapshot().acceptedDisclosureVersion)
    }

    @Test
    fun failedIntroductionWriteRollsBackInsteadOfReportingCompletedAndCanBeRetried() {
        val failing = FailingCommitContext(context)
        val guarded = Gate0Preferences(failing)
        failing.failNextCommit = true
        assertFalse(guarded.completeOnboarding())
        assertFalse(guarded.hasCompletedOnboarding())
        assertFalse(Gate0Preferences(context).hasCompletedOnboarding())
        assertTrue(guarded.snapshot().recoveryRequired)
        assertTrue(guarded.completeOnboarding())
        assertTrue(Gate0Preferences(context).hasCompletedOnboarding())
        // A tutorial retry is not permission to dismiss genuine persistence recovery.
        assertTrue(guarded.snapshot().recoveryRequired)
    }

    @Test
    fun withdrawalResetsIntroductionAlongsideExistingPrivateConfiguration() {
        assertTrue(preferences.completeOnboarding())
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.saved.target")))
        assertTrue(preferences.clearForWithdrawal())
        val reopened = Gate0Preferences(context)
        assertFalse(reopened.hasCompletedOnboarding())
        assertTrue(reopened.snapshot().selectedPackages.isEmpty())
    }

    @Test
    fun initialPairingAuthorizesOnlyItsWinnerAndNeverReplacesAnExistingPair() = withSyntheticRecords { first, second ->
        val controller = Gate0Controller(context, preferences, store)
        assertTrue(controller.pairInitialZap(first))
        val before = rawPreferences().all
        assertFalse(controller.pairInitialZap(second))
        assertEquals(before, rawPreferences().all)
        assertTrue(store.isAuthorized(first))
        assertFalse(store.isAuthorized(second))
        assertFalse(preferences.snapshot().desiredActive)
        assertNull(preferences.snapshot().acceptedDisclosureVersion)
        // Replacement now requires a fresh intent frozen before the user's confirmation.
        assertFalse(controller.pairZap(second))
        assertTrue(controller.pairZap(second, requireNotNull(controller.prepareZapPairing())))
        assertTrue(store.isAuthorized(second))
        assertFalse(store.isAuthorized(first))
    }

    @Test
    fun concurrentInitialPairingHasExactlyOneWinnerWhoseDigestStillAuthorizes() = withSyntheticRecords { first, second ->
        val ready = CountDownLatch(2)
        val begin = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val records = listOf(first, second)
            val results = records.map { record ->
                executor.submit<Boolean> {
                    ready.countDown()
                    check(begin.await(5, TimeUnit.SECONDS))
                    Gate0Controller(context, Gate0Preferences(context)).pairInitialZap(record)
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            begin.countDown()
            val paired = results.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, paired.count { it })
            paired.forEachIndexed { index, won -> assertEquals(won, store.isAuthorized(records[index])) }
        } finally {
            begin.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun requestedSessionRefusesInitialPairingWithoutAlteringActiveState() = withSyntheticRecords { first, _ ->
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.saved.target")))
        assertTrue(preferences.startSession(100_000L))
        val before = rawPreferences().all
        assertFalse(Gate0Controller(context, preferences, store).pairInitialZap(first))
        assertEquals(before, rawPreferences().all)
        assertNull(preferences.snapshot().zapDigest)
    }

    @Test
    fun staleInitialPreflightCannotCommitAfterPairingOrSessionStateChanges() {
        assertTrue(preferences.canPairInitialZap())
        assertTrue(preferences.setZapDigest("synthetic-existing-digest"))
        var before = rawPreferences().all
        assertFalse(preferences.setInitialZapDigest("synthetic-late-digest"))
        assertEquals(before, rawPreferences().all)

        assertTrue(preferences.clearZapDigest())
        assertTrue(preferences.canPairInitialZap())
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.saved.target")))
        assertTrue(preferences.startSession(100_000L))
        before = rawPreferences().all
        assertFalse(preferences.setInitialZapDigest("synthetic-late-digest"))
        assertEquals(before, rawPreferences().all)
    }

    @Test
    fun sessionRecoveryRefusesInitialPairingWithoutClearingTheGuard() = withSyntheticRecords { first, _ ->
        preferences.markRecoveryRequired()
        val before = rawPreferences().all
        assertFalse(Gate0Controller(context, preferences, store).pairInitialZap(first))
        assertEquals(before, rawPreferences().all)
    }

    @Test
    fun scheduleOnlySuspensionAllowsInitialPairingWithoutGrantingConsentOrResumingSchedules() = withSyntheticRecords { first, _ ->
        // Model conservative startup evidence without manufacturing consent or an active session.
        assertTrue(rawPreferences().edit().putBoolean("schedule_suspended", true).commit())
        assertTrue(Gate0Controller(context, preferences, store).pairInitialZap(first))
        assertTrue(preferences.areSchedulesSuspended())
        assertTrue(store.isAuthorized(first))
        assertNull(preferences.snapshot().acceptedDisclosureVersion)
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun blankExistingDigestAndPendingDeletionAreNotAnInvitationToPairAgain() = withSyntheticRecords { first, _ ->
        // Deliberately corrupt fixture storage; the production writer now rejects blank digests.
        assertTrue(rawPreferences().edit().putString("zap_hmac_digest", "").commit())
        var before = rawPreferences().all
        assertFalse(Gate0Controller(context, preferences, store).pairInitialZap(first))
        assertEquals(before, rawPreferences().all)
        assertTrue(preferences.clearZapDigest())
        assertTrue(rawPreferences().edit().putBoolean("history_deletion_pending", true).commit())
        before = rawPreferences().all
        assertFalse(Gate0Controller(context, preferences, store).pairInitialZap(first))
        assertEquals(before, rawPreferences().all)
        assertFalse(preferences.hasCompletedOnboarding())
    }

    @Test
    fun failedInitialPairWriteRollsBackDigestAndEmptyInputDoesNotCreatePairing() = withSyntheticRecords { first, _ ->
        val failing = FailingCommitContext(context)
        val guarded = Gate0Preferences(failing)
        val controller = Gate0Controller(context, guarded)
        assertFalse(controller.pairInitialZap(byteArrayOf()))
        assertNull(guarded.snapshot().zapDigest)
        failing.failNextCommit = true
        assertFalse(controller.pairInitialZap(first))
        assertNull(guarded.snapshot().zapDigest)
        assertTrue(guarded.snapshot().recoveryRequired)
        assertFalse(HmacZapStore(guarded).isAuthorized(first))
    }

    private fun rawPreferences(): SharedPreferences = context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)

    private inline fun withSyntheticRecords(block: (ByteArray, ByteArray) -> Unit) {
        val first = "synthetic-first-run-one".toByteArray()
        val second = "synthetic-first-run-two".toByteArray()
        try {
            block(first, second)
        } finally {
            first.fill(0)
            second.fill(0)
        }
    }

    private class FailingCommitContext(context: Context) : ContextWrapper(context) {
        var failNextCommit = false

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val storage = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by storage {
                override fun edit(): SharedPreferences.Editor {
                    val editor = storage.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { editor.putBoolean(key, value) }
                        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { editor.putString(key, value) }
                        override fun commit(): Boolean {
                            val fail = failNextCommit.also { failNextCommit = false }
                            val committed = editor.commit()
                            return committed && !fail // Also simulates Android's changed in-memory map.
                        }
                    }
                }
            }
        }
    }
}
