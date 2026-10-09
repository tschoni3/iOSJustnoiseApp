package com.justnoise.integrated

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import com.justnoise.gate0.R
import com.justnoise.gate0.app.MainActivity as Gate0SetupActivity
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.StoredGate0State
import com.justnoise.gate0.domain.SessionTransition
import com.justnoise.gate0.domain.ZapReadOutcome
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.feature.Gate0Truth
import com.justnoise.gate0.feature.SessionLiveUpdateStatus
import com.justnoise.gate0.feature.ScheduleRuntime
import com.justnoise.gate0.platform.nfc.NdefReadFailure
import com.justnoise.gate0.platform.nfc.NdefReadResult
import com.justnoise.gate0.platform.nfc.NdefTagReader
import com.justnoise.gate0.platform.nfc.OneShotScanCoordinator
import com.justnoise.gate0.platform.nfc.ProcessDirectNfcMutationGate
import com.justnoise.gate0.platform.notification.SessionNotifications
import com.justnoise.gate0.platform.schedule.ScheduleAlarmScheduler
import java.util.Locale
import java.util.concurrent.Executors

/** User-invoked compose handoff only: no message body, diagnostics or automatic send. */
internal fun launchSupportEmail(address: String, launch: (Intent) -> Unit): Boolean = try {
    launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)))
    true
} catch (_: RuntimeException) {
    false
}

class MainActivity : ComponentActivity() {
    private lateinit var controller: Gate0Controller
    private lateinit var preferences: Gate0Preferences
    private var nfcAdapter: NfcAdapter? = null
    private val scanCoordinator = OneShotScanCoordinator<IntegratedScanPurpose>()
    private val handler = Handler(Looper.getMainLooper())
    private var screenState by mutableStateOf(IntegratedZapState())
    private var showingHistory by mutableStateOf(false)
    private var showingSchedules by mutableStateOf(false)
    private var showingSettings by mutableStateOf(false)
    private var settingsPage by mutableStateOf(IntegratedSettingsPage.MAIN)
    private var tutorialReplayPage by mutableStateOf<Int?>(null)
    private var settingsMessage by mutableStateOf<String?>(null)
    private var notificationsEnabled by mutableStateOf(false)
    private var liveUpdateStatus by mutableStateOf(SessionLiveUpdateStatus.UNSUPPORTED)
    private var installedVersion = ""
    private var completionState by mutableStateOf(IntegratedCompletionState())
    private var firstRunRoute by mutableStateOf(IntegratedFirstRunRoute.MAIN)
    private var onboardingPage by mutableStateOf(0)
    private var onboardingSaveBusy by mutableStateOf(false)
    private var onboardingSaveError by mutableStateOf<String?>(null)
    private var initialZapPaired by mutableStateOf(false)
    private lateinit var schedules: IntegratedScheduleViewModel
    private lateinit var modes: IntegratedModeViewModel
    private lateinit var history: IntegratedHistoryViewModel
    private var scheduleRefreshAt = 0L
    private var accountViewGeneration by mutableStateOf(-1L)
    private var promptedAccountGeneration = -1L
    private val storageExecutor = Executors.newSingleThreadExecutor()

    private val clearTransientStatus = Runnable {
        screenState = screenState.copy(transientStatus = null)
    }

    private val refresh = object : Runnable {
        override fun run() {
            refreshTruth()
            if (showingHistory && !showingSettings) history.refreshIfDateOrZoneChanged()
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
        val initialFacts = firstRunFacts(preferences.snapshot())
        initialZapPaired = initialFacts.zapPaired
        firstRunRoute = IntegratedFirstRunRules.route(
            initialFacts,
            IntegratedFirstRunRoute.entries.firstOrNull {
                it.name == savedInstanceState?.getString(FIRST_RUN_ROUTE_KEY)
            },
        )
        onboardingPage = IntegratedFirstRunRules.page(savedInstanceState?.getInt(ONBOARDING_PAGE_KEY) ?: 0)
        if (savedInstanceState?.getBoolean(ONBOARDING_SAVE_FAILED_KEY) == true && !initialFacts.tutorialCompleted) {
            onboardingSaveError = getString(R.string.first_run_save_failed)
        }
        showingHistory = savedInstanceState?.getBoolean(HISTORY_ROUTE_KEY) ?: false
        showingSchedules = savedInstanceState?.getBoolean(SCHEDULE_ROUTE_KEY) ?: false
        showingSettings = savedInstanceState?.getBoolean(SETTINGS_ROUTE_KEY) ?: false
        settingsPage = IntegratedSettingsPage.entries.firstOrNull {
            it.name == savedInstanceState?.getString(SETTINGS_PAGE_KEY)
        } ?: IntegratedSettingsPage.MAIN
        tutorialReplayPage = savedInstanceState?.getInt(TUTORIAL_REPLAY_PAGE_KEY, -1)
            ?.takeIf { it in 0..1 }
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        installedVersion = "${packageInfo.versionName.orEmpty()} (${packageInfo.longVersionCode})"
        if (firstRunRoute != IntegratedFirstRunRoute.MAIN) {
            showingHistory = false
            showingSchedules = false
            showingSettings = false
        }
        if (showingSettings) { showingHistory = true; showingSchedules = false }
        if (!showingSettings) tutorialReplayPage = null
        if (showingSchedules) showingHistory = false
        schedules = ViewModelProvider(this)[IntegratedScheduleViewModel::class.java]
        schedules.initialize(savedInstanceState?.getBundle(SCHEDULE_STATE_KEY))
        modes = ViewModelProvider(this)[IntegratedModeViewModel::class.java]
        history = ViewModelProvider(this)[IntegratedHistoryViewModel::class.java]
        history.initialize(savedInstanceState?.getBundle(HISTORY_STATE_KEY))
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
                val accountHost = application as? IntegratedAccountHost
                val access = accountHost?.accountAccess?.collectAsState()?.value
                if (access != null && access.blocksRoute(firstRunRoute, accountViewGeneration)) {
                    LaunchedEffect(access) {
                        if (access.productAllowed) {
                            // Clearing cancels old workers and discards every retained draft/
                            // History result before another account's product UI is exposed.
                            viewModelStore.clear()
                            schedules = ViewModelProvider(this@MainActivity)[IntegratedScheduleViewModel::class.java]
                            schedules.initialize(null)
                            modes = ViewModelProvider(this@MainActivity)[IntegratedModeViewModel::class.java]
                            history = ViewModelProvider(this@MainActivity)[IntegratedHistoryViewModel::class.java]
                            history.initialize(null)
                            completionState = IntegratedCompletionState()
                            showingHistory = false; showingSchedules = false; showingSettings = false
                            tutorialReplayPage = null
                            refreshTruth()
                            accountViewGeneration = access.generation
                        }
                    }
                    LaunchedEffect(access.ready, access.signedIn) {
                        maybeOpenRequiredAccount()
                    }
                    AlertDialog(onDismissRequest = {}, title = { Text(if (access.ready) "Welcome to Justnoise" else "Account recovery") },
                        text = { Text(if (access.ready) "Sign in or create an account to use Justnoise. An existing session can still be stopped with your Zap."
                            else "Checking your local account workspace. Your physical Zap can still stop a session.") },
                        confirmButton = { TextButton(onClick = { accountHost.openAccount(this@MainActivity) }) { Text(if (access.ready) "Sign in / Create account" else "Open account") } },
                        dismissButton = { TextButton(onClick = {
                            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("Android recovery settings") } })
                    return@JustnoiseIntegratedTheme
                }
                LaunchedEffect(modes.state.savedVersion) {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refreshTruth()
                }
                BackHandler(enabled = showingHistory || showingSchedules || showingSettings ||
                    completionState.visibleEntry != null || modes.state.visible || screenState.isScanning ||
                    firstRunRoute == IntegratedFirstRunRoute.ACTIVATION ||
                    (firstRunRoute == IntegratedFirstRunRoute.ONBOARDING && onboardingPage > 0)
                ) {
                    when {
                        completionState.visibleEntry != null -> completionState = completionState.dismiss()
                        firstRunRoute == IntegratedFirstRunRoute.ACTIVATION -> {
                            if (screenState.isScanning) cancelInitialScan() else leaveFirstRun()
                        }
                        firstRunRoute == IntegratedFirstRunRoute.ONBOARDING -> {
                            if (!onboardingSaveBusy) onboardingPage = 0
                        }
                        modes.state.visible -> if (modes.state.editor != null) modes.cancelEditor() else modes.dismiss()
                        screenState.isScanning -> cancelSessionScan()
                        showingSettings -> backFromSettings()
                        showingSchedules -> backFromSchedules()
                        showingHistory && history.state.selectedDate != null -> history.dismissDay()
                        else -> closeHistory()
                    }
                }
                if (firstRunRoute == IntegratedFirstRunRoute.ONBOARDING) {
                    IntegratedOnboardingSurface(
                        page = onboardingPage,
                        busy = onboardingSaveBusy,
                        errorMessage = onboardingSaveError,
                        onPageChange = { if (!onboardingSaveBusy) onboardingPage = IntegratedFirstRunRules.page(it) },
                        onContinue = ::advanceOnboarding,
                    )
                } else if (firstRunRoute == IntegratedFirstRunRoute.ACTIVATION) {
                    IntegratedActivationSurface(
                        isScanning = screenState.isScanning,
                        isPaired = initialZapPaired,
                        status = screenState.transientStatus,
                        onScan = ::beginInitialPairing,
                        onCancelScan = ::cancelInitialScan,
                        onContinueToSetup = { leaveFirstRun(); openSetup() },
                        continueLabel = if (accountHost != null && access?.productAllowed != true)
                            "Continue to sign in" else null,
                        onSkip = ::leaveFirstRun,
                        onPurchaseZap = ::openZapStore,
                    )
                } else if (showingSettings && tutorialReplayPage != null) {
                    IntegratedOnboardingSurface(
                        page = tutorialReplayPage ?: 0,
                        busy = false,
                        errorMessage = null,
                        onPageChange = { tutorialReplayPage = IntegratedFirstRunRules.page(it) },
                        onContinue = {
                            if (tutorialReplayPage == 0) tutorialReplayPage = 1 else tutorialReplayPage = null
                        },
                        replaying = true,
                        onClose = { tutorialReplayPage = null },
                    )
                } else if (showingSettings) {
                    IntegratedSettingsSurface(
                        page = settingsPage,
                        appVersion = installedVersion,
                        setupSummary = getString(when (screenState.truth) {
                            Gate0Truth.CONSENT_REQUIRED -> R.string.status_consent_required
                            Gate0Truth.TARGET_REQUIRED -> R.string.status_target_required
                            Gate0Truth.ZAP_REQUIRED -> R.string.status_zap_required
                            Gate0Truth.PERMISSION_REQUIRED -> R.string.status_permission_required
                            Gate0Truth.IDLE -> R.string.status_idle
                            Gate0Truth.ACTIVE -> R.string.status_active
                            Gate0Truth.RECOVERY_REQUIRED -> R.string.status_recovery_required
                        }),
                        message = settingsMessage,
                        onDone = ::closeSettings,
                        onBackFromPrivacy = { settingsPage = IntegratedSettingsPage.MAIN },
                        onOpenSetup = ::openSetup,
                        onShowTutorial = { tutorialReplayPage = 0 },
                        onShowPrivacy = { settingsPage = IntegratedSettingsPage.PRIVACY },
                        onReviewLocalData = ::openSetup,
                        onOpenAppInfo = ::openAppInfo,
                        notificationsEnabled = notificationsEnabled,
                        onManageNotifications = ::manageSessionNotifications,
                        liveUpdateStatus = liveUpdateStatus,
                        onManageLiveUpdates = ::manageLiveUpdates,
                        onOpenAccount = accountHost?.let { { it.openAccount(this@MainActivity) } },
                        onComposeSupportEmail = {
                            launchSupportEmail(getString(R.string.integrated_settings_support_email), ::startActivity)
                        },
                    )
                } else if (showingSchedules) {
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
                        state = history.state,
                        onBack = ::closeHistory,
                        onRetry = history::refresh,
                        onOpenSettings = ::openSettings,
                        onSelectWindow = history::selectWindow,
                        onSelectDay = history::selectDay,
                        onDismissDay = history::dismissDay,
                    )
                } else {
                    IntegratedZapSurface(
                        state = screenState,
                        onZap = ::handleZapPress,
                        onOpenSetup = ::openSetup,
                        onOpenHistory = ::openHistory,
                        onOpenSchedules = ::openSchedules,
                        onOpenSelection = ::openModes,
                        onCancelScan = ::cancelSessionScan,
                    )
                }
                if (modes.state.visible && firstRunRoute == IntegratedFirstRunRoute.MAIN) {
                    IntegratedModeSheet(
                        state = modes.state,
                        onSelect = modes::select,
                        onNew = modes::newMode,
                        onEdit = modes::edit,
                        onName = modes::changeName,
                        onToggle = modes::toggle,
                        onSave = modes::save,
                        onDelete = modes::delete,
                        onCancelEditor = modes::cancelEditor,
                        onReload = modes::reload,
                        onDismiss = modes::dismiss,
                    )
                }
                completionState.visibleEntry?.let { entry ->
                    IntegratedCompletionSurface(entry = entry, onDismiss = {
                        completionState = completionState.dismiss()
                    })
                }
                // Storage quarantine can move the underlying screen to recovery. Keep the
                // tutorial's failed-save action available without dismissing that quarantine.
                onboardingSaveError?.let { message ->
                    AlertDialog(
                        onDismissRequest = { if (!onboardingSaveBusy) onboardingSaveError = null },
                        title = { Text(stringResource(R.string.first_run_save_error_title)) },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = ::persistOnboardingCompletion, enabled = !onboardingSaveBusy) {
                                Text(stringResource(R.string.first_run_retry))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { onboardingSaveError = null; leaveFirstRun(); openSetup() },
                                enabled = !onboardingSaveBusy,
                            ) { Text(stringResource(R.string.first_run_open_setup)) }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        maybeOpenRequiredAccount()
        ScheduleRuntime(this).onExplicitForeground()
        handler.removeCallbacks(refresh)
        refresh.run()
        notificationsEnabled = SessionNotifications.areEnabled(this)
        SessionNotifications.refresh(this)
        liveUpdateStatus = SessionNotifications.liveUpdateStatus(this)
        if (showingHistory && !showingSettings) history.onForeground()
        if (showingSchedules) schedules.load()
    }

    override fun onPause() {
        cancelReaderMode()
        modes.onHostPaused()
        completionState = completionState.onBackground()
        handler.removeCallbacks(clearTransientStatus)
        screenState = screenState.copy(transientStatus = null)
        handler.removeCallbacks(refresh)
        history.onBackground()
        super.onPause()
    }

    private fun maybeOpenRequiredAccount() {
        val host = application as? IntegratedAccountHost ?: return
        val access = host.accountAccess.value
        if (firstRunRoute == IntegratedFirstRunRoute.MAIN &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && access.ready && !access.signedIn &&
            promptedAccountGeneration != access.generation) {
            promptedAccountGeneration = access.generation
            host.openAccount(this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FIRST_RUN_ROUTE_KEY, firstRunRoute.name)
        outState.putInt(ONBOARDING_PAGE_KEY, onboardingPage)
        outState.putBoolean(ONBOARDING_SAVE_FAILED_KEY, onboardingSaveError != null)
        outState.putBoolean(HISTORY_ROUTE_KEY, showingHistory)
        outState.putBoolean(SCHEDULE_ROUTE_KEY, showingSchedules)
        outState.putBoolean(SETTINGS_ROUTE_KEY, showingSettings)
        outState.putString(SETTINGS_PAGE_KEY, settingsPage.name)
        outState.putInt(TUTORIAL_REPLAY_PAGE_KEY, tutorialReplayPage ?: -1)
        outState.putBundle(SCHEDULE_STATE_KEY, schedules.saveInstanceState())
        outState.putBundle(HISTORY_STATE_KEY, history.saveInstanceState())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        storageExecutor.shutdown()
        super.onDestroy()
    }

    private fun openHistory() {
        cancelReaderMode()
        completionState = completionState.dismiss()
        modes.onHostPaused()
        showingSchedules = false
        showingHistory = true
        history.onForeground()
    }

    private fun closeHistory() {
        history.onBackground()
        history.dismissDay()
        showingHistory = false
        refreshTruth()
    }

    private fun openSchedules() {
        cancelReaderMode()
        completionState = completionState.dismiss()
        modes.onHostPaused()
        history.onBackground()
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

    private fun handleZapPress() {
        if (screenState.canScan) beginOneShotScan() else openSetup()
    }

    private fun openModes() {
        cancelReaderMode()
        refreshTruth()
        if (preferences.snapshot().recoveryRequired) {
            openSetup()
            return
        }
        if (!screenState.desiredActive && firstRunRoute == IntegratedFirstRunRoute.MAIN) modes.open()
    }

    private fun cancelSessionScan() {
        cancelReaderMode()
        showTransientStatus(R.string.first_run_scan_canceled)
    }

    private fun openSetup() {
        cancelReaderMode()
        modes.onHostPaused()
        val host = application as? IntegratedAccountHost
        if (host != null && !host.accountAccess.value.productAllowed) {
            host.openAccount(this)
            return
        }
        startActivity(Intent(this, Gate0SetupActivity::class.java))
    }

    private fun openSettings() {
        cancelReaderMode()
        modes.onHostPaused()
        completionState = completionState.dismiss()
        history.onBackground()
        settingsMessage = null
        settingsPage = IntegratedSettingsPage.MAIN
        notificationsEnabled = SessionNotifications.areEnabled(this)
        showingSettings = true
        liveUpdateStatus = SessionNotifications.liveUpdateStatus(this)
    }

    private fun closeSettings() {
        tutorialReplayPage = null
        showingSettings = false
        settingsPage = IntegratedSettingsPage.MAIN
        refreshTruth()
        if (showingHistory && firstRunRoute == IntegratedFirstRunRoute.MAIN) history.onForeground()
    }

    private fun backFromSettings() {
        when {
            tutorialReplayPage != null -> tutorialReplayPage = null
            settingsPage == IntegratedSettingsPage.PRIVACY -> settingsPage = IntegratedSettingsPage.MAIN
            else -> closeSettings()
        }
    }

    /** Explicit user navigation only; rendering Settings never changes Android permissions. */
    private fun openAppInfo() {
        cancelReaderMode()
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: RuntimeException) {
            settingsMessage = getString(R.string.settings_app_info_unavailable)
        }
    }

    /** Optional Android settings, opened only after the user selects this row. */
    private fun manageSessionNotifications() {
        cancelReaderMode()
        SessionNotifications.ensureChannel(this)
        try {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } catch (_: RuntimeException) {
            settingsMessage = getString(R.string.session_notification_settings_unavailable)
        }
    }

    private fun manageLiveUpdates() {
        cancelReaderMode()
        if (Build.VERSION.SDK_INT >= 36 && SessionNotifications.supportsLiveUpdates(this)) {
            try {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                return
            } catch (_: RuntimeException) {
                // Not all OEMs expose the dedicated system page. Use the normal app page.
            }
        }
        manageSessionNotifications()
    }

    private fun firstRunFacts(stored: StoredGate0State) = IntegratedFirstRunFacts(
        tutorialCompleted = preferences.hasCompletedOnboarding(),
        zapPaired = !stored.zapDigest.isNullOrBlank(),
        existingSetup = stored.acceptedDisclosureVersion != null ||
            stored.acceptedDisclosureEpochMilliseconds > 0L ||
            stored.selectedPackages.isNotEmpty() || stored.sessionRevision > 0L,
        sessionRequested = stored.desiredActive,
        // Schedule startup can be conservatively suspended even on a fresh install.
        // Pairing grants no scheduling authority; keep that separate guard intact.
        recoveryRequired = stored.recoveryRequired,
    )

    private fun advanceOnboarding() {
        if (onboardingSaveBusy || firstRunRoute != IntegratedFirstRunRoute.ONBOARDING) return
        if (onboardingPage == 0) {
            onboardingPage = 1
            return
        }
        persistOnboardingCompletion()
    }

    private fun persistOnboardingCompletion() {
        if (onboardingSaveBusy) return
        onboardingSaveBusy = true
        storageExecutor.execute {
            val saved = try { preferences.completeOnboarding() } catch (_: Exception) { false }
            handler.post {
                if (isDestroyed) return@post
                onboardingSaveBusy = false
                if (saved) {
                    onboardingSaveError = null
                    refreshTruth()
                } else {
                    onboardingSaveError = getString(R.string.first_run_save_failed)
                }
            }
        }
    }

    private fun leaveFirstRun() {
        cancelReaderMode()
        firstRunRoute = IntegratedFirstRunRoute.MAIN
        refreshTruth()
    }

    private fun beginInitialPairing() {
        val facts = firstRunFacts(preferences.snapshot())
        if (firstRunRoute != IntegratedFirstRunRoute.ACTIVATION || facts.zapPaired ||
            facts.sessionRequested || facts.recoveryRequired
        ) {
            refreshTruth()
            return
        }
        beginOneShotScan(IntegratedScanPurpose.PAIR_INITIAL)
    }

    private fun cancelInitialScan() {
        cancelReaderMode()
        showTransientStatus(R.string.first_run_scan_canceled)
    }

    private fun openZapStore() {
        cancelReaderMode()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://store.justnoise.shop/products/thezap")))
        } catch (_: RuntimeException) {
            showTransientStatus(R.string.first_run_store_unavailable)
        }
    }

    private fun refreshTruth() {
        val truth = controller.truth()
        val stored = preferences.snapshot()
        screenState = screenState.copy(
            truth = truth,
            selectedAppName = selectedAppsLabel(controller.sessionPackages()),
            modeName = if (stored.desiredActive) stored.activeModeName else stored.recentModeName,
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
        val firstRun = firstRunFacts(stored)
        if (stored.desiredActive) completionState = completionState.onNewSession()
        if (!controller.hasAcceptedCurrentDisclosure()) completionState = completionState.onWithdrawal()
        if (stored.desiredActive || stored.recoveryRequired) modes.onHostPaused()
        initialZapPaired = firstRun.zapPaired
        val resolvedRoute = IntegratedFirstRunRules.route(firstRun, firstRunRoute)
        if (resolvedRoute != firstRunRoute) {
            cancelReaderMode()
            modes.onHostPaused()
            firstRunRoute = resolvedRoute
            if (resolvedRoute != IntegratedFirstRunRoute.MAIN) {
                showingSettings = false
                tutorialReplayPage = null
                showingHistory = false
                history.onBackground()
            }
        }
    }

    private fun beginOneShotScan(purpose: IntegratedScanPurpose = IntegratedScanPurpose.TOGGLE_SESSION) {
        if (scanCoordinator.isActive()) return
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled) {
            showTransientStatus(R.string.status_nfc_unavailable)
            return
        }

        val token = scanCoordinator.begin(purpose)
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
            transientStatus = getString(
                if (purpose == IntegratedScanPurpose.PAIR_INITIAL) R.string.first_run_pair_scanning
                else R.string.status_scanning,
            ),
        )
    }

    private fun onTagDiscovered(
        token: OneShotScanCoordinator.Token<IntegratedScanPurpose>,
        tag: Tag,
    ) {
        if (!scanCoordinator.claim(token)) return
        val result = NdefTagReader.readSingleStrictTextRecord(tag)
        runOnUiThread { completeOneShotScan(token, result) }
    }

    private fun completeOneShotScan(
        token: OneShotScanCoordinator.Token<IntegratedScanPurpose>,
        result: NdefReadResult,
    ) {
        if (!scanCoordinator.isClaimActive(token) || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
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
                is NdefReadResult.Accepted -> when {
                    !foregroundInteractionClaimed -> R.string.status_direct_duplicate_ignored
                    token.purpose == IntegratedScanPurpose.PAIR_INITIAL -> {
                        if (controller.pairInitialZap(result.transientCanonicalRecord)) R.string.first_run_pair_success
                        else R.string.first_run_pair_failed
                    }
                    else -> sessionToggleStatus(result)
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

    private fun sessionToggleStatus(result: NdefReadResult.Accepted): Int {
        val interaction = try {
            controller.handleZap(
                transientCanonicalRecord = result.transientCanonicalRecord,
                acceptedReadAlreadyHandled = false,
            )
        } catch (_: Exception) {
            null
        }
        // Only this current foreground scan may present its newly committed receipt.
        // DirectNfcActivity intentionally ignores it and never opens a completion popup.
        if (interaction?.persistenceFailed == false &&
            interaction.sessionDecision?.transition == SessionTransition.STOP &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            firstRunRoute == IntegratedFirstRunRoute.MAIN &&
            !showingHistory && !showingSchedules && !showingSettings &&
            controller.hasAcceptedCurrentDisclosure()
        ) {
            interaction.completedSession?.let { completionState = completionState.receive(it) }
        }
        return when {
            interaction == null -> R.string.status_start_failed
            interaction.accountRequired -> R.string.status_account_required
            interaction.persistenceFailed -> R.string.status_session_storage_failed
            interaction.readDecision.outcome == ZapReadOutcome.UNAUTHORIZED -> R.string.status_scan_unauthorized
            interaction.readDecision.outcome == ZapReadOutcome.INVALID -> R.string.status_scan_invalid
            interaction.sessionDecision?.transition == SessionTransition.START -> R.string.status_started
            interaction.sessionDecision?.transition == SessionTransition.STOP -> R.string.status_stopped
            interaction.sessionDecision?.transition == SessionTransition.STOP_REJECTED -> R.string.status_stop_guard
            interaction.sessionDecision?.transition == SessionTransition.START_FAILED -> R.string.status_start_failed
            else -> R.string.status_no_change
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
        // Count is real persisted state; names are discovered on the selection worker,
        // not via PackageManager on every one-second elapsed-time refresh.
        return resources.getQuantityString(R.plurals.selected_apps_count, packages.size, packages.size)
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
        const val HISTORY_ROUTE_KEY = "showing_history"
        const val HISTORY_STATE_KEY = "history_screen_state"
        const val SCHEDULE_ROUTE_KEY = "showing_schedules"
        const val SCHEDULE_STATE_KEY = "schedule_screen_state"
        const val SETTINGS_ROUTE_KEY = "showing_settings"
        const val SETTINGS_PAGE_KEY = "settings_page"
        const val TUTORIAL_REPLAY_PAGE_KEY = "tutorial_replay_page"
        const val FIRST_RUN_ROUTE_KEY = "first_run_route"
        const val ONBOARDING_PAGE_KEY = "onboarding_page"
        const val ONBOARDING_SAVE_FAILED_KEY = "onboarding_save_failed"
    }
}
