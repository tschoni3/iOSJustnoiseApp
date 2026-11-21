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
import com.justnoise.gate0.data.ModeRules
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
import com.justnoise.gate0.platform.restriction.AccessibilityServiceStatus
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
    private var showingHowItWorksReplay by mutableStateOf(false)
    private var settingsMessage by mutableStateOf<String?>(null)
    private var notificationsEnabled by mutableStateOf(false)
    private var liveUpdateStatus by mutableStateOf(SessionLiveUpdateStatus.UNSUPPORTED)
    private var installedVersion = ""
    private var emergencyUnlocks by mutableStateOf(5)
    private var emergencySessionId by mutableStateOf<String?>(null)
    private var completionState by mutableStateOf(IntegratedCompletionState())
    private var firstRunRoute by mutableStateOf(IntegratedFirstRunRoute.MAIN)
    private var firstModeSaveRequested = false
    private var zapScanGuideSeen by mutableStateOf(false)
    private var onboardingSaveBusy by mutableStateOf(false)
    private var onboardingSaveError by mutableStateOf<String?>(null)
    private var initialZapPaired by mutableStateOf(false)
    private var firstSessionGuideDismissed by mutableStateOf(false)
    private var showingAccessibilitySetup by mutableStateOf(false)
    private var showingScheduleSetup by mutableStateOf(false)
    private var scheduleSetupPending = false
    private var blockingSetupIsRequired by mutableStateOf(false)
    private var accessibilityEnabled by mutableStateOf(false)
    private var disclosureAccepted by mutableStateOf(false)
    private var accessibilitySetupError by mutableStateOf<String?>(null)
    private var storageRecoveryRequired by mutableStateOf(false)
    private var alarmAccessEnabled by mutableStateOf(false)
    private lateinit var schedules: IntegratedScheduleViewModel
    private lateinit var modes: IntegratedModeViewModel
    private lateinit var history: IntegratedHistoryViewModel
    private var scheduleRefreshAt = 0L
    private var accountViewGeneration by mutableStateOf(-1L)
    private var promptedAccountGeneration = -1L
    private var requiredAccountOpened = false
    private var requiredAccountHostPaused = false
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
        val guidance = getSharedPreferences("justnoise_installation_guidance", MODE_PRIVATE)
        firstSessionGuideDismissed = guidance.getBoolean("first_session_guide_dismissed_v1", false)
        zapScanGuideSeen = guidance.getBoolean("zap_scan_guide_seen_v1", false)
        alarmAccessEnabled = ScheduleAlarmScheduler(this).hasExactAlarmAccess()
        val initialStored = preferences.snapshot()
        val initialFacts = firstRunFacts(initialStored)
        promptedAccountGeneration = savedInstanceState?.getLong(PROMPTED_ACCOUNT_GENERATION_KEY) ?: -1L
        requiredAccountOpened = savedInstanceState?.getBoolean(REQUIRED_ACCOUNT_OPENED_KEY) ?: false
        requiredAccountHostPaused = savedInstanceState?.getBoolean(REQUIRED_ACCOUNT_PAUSED_KEY) ?: false
        initialZapPaired = initialFacts.zapPaired
        val needsBlockingSetup = IntegratedFirstRunRules.needsBlockingSetup(initialFacts,
            controller.hasAcceptedCurrentDisclosure(), AccessibilityServiceStatus.isEnabled(this))
        // An old saved first-mode route must not bypass the remaining permission gate.
        showingAccessibilitySetup = needsBlockingSetup ||
            savedInstanceState?.getBoolean(ACCESSIBILITY_SETUP_ROUTE_KEY) == true
        blockingSetupIsRequired = savedInstanceState?.getBoolean(REQUIRED_BLOCKING_SETUP_KEY)
            ?: needsBlockingSetup
        showingScheduleSetup = savedInstanceState?.getBoolean(SCHEDULE_SETUP_ROUTE_KEY) ?: false
        scheduleSetupPending = savedInstanceState?.getBoolean(SCHEDULE_SETUP_PENDING_KEY)
            ?: blockingSetupIsRequired
        firstRunRoute = IntegratedFirstRunRules.route(
            initialFacts,
            IntegratedFirstRunRoute.entries.firstOrNull {
                it.name == savedInstanceState?.getString(FIRST_RUN_ROUTE_KEY)
            },
        )
        if (savedInstanceState?.getBoolean(ONBOARDING_SAVE_FAILED_KEY) == true && !initialFacts.tutorialCompleted) {
            onboardingSaveError = getString(R.string.first_run_save_failed)
        }
        showingHistory = savedInstanceState?.getBoolean(HISTORY_ROUTE_KEY) ?: false
        showingSchedules = savedInstanceState?.getBoolean(SCHEDULE_ROUTE_KEY) ?: false
        showingSettings = savedInstanceState?.getBoolean(SETTINGS_ROUTE_KEY) ?: false
        settingsPage = IntegratedSettingsPage.entries.firstOrNull {
            it.name == savedInstanceState?.getString(SETTINGS_PAGE_KEY)
        } ?: IntegratedSettingsPage.MAIN
        showingHowItWorksReplay = savedInstanceState?.getBoolean(TUTORIAL_REPLAY_KEY) == true
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        installedVersion = "${packageInfo.versionName.orEmpty()} (${packageInfo.longVersionCode})"
        if (firstRunRoute != IntegratedFirstRunRoute.MAIN) {
            showingHistory = false
            showingSchedules = false
            showingSettings = false
        }
        if (showingSettings) { showingHistory = true; showingSchedules = false }
        if (!showingSettings) showingHowItWorksReplay = false
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
                val holdAccount = IntegratedFirstRunRules.shouldHoldRequiredAccount(
                    firstRunRoute, showingAccessibilitySetup, showingScheduleSetup,
                    IntegratedFirstRunRules.needsBlockingSetup(
                        firstRunFacts(preferences.snapshot()), disclosureAccepted, accessibilityEnabled,
                    ),
                )
                if (access != null && access.blocksRoute(firstRunRoute, accountViewGeneration) && !holdAccount) {
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
                            showingHowItWorksReplay = false
                            refreshTruth()
                            accountViewGeneration = access.generation
                        }
                    }
                    LaunchedEffect(access.ready, access.signedIn) {
                        maybeOpenRequiredAccount()
                    }
                    IntegratedAccountTransitionSurface()
                    return@JustnoiseIntegratedTheme
                }
                LaunchedEffect(modes.state.savedVersion) {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        refreshTruth()
                    }
                }
                BackHandler(enabled = showingAccessibilitySetup || showingScheduleSetup || showingHistory || showingSchedules || showingSettings ||
                    completionState.visibleEntry != null || modes.state.visible || screenState.isScanning ||
                    firstRunRoute == IntegratedFirstRunRoute.ACTIVATION ||
                    firstRunRoute == IntegratedFirstRunRoute.FIRST_MODE ||
                    showingHowItWorksReplay
                ) {
                    when {
                        completionState.visibleEntry != null -> completionState = completionState.dismiss()
                        showingHowItWorksReplay -> showingHowItWorksReplay = false
                        showingAccessibilitySetup -> deferBlockingSetup()
                        showingScheduleSetup -> skipScheduleSetup()
                        firstRunRoute == IntegratedFirstRunRoute.FIRST_MODE -> leaveFirstModeSetup()
                        firstRunRoute == IntegratedFirstRunRoute.ACTIVATION -> {
                            if (screenState.isScanning) cancelInitialScan()
                            else if (initialZapPaired) continueAfterPairing()
                            else finish()
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
                        busy = onboardingSaveBusy,
                        errorMessage = onboardingSaveError,
                        onContinue = ::advanceOnboarding,
                    )
                } else if (firstRunRoute == IntegratedFirstRunRoute.FIRST_MODE) {
                    LaunchedEffect(firstRunRoute, modes.state.visible, modes.state.loading, modes.state.editor, modes.state.canMutate) {
                        if (!modes.state.visible && !modes.state.saving) modes.open()
                        else if (modes.state.visible && !modes.state.loading && modes.state.editor == null && modes.state.canMutate) {
                            val existing = modes.state.catalog?.modes?.firstOrNull {
                                ModeRules.validName(it.name) && ModeRules.validTargets(it.selectedPackages)
                            }
                            if (existing != null) modes.edit(existing.id) else modes.newMode()
                        }
                    }
                    LaunchedEffect(modes.state.savedVersion, modes.state.saving, modes.state.catalog) {
                        maybeCompleteFirstMode()
                    }
                    IntegratedFirstModeSetupSurface(
                        state = modes.state,
                        onName = modes::changeName,
                        onToggle = modes::toggle,
                        onSave = ::saveFirstMode,
                        onReload = modes::reload,
                        onBack = ::leaveFirstModeSetup,
                    )
                } else if (firstRunRoute == IntegratedFirstRunRoute.ACTIVATION) {
                    IntegratedActivationSurface(
                        isScanning = screenState.isScanning,
                        isPaired = initialZapPaired,
                        status = screenState.transientStatus,
                        onScan = ::beginInitialPairing,
                        onCancelScan = ::cancelInitialScan,
                        onContinueToSetup = ::continueAfterPairing,
                        onPurchaseZap = ::openZapStore,
                        hasSeenGuide = zapScanGuideSeen,
                        onGuideSeen = ::markZapScanGuideSeen,
                    )
                } else if (showingAccessibilitySetup) {
                    IntegratedAccessibilitySetupSurface(
                        consentAccepted = disclosureAccepted,
                        errorMessage = accessibilitySetupError,
                        onEnable = ::enableAccessibilityFromSetup,
                        onLater = ::deferBlockingSetup,
                        exitOnLater = blockingSetupIsRequired,
                    )
                } else if (showingScheduleSetup) {
                    IntegratedScheduleSetupSurface(
                        errorMessage = accessibilitySetupError,
                        onEnable = ::enableScheduleAccessFromSetup,
                        onLater = ::skipScheduleSetup,
                    )
                } else if (IntegratedProductEntryRules.needsRecovery(screenState.truth, screenState.desiredActive,
                        storageRecoveryRequired, showingSchedules, schedules.state.snapshot?.suspended == true) && !showingSettings) {
                    IntegratedRecoverySurface(
                        sessionRequested = screenState.desiredActive,
                        storageRecovery = storageRecoveryRequired,
                        schedulesOnly = screenState.truth in setOf(Gate0Truth.IDLE, Gate0Truth.TARGET_REQUIRED) &&
                            !storageRecoveryRequired && !screenState.desiredActive,
                        consentNeeded = !disclosureAccepted,
                        errorMessage = accessibilitySetupError ?: screenState.transientStatus,
                        isScanning = screenState.isScanning,
                        onRestore = ::restoreFromGuidedRecovery,
                        onStop = { if (screenState.desiredActive) beginOneShotScan() },
                        onCancelScan = ::cancelSessionScan,
                        onHelp = {
                            if (!launchSupportEmail(getString(R.string.integrated_settings_support_email), ::startActivity))
                                accessibilitySetupError = getString(R.string.integrated_settings_email_unavailable)
                        },
                        blockingEnabled = accessibilityEnabled,
                        onEmergencySettings = ::openSettings,
                    )
                } else if (IntegratedProductEntryRules.needsBlocking(screenState.truth,
                        disclosureAccepted, accessibilityEnabled) && !showingSettings) {
                    IntegratedAccessibilitySetupSurface(disclosureAccepted, accessibilitySetupError,
                        ::enableAccessibilityFromSetup, ::finish, exitOnLater = true)
                } else if (showingSchedules && !alarmAccessEnabled) {
                    IntegratedScheduleSetupSurface(accessibilitySetupError,
                        ::enableScheduleAccessFromSetup, ::skipScheduleSetup)
                } else if (screenState.truth == Gate0Truth.ZAP_REQUIRED) {
                    IntegratedActivationSurface(
                        isScanning = screenState.isScanning,
                        isPaired = initialZapPaired,
                        status = screenState.transientStatus,
                        onScan = ::beginInitialPairing,
                        onCancelScan = ::cancelInitialScan,
                        onContinueToSetup = ::continueAfterPairing,
                        onPurchaseZap = ::openZapStore,
                        hasSeenGuide = zapScanGuideSeen,
                        onGuideSeen = ::markZapScanGuideSeen,
                    )
                } else if (showingSettings && showingHowItWorksReplay) {
                    IntegratedHowItWorksGuide(
                        continueLabel = R.string.first_session_guide_continue,
                        onContinue = { showingHowItWorksReplay = false },
                        onDismiss = { showingHowItWorksReplay = false },
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
                        onBackFromPrivacy = ::backFromSettings,
                        onOpenSetup = ::openSetup,
                        onShowTutorial = { showingHowItWorksReplay = true },
                        onShowPrivacy = { settingsPage = IntegratedSettingsPage.PRIVACY },
                        onShowMore = { settingsPage = IntegratedSettingsPage.ADVANCED },
                        onReviewLocalData = ::openSetup,
                        onOpenAppInfo = ::openAppInfo,
                        notificationsEnabled = notificationsEnabled,
                        onManageNotifications = ::manageSessionNotifications,
                        liveUpdateStatus = liveUpdateStatus,
                        onManageLiveUpdates = ::manageLiveUpdates,
                        onOpenAccount = accountHost?.let { { it.openAccount(this@MainActivity) } },
                        emergencyUnlocks = emergencyUnlocks,
                        emergencySessionId = emergencySessionId,
                        emergencyBusy = screenState.shortcutBusy,
                        onEmergencyUnzap = ::handleEmergencyUnzap,
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
                        onLongPressZap = ::handleLongPressZap,
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
        com.justnoise.gate0.platform.restriction.AccessibilitySetupReturn.cancel()
        maybeOpenRequiredAccount(returnedToHost = true)
        ScheduleRuntime(this).onExplicitForeground()
        handler.removeCallbacks(refresh)
        refresh.run()
        notificationsEnabled = SessionNotifications.areEnabled(this)
        SessionNotifications.refresh(this)
        liveUpdateStatus = SessionNotifications.liveUpdateStatus(this)
        accessibilityEnabled = AccessibilityServiceStatus.isEnabled(this)
        alarmAccessEnabled = ScheduleAlarmScheduler(this).hasExactAlarmAccess()
        disclosureAccepted = controller.hasAcceptedCurrentDisclosure()
        if (showingAccessibilitySetup || showingScheduleSetup) advancePermissionSetup()
        if (showingHistory && !showingSettings) history.onForeground()
        if (showingSchedules) schedules.load()
    }

    override fun onPause() {
        if (requiredAccountOpened) requiredAccountHostPaused = true
        cancelReaderMode()
        modes.onHostPaused()
        completionState = completionState.onBackground()
        handler.removeCallbacks(clearTransientStatus)
        screenState = screenState.copy(transientStatus = null)
        handler.removeCallbacks(refresh)
        history.onBackground()
        super.onPause()
    }

    private fun maybeOpenRequiredAccount(returnedToHost: Boolean = false) {
        val host = application as? IntegratedAccountHost ?: return
        val access = host.accountAccess.value
        if (IntegratedFirstRunRules.shouldHoldRequiredAccount(
                firstRunRoute, showingAccessibilitySetup, showingScheduleSetup,
                IntegratedFirstRunRules.needsBlockingSetup(
                    firstRunFacts(preferences.snapshot()), disclosureAccepted, accessibilityEnabled,
                ),
            )
        ) {
            return
        }
        if (firstRunRoute == IntegratedFirstRunRoute.MAIN &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && access.ready && !access.signedIn &&
            promptedAccountGeneration != access.generation) {
            promptedAccountGeneration = access.generation
            requiredAccountOpened = true
            requiredAccountHostPaused = false
            host.openAccount(this)
        } else if (firstRunRoute == IntegratedFirstRunRoute.MAIN &&
            returnedToHost && access.ready && !access.signedIn &&
            promptedAccountGeneration == access.generation &&
            requiredAccountOpened && requiredAccountHostPaused
        ) {
            // Backing out of required sign-in exits instead of revealing a second
            // account or recovery dialog underneath it.
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(FIRST_RUN_ROUTE_KEY, firstRunRoute.name)
        outState.putBoolean(ACCESSIBILITY_SETUP_ROUTE_KEY, showingAccessibilitySetup)
        outState.putBoolean(SCHEDULE_SETUP_ROUTE_KEY, showingScheduleSetup)
        outState.putBoolean(SCHEDULE_SETUP_PENDING_KEY, scheduleSetupPending)
        outState.putBoolean(REQUIRED_BLOCKING_SETUP_KEY, blockingSetupIsRequired)
        outState.putLong(PROMPTED_ACCOUNT_GENERATION_KEY, promptedAccountGeneration)
        outState.putBoolean(REQUIRED_ACCOUNT_OPENED_KEY, requiredAccountOpened)
        outState.putBoolean(REQUIRED_ACCOUNT_PAUSED_KEY, requiredAccountHostPaused)
        outState.putBoolean(ONBOARDING_SAVE_FAILED_KEY, onboardingSaveError != null)
        outState.putBoolean(HISTORY_ROUTE_KEY, showingHistory)
        outState.putBoolean(SCHEDULE_ROUTE_KEY, showingSchedules)
        outState.putBoolean(SETTINGS_ROUTE_KEY, showingSettings)
        outState.putString(SETTINGS_PAGE_KEY, settingsPage.name)
        outState.putBoolean(TUTORIAL_REPLAY_KEY, showingHowItWorksReplay)
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
        if (!ScheduleAlarmScheduler(this).hasExactAlarmAccess()) openScheduleAlarmAccess()
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

    /** A single guided route, not another technical warning panel inside the editor. */
    private fun openScheduleAlarmAccess() {
        cancelReaderMode()
        val stored = preferences.snapshot()
        if (stored.desiredActive || stored.recoveryRequired) {
            openSetup()
            return
        }
        scheduleSetupPending = true
        blockingSetupIsRequired = false
        accessibilitySetupError = null
        showingScheduleSetup = true
        advancePermissionSetup()
    }

    /** Only an affirmative action opens the app-specific system switch. */
    private fun enableScheduleAccessFromSetup() {
        if (ScheduleAlarmScheduler(this).hasExactAlarmAccess()) {
            advancePermissionSetup()
            return
        }
        try {
            startActivity(ScheduleAlarmScheduler(this).permissionIntent())
        } catch (_: RuntimeException) {
            accessibilitySetupError = getString(R.string.first_run_schedules_unavailable)
        }
    }

    private fun handleZapPress() {
        if (screenState.shortcutBusy) return
        when {
            screenState.canScan -> beginOneShotScan()
            screenState.truth == Gate0Truth.TARGET_REQUIRED -> openModes()
            else -> openSetup()
        }
    }

    private fun handleLongPressZap() {
        if (screenState.shortcutBusy || screenState.isScanning || screenState.desiredActive) return
        if (screenState.truth != Gate0Truth.IDLE) {
            if (screenState.truth == Gate0Truth.TARGET_REQUIRED) openModes() else openSetup()
            return
        }
        screenState = screenState.copy(shortcutBusy = true)
        storageExecutor.execute {
            val result = runCatching { controller.startWithoutScan() }.getOrNull()
            handler.post {
                screenState = screenState.copy(shortcutBusy = false)
                if (!isDestroyed) {
                    if (result?.noChange != true) showTransientStatus(if (result?.succeeded == true)
                        R.string.status_started else R.string.status_start_failed)
                    refreshTruth()
                }
            }
        }
    }

    private fun handleEmergencyUnzap(sessionId: String) {
        if (screenState.shortcutBusy) return
        cancelReaderMode()
        screenState = screenState.copy(shortcutBusy = true)
        settingsMessage = null
        storageExecutor.execute {
            val result = runCatching { controller.emergencyUnzap(sessionId) }.getOrNull()
            handler.post {
                screenState = screenState.copy(shortcutBusy = false)
                if (!isDestroyed) {
                    refreshTruth()
                    if (result?.succeeded == true) {
                        showingSettings = false
                        showingHistory = false
                        history.onBackground()
                        if (!screenState.desiredActive)
                            result.completedSession?.let { completionState = completionState.receive(it) }
                    } else settingsMessage = getString(R.string.integrated_emergency_failed)
                }
            }
        }
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
        val stored = preferences.snapshot()
        if (stored.desiredActive || stored.recoveryRequired || !firstRunFacts(stored).zapPaired) {
            startActivity(Intent(this, Gate0SetupActivity::class.java))
            return
        }
        if (!controller.hasAcceptedCurrentDisclosure() || !AccessibilityServiceStatus.isEnabled(this)) {
            openAccessibilitySetup(required = false)
            return
        }
        startActivity(Intent(this, Gate0SetupActivity::class.java))
    }

    private fun openAccessibilitySetup(required: Boolean) {
        cancelReaderMode()
        accessibilitySetupError = null
        accessibilityEnabled = AccessibilityServiceStatus.isEnabled(this)
        disclosureAccepted = controller.hasAcceptedCurrentDisclosure()
        blockingSetupIsRequired = required
        scheduleSetupPending = required
        showingScheduleSetup = false
        showingAccessibilitySetup = true
        advancePermissionSetup()
    }

    private fun closeAccessibilitySetup() {
        advancePermissionSetup()
    }

    private fun advancePermissionSetup() {
        val stored = preferences.snapshot()
        // Permission education must never obscure a real stop or recovery route.
        if (stored.desiredActive || stored.recoveryRequired) {
            finishPermissionSetup()
            return
        }
        when (IntegratedPermissionFlow.next(controller.hasAcceptedCurrentDisclosure(),
            AccessibilityServiceStatus.isEnabled(this), scheduleSetupPending,
            ScheduleAlarmScheduler(this).hasExactAlarmAccess())) {
            IntegratedPermissionStep.BLOCKING -> {
                showingAccessibilitySetup = true
                showingScheduleSetup = false
            }
            IntegratedPermissionStep.SCHEDULES -> {
                showingAccessibilitySetup = false
                showingScheduleSetup = true
                accessibilitySetupError = null
            }
            IntegratedPermissionStep.COMPLETE -> finishPermissionSetup()
        }
    }

    private fun finishPermissionSetup() {
        showingAccessibilitySetup = false
        showingScheduleSetup = false
        scheduleSetupPending = false
        blockingSetupIsRequired = false
        accessibilitySetupError = null
        refreshTruth()
        schedules.load()
        maybeOpenRequiredAccount()
    }

    private fun skipScheduleSetup() {
        // Declining optional schedules returns to manual Zap use, not a broken editor.
        showingSchedules = false
        finishPermissionSetup()
    }

    private fun restoreFromGuidedRecovery() {
        accessibilitySetupError = null
        when {
            storageRecoveryRequired -> refreshTruth() // Retry reads; never clear data or quarantine flags.
            !controller.hasAcceptedCurrentDisclosure() -> enableAccessibilityFromSetup()
            screenState.truth in setOf(Gate0Truth.IDLE, Gate0Truth.TARGET_REQUIRED) && !screenState.desiredActive -> {
                ScheduleRuntime(this).onExplicitForeground()
                schedules.refresh()
            }
            else -> try {
                if (!AccessibilitySetupNavigation.open(this))
                    accessibilitySetupError = getString(R.string.first_run_blocking_settings_unavailable)
            } catch (_: RuntimeException) {
                accessibilitySetupError = getString(R.string.first_run_blocking_settings_unavailable)
            }
        }
    }

    private fun deferBlockingSetup() {
        if (blockingSetupIsRequired) finish() else finishPermissionSetup()
    }

    private fun continueAfterPairing() {
        leaveFirstRun()
        openAccessibilitySetup(required = true)
    }

    private fun enableAccessibilityFromSetup() {
        if (!controller.hasAcceptedCurrentDisclosure() && !controller.acceptDisclosure()) {
            accessibilitySetupError = getString(R.string.first_run_blocking_save_failed)
            return
        }
        accessibilitySetupError = null
        disclosureAccepted = true
        if (AccessibilityServiceStatus.isEnabled(this)) {
            closeAccessibilitySetup()
            return
        }
        try {
            if (!AccessibilitySetupNavigation.open(this)) {
                accessibilitySetupError = getString(R.string.first_run_blocking_settings_unavailable)
            }
        } catch (_: RuntimeException) {
            accessibilitySetupError = getString(R.string.first_run_blocking_settings_unavailable)
        }
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
        showingHowItWorksReplay = false
        showingSettings = false
        settingsPage = IntegratedSettingsPage.MAIN
        refreshTruth()
        if (showingHistory && firstRunRoute == IntegratedFirstRunRoute.MAIN) history.onForeground()
    }

    private fun backFromSettings() {
        when {
            showingHowItWorksReplay -> showingHowItWorksReplay = false
            settingsPage == IntegratedSettingsPage.PRIVACY -> settingsPage = IntegratedSettingsPage.ADVANCED
            settingsPage == IntegratedSettingsPage.ADVANCED -> settingsPage = IntegratedSettingsPage.MAIN
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
        firstRunRoute = IntegratedFirstRunRoute.FIRST_MODE
    }

    private fun leaveFirstModeSetup() {
        if (onboardingSaveBusy) return
        firstModeSaveRequested = false
        modes.dismiss()
        firstRunRoute = IntegratedFirstRunRoute.ONBOARDING
    }

    private fun saveFirstMode() {
        if (onboardingSaveBusy || firstRunRoute != IntegratedFirstRunRoute.FIRST_MODE) return
        firstModeSaveRequested = true
        if (modes.state.reloadRequired) modes.reload() else modes.save()
    }

    private fun maybeCompleteFirstMode() {
        if (!firstModeSaveRequested || firstRunRoute != IntegratedFirstRunRoute.FIRST_MODE || modes.state.saving) return
        val editor = modes.state.editor
        val saved = modes.state.catalog?.modes.orEmpty().any { mode ->
            ModeRules.validName(mode.name) && ModeRules.validTargets(mode.selectedPackages) &&
                (editor == null || editor.id == null || editor.id == mode.id)
        }
        if (saved) {
            firstModeSaveRequested = false
            persistOnboardingCompletion()
        }
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
                    modes.dismiss()
                    refreshTruth()
                } else {
                    onboardingSaveError = getString(R.string.first_run_save_failed)
                }
            }
        }
    }

    private fun markZapScanGuideSeen() {
        zapScanGuideSeen = true
        getSharedPreferences("justnoise_installation_guidance", MODE_PRIVATE).edit()
            .putBoolean("zap_scan_guide_seen_v1", true).apply()
    }

    private fun leaveFirstRun() {
        cancelReaderMode()
        firstRunRoute = IntegratedFirstRunRoute.MAIN
        refreshTruth()
    }

    private fun beginInitialPairing() {
        val facts = firstRunFacts(preferences.snapshot())
        if ((firstRunRoute != IntegratedFirstRunRoute.ACTIVATION && screenState.truth != Gate0Truth.ZAP_REQUIRED) || facts.zapPaired ||
            facts.sessionRequested || facts.recoveryRequired
        ) {
            refreshTruth()
            return
        }
        beginOneShotScan(IntegratedScanPurpose.PAIR_INITIAL)
    }

    private fun cancelInitialScan() {
        cancelReaderMode()
    }

    private fun openZapStore() {
        cancelReaderMode()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://justnoise.shop/pages/the-zap")))
        } catch (_: RuntimeException) {
            showTransientStatus(R.string.first_run_store_unavailable)
        }
    }

    private fun refreshTruth() {
        val truth = controller.truth()
        disclosureAccepted = controller.hasAcceptedCurrentDisclosure()
        // TARGET_REQUIRED precedes permission truth; use the actual grant for presentation.
        accessibilityEnabled = AccessibilityServiceStatus.isEnabled(this)
        val stored = preferences.snapshot()
        storageRecoveryRequired = stored.recoveryRequired
        emergencyUnlocks = preferences.emergencyUnlocksRemaining()
        emergencySessionId = stored.activeSessionId.takeIf { stored.desiredActive }
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
        if (stored.desiredActive || stored.recoveryRequired) {
            // A scheduled start or recovery may occur while onboarding is backgrounded.
            // Never let a restored setup page cover the real stop/recovery route.
            modes.onHostPaused()
            showingAccessibilitySetup = false
            showingScheduleSetup = false
            scheduleSetupPending = false
            blockingSetupIsRequired = false
        }
        initialZapPaired = firstRun.zapPaired
        val resolvedRoute = IntegratedFirstRunRules.route(firstRun, firstRunRoute)
        if (resolvedRoute != firstRunRoute) {
            val leavingActivation = firstRunRoute == IntegratedFirstRunRoute.ACTIVATION &&
                resolvedRoute == IntegratedFirstRunRoute.MAIN
            cancelReaderMode()
            modes.onHostPaused()
            firstRunRoute = resolvedRoute
            if (leavingActivation && IntegratedFirstRunRules.needsBlockingSetup(firstRun,
                    disclosureAccepted, AccessibilityServiceStatus.isEnabled(this))) {
                blockingSetupIsRequired = true
                scheduleSetupPending = true
                showingAccessibilitySetup = true
            }
            if (resolvedRoute != IntegratedFirstRunRoute.MAIN) {
                showingAccessibilitySetup = false
                showingScheduleSetup = false
                scheduleSetupPending = false
                showingSettings = false
                showingHowItWorksReplay = false
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
                        if (controller.pairInitialZap(
                                result.transientCanonicalRecord,
                                transientUid = result.transientUid,
                                recordCount = result.recordCount,
                            )) R.string.first_run_pair_success
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
                transientUid = result.transientUid,
                recordCount = result.recordCount,
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
        if (this is NdefReadResult.Accepted) clear()
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
        const val TUTORIAL_REPLAY_KEY = "how_it_works_replay"
        const val FIRST_RUN_ROUTE_KEY = "first_run_route"
        const val ACCESSIBILITY_SETUP_ROUTE_KEY = "showing_accessibility_setup"
        const val SCHEDULE_SETUP_ROUTE_KEY = "showing_schedule_setup"
        const val SCHEDULE_SETUP_PENDING_KEY = "schedule_setup_pending"
        const val REQUIRED_BLOCKING_SETUP_KEY = "required_blocking_setup"
        const val PROMPTED_ACCOUNT_GENERATION_KEY = "prompted_account_generation"
        const val REQUIRED_ACCOUNT_OPENED_KEY = "required_account_opened"
        const val REQUIRED_ACCOUNT_PAUSED_KEY = "required_account_paused"
        const val ONBOARDING_SAVE_FAILED_KEY = "onboarding_save_failed"
    }
}
