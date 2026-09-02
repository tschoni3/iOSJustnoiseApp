package com.justnoise.integrated

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.justnoise.gate0.R
import com.justnoise.gate0.app.MainActivity as Gate0SetupActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.SessionHistorySnapshot
import com.justnoise.gate0.domain.SessionTransition
import com.justnoise.gate0.domain.ZapReadOutcome
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.feature.Gate0Truth
import com.justnoise.gate0.feature.ScheduleRuntime
import com.justnoise.gate0.platform.nfc.NdefReadFailure
import com.justnoise.gate0.platform.nfc.NdefReadResult
import com.justnoise.gate0.platform.nfc.NdefTagReader
import com.justnoise.gate0.platform.nfc.OneShotScanCoordinator
import com.justnoise.gate0.platform.nfc.ProcessDirectNfcMutationGate
import com.justnoise.gate0.platform.schedule.ScheduleAlarmScheduler
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var controller: Gate0Controller
    private lateinit var preferences: Gate0Preferences
    private var nfcAdapter: NfcAdapter? = null
    private val scanCoordinator = OneShotScanCoordinator<Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private var screenState by mutableStateOf(IntegratedZapState())
    private var showingHistory by mutableStateOf(false)
    private var showingSchedules by mutableStateOf(false)
    private lateinit var schedules: IntegratedScheduleViewModel
    private var scheduleRefreshAt = 0L
    private var historyState by mutableStateOf(IntegratedHistoryState())
    private val historyExecutor = Executors.newSingleThreadExecutor()
    private var historyRequestGeneration = 0
    private var historyLimit = HISTORY_PAGE_SIZE

    private val clearTransientStatus = Runnable {
        screenState = screenState.copy(transientStatus = null)
    }

    private val refresh = object : Runnable {
        override fun run() {
            refreshTruth()
            if (showingSchedules && schedules.state.editor == null &&
                SystemClock.elapsedRealtime() - scheduleRefreshAt >= 5_000L
            ) {
                scheduleRefreshAt = SystemClock.elapsedRealtime()
                schedules.load()
            }
            handler.postDelayed(this, REFRESH_INTERVAL_MILLISECONDS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = Gate0Controller(this)
        preferences = Gate0Preferences(this)
        showingHistory = savedInstanceState?.getBoolean(HISTORY_ROUTE_KEY) ?: false
        showingSchedules = savedInstanceState?.getBoolean(SCHEDULE_ROUTE_KEY) ?: false
        if (showingSchedules) showingHistory = false
        schedules = ViewModelProvider(this)[IntegratedScheduleViewModel::class.java]
        schedules.initialize(savedInstanceState?.getBundle(SCHEDULE_STATE_KEY))
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            JustnoiseIntegratedTheme {
                BackHandler(enabled = showingHistory || showingSchedules) {
                    if (showingSchedules) backFromSchedules() else closeHistory()
                }
                if (showingSchedules) {
                    IntegratedScheduleSurface(
                        state = schedules.state,
                        onBack = ::backFromSchedules,
                        onRefresh = schedules::refresh,
                        onNew = schedules::newSchedule,
                        onEdit = schedules::edit,
                        onEditorChange = schedules::changeEditor,
                        onSave = schedules::save,
                        onCancelEditor = schedules::cancelEditor,
                        onReloadEditor = schedules::reloadEditor,
                        onDelete = schedules::delete,
                        onToggle = schedules::setEnabled,
                        onOpenSetup = ::openSetup,
                        onAlarmAccess = ::openScheduleAlarmAccess,
                    )
                } else if (showingHistory) {
                    IntegratedHistorySurface(
                        state = historyState,
                        onBack = ::closeHistory,
                        onRetry = { loadHistory(historyLimit) },
                        onLoadMore = {
                            loadHistory(
                                (historyLimit.toLong() + HISTORY_PAGE_SIZE)
                                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            )
                        },
                    )
                } else {
                    IntegratedZapSurface(
                        state = screenState,
                        onZap = ::handleZapPress,
                        onOpenSetup = ::openSetup,
                        onOpenHistory = ::openHistory,
                        onOpenSchedules = ::openSchedules,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ScheduleRuntime(this).onExplicitForeground()
        handler.removeCallbacks(refresh)
        refresh.run()
        if (showingHistory) loadHistory(historyLimit)
        if (showingSchedules) schedules.load()
    }

    override fun onPause() {
        cancelReaderMode()
        handler.removeCallbacks(clearTransientStatus)
        screenState = screenState.copy(transientStatus = null)
        handler.removeCallbacks(refresh)
        historyRequestGeneration += 1
        historyState = IntegratedHistoryState()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(HISTORY_ROUTE_KEY, showingHistory)
        outState.putBoolean(SCHEDULE_ROUTE_KEY, showingSchedules)
        outState.putBundle(SCHEDULE_STATE_KEY, schedules.saveInstanceState())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        historyRequestGeneration += 1
        historyExecutor.shutdown()
        super.onDestroy()
    }

    private fun openHistory() {
        cancelReaderMode()
        showingSchedules = false
        showingHistory = true
        loadHistory(HISTORY_PAGE_SIZE)
    }

    private fun closeHistory() {
        historyRequestGeneration += 1
        showingHistory = false
        historyState = IntegratedHistoryState()
        refreshTruth()
    }

    private fun openSchedules() {
        cancelReaderMode()
        historyRequestGeneration += 1
        showingHistory = false
        showingSchedules = true
        schedules.load()
    }

    private fun backFromSchedules() {
        if (schedules.state.saving) {
            // Leaving the route does not cancel a write already in progress. The retained
            // ViewModel settles it once, even if the user returns before it completes.
            showingSchedules = false
            refreshTruth()
        } else if (schedules.state.editor != null) {
            schedules.cancelEditor()
        } else {
            showingSchedules = false
            refreshTruth()
        }
    }

    /** Only this explicit user action opens alarm Settings; onResume merely refreshes access. */
    private fun openScheduleAlarmAccess() {
        cancelReaderMode()
        try {
            startActivity(ScheduleAlarmScheduler(this).permissionIntent())
        } catch (_: RuntimeException) {
            schedules.showMessage("Android couldn’t open alarm access. Open Settings → Apps → Special access → Alarms & reminders.")
        }
    }

    private fun loadHistory(limit: Int) {
        val generation = ++historyRequestGeneration
        historyLimit = limit
        historyState = historyState.copy(loading = true)
        historyExecutor.execute {
            val snapshot = try {
                preferences.loadHistory(limit)
            } catch (_: Exception) {
                SessionHistorySnapshot(error = true)
            }
            handler.post {
                if (generation == historyRequestGeneration && showingHistory && !isDestroyed) {
                    historyState = IntegratedHistoryState(snapshot = snapshot)
                }
            }
        }
    }

    private fun handleZapPress() {
        if (screenState.canScan) beginOneShotScan() else openSetup()
    }

    private fun openSetup() {
        startActivity(Intent(this, Gate0SetupActivity::class.java))
    }

    private fun refreshTruth() {
        val truth = controller.truth()
        val stored = preferences.snapshot()
        screenState = screenState.copy(
            truth = truth,
            selectedAppName = selectedAppsLabel(controller.sessionPackages()),
            desiredActive = stored.desiredActive,
            elapsedText = if (truth == Gate0Truth.ACTIVE) {
                formatElapsed(
                    (System.currentTimeMillis() - stored.activeSinceEpochMilliseconds)
                        .coerceAtLeast(0L),
                )
            } else {
                "00:00:00"
            },
        )
    }

    private fun beginOneShotScan() {
        if (scanCoordinator.isActive()) return
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled) {
            showTransientStatus(R.string.status_nfc_unavailable)
            return
        }

        val token = scanCoordinator.begin(Unit)
        try {
            adapter.enableReaderMode(
                this,
                { tag -> onTagDiscovered(token, tag) },
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V,
                Bundle().apply {
                    putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
                },
            )
        } catch (_: RuntimeException) {
            scanCoordinator.cancel(token)
            showTransientStatus(R.string.status_nfc_unavailable)
            return
        }
        handler.removeCallbacks(clearTransientStatus)
        screenState = screenState.copy(
            isScanning = true,
            transientStatus = getString(R.string.status_scanning),
        )
    }

    private fun onTagDiscovered(
        token: OneShotScanCoordinator.Token<Unit>,
        tag: Tag,
    ) {
        if (!scanCoordinator.claim(token)) return
        val result = NdefTagReader.readSingleStrictTextRecord(tag)
        runOnUiThread { completeOneShotScan(token, result) }
    }

    private fun completeOneShotScan(
        token: OneShotScanCoordinator.Token<Unit>,
        result: NdefReadResult,
    ) {
        if (!scanCoordinator.isClaimActive(token)) {
            result.clearSensitiveData()
            return
        }

        val foregroundInteractionClaimed = if (result is NdefReadResult.Accepted) {
            ProcessDirectNfcMutationGate.tryClaim(SystemClock.elapsedRealtime())
        } else {
            true
        }
        // Claim this physical interaction before direct system dispatch is restored.
        disableReaderModeHardware()
        try {
            val statusResource = when (result) {
                is NdefReadResult.Rejected -> result.statusResource()
                is NdefReadResult.Accepted -> {
                    val interaction = if (foregroundInteractionClaimed) {
                        try {
                            controller.handleZap(
                                transientCanonicalRecord = result.transientCanonicalRecord,
                                acceptedReadAlreadyHandled = false,
                            )
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    when {
                        !foregroundInteractionClaimed ->
                            R.string.status_direct_duplicate_ignored

                        interaction == null -> R.string.status_start_failed
                        interaction.persistenceFailed -> R.string.status_session_storage_failed
                        interaction.readDecision.outcome == ZapReadOutcome.UNAUTHORIZED ->
                            R.string.status_scan_unauthorized

                        interaction.readDecision.outcome == ZapReadOutcome.INVALID ->
                            R.string.status_scan_invalid

                        interaction.sessionDecision?.transition == SessionTransition.START ->
                            R.string.status_started

                        interaction.sessionDecision?.transition == SessionTransition.STOP ->
                            R.string.status_stopped

                        interaction.sessionDecision?.transition == SessionTransition.STOP_REJECTED ->
                            R.string.status_stop_guard

                        interaction.sessionDecision?.transition == SessionTransition.START_FAILED ->
                            R.string.status_start_failed

                        else -> R.string.status_no_change
                    }
                }
            }
            showTransientStatus(statusResource)
        } finally {
            result.clearSensitiveData()
            scanCoordinator.finish(token)
            screenState = screenState.copy(isScanning = false)
            refreshTruth()
        }
    }

    private fun NdefReadResult.Rejected.statusResource(): Int = when (reason) {
        NdefReadFailure.NDEF_UNAVAILABLE -> R.string.status_scan_not_ndef
        NdefReadFailure.EMPTY_NDEF_MESSAGE -> R.string.status_scan_empty_ndef
        NdefReadFailure.TAG_LOST -> R.string.status_scan_tag_lost
        NdefReadFailure.MALFORMED_NDEF -> R.string.status_scan_malformed_ndef
        NdefReadFailure.IO_FAILED -> R.string.status_scan_io_failed
        NdefReadFailure.SECURITY_DENIED -> R.string.status_scan_security_denied
        NdefReadFailure.READ_FAILED -> R.string.status_scan_read_failed
        NdefReadFailure.NO_TEXT_RECORD -> R.string.status_scan_no_text
        NdefReadFailure.INVALID_TEXT_RECORD -> R.string.status_scan_invalid_text
        NdefReadFailure.AMBIGUOUS_TEXT_RECORDS -> R.string.status_scan_ambiguous_text
    }

    private fun showTransientStatus(messageResource: Int) {
        handler.removeCallbacks(clearTransientStatus)
        screenState = screenState.copy(
            isScanning = false,
            transientStatus = getString(messageResource),
        )
        handler.postDelayed(clearTransientStatus, TRANSIENT_STATUS_MILLISECONDS)
    }

    private fun cancelReaderMode() {
        scanCoordinator.cancel()
        disableReaderModeHardware()
        if (screenState.isScanning) {
            screenState = screenState.copy(isScanning = false, transientStatus = null)
        }
    }

    private fun disableReaderModeHardware() {
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (_: Exception) {
            // The Activity may already be paused or NFC may have been disabled.
        }
    }

    private fun NdefReadResult.clearSensitiveData() {
        if (this is NdefReadResult.Accepted) transientCanonicalRecord.fill(0)
    }

    private fun selectedAppsLabel(packages: Set<String>): String? {
        if (packages.isEmpty()) return null
        if (packages.size > 1) {
            return resources.getQuantityString(R.plurals.selected_apps_count, packages.size, packages.size)
        }
        val packageName = packages.single()
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun formatElapsed(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = totalSeconds % 3_600L / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private companion object {
        const val REFRESH_INTERVAL_MILLISECONDS = 1_000L
        const val TRANSIENT_STATUS_MILLISECONDS = 5_000L
        const val HISTORY_PAGE_SIZE = 100
        const val HISTORY_ROUTE_KEY = "showing_history"
        const val SCHEDULE_ROUTE_KEY = "showing_schedules"
        const val SCHEDULE_STATE_KEY = "schedule_screen_state"
    }
}
