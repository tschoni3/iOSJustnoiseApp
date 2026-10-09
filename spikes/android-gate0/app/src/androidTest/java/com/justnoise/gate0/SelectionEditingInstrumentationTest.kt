package com.justnoise.gate0

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.SelectionEditResult
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.data.SqliteSessionHistoryStorage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic storage mutation tests: an executable emulator guard precedes all cleanup. */
@RunWith(AndroidJUnit4::class)
class SelectionEditingInstrumentationTest {
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences
    private var emulatorCleanupAllowed = false

    @Before
    fun prepareSyntheticStorage() {
        assumeTrue("Synthetic storage tests require the Android emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        emulatorCleanupAllowed = true
        context = ApplicationProvider.getApplicationContext()
        preferences = Gate0Preferences(context)
        assertTrue(preferences.clearForWithdrawal())
    }

    @After
    fun clearSyntheticStorage() {
        if (!emulatorCleanupAllowed || !::preferences.isInitialized) return
        assertTrue(preferences.clearForWithdrawal())
    }

    @Test
    fun repeatedSnapshotTokensAreEqualOpaqueAndReadDoesNotMutateState() {
        assertTrue(preferences.setSelectedPackages(FIRST))
        val before = raw().all
        val first = preferences.readSelectionEdit()
        val second = Gate0Preferences(context).readSelectionEdit()
        assertFalse(first.error)
        assertEquals(FIRST, first.selectedPackages)
        assertEquals(first.token, second.token)
        assertEquals(first.token.hashCode(), second.token.hashCode())
        assertEquals("SelectionEditToken(opaque)", first.token.toString())
        assertEquals(before, raw().all)
    }

    @Test
    fun failedSelectionCommitRollsBackSelectionAndRevisionAndQuarantinesFurtherEdits() {
        assertTrue(preferences.setSelectedPackages(FIRST))
        assertTrue(preferences.completeOnboarding())
        val before = preferences.snapshot()
        val failing = FailingCommitContext(context)
        val guarded = Gate0Preferences(failing)
        val token = requireNotNull(guarded.readSelectionEdit().token)
        failing.failNextCommit = true
        assertEquals(SelectionEditResult.STORAGE_FAILED, guarded.commitSelectionEdit(token, SECOND))
        val after = guarded.snapshot()
        assertEquals(before.selectedPackages, after.selectedPackages)
        assertEquals(before.sessionRevision, after.sessionRevision)
        assertEquals(before.desiredActive, after.desiredActive)
        assertTrue(after.recoveryRequired)
        val failed = guarded.readSelectionEdit()
        assertTrue(failed.error)
        assertTrue(failed.recoveryRequired)
        assertNull(failed.token)
        assertFalse(guarded.setSelectedPackages(SECOND))
        assertEquals(SelectionEditResult.STORAGE_FAILED, guarded.commitSelectionEdit(token, SECOND))
    }

    @Test
    fun legacyChangedSelectionAlsoRollsBackAFailedWrite() {
        assertTrue(preferences.setSelectedPackages(FIRST))
        val before = preferences.snapshot()
        val failing = FailingCommitContext(context)
        val guarded = Gate0Preferences(failing)
        failing.failNextCommit = true
        assertFalse(guarded.setSelectedPackages(SECOND))
        assertEquals(before.selectedPackages, guarded.snapshot().selectedPackages)
        assertEquals(before.sessionRevision, guarded.snapshot().sessionRevision)
        assertTrue(guarded.snapshot().recoveryRequired)
        assertTrue(guarded.readSelectionEdit().error)
    }

    @Test
    fun anotherSaveMakesCapturedRevisionStaleInsteadOfOverwritingIt() {
        val firstToken = requireNotNull(preferences.readSelectionEdit().token)
        val secondToken = requireNotNull(preferences.readSelectionEdit().token)
        assertEquals(SelectionEditResult.SAVED, preferences.commitSelectionEdit(firstToken, FIRST))
        val before = raw().all
        assertEquals(SelectionEditResult.CONFLICT, preferences.commitSelectionEdit(secondToken, SECOND))
        assertEquals(before, raw().all)
    }

    @Test
    fun startBlocksChangedAndUnchangedEditorSaveAndStopDoesNotRestoreStaleToken() {
        assertTrue(preferences.setSelectedPackages(FIRST))
        val token = requireNotNull(preferences.readSelectionEdit().token)
        assertTrue(preferences.startSession(100_000L))
        val active = raw().all
        assertEquals(SelectionEditResult.ACTIVE_SESSION, preferences.commitSelectionEdit(token, SECOND))
        assertEquals(SelectionEditResult.ACTIVE_SESSION, preferences.commitSelectionEdit(token, FIRST))
        assertTrue(preferences.setSelectedPackages(FIRST)) // Established legacy no-op stays valid.
        assertFalse(preferences.setSelectedPackages(SECOND))
        assertEquals(active, raw().all)
        assertTrue(preferences.stopSession(200_000L))
        assertEquals(SelectionEditResult.CONFLICT, preferences.commitSelectionEdit(token, SECOND))
        assertEquals(FIRST, preferences.snapshot().selectedPackages)
    }

    @Test
    fun withdrawalAndSameMillisecondReconsentInvalidateQueuedSaveEvenIfRevisionMatchesAgain() {
        val epoch = 1_000L
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, epoch))
        val token = requireNotNull(preferences.readSelectionEdit().token)
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, epoch))
        val current = requireNotNull(preferences.readSelectionEdit().token)
        assertEquals(token.sessionRevision, current.sessionRevision)
        assertEquals(token.consentVersion, current.consentVersion)
        assertEquals(token.consentEpochMilliseconds, current.consentEpochMilliseconds)
        assertNotEquals(token, current)
        assertEquals(SelectionEditResult.CONFLICT, preferences.commitSelectionEdit(token, FIRST))
        assertTrue(preferences.snapshot().selectedPackages.isEmpty())
    }

    @Test
    fun repeatedOrFailedDisclosureAcceptanceInvalidatesOldTokenAtMethodEntry() {
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        var token = requireNotNull(preferences.readSelectionEdit().token)
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertEquals(SelectionEditResult.CONFLICT, preferences.commitSelectionEdit(token, FIRST))
        token = requireNotNull(preferences.readSelectionEdit().token)
        val failing = FailingCommitContext(context)
        failing.failNextCommit = true
        assertFalse(Gate0Preferences(failing).acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertEquals(SelectionEditResult.CONFLICT, preferences.commitSelectionEdit(token, FIRST))
    }

    @Test
    fun failedWithdrawalEntryInvalidatesTokenEvenWhenItsCleanupRetryFinishes() {
        val token = requireNotNull(preferences.readSelectionEdit().token)
        val failing = FailingCommitContext(context)
        failing.failNextCommit = true
        assertFalse(Gate0Preferences(failing).clearForWithdrawal())
        // The first revocation commit reports failure, but the existing in-method deletion retry
        // can finish. Its old token must still be invalid even when persisted fields match again.
        assertFalse(preferences.readSelectionEdit().error)
        assertNotEquals(token, preferences.readSelectionEdit().token)
        assertEquals(SelectionEditResult.CONFLICT, preferences.commitSelectionEdit(token, FIRST))
    }

    @Test
    fun pendingDeletionAndRecoveryRejectWithoutResurrectingSelections() {
        assertTrue(preferences.setSelectedPackages(FIRST))
        val token = requireNotNull(preferences.readSelectionEdit().token)
        assertTrue(raw().edit().putBoolean("history_deletion_pending", true).commit())
        var before = raw().all
        val pending = preferences.readSelectionEdit()
        assertTrue(pending.error)
        assertNull(pending.token)
        assertEquals(SelectionEditResult.STORAGE_FAILED, preferences.commitSelectionEdit(token, SECOND))
        assertFalse(preferences.setSelectedPackages(SECOND))
        assertEquals(before, raw().all)

        assertTrue(raw().edit().remove("history_deletion_pending").commit())
        preferences.markRecoveryRequired()
        before = raw().all
        val recovering = preferences.readSelectionEdit()
        assertTrue(recovering.recoveryRequired)
        assertFalse(recovering.error)
        assertNull(recovering.token)
        assertEquals(SelectionEditResult.RECOVERY_REQUIRED, preferences.commitSelectionEdit(token, SECOND))
        assertFalse(preferences.setSelectedPackages(SECOND))
        assertEquals(before, raw().all)
    }

    @Test
    fun noOpDoesNotIncrementRevisionAndExplicitEmptyIdleSelectionIsSaved() {
        assertTrue(preferences.setSelectedPackages(FIRST))
        val token = requireNotNull(preferences.readSelectionEdit().token)
        val before = raw().all
        assertEquals(SelectionEditResult.UNCHANGED, preferences.commitSelectionEdit(token, FIRST))
        assertEquals(before, raw().all)
        assertEquals(SelectionEditResult.SAVED, preferences.commitSelectionEdit(token, emptySet()))
        val state = preferences.snapshot()
        assertTrue(state.selectedPackages.isEmpty())
        assertFalse(state.desiredActive)
        assertEquals(token.sessionRevision + 1L, state.sessionRevision)
        assertFalse(preferences.startSession(100_000L))
    }

    @Test
    fun blankPackageNamesAreRejectedWithoutMutation() {
        val token = requireNotNull(preferences.readSelectionEdit().token)
        val before = raw().all
        for (invalid in listOf(setOf(""), setOf(" "), FIRST + "\t")) {
            assertEquals(SelectionEditResult.INVALID_SELECTION, preferences.commitSelectionEdit(token, invalid))
            assertFalse(preferences.setSelectedPackages(invalid))
        }
        assertEquals(before, raw().all)
    }

    @Test
    fun concurrentSavesFromOneSnapshotHaveOneWinner() {
        val token = requireNotNull(preferences.readSelectionEdit().token)
        val ready = CountDownLatch(2)
        val begin = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val targets = listOf(FIRST, SECOND)
            val tasks = targets.map { target -> executor.submit<SelectionEditResult> {
                ready.countDown()
                check(begin.await(5, TimeUnit.SECONDS))
                Gate0Preferences(context).commitSelectionEdit(token, target)
            } }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            begin.countDown()
            val results = tasks.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it == SelectionEditResult.SAVED })
            assertEquals(1, results.count { it == SelectionEditResult.CONFLICT })
            assertEquals(targets[results.indexOf(SelectionEditResult.SAVED)], preferences.snapshot().selectedPackages)
            assertEquals(token.sessionRevision + 1L, preferences.snapshot().sessionRevision)
        } finally {
            begin.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun otherProcessTokenIsRejectedEvenWithMatchingPersistedFields() {
        val current = requireNotNull(preferences.readSelectionEdit().token)
        val priorProcess = SelectionEditToken("synthetic-old-process", current.consentGeneration,
            current.sessionRevision, current.consentVersion, current.consentEpochMilliseconds)
        assertEquals(SelectionEditResult.CONFLICT, preferences.commitSelectionEdit(priorProcess, FIRST))
        assertTrue(preferences.snapshot().selectedPackages.isEmpty())
    }

    @Test
    fun legacySingleTargetPairingConsentOnboardingAndHistorySurviveSelectionMigration() {
        assertTrue(raw().edit().putString("selected_package", FIRST.single()).commit())
        assertTrue(preferences.setZapDigest("synthetic-existing-digest"))
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertTrue(preferences.completeOnboarding())
        val history = SqliteSessionHistoryStorage(context)
        val row = SessionHistoryEntry("synthetic-history", 10_000L, 40_000L, 30_000L, 1, false)
        history.insert(row)
        val edit = preferences.readSelectionEdit()
        assertEquals(FIRST, edit.selectedPackages)
        assertEquals(SelectionEditResult.SAVED, preferences.commitSelectionEdit(requireNotNull(edit.token), FIRST + SECOND))
        assertFalse(raw().contains("selected_package"))
        val state = Gate0Preferences(context).snapshot()
        assertEquals(FIRST + SECOND, state.selectedPackages)
        assertEquals("synthetic-existing-digest", state.zapDigest)
        assertEquals(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, state.acceptedDisclosureVersion)
        assertEquals(1_000L, state.acceptedDisclosureEpochMilliseconds)
        assertTrue(preferences.hasCompletedOnboarding())
        assertEquals(listOf(row), history.load(100).entries)
        assertFalse(state.desiredActive)
    }

    private fun raw(): SharedPreferences = context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)

    private class FailingCommitContext(context: Context) : ContextWrapper(context) {
        var failNextCommit = false
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val storage = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by storage {
                override fun edit(): SharedPreferences.Editor {
                    val editor = storage.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { editor.putBoolean(key, value) }
                        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { editor.putStringSet(key, values) }
                        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { editor.putString(key, value) }
                        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { editor.putLong(key, value) }
                        override fun remove(key: String): SharedPreferences.Editor = apply { editor.remove(key) }
                        override fun clear(): SharedPreferences.Editor = apply { editor.clear() }
                        override fun commit(): Boolean {
                            val fail = failNextCommit.also { failNextCommit = false }
                            val committed = editor.commit()
                            return committed && !fail
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val FIRST = setOf("synthetic.first")
        private val SECOND = setOf("synthetic.second")
    }
}
