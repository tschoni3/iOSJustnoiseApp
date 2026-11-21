package com.justnoise.integrated

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.SessionHistoryTimelineSnapshot
import com.justnoise.gate0.domain.HistoryDateRange
import com.justnoise.gate0.domain.HistoryDaySummary
import com.justnoise.gate0.domain.HistoryWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.floor
import kotlin.math.roundToInt

data class IntegratedHistoryState(
    val window: HistoryWindow = HistoryWindow.NINETY_DAYS,
    val today: LocalDate = LocalDate.now(),
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val snapshot: SessionHistoryTimelineSnapshot? = null,
    val loading: Boolean = false,
    val selectedDate: LocalDate? = null,
) {
    val range: HistoryDateRange get() = HistoryDateRange.forWindow(window, today, zoneId)
    val selectedDay: HistoryDaySummary?
        get() = snapshot?.takeUnless { it.error }?.days?.firstOrNull { it.date == selectedDate }

    fun selecting(date: LocalDate?): IntegratedHistoryState = when {
        date == null -> copy(selectedDate = null)
        loading || snapshot?.error != false -> this
        snapshot.days.none { it.date == date } -> this
        else -> copy(selectedDate = date)
    }
}

/** UI-only calendar geometry. It never derives or rewrites stored session duration. */
internal object IntegratedHistoryGridRules {
    data class YearLayout(val columns: Int, val rows: Int, val cellSizeDp: Float)

    /** All days fit the measured remaining viewport, including leap years. */
    fun yearLayout(dayCount: Int, widthDp: Float, heightDp: Float): YearLayout {
        require(dayCount in 1..366)
        val width = widthDp.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        val height = heightDp.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        return (1..dayCount).map { columns ->
            val rows = (dayCount + columns - 1) / columns
            YearLayout(columns, rows, minOf(width / columns, height / rows))
        }.maxBy { it.cellSizeDp }
    }

    fun columns(window: HistoryWindow, availableWidthDp: Float): Int {
        val accessibleColumns = floor((availableWidthDp.coerceAtLeast(0f) + 6f) / 54f).toInt().coerceAtLeast(1)
        return if (window == HistoryWindow.NINETY_DAYS) minOf(4, accessibleColumns) else accessibleColumns
    }

    fun todayContextIndex(range: HistoryDateRange, today: LocalDate, columns: Int): Int {
        val index = ChronoUnit.DAYS.between(range.startInclusive, today).toInt()
        return ((index - 14).coerceIn(0, range.dayCount - 1) / columns.coerceAtLeast(1)) * columns.coerceAtLeast(1)
    }

    fun yearProgress(today: LocalDate): Pair<Int, Int> =
        (today.lengthOfYear() - today.dayOfYear) to
            (today.dayOfYear.toDouble() / today.lengthOfYear() * 100.0).roundToInt()

    fun validSnapshot(snapshot: SessionHistoryTimelineSnapshot, range: HistoryDateRange): Boolean =
        snapshot.range == range && !snapshot.error && snapshot.days.size == range.dayCount &&
            snapshot.days.withIndex().all { (index, day) ->
                day.date == range.startInclusive.plusDays(index.toLong()) &&
                    day.durationMilliseconds >= 0 && day.sessionCount >= 0 &&
                    day.interruptedCount in 0..day.sessionCount && day.timingUncertainCount in 0..day.sessionCount
            } && snapshot.totalCount >= 0 && snapshot.totalDurationMilliseconds >= 0 &&
            snapshot.interruptedCount in 0..snapshot.totalCount && snapshot.timingUncertainCount in 0..snapshot.totalCount
}

/**
 * One worker owns blocking range reads. No Activity, NFC adapter, history rows or consent are
 * retained in instance state. Background/withdrawal navigation invalidates late read results.
 */
class IntegratedHistoryViewModel internal constructor(
    application: Application,
    private val readTimeline: (HistoryDateRange) -> SessionHistoryTimelineSnapshot,
    private val worker: ExecutorService,
    private val main: Handler,
    private val now: () -> Instant,
    private val deviceZone: () -> ZoneId,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application, Gate0Preferences(application)::loadHistoryTimeline,
        Executors.newSingleThreadExecutor(), Handler(Looper.getMainLooper()),
        Instant::now, ZoneId::systemDefault,
    )

    @Volatile private var generation = 0L
    @Volatile private var closed = false
    @Volatile private var foreground = false
    private var initialized = false

    var state by mutableStateOf(currentState())
        private set

    private fun currentState(): IntegratedHistoryState {
        val zone = deviceZone()
        return IntegratedHistoryState(today = now().atZone(zone).toLocalDate(), zoneId = zone)
    }

    @MainThread
    fun initialize(saved: Bundle?) {
        if (initialized) return
        initialized = true
        val window = saved?.getString("window")?.let { name ->
            HistoryWindow.entries.firstOrNull { it.name == name }
        } ?: HistoryWindow.NINETY_DAYS
        val date = saved?.getString("selected_date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        state = currentState().copy(window = window).let { fresh ->
            fresh.copy(selectedDate = date?.takeIf { it >= fresh.range.startInclusive && it < fresh.range.endExclusive })
        }
    }

    @MainThread
    fun saveInstanceState(): Bundle = Bundle().apply {
        putString("window", state.window.name)
        state.selectedDate?.let { putString("selected_date", it.toString()) }
    }

    @MainThread fun onForeground() = load()

    @MainThread
    fun onBackground() {
        foreground = false
        generation += 1
        state = state.copy(snapshot = null, loading = false)
    }

    @MainThread fun refresh() = load()

    @MainThread
    fun refreshIfDateOrZoneChanged() {
        if (!foreground || closed) return
        val current = currentState()
        if (state.today != current.today || state.zoneId != current.zoneId) load()
    }

    @MainThread
    fun selectWindow(window: HistoryWindow) {
        if (state.window == window || closed) return
        state = state.copy(window = window, selectedDate = null)
        load()
    }

    @MainThread fun selectDay(date: LocalDate) { state = state.selecting(date) }
    @MainThread fun dismissDay() { state = state.copy(selectedDate = null) }

    @MainThread
    fun load() {
        if (closed) return
        foreground = true
        val current = currentState()
        val range = HistoryDateRange.forWindow(state.window, current.today, current.zoneId)
        val request = ++generation
        val selected = state.selectedDate?.takeIf { it >= range.startInclusive && it < range.endExclusive }
        // Do not display stale totals or a stale selected-day card while storage is revalidated.
        state = state.copy(today = current.today, zoneId = current.zoneId, snapshot = null,
            selectedDate = selected, loading = true)
        worker.execute {
            if (closed || !foreground || request != generation) return@execute
            val snapshot = try {
                readTimeline(range).takeIf { IntegratedHistoryGridRules.validSnapshot(it, range) }
                    ?: SessionHistoryTimelineSnapshot(range, error = true)
            } catch (_: Exception) {
                SessionHistoryTimelineSnapshot(range, error = true)
            }
            main.post {
                if (closed || !foreground || request != generation) return@post
                val latest = currentState()
                if (latest.today != state.today || latest.zoneId != state.zoneId) {
                    load()
                } else {
                    state = state.copy(snapshot = snapshot, loading = false,
                        selectedDate = state.selectedDate.takeUnless { snapshot.error })
                }
            }
        }
    }

    override fun onCleared() {
        closed = true
        foreground = false
        generation += 1
        worker.shutdown()
        super.onCleared()
    }
}
