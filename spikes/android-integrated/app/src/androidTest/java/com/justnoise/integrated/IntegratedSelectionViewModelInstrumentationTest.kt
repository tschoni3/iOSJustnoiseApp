package com.justnoise.integrated

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.feature.AppSelectionManagement
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
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

/** Real manager/storage/ViewModel. Synthetic fixtures are never allowed on a physical device. */
@RunWith(AndroidJUnit4::class)
class IntegratedSelectionViewModelInstrumentationTest {
    private var emulatorGuardPassed = false
    private lateinit var application: Application
    private lateinit var preferences: Gate0Preferences
    private val stores = mutableListOf<ViewModelStore>()
    private val workers = mutableListOf<ExecutorService>()
    private val barriers = mutableListOf<CountDownLatch>()

    @Before
    fun guardBeforeAnyFixtureReadWriteOrCleanup() {
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        application = ApplicationProvider.getApplicationContext()
        assumeTrue(application.packageName == "com.justnoise.androiddev")
        preferences = Gate0Preferences(application)
        emulatorGuardPassed = true
        assertTrue(preferences.clearForWithdrawal())
    }

    @After
    fun clearOnlyGuardedEmulatorFixturesAfterWorkersSettle() {
        if (!emulatorGuardPassed) return
        barriers.forEach(CountDownLatch::countDown)
        onMain { stores.forEach(ViewModelStore::clear) }
        workers.forEach {
            it.shutdown()
            assertTrue("Selection worker did not finish", it.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertTrue(preferences.clearForWithdrawal())
    }

    @Test
    fun draftAndCancelNeverWriteAndFreshOpenReadsSavedSelection() {
        val targets = targets()
        assertTrue(preferences.setSelectedPackages(setOf(targets[0])))
        val before = rawState()
        val (model, _) = createModel()
        openAndAwait(model)
        onMain { model.toggle(targets[1], true) }
        assertEquals(setOf(targets[0], targets[1]), state(model).draft)
        assertEquals(before, rawState())
        onMain { model.cancel() }
        assertFalse(state(model).visible)
        assertTrue(state(model).draft.isEmpty())
        openAndAwait(model)
        assertEquals(setOf(targets[0]), state(model).draft)
        assertEquals(before, rawState())
    }

    @Test
    fun explicitSavePersistsOnceWithoutConsentPairingOrSessionGrant() {
        val targets = targets()
        val (model, _) = createModel()
        openAndAwait(model)
        onMain {
            targets.forEach { model.toggle(it, true) }
            model.save()
            model.save() // A repeated UI callback must not enqueue another write.
        }
        await("Save did not finish") { !state(model).saving }
        assertEquals(targets.toSet(), preferences.snapshot().selectedPackages)
        assertEquals(1L, preferences.snapshot().sessionRevision)
        assertEquals(1L, state(model).savedVersion)
        assertFalse(state(model).visible)
        assertNull(preferences.snapshot().acceptedDisclosureVersion)
        assertNull(preferences.snapshot().zapDigest)
        assertFalse(preferences.snapshot().desiredActive)
    }

    @Test
    fun changedSavedSelectionRequiresExplicitReloadWithoutReplacingDraftToken() {
        val targets = targets()
        val (model, _) = createModel()
        openAndAwait(model)
        val originalToken = state(model).token
        onMain { model.toggle(targets[0], true) }
        assertTrue(preferences.setSelectedPackages(setOf(targets[1])))
        onMain { model.save() }
        await("Conflict did not settle") { !state(model).saving }
        assertEquals(IntegratedSelectionProblem.CONFLICT, state(model).problem)
        assertEquals(originalToken, state(model).token)
        assertEquals(setOf(targets[0]), state(model).draft)
        assertFalse(state(model).canSave)
        onMain { model.toggle(targets[1], true); model.save() }
        assertEquals(setOf(targets[1]), preferences.snapshot().selectedPackages)
        onMain { model.reload() }
        await("Explicit reload did not finish") { !state(model).loading }
        assertEquals(setOf(targets[1]), state(model).draft)
        assertTrue(state(model).canSave)
    }

    @Test
    fun withdrawalAndReconsentInvalidateFrozenDraftAuthority() {
        val target = targets().first()
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 100L))
        val (model, _) = createModel()
        openAndAwait(model)
        onMain { model.toggle(target, true) }
        assertTrue(preferences.clearForWithdrawal())
        assertTrue(preferences.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 100L))
        val afterReconsent = rawState()
        onMain { model.save() }
        await("Stale consent save did not settle") { !state(model).saving }
        assertEquals(IntegratedSelectionProblem.CONFLICT, state(model).problem)
        assertEquals(afterReconsent, rawState())
        assertEquals(0L, state(model).savedVersion)
    }

    @Test
    fun lateLoadCannotReopenPausedSheetOrRestoreDiscardedDraft() {
        val (model, worker) = createModel()
        val before = rawState()
        val release = blockWorker(worker)
        onMain { model.open(); model.onHostPaused() }
        release.countDown()
        worker.submit {}.get(5, TimeUnit.SECONDS)
        onMain { }
        assertFalse(state(model).visible)
        assertTrue(state(model).draft.isEmpty())
        assertFalse(state(model).loading)
        assertEquals(before, rawState())
        openAndAwait(model)
        assertTrue(state(model).canEdit)
    }

    @Test
    fun confirmedSaveSettlesOnceAfterPauseWithoutReopeningOrReplayingOnNewViewModel() {
        val target = targets().first()
        val (model, worker) = createModel()
        openAndAwait(model)
        onMain { model.toggle(target, true) }
        val release = blockWorker(worker)
        onMain {
            model.save()
            model.onHostPaused()
            model.open() // An in-flight confirmed write is not a fresh editor.
        }
        assertFalse(state(model).visible)
        assertTrue(state(model).saving)
        release.countDown()
        await("Background save did not settle") { !state(model).saving }
        assertFalse(state(model).visible)
        assertTrue(state(model).draft.isEmpty())
        assertEquals(setOf(target), preferences.snapshot().selectedPackages)
        assertEquals(1L, state(model).savedVersion)
        val persisted = rawState()
        val (newModel, _) = createModel()
        assertFalse(state(newModel).visible)
        openAndAwait(newModel)
        assertEquals(setOf(target), state(newModel).draft)
        assertEquals(persisted, rawState())
        assertEquals(0L, state(newModel).savedVersion)
    }

    @Test
    fun activeAndRecoveryStatesRefuseEditingWithoutChangingSessionIntent() {
        val targets = targets()
        assertTrue(preferences.setSelectedPackages(setOf(targets[0])))
        assertTrue(preferences.startSession(100_000L))
        val (model, _) = createModel()
        openAndAwait(model)
        assertEquals(IntegratedSelectionProblem.ACTIVE_SESSION, state(model).problem)
        val active = rawState()
        onMain { model.toggle(targets[1], true); model.save() }
        assertEquals(active, rawState())
        assertTrue(preferences.stopSession())
        preferences.markRecoveryRequired()
        onMain { model.reload() }
        await("Recovery reload did not finish") { !state(model).loading }
        assertEquals(IntegratedSelectionProblem.RECOVERY_REQUIRED, state(model).problem)
        val recovery = rawState()
        onMain { model.toggle(targets[1], true); model.save() }
        assertEquals(recovery, rawState())
    }

    private fun createModel(): Pair<IntegratedSelectionViewModel, ExecutorService> {
        check(emulatorGuardPassed)
        val worker = Executors.newSingleThreadExecutor().also(workers::add)
        val model = onMain {
            val created = IntegratedSelectionViewModel(
                application, AppSelectionManagement(application, preferences), worker, Handler(Looper.getMainLooper()),
            )
            val store = ViewModelStore().also(stores::add)
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
            }
            ViewModelProvider(store, factory)[IntegratedSelectionViewModel::class.java]
        }
        return model to worker
    }

    private fun blockWorker(worker: ExecutorService): CountDownLatch {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1).also(barriers::add)
        worker.execute { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        return release
    }

    private fun openAndAwait(model: IntegratedSelectionViewModel) {
        onMain { model.open() }
        await("App discovery did not finish") { !state(model).loading }
    }

    private fun state(model: IntegratedSelectionViewModel): IntegratedSelectionState = onMain { model.state }

    private fun targets(): List<String> = LauncherAppRepository(application).load().take(2).map { it.packageName }
        .also { assertEquals("Emulator needs two selectable apps", 2, it.size) }

    private fun rawState(): Map<String, *> =
        application.getSharedPreferences("gate0_local_state", Context.MODE_PRIVATE).all.toMap()

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        do {
            if (condition()) return
            SystemClock.sleep(25L)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(message)
    }

    private fun <T> onMain(action: () -> T): T {
        val task = FutureTask(Callable(action))
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
        return task.get()
    }
}
