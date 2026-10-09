package com.justnoise.integrated

import android.app.Application
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
import com.justnoise.gate0.data.SessionHistoryEntry
import com.justnoise.gate0.data.SessionHistoryTimelineSnapshot
import com.justnoise.gate0.data.SqliteSessionHistoryStorage
import com.justnoise.gate0.domain.HistoryDateRange
import com.justnoise.gate0.domain.HistoryWindow
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite/read API, disposable synthetic data only; never run on the owner's phone. */
@RunWith(AndroidJUnit4::class)
class IntegratedHistoryViewModelInstrumentationTest {
    private var emulatorGuardPassed = false
    private lateinit var application: Application
    private lateinit var preferences: Gate0Preferences
    private lateinit var storage: SqliteSessionHistoryStorage
    private val stores = mutableListOf<ViewModelStore>()
    private val workers = mutableListOf<ExecutorService>()
    private val barriers = mutableListOf<CountDownLatch>()
    private val now = AtomicReference(Instant.parse("2028-02-29T12:00:00Z"))
    private val zone = AtomicReference(ZoneId.of("Europe/Berlin"))

    @Before fun guardBeforeAnyFixtureReadWriteOrCleanup() {
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        application = ApplicationProvider.getApplicationContext()
        assumeTrue(application.packageName == "com.justnoise.androiddev")
        emulatorGuardPassed = true
        preferences = Gate0Preferences(application)
        storage = SqliteSessionHistoryStorage(application)
        assertTrue(preferences.clearForWithdrawal())
    }

    @After fun cleanOnlyGuardedFixturesAfterTheReadWorkersSettle() {
        if (!emulatorGuardPassed) return
        barriers.forEach(CountDownLatch::countDown)
        onMain { stores.forEach(ViewModelStore::clear) }
        workers.forEach {
            it.shutdown()
            assertTrue("History worker did not finish", it.awaitTermination(5, TimeUnit.SECONDS))
        }
        if (::preferences.isInitialized) assertTrue(preferences.clearForWithdrawal())
    }

    @Test fun workerReadsEveryRowIndependentlyOfPaginationAndPreservesAllStoredFields() {
        val start = Instant.parse("2028-02-28T22:59:30Z").toEpochMilli()
        repeat(126) { index -> storage.insert(SessionHistoryEntry(
            UUID.randomUUID().toString(), start, start + 60_000L, 60_000L, 2, index == 125)) }
        val before = storage.load(200).entries
        assertEquals(100, storage.load(100).entries.size)
        val model = createModel { range ->
            assertTrue("History I/O ran on the main thread", Looper.myLooper() != Looper.getMainLooper())
            preferences.loadHistoryTimeline(range)
        }.first
        load(model)
        val snapshot = state(model).snapshot!!
        assertEquals(126, snapshot.totalCount)
        assertEquals(7_560_000L, snapshot.totalDurationMilliseconds)
        assertEquals(1, snapshot.interruptedCount)
        assertEquals(90, snapshot.days.size)
        assertEquals(3_780_000L, snapshot.days.single { it.date.toString() == "2028-02-29" }.durationMilliseconds)
        onMain { model.selectWindow(HistoryWindow.YEAR) }
        awaitLoaded(model)
        assertEquals(366, state(model).snapshot!!.days.size)
        assertEquals(before, storage.load(200).entries)
    }

    @Test fun selectedDayRestoresOnlyUiIdentityAndReadsFreshDataAfterRecreation() {
        insert(Instant.parse("2028-02-29T11:00:00Z").toEpochMilli(), 42L, interrupted = true)
        val model = createModel().first
        load(model)
        onMain { model.selectWindow(HistoryWindow.YEAR) }
        awaitLoaded(model)
        onMain { model.selectDay(state(model).today) }
        val saved = onMain { model.saveInstanceState() }
        assertEquals(setOf("window", "selected_date"), saved.keySet())
        val replacement = createModel().first
        onMain { replacement.initialize(saved) }
        assertNull(state(replacement).snapshot)
        load(replacement)
        assertEquals(HistoryWindow.YEAR, state(replacement).window)
        assertEquals(42L, state(replacement).selectedDay!!.durationMilliseconds)
        assertEquals(1, state(replacement).selectedDay!!.interruptedCount)
    }

    @Test fun latestWindowWinsWhenAnOlderReadCompletesLate() {
        val began = CountDownLatch(1)
        val release = CountDownLatch(1).also(barriers::add)
        val reads = AtomicInteger()
        val (model, worker) = createModel { range ->
            val result = preferences.loadHistoryTimeline(range)
            if (reads.incrementAndGet() == 1) {
                began.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
            }
            result
        }
        onMain { model.load() }
        assertTrue(began.await(5, TimeUnit.SECONDS))
        onMain { model.selectWindow(HistoryWindow.YEAR) }
        release.countDown()
        worker.submit {}.get(5, TimeUnit.SECONDS)
        awaitLoaded(model)
        assertEquals(HistoryWindow.YEAR, state(model).window)
        assertEquals(366, state(model).snapshot!!.days.size)
    }

    @Test fun backgroundInvalidatesLateResultAndWithdrawalCannotReappearOnForeground() {
        insert(Instant.parse("2028-02-29T11:00:00Z").toEpochMilli(), 60_000L)
        val began = CountDownLatch(1)
        val release = CountDownLatch(1).also(barriers::add)
        val reads = AtomicInteger()
        val (model, worker) = createModel { range ->
            val result = preferences.loadHistoryTimeline(range)
            if (reads.incrementAndGet() == 1) { began.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)) }
            result
        }
        onMain { model.onForeground() }
        assertTrue(began.await(5, TimeUnit.SECONDS))
        onMain { model.onBackground() }
        assertTrue(preferences.clearForWithdrawal())
        release.countDown()
        worker.submit {}.get(5, TimeUnit.SECONDS)
        onMain { }
        assertNull(state(model).snapshot)
        assertFalse(state(model).loading)
        load(model)
        assertEquals(0, state(model).snapshot!!.totalCount)
    }

    @Test fun dateAndZoneChangesRecomputeRangesWithoutRewritingRows() {
        insert(Instant.parse("2028-02-29T00:30:00Z").toEpochMilli(), 60_000L)
        val before = storage.load(200).entries
        val model = createModel().first
        load(model)
        zone.set(ZoneId.of("America/Los_Angeles"))
        onMain { model.refreshIfDateOrZoneChanged() }
        awaitLoaded(model)
        assertEquals(60_000L, state(model).snapshot!!.days.single { it.date.toString() == "2028-02-28" }.durationMilliseconds)
        assertEquals(zone.get(), state(model).range.zoneId)
        now.set(Instant.parse("2028-03-01T12:00:00Z"))
        onMain { model.refreshIfDateOrZoneChanged() }
        awaitLoaded(model)
        assertEquals("2028-03-01", state(model).today.toString())
        assertEquals(before, storage.load(200).entries)
    }

    @Test fun failedOrIncompleteReadIsTruthfulAndExplicitRetryLoadsRealData() {
        val reads = AtomicInteger()
        val model = createModel { range ->
            when (reads.incrementAndGet()) {
                1 -> throw IllegalStateException("synthetic read failure")
                2 -> SessionHistoryTimelineSnapshot(range) // Not a complete range projection.
                else -> preferences.loadHistoryTimeline(range)
            }
        }.first
        load(model)
        assertTrue(state(model).snapshot!!.error)
        onMain { model.selectDay(state(model).today) }
        assertNull(state(model).selectedDay)
        onMain { model.refresh() }
        awaitLoaded(model)
        assertTrue(state(model).snapshot!!.error)
        onMain { model.refresh() }
        awaitLoaded(model)
        assertFalse(state(model).snapshot!!.error)
        assertEquals(90, state(model).snapshot!!.days.size)
    }

    @Test fun foregroundRefreshFindsNewCompletionsAndKeepsUncertaintyIndependent() {
        val model = createModel().first
        load(model)
        insert(Instant.parse("2028-02-29T11:00:00Z").toEpochMilli(), 3_000L,
            wallDuration = 1_000L, interrupted = false)
        onMain { model.onBackground(); model.onForeground() }
        awaitLoaded(model)
        val snapshot = state(model).snapshot!!
        assertEquals(1, snapshot.totalCount)
        assertEquals(0, snapshot.interruptedCount)
        assertEquals(1, snapshot.timingUncertainCount)
        onMain { model.selectDay(state(model).today) }
        assertEquals(1, state(model).selectedDay!!.timingUncertainCount)
    }

    private fun createModel(read: (HistoryDateRange) -> SessionHistoryTimelineSnapshot = preferences::loadHistoryTimeline):
        Pair<IntegratedHistoryViewModel, ExecutorService> {
        val worker = Executors.newSingleThreadExecutor().also(workers::add)
        val store = ViewModelStore().also(stores::add)
        val model = onMain {
            ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = IntegratedHistoryViewModel(
                    application, read, worker, Handler(Looper.getMainLooper()), now::get, zone::get) as T
            })[IntegratedHistoryViewModel::class.java]
        }
        return model to worker
    }

    private fun insert(start: Long, duration: Long, wallDuration: Long = duration, interrupted: Boolean = false) {
        storage.insert(SessionHistoryEntry(UUID.randomUUID().toString(), start, start + wallDuration, duration, 2, interrupted))
    }

    private fun load(model: IntegratedHistoryViewModel) { onMain { model.load() }; awaitLoaded(model) }
    private fun state(model: IntegratedHistoryViewModel): IntegratedHistoryState = onMain { model.state }
    private fun awaitLoaded(model: IntegratedHistoryViewModel) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            if (!state(model).loading && state(model).snapshot != null) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("History read did not settle")
    }
    private fun <T> onMain(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val task = FutureTask(Callable(action))
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
        return task.get()
    }
}
