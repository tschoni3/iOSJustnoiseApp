package com.justnoise.gate0

import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.gate0.app.MainActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.HmacZapStore
import com.justnoise.gate0.data.SelectionEditToken
import com.justnoise.gate0.data.ZapPairingToken
import com.justnoise.gate0.feature.Gate0Controller
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

/** Synthetic local-state/UI regressions only. Never run against the owner's paired phone. */
@RunWith(AndroidJUnit4::class)
class ZapPairingSafetyInstrumentationTest {
    private lateinit var context: Context
    private lateinit var preferences: Gate0Preferences
    private lateinit var store: HmacZapStore
    private var cleanupAllowed = false
    private val first = "synthetic-pairing-original".toByteArray()
    private val replacement = "synthetic-pairing-replacement".toByteArray()

    @Before
    fun setUp() {
        assumeTrue("Pairing tests require disposable emulator storage",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        context = ApplicationProvider.getApplicationContext()
        assumeTrue("Do not use a production application ID", context.packageName == "com.justnoise.gate0")
        cleanupAllowed = true
        preferences = Gate0Preferences(context)
        store = HmacZapStore(preferences)
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(store.clear())
    }

    @After
    fun tearDown() {
        first.fill(0)
        replacement.fill(0)
        if (!cleanupAllowed || !::store.isInitialized) return
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(store.clear())
    }

    @Test
    fun legacyNoTokenCallsCannotReplaceWhileExplicitIntentChangesOnlyDigest() {
        assertTrue(store.pairInitial(first))
        val before = raw().all
        assertFalse(store.pair(replacement))
        assertFalse(Gate0Controller(context, preferences, store).pairZap(replacement))
        assertFalse(preferences.setZapDigest("synthetic-unconfirmed-digest"))
        assertEquals(before, raw().all)
        val token = requireNotNull(preferences.prepareZapPairing())
        assertTrue(token.replacesExistingZap)
        assertTrue(store.pair(replacement, token))
        assertTrue(store.isAuthorized(replacement))
        assertFalse(store.isAuthorized(first))
        assertEquals(before - "zap_hmac_digest", raw().all - "zap_hmac_digest")
        assertNull(preferences.snapshot().acceptedDisclosureVersion)
    }

    @Test
    fun initialSetupTokenAllowsPairingBeforeConsentAndNeverStartsSession() {
        val token = requireNotNull(preferences.prepareZapPairing())
        assertFalse(token.replacesExistingZap)
        assertTrue(store.pair(first, token))
        assertNull(preferences.snapshot().acceptedDisclosureVersion)
        assertFalse(preferences.snapshot().desiredActive)
        assertTrue(store.isAuthorized(first))
        assertFalse(store.pair(replacement, token))
    }

    @Test
    fun sessionStartStopInvalidatesAnEarlierIdleConfirmation() {
        assertTrue(store.pairInitial(first))
        assertTrue(preferences.setSelectedPackages(setOf("synthetic.target")))
        val token = requireNotNull(preferences.prepareZapPairing())
        assertTrue(preferences.startSession(100_000L))
        var before = raw().all
        assertNull(preferences.prepareZapPairing())
        assertFalse(store.pair(replacement, token))
        assertEquals(before, raw().all)
        assertTrue(preferences.stopSession(104_000L, preferences.snapshot().activeSessionId))
        before = raw().all
        assertFalse(store.pair(replacement, token))
        assertEquals(before, raw().all)
        assertTrue(store.isAuthorized(first))
    }

    @Test
    fun recoveryAndPendingDeletionRejectNewAndExistingIntentWithoutMutation() {
        assertTrue(store.pairInitial(first))
        val token = requireNotNull(preferences.prepareZapPairing())
        preferences.markRecoveryRequired()
        var before = raw().all
        assertNull(preferences.prepareZapPairing())
        assertFalse(store.pair(replacement, token))
        assertEquals(before, raw().all)
        assertTrue(raw().edit().remove("recovery_required").putBoolean("history_deletion_pending", true).commit())
        before = raw().all
        assertNull(preferences.prepareZapPairing())
        assertFalse(store.pair(replacement, token))
        assertEquals(before, raw().all)
    }

    @Test
    fun withdrawalAndSameTimeReconsentInvalidateEvenIdenticalPairingState() {
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertTrue(store.pairInitial(first))
        val token = requireNotNull(preferences.prepareZapPairing())
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        // Keep the test key to restore byte-identical digest fields and exercise the epoch fence.
        assertTrue(store.pairInitial(first))
        val before = raw().all
        assertFalse(store.pair(replacement, token))
        assertEquals(before, raw().all)
        assertTrue(store.isAuthorized(first))
    }

    @Test
    fun reconsentWithoutWithdrawalAlsoInvalidatesTheFrozenIntent() {
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertTrue(store.pairInitial(first))
        val token = requireNotNull(preferences.prepareZapPairing())
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        val before = raw().all
        assertFalse(store.pair(replacement, token))
        assertEquals(before, raw().all)
    }

    @Test
    fun sameDigestPairingConsumesIntentAndDigestRoundTripCannotReviveOldIntent() {
        assertTrue(store.pairInitial(first))
        val token = requireNotNull(preferences.prepareZapPairing())
        assertTrue(store.pair(first, token))
        assertFalse(store.pair(replacement, token))
        val old = requireNotNull(preferences.prepareZapPairing())
        assertTrue(store.pair(replacement, requireNotNull(preferences.prepareZapPairing())))
        assertTrue(store.pair(first, requireNotNull(preferences.prepareZapPairing())))
        val before = raw().all
        assertFalse(store.pair(replacement, old))
        assertEquals(before, raw().all)
        assertTrue(store.isAuthorized(first))
    }

    @Test
    fun anotherProcessTokenIsRejectedWithoutMutation() {
        assertTrue(store.pairInitial(first))
        val current = requireNotNull(preferences.prepareZapPairing())
        val state = current.stateToken
        val previousProcess = SelectionEditToken("synthetic-previous-process", state.consentGeneration,
            state.sessionRevision, state.consentVersion, state.consentEpochMilliseconds)
        val stale = ZapPairingToken(previousProcess, current.pairingGeneration, current.expectedDigest, true)
        val before = raw().all
        assertFalse(store.pair(replacement, stale))
        assertEquals(before, raw().all)
        assertEquals("ZapPairingToken(opaque)", stale.toString())
    }

    @Test
    fun failedReplacementRollsBackOriginalDigestAndQuarantinesFurtherPairing() {
        assertTrue(store.pairInitial(first))
        val failingContext = FailingCommitContext(context)
        val guarded = Gate0Preferences(failingContext)
        val guardedStore = HmacZapStore(guarded)
        val token = requireNotNull(guarded.prepareZapPairing())
        val before = guarded.snapshot()
        failingContext.failNextCommit = true
        assertFalse(guardedStore.pair(replacement, token))
        assertEquals(before.zapDigest, guarded.snapshot().zapDigest)
        assertTrue(guarded.snapshot().recoveryRequired)
        assertTrue(guardedStore.isAuthorized(first))
        assertFalse(guardedStore.isAuthorized(replacement))
        assertNull(guarded.prepareZapPairing())
        assertFalse(guardedStore.pair(replacement, token))
    }

    @Test
    fun emptyInputAndBlankDigestCannotConsumeOrCorruptAValidPairing() {
        assertTrue(store.pairInitial(first))
        val token = requireNotNull(preferences.prepareZapPairing())
        val before = raw().all
        assertFalse(store.pair(byteArrayOf(), token))
        assertFalse(preferences.commitZapPairing(token, ""))
        assertEquals(before, raw().all)
        assertTrue(store.pair(replacement, token))
    }

    @Test
    fun replacementConfirmationCancelAndRecreationDoNotStartScanOrChangePairing() {
        assertTrue(store.pairInitial(first))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var before = emptyMap<String, Any?>()
            scenario.onActivity { activity ->
                before = raw().all
                activity.findViewById<Button>(R.id.pairZapButton).performClick()
                val dialog = requireNotNull(pairingDialog(activity))
                assertTrue(dialog.isShowing)
                assertNull(activeToken(activity))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertNull(pairingDialog(activity))
                assertNull(activeToken(activity))
                assertEquals(before, raw().all)
                activity.findViewById<Button>(R.id.pairZapButton).performClick()
                assertNotNull(pairingDialog(activity))
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertNull(pairingDialog(activity))
                assertNull(activeToken(activity))
                assertTrue(store.isAuthorized(first))
            }
        }
    }

    @Test
    fun pausingSetupDiscardsPairingConfirmationAndTransientScanAuthority() {
        assertTrue(store.pairInitial(first))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.pairZapButton).performClick()
                assertNotNull(pairingDialog(activity))
                // No RF activity: model a scan's memory-only intent to verify the pause boundary.
                MainActivity::class.java.getDeclaredField("activePairingToken").apply { isAccessible = true }
                    .set(activity, requireNotNull(preferences.prepareZapPairing()))
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertNull(pairingDialog(activity))
                assertNull(activeToken(activity))
                assertTrue(store.isAuthorized(first))
            }
        }
    }

    private fun raw(): SharedPreferences = context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)

    private fun pairingDialog(activity: MainActivity): AlertDialog? = MainActivity::class.java
        .getDeclaredField("pairingConfirmation").apply { isAccessible = true }.get(activity) as? AlertDialog

    private fun activeToken(activity: MainActivity): ZapPairingToken? = MainActivity::class.java
        .getDeclaredField("activePairingToken").apply { isAccessible = true }.get(activity) as? ZapPairingToken

    private class FailingCommitContext(context: Context) : ContextWrapper(context) {
        var failNextCommit = false
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val storage = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by storage {
                override fun edit(): SharedPreferences.Editor {
                    val editor = storage.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
                            editor.putString(key, value)
                        }
                        override fun commit(): Boolean {
                            val fail = failNextCommit.also { failNextCommit = false }
                            return editor.commit() && !fail
                        }
                    }
                }
            }
        }
    }
}
