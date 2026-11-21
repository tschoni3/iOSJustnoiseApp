package com.justnoise.gate0.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import com.justnoise.gate0.R
import com.justnoise.gate0.data.SelectionEditResult
import com.justnoise.gate0.data.ZapPairingToken
import com.justnoise.gate0.domain.SessionTransition
import com.justnoise.gate0.domain.ZapReadOutcome
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.feature.Gate0Truth
import com.justnoise.gate0.feature.ScheduleRuntime
import com.justnoise.gate0.feature.AppSelectionChoice
import com.justnoise.gate0.feature.AppSelectionManagement
import com.justnoise.gate0.feature.AppSelectionSnapshot
import com.justnoise.gate0.platform.nfc.NdefReadFailure
import com.justnoise.gate0.platform.nfc.NdefReadResult
import com.justnoise.gate0.platform.nfc.NdefTagReader
import com.justnoise.gate0.platform.nfc.OneShotScanCoordinator
import com.justnoise.gate0.platform.nfc.ProcessDirectNfcMutationGate
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var controller: Gate0Controller
    private lateinit var appSelection: AppSelectionManagement
    private lateinit var statusText: TextView
    private lateinit var selectAppsButton: Button
    private lateinit var selectedAppsText: TextView
    private var appChoices = emptyList<AppSelectionChoice>()
    private var appPickerDialog: AlertDialog? = null
    private var appSelectionLoading = false
    private var appSelectionSaving = false
    private var pickerOpenRequested = false
    private var selectionGeneration = 0
    private var resumed = false
    private val selectionWorker = Executors.newSingleThreadExecutor()
    private lateinit var consentCheckbox: CheckBox
    private lateinit var accessibilitySettingsButton: Button
    private var nfcAdapter: NfcAdapter? = null
    private val scanCoordinator = OneShotScanCoordinator<ScanPurpose>()
    private var pairingConfirmation: AlertDialog? = null
    private var activePairingToken: ZapPairingToken? = null
    private val handler = Handler(Looper.getMainLooper())

    private val refreshStatus = object : Runnable {
        override fun run() {
            if (!scanCoordinator.isActive()) {
                renderTruth()
            }
            handler.postDelayed(this, STATUS_REFRESH_INTERVAL_MILLISECONDS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        controller = Gate0Controller(this)
        appSelection = AppSelectionManagement(applicationContext)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        statusText = findViewById(R.id.statusText)
        selectAppsButton = findViewById(R.id.selectAppsButton)
        selectedAppsText = findViewById(R.id.selectedAppsText)
        consentCheckbox = findViewById(R.id.consentCheckbox)
        accessibilitySettingsButton = findViewById(R.id.accessibilitySettingsButton)

        configureAppPicker()
        configureConsent()
        configureZapButtons()
        configureEscapeControls()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        ScheduleRuntime(this).onExplicitForeground()
        val accepted = controller.hasAcceptedCurrentDisclosure()
        consentCheckbox.isChecked = accepted
        consentCheckbox.isEnabled = !accepted
        accessibilitySettingsButton.isEnabled = consentCheckbox.isChecked
        loadApps()
        handler.removeCallbacks(refreshStatus)
        refreshStatus.run()
    }

    override fun onPause() {
        resumed = false
        selectionGeneration += 1
        pickerOpenRequested = false
        appSelectionLoading = false
        appPickerDialog?.dismiss()
        pairingConfirmation?.dismiss()
        cancelReaderMode()
        handler.removeCallbacks(refreshStatus)
        super.onPause()
    }

    override fun onDestroy() {
        selectionWorker.shutdown() // A user-confirmed in-flight Save may settle once; never replay it.
        super.onDestroy()
    }

    private fun onTagDiscovered(
        token: OneShotScanCoordinator.Token<ScanPurpose>,
        tag: Tag,
    ) {
        if (!scanCoordinator.claim(token)) return
        val result = NdefTagReader.readSingleStrictTextRecord(tag)
        runOnUiThread {
            completeOneShotScan(token, result)
        }
    }

    private fun completeOneShotScan(
        token: OneShotScanCoordinator.Token<ScanPurpose>,
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
        // Reserve this physical interaction before restoring normal NFC dispatch, then restore
        // normal behavior before applying any state mutation.
        disableReaderModeHardware()
        try {
            if (result is NdefReadResult.Rejected) {
                showScanDiagnosticStatus(
                    when (result.reason) {
                        NdefReadFailure.NDEF_UNAVAILABLE ->
                            getString(R.string.status_scan_not_ndef)

                        NdefReadFailure.EMPTY_NDEF_MESSAGE ->
                            getString(R.string.status_scan_empty_ndef)

                        NdefReadFailure.TAG_LOST ->
                            getString(R.string.status_scan_tag_lost)

                        NdefReadFailure.MALFORMED_NDEF ->
                            getString(R.string.status_scan_malformed_ndef)

                        NdefReadFailure.IO_FAILED ->
                            getString(R.string.status_scan_io_failed)

                        NdefReadFailure.SECURITY_DENIED ->
                            getString(R.string.status_scan_security_denied)

                        NdefReadFailure.READ_FAILED ->
                            getString(R.string.status_scan_read_failed)

                        NdefReadFailure.NO_TEXT_RECORD ->
                            getString(R.string.status_scan_no_text)

                        NdefReadFailure.INVALID_TEXT_RECORD ->
                            getString(R.string.status_scan_invalid_text)

                        NdefReadFailure.AMBIGUOUS_TEXT_RECORDS ->
                            getString(R.string.status_scan_ambiguous_text)
                    },
                )
                return
            }

            val acceptedResult = result as NdefReadResult.Accepted
            val transientCanonicalRecord = acceptedResult.transientCanonicalRecord
            when (token.purpose) {
                ScanPurpose.PAIR -> {
                    val pairingToken = activePairingToken
                    val paired = foregroundInteractionClaimed && pairingToken != null &&
                        controller.pairZap(
                            transientCanonicalRecord,
                            pairingToken,
                            transientUid = acceptedResult.transientUid,
                            recordCount = acceptedResult.recordCount,
                        )
                    showTransientStatus(
                        when {
                            !foregroundInteractionClaimed -> R.string.status_direct_duplicate_ignored
                            paired -> R.string.status_pair_success
                            else -> R.string.status_pair_not_saved
                        },
                    )
                }

                ScanPurpose.TOGGLE -> {
                    val interaction = if (foregroundInteractionClaimed) {
                        try {
                            controller.handleZap(
                                transientCanonicalRecord = transientCanonicalRecord,
                                acceptedReadAlreadyHandled = false,
                                transientUid = acceptedResult.transientUid,
                                recordCount = acceptedResult.recordCount,
                            )
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    val message = when {
                        !foregroundInteractionClaimed ->
                            R.string.status_direct_duplicate_ignored

                        interaction == null -> R.string.status_start_failed
                        interaction.accountRequired -> R.string.status_account_required
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
                    showTransientStatus(message)
                }
            }
        } finally {
            result.clearSensitiveData()
            scanCoordinator.finish(token)
            if (token.purpose == ScanPurpose.PAIR) activePairingToken = null
        }
    }

    private fun configureAppPicker() {
        selectAppsButton.setOnClickListener { showAppPicker() }
        findViewById<Button>(R.id.refreshAppsButton).setOnClickListener { loadApps() }
    }

    private fun showAppPicker() {
        if (controller.isSessionRequested()) {
            showTransientStatus(R.string.target_edit_requires_stop)
            return
        }
        if (appPickerDialog?.isShowing == true || appSelectionSaving) return
        pickerOpenRequested = true
        loadApps()
    }

    private fun openLoadedAppPicker(snapshot: AppSelectionSnapshot) {
        val edit = snapshot.edit ?: return
        if (edit.sessionRequested || controller.isSessionRequested()) {
            showTransientStatus(R.string.target_edit_requires_stop)
            return
        }
        val token = edit.token ?: run {
            showTransientStatus(R.string.target_selection_recovery)
            return
        }
        val choices = snapshot.choices
        val pendingSelection = edit.selectedPackages.toMutableSet()
        val checked = BooleanArray(choices.size) { choices[it].packageName in pendingSelection }
        appPickerDialog = AlertDialog.Builder(this)
            .setTitle(R.string.target_picker_title)
            .setMultiChoiceItems(choices.map(::choiceLabel).toTypedArray(), checked) { dialog, which, selected ->
                if (controller.isSessionRequested()) {
                    dialog.dismiss()
                    showTransientStatus(R.string.target_edit_requires_stop)
                } else if (selected && !choices[which].available) {
                    // A formerly selected app can be removed but never re-added as a protected target.
                    // AlertDialog also reuses this array when an offscreen row is rebound.
                    checked[which] = false
                    (dialog as AlertDialog).listView.setItemChecked(which, false)
                    pendingSelection.remove(choices[which].packageName)
                    Toast.makeText(this, R.string.target_removal_only, Toast.LENGTH_LONG).show()
                } else if (selected) {
                    pendingSelection.add(choices[which].packageName)
                } else {
                    pendingSelection.remove(choices[which].packageName)
                }
            }
            .setPositiveButton(R.string.target_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener { appPickerDialog = null }
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (appSelectionSaving) return@setOnClickListener
                    val draft = pendingSelection.toSet()
                    val generation = selectionGeneration
                    appSelectionSaving = true
                    dialog.listView.isEnabled = false
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                    dialog.setCancelable(false)
                    renderAppSelection()
                    selectionWorker.execute {
                        val result = appSelection.save(token, draft)
                        handler.post {
                            if (isDestroyed) return@post
                            appSelectionSaving = false
                            if (!resumed) return@post
                            if (generation != selectionGeneration || appPickerDialog !== dialog) {
                                loadApps()
                                return@post
                            }
                            dialog.setCancelable(true)
                            dialog.listView.isEnabled = true
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                            when (result) {
                                SelectionEditResult.SAVED, SelectionEditResult.UNCHANGED -> {
                                    dialog.dismiss()
                                    loadApps()
                                    renderTruth()
                                }
                                else -> {
                                    val message = selectionFailureMessage(result)
                                    showTransientStatus(message)
                                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                                    if (result == SelectionEditResult.CONFLICT) {
                                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                                            setText(R.string.target_reload)
                                            setOnClickListener { dialog.dismiss(); showAppPicker() }
                                        }
                                    } else if (result == SelectionEditResult.ACTIVE_SESSION ||
                                        result == SelectionEditResult.RECOVERY_REQUIRED
                                    ) dialog.dismiss()
                                    renderAppSelection()
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun configureConsent() {
        consentCheckbox.isChecked = controller.hasAcceptedCurrentDisclosure()
        consentCheckbox.isEnabled = !consentCheckbox.isChecked
        accessibilitySettingsButton.isEnabled = consentCheckbox.isChecked
        consentCheckbox.setOnCheckedChangeListener { _, checked ->
            accessibilitySettingsButton.isEnabled = checked
        }
        accessibilitySettingsButton.setOnClickListener {
            if (!consentCheckbox.isChecked) return@setOnClickListener
            if (controller.acceptDisclosure()) {
                consentCheckbox.isEnabled = false
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    private fun configureZapButtons() {
        findViewById<Button>(R.id.pairZapButton).setOnClickListener {
            requestZapPairing()
        }
        findViewById<Button>(R.id.scanZapButton).setOnClickListener {
            beginOneShotScan(ScanPurpose.TOGGLE)
        }
    }

    private fun requestZapPairing() {
        if (!resumed || pairingConfirmation?.isShowing == true) return
        cancelReaderMode()
        val intent = controller.prepareZapPairing()
        if (intent == null) {
            showTransientStatus(R.string.status_pair_unavailable)
            return
        }
        if (!intent.replacesExistingZap) {
            beginOneShotScan(ScanPurpose.PAIR, intent)
            return
        }
        // Freeze before asking: changes while the dialog or scan is open invalidate this intent.
        pairingConfirmation = AlertDialog.Builder(this)
            .setTitle(R.string.pair_replace_title)
            .setMessage(R.string.pair_replace_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.pair_replace_confirm) { _, _ ->
                if (resumed) beginOneShotScan(ScanPurpose.PAIR, intent)
            }
            .create().also { dialog ->
                dialog.setOnDismissListener { pairingConfirmation = null }
                dialog.show()
            }
    }

    private fun configureEscapeControls() {
        findViewById<Button>(R.id.appInfoButton).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
        findViewById<Button>(R.id.withdrawButton).setOnClickListener {
            cancelReaderMode()
            val cleared = controller.withdrawAndClear()
            selectionGeneration += 1
            appSelectionLoading = false
            pickerOpenRequested = false
            appChoices = emptyList()
            consentCheckbox.isChecked = false
            consentCheckbox.isEnabled = true
            accessibilitySettingsButton.isEnabled = false
            loadApps()
            showTransientStatus(
                getString(withdrawalStatusMessage(cleared)),
                durationMilliseconds = if (cleared) {
                    TRANSIENT_STATUS_MILLISECONDS
                } else {
                    WITHDRAWAL_FAILURE_STATUS_MILLISECONDS
                },
            )
        }
    }

    private fun loadApps() {
        if (!resumed || appSelectionLoading || appSelectionSaving || appPickerDialog?.isShowing == true) return
        val generation = ++selectionGeneration
        appSelectionLoading = true
        renderAppSelection()
        selectionWorker.execute {
            val snapshot = appSelection.load()
            handler.post {
                if (isDestroyed || !resumed || generation != selectionGeneration) return@post
                appSelectionLoading = false
                if (!snapshot.error) appChoices = snapshot.choices
                renderAppSelection()
                val openPicker = pickerOpenRequested
                pickerOpenRequested = false
                if (snapshot.error) showTransientStatus(R.string.target_load_failed)
                else if (openPicker) openLoadedAppPicker(snapshot)
            }
        }
    }

    private fun choiceLabel(choice: AppSelectionChoice): String =
        choice.label ?: getString(R.string.target_unavailable, choice.packageName)

    private fun renderAppSelection() {
        val sessionRequested = controller.isSessionRequested()
        val selectedPackages = controller.sessionPackages()
        val labels = appChoices.associateBy(AppSelectionChoice::packageName)
        val summary = if (selectedPackages.isEmpty()) {
            getString(R.string.target_placeholder)
        } else {
            resources.getQuantityString(
                R.plurals.selected_apps_count,
                selectedPackages.size,
                selectedPackages.size,
            ) + "\n" + selectedPackages.sorted().joinToString("\n") {
                labels[it]?.let(::choiceLabel) ?: getString(R.string.target_unavailable, it)
            }
        }
        selectAppsButton.isEnabled = !sessionRequested && !appSelectionLoading && !appSelectionSaving
        findViewById<Button>(R.id.refreshAppsButton).isEnabled = !appSelectionLoading && !appSelectionSaving
        val selectionText = if (sessionRequested) {
            summary + "\n\n" + getString(R.string.target_edit_requires_stop)
        } else {
            summary
        }
        if (selectedAppsText.text.toString() != selectionText) selectedAppsText.text = selectionText
    }

    private fun beginOneShotScan(purpose: ScanPurpose, pairingToken: ZapPairingToken? = null) {
        cancelReaderMode()
        if (purpose == ScanPurpose.PAIR && pairingToken == null) {
            showTransientStatus(R.string.status_pair_unavailable)
            return
        }
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled) {
            showTransientStatus(R.string.status_nfc_unavailable)
            return
        }
        val token = scanCoordinator.begin(purpose)
        activePairingToken = pairingToken
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
            activePairingToken = null
            showTransientStatus(R.string.status_nfc_unavailable)
            return
        }
        statusText.setText(
            if (purpose == ScanPurpose.PAIR) R.string.status_pairing else R.string.status_scanning,
        )
    }

    private fun cancelReaderMode() {
        scanCoordinator.cancel()
        activePairingToken = null
        disableReaderModeHardware()
    }

    private fun disableReaderModeHardware() {
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (_: Exception) {
            // The activity may already be paused or NFC may have been disabled.
        }
    }

    private fun renderTruth() {
        renderAppSelection()
        statusText.setText(
            when (controller.truth()) {
                Gate0Truth.CONSENT_REQUIRED -> R.string.status_consent_required
                Gate0Truth.TARGET_REQUIRED -> R.string.status_target_required
                Gate0Truth.ZAP_REQUIRED -> R.string.status_zap_required
                Gate0Truth.PERMISSION_REQUIRED -> R.string.status_permission_required
                Gate0Truth.IDLE -> R.string.status_idle
                Gate0Truth.ACTIVE -> R.string.status_active
                Gate0Truth.RECOVERY_REQUIRED -> R.string.status_recovery_required
            },
        )
    }

    private fun showTransientStatus(message: Int) {
        showTransientStatus(getString(message))
    }

    private fun showTransientStatus(
        message: CharSequence,
        durationMilliseconds: Long = TRANSIENT_STATUS_MILLISECONDS,
    ) {
        runOnUiThread {
            statusText.text = message
            handler.removeCallbacks(refreshStatus)
            handler.postDelayed(refreshStatus, durationMilliseconds)
        }
    }

    private fun showScanDiagnosticStatus(message: CharSequence) {
        showTransientStatus(message, SCAN_DIAGNOSTIC_STATUS_MILLISECONDS)
    }

    private fun NdefReadResult.clearSensitiveData() {
        if (this is NdefReadResult.Accepted) {
            clear()
        }
    }

    private enum class ScanPurpose {
        PAIR,
        TOGGLE,
    }

    companion object {
        private const val STATUS_REFRESH_INTERVAL_MILLISECONDS = 2_500L
        private const val TRANSIENT_STATUS_MILLISECONDS = 3_500L
        private const val SCAN_DIAGNOSTIC_STATUS_MILLISECONDS = 15_000L
        private const val WITHDRAWAL_FAILURE_STATUS_MILLISECONDS = 15_000L
    }
}

internal fun withdrawalStatusMessage(cleared: Boolean): Int = if (cleared) {
    R.string.status_withdrawn
} else {
    R.string.status_withdrawal_failed
}

internal fun selectionFailureMessage(result: SelectionEditResult): Int = when (result) {
    SelectionEditResult.ACTIVE_SESSION -> R.string.target_edit_requires_stop
    SelectionEditResult.CONFLICT -> R.string.target_selection_conflict
    SelectionEditResult.RECOVERY_REQUIRED -> R.string.target_selection_recovery
    SelectionEditResult.INVALID_SELECTION -> R.string.target_invalid_selection
    else -> R.string.target_save_failed
}
