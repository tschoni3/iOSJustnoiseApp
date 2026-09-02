package com.justnoise.gate0

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import com.justnoise.gate0.platform.restriction.Gate0AccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStateInstrumentationTest {
    @Test
    fun sameStoredTargetsPreserveActiveAndRecoveryState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        preferences.clearForWithdrawal()

        try {
            assertTrue(preferences.setSelectedPackages(setOf("synthetic.first", "synthetic.second")))
            for (recoveryRequired in listOf(false, true)) {
                assertTrue(preferences.startSession(200_000L))
                if (recoveryRequired) preferences.markRecoveryRequired()
                val before = preferences.snapshot()

                assertTrue(preferences.setSelectedPackages(linkedSetOf("synthetic.second", "synthetic.first")))

                assertEquals(before, preferences.snapshot())
                assertTrue(preferences.stopSession())
            }
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun sameControllerTargetsDoNotClearStateOrBroadcast() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val targets = eligibleTargets(context)
        val recordingContext = BroadcastRecordingContext(context)
        preferences.clearForWithdrawal()

        try {
            assertTrue(preferences.setSelectedPackages(targets))
            assertTrue(preferences.startSession(200_000L))
            preferences.markRecoveryRequired()
            val before = preferences.snapshot()
            val controller = Gate0Controller(recordingContext, preferences)

            assertTrue(controller.selectTargets(targets.reversed().toSet()))
            assertEquals(before, preferences.snapshot())
            assertTrue(recordingContext.broadcasts.isEmpty())
            assertEquals(targets, controller.selectedPackages())
            assertEquals(targets, controller.sessionPackages())
            assertTrue(controller.isSessionRequested())

            assertFalse(controller.selectTargets(setOf("synthetic.missing.target")))
            assertEquals(before, preferences.snapshot())
            assertTrue(recordingContext.broadcasts.isEmpty())
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun changingOrClearingTargetsWhileActiveIsRejectedWithoutBroadcast() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val targets = eligibleTargets(context).toList()
        val recordingContext = BroadcastRecordingContext(context)
        preferences.clearForWithdrawal()

        try {
            assertTrue(preferences.setSelectedPackages(setOf(targets.first())))
            assertTrue(preferences.startSession(200_000L))
            preferences.markRecoveryRequired()
            val before = preferences.snapshot()

            val controller = Gate0Controller(recordingContext, preferences)
            for (replacement in listOf(setOf(targets.last()), emptySet())) {
                assertFalse(preferences.setSelectedPackages(replacement))
                assertFalse(controller.selectTargets(replacement))
                assertEquals(before, preferences.snapshot())
                assertTrue(recordingContext.broadcasts.isEmpty())
            }
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun startCapturesTargetsAndStopClearsSnapshotWithDurableMonotonicRevision() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        preferences.clearForWithdrawal()

        try {
            val selected = setOf("synthetic.first", "synthetic.second")
            assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 123_456L))
            assertTrue(preferences.setSelectedPackages(selected))
            val selectedRevision = preferences.snapshot().sessionRevision
            assertTrue(preferences.startSession(200_000L))
            val started = Gate0Preferences(context).snapshot()
            assertEquals(selected, started.activePackages)
            assertEquals(123_456L, started.acceptedDisclosureEpochMilliseconds)
            assertEquals(200_000L, started.activeSinceEpochMilliseconds)
            assertTrue(started.desiredActive)

            // Simulate a later stored-selection change, not an allowed active-session UI edit.
            assertTrue(rawPreferences(context).edit()
                .putStringSet("selected_packages", setOf("synthetic.later.target"))
                .commit())
            assertEquals(selected, Gate0Preferences(context).snapshot().activePackages)
            assertEquals(selected, Gate0Controller(context, preferences).sessionPackages())

            assertTrue(preferences.stopSession())
            val stopped = Gate0Preferences(context).snapshot()
            assertTrue(stopped.activePackages.isEmpty())
            assertFalse(stopped.desiredActive)
            assertEquals(0L, stopped.activeSinceEpochMilliseconds)
            assertEquals(setOf("synthetic.later.target"), stopped.selectedPackages)
            assertTrue(selectedRevision > 0L)
            assertEquals(selectedRevision + 1L, started.sessionRevision)
            assertEquals(started.sessionRevision + 1L, stopped.sessionRevision)
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun idleMultiTargetChangePersistsAndBroadcastsWithoutErasingPairing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val targets = eligibleTargets(context)
        val recordingContext = BroadcastRecordingContext(context)
        preferences.clearForWithdrawal()

        try {
            assertTrue(preferences.setSelectedPackages(setOf("synthetic.previous.target")))
            assertTrue(preferences.setZapDigest("synthetic-pairing-digest"))
            val before = preferences.snapshot()
            val controller = Gate0Controller(recordingContext, preferences)

            assertTrue(controller.selectTargets(targets))

            val after = preferences.snapshot()
            assertEquals(targets, after.selectedPackages)
            assertTrue(after.activePackages.isEmpty())
            assertFalse(after.desiredActive)
            assertEquals(before.zapDigest, after.zapDigest)
            assertEquals(before.sessionRevision + 1L, after.sessionRevision)
            assertEquals(after, Gate0Preferences(context).snapshot())
            assertFalse(controller.isSessionRequested())
            assertEquals(1, recordingContext.broadcasts.size)
            assertEquals(
                Gate0AccessibilityService.ACTION_STATE_CHANGED,
                recordingContext.broadcasts.single().action,
            )
            assertEquals(context.packageName, recordingContext.broadcasts.single().`package`)
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun invalidOrProtectedMemberRejectsEntireSelectionWithoutMutation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val targets = eligibleTargets(context)
        val recordingContext = BroadcastRecordingContext(context)
        preferences.clearForWithdrawal()

        try {
            assertTrue(preferences.setSelectedPackages(setOf(targets.first())))
            val before = preferences.snapshot()
            val controller = Gate0Controller(recordingContext, preferences)

            assertFalse(preferences.setSelectedPackages(targets + " "))
            assertEquals(before, preferences.snapshot())
            for (invalidPackage in listOf("synthetic.missing.target", context.packageName, " ")) {
                assertFalse(controller.selectTargets(targets + invalidPackage))
                assertEquals(before, preferences.snapshot())
                assertTrue(recordingContext.broadcasts.isEmpty())
            }
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun emptySelectionCanBeSavedWhileIdleWithoutLosingPairing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val recordingContext = BroadcastRecordingContext(context)
        preferences.clearForWithdrawal()

        try {
            assertTrue(preferences.setSelectedPackages(eligibleTargets(context)))
            assertTrue(preferences.setZapDigest("synthetic-pairing-digest"))
            val before = preferences.snapshot()
            val controller = Gate0Controller(recordingContext, preferences)

            assertTrue(controller.selectTargets(emptySet()))

            val after = preferences.snapshot()
            assertTrue(after.selectedPackages.isEmpty())
            assertTrue(after.activePackages.isEmpty())
            assertEquals(before.zapDigest, after.zapDigest)
            assertEquals(before.sessionRevision + 1L, after.sessionRevision)
            assertEquals(1, recordingContext.broadcasts.size)

            assertTrue(controller.selectTargets(emptySet()))
            assertEquals(after, preferences.snapshot())
            assertEquals("Repeated empty selection is also a no-op", 1, recordingContext.broadcasts.size)
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun legacySingleTargetRestoresActiveSnapshotWithoutUpgradingConsentOrLosingPairing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        preferences.clearForWithdrawal()

        try {
            assertTrue(rawPreferences(context).edit()
                .putString("selected_package", "synthetic.legacy.target")
                .putString("accepted_disclosure_version", "gate0-accessibility-v1")
                .putLong("accepted_disclosure_epoch_millis", 123_456L)
                .putString("zap_hmac_digest", "synthetic-legacy-digest")
                .putBoolean("desired_active", true)
                .putLong("active_since_epoch_millis", 200_000L)
                .putLong("session_revision", 7L)
                .commit())

            val migrated = Gate0Preferences(context).snapshot()
            assertEquals(setOf("synthetic.legacy.target"), migrated.selectedPackages)
            assertEquals(migrated.selectedPackages, migrated.activePackages)
            assertTrue(migrated.desiredActive)
            assertEquals(200_000L, migrated.activeSinceEpochMilliseconds)
            assertEquals(7L, migrated.sessionRevision)
            assertEquals("gate0-accessibility-v1", migrated.acceptedDisclosureVersion)
            assertEquals(123_456L, migrated.acceptedDisclosureEpochMilliseconds)
            assertEquals("synthetic-legacy-digest", migrated.zapDigest)

            assertTrue(rawPreferences(context).edit().putBoolean("desired_active", false).commit())
            assertTrue("Idle legacy selection is not an active session", preferences.snapshot().activePackages.isEmpty())
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    @Test
    fun explicitNewSelectionsAndActiveSnapshotsTakePrecedenceOverLegacyTarget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Gate0Preferences(context)
        val selected = setOf("synthetic.new.first", "synthetic.new.second")
        val active = setOf("synthetic.session.first", "synthetic.session.second")
        preferences.clearForWithdrawal()

        try {
            assertTrue(rawPreferences(context).edit()
                .putString("selected_package", "synthetic.legacy.target")
                .putStringSet("selected_packages", selected)
                .putStringSet("active_packages", active)
                .putBoolean("desired_active", true)
                .commit())

            assertEquals(selected, preferences.snapshot().selectedPackages)
            assertEquals(active, preferences.snapshot().activePackages)

            assertTrue(rawPreferences(context).edit().putStringSet("active_packages", emptySet()).commit())
            assertTrue("Explicitly empty active snapshot must not fall back", preferences.snapshot().activePackages.isEmpty())
            assertTrue(rawPreferences(context).edit().remove("active_packages").commit())
            assertTrue("New-format sessions cannot borrow a missing active snapshot from selection", preferences.snapshot().activePackages.isEmpty())
            assertTrue(rawPreferences(context).edit().putStringSet("selected_packages", emptySet()).commit())
            assertTrue("Explicitly empty selection must not restore legacy target", preferences.snapshot().selectedPackages.isEmpty())
        } finally {
            preferences.clearForWithdrawal()
        }
    }

    private fun eligibleTargets(context: Context): Set<String> {
        val packages = LauncherAppRepository(context).load().take(2).map { it.packageName }.toSet()
        assertEquals("Emulator fixture requires two eligible launcher apps", 2, packages.size)
        return packages
    }

    private fun rawPreferences(context: Context) =
        context.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE)

    private class BroadcastRecordingContext(context: Context) : ContextWrapper(context) {
        val broadcasts = mutableListOf<Intent>()

        override fun getApplicationContext(): Context = this

        override fun sendBroadcast(intent: Intent) {
            broadcasts += intent
        }
    }
}
