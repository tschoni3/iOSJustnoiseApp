package com.justnoise.gate0.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import com.justnoise.gate0.R
import com.justnoise.gate0.domain.SessionTransition
import com.justnoise.gate0.domain.ZapReadOutcome
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.feature.Gate0Truth
import com.justnoise.gate0.platform.discovery.LauncherAppRepository
import com.justnoise.gate0.platform.nfc.NdefTagReader
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private lateinit var controller: Gate0Controller
    private lateinit var appRepository: LauncherAppRepository
    private lateinit var statusText: TextView
    private lateinit var appSpinner: Spinner
    private lateinit var consentCheckbox: CheckBox
    private lateinit var accessibilitySettingsButton: Button
    private var nfcAdapter: NfcAdapter? = null
    private var scanPurpose: ScanPurpose? = null
    private val scanHandled = AtomicBoolean(false)
    private var bindingSpinner = false
    private val handler = Handler(Looper.getMainLooper())

    private val refreshStatus = object : Runnable {
        override fun run() {
            renderTruth()
            handler.postDelayed(this, STATUS_REFRESH_INTERVAL_MILLISECONDS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        controller = Gate0Controller(this)
        appRepository = LauncherAppRepository(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        statusText = findViewById(R.id.statusText)
        appSpinner = findViewById(R.id.appSpinner)
        consentCheckbox = findViewById(R.id.consentCheckbox)
        accessibilitySettingsButton = findViewById(R.id.accessibilitySettingsButton)

        configureAppPicker()
        configureConsent()
        configureZapButtons()
        configureEscapeControls()
        loadApps()
        renderTruth()
    }

    override fun onResume() {
        super.onResume()
        val accepted = controller.hasAcceptedCurrentDisclosure()
        consentCheckbox.isChecked = accepted
        consentCheckbox.isEnabled = !accepted
        accessibilitySettingsButton.isEnabled = consentCheckbox.isChecked
        handler.removeCallbacks(refreshStatus)
        refreshStatus.run()
    }

    override fun onPause() {
        cancelReaderMode()
        handler.removeCallbacks(refreshStatus)
        super.onPause()
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!scanHandled.compareAndSet(false, true)) return
        val purpose = scanPurpose ?: return
        runOnUiThread { disableReaderModeOnly() }

        val transientCanonicalRecord = NdefTagReader.readSingleStrictTextRecord(tag)
        if (transientCanonicalRecord == null) {
            showTransientStatus(R.string.status_scan_invalid)
            return
        }

        try {
            when (purpose) {
                ScanPurpose.PAIR -> {
                    val paired = try {
                        controller.pairZap(transientCanonicalRecord)
                    } catch (_: Exception) {
                        false
                    }
                    showTransientStatus(
                        if (paired) R.string.status_pair_success else R.string.status_scan_invalid,
                    )
                }

                ScanPurpose.TOGGLE -> {
                    val interaction = try {
                        controller.handleZap(
                            transientCanonicalRecord = transientCanonicalRecord,
                            acceptedReadAlreadyHandled = false,
                        )
                    } catch (_: Exception) {
                        null
                    }
                    val message = when {
                        interaction == null -> R.string.status_start_failed
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
            transientCanonicalRecord.fill(0)
        }
    }

    private fun configureAppPicker() {
        appSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (bindingSpinner) return
                val choice = parent?.getItemAtPosition(position) as? AppChoice ?: return
                choice.packageName?.let { packageName ->
                    if (!controller.selectTarget(packageName)) {
                        showTransientStatus(R.string.status_target_required)
                    } else {
                        renderTruth()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        findViewById<Button>(R.id.refreshAppsButton).setOnClickListener { loadApps() }
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
            beginOneShotScan(ScanPurpose.PAIR)
        }
        findViewById<Button>(R.id.scanZapButton).setOnClickListener {
            beginOneShotScan(ScanPurpose.TOGGLE)
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
            controller.withdrawAndClear()
            consentCheckbox.isChecked = false
            consentCheckbox.isEnabled = true
            accessibilitySettingsButton.isEnabled = false
            loadApps()
            showTransientStatus(R.string.status_withdrawn)
        }
    }

    private fun loadApps() {
        val selectedPackage = controller.selectedPackage()
        val choices = buildList {
            add(AppChoice(getString(R.string.target_placeholder), null))
            addAll(appRepository.load().map { AppChoice(it.label, it.packageName) })
        }
        bindingSpinner = true
        appSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            choices,
        )
        val selectedIndex = choices.indexOfFirst { it.packageName == selectedPackage }
        appSpinner.setSelection(selectedIndex.coerceAtLeast(0), false)
        bindingSpinner = false
    }

    private fun beginOneShotScan(purpose: ScanPurpose) {
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled) {
            showTransientStatus(R.string.status_nfc_unavailable)
            return
        }
        scanPurpose = purpose
        scanHandled.set(false)
        adapter.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V,
            Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            },
        )
        statusText.setText(
            if (purpose == ScanPurpose.PAIR) R.string.status_pairing else R.string.status_scanning,
        )
    }

    private fun cancelReaderMode() {
        disableReaderModeOnly()
        scanHandled.set(false)
    }

    private fun disableReaderModeOnly() {
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (_: Exception) {
            // The activity may already be paused or NFC may have been disabled.
        }
        scanPurpose = null
    }

    private fun renderTruth() {
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
        runOnUiThread {
            statusText.setText(message)
            handler.removeCallbacks(refreshStatus)
            handler.postDelayed(refreshStatus, TRANSIENT_STATUS_MILLISECONDS)
        }
    }

    private data class AppChoice(
        val label: String,
        val packageName: String?,
    ) {
        override fun toString(): String = label
    }

    private enum class ScanPurpose {
        PAIR,
        TOGGLE,
    }

    companion object {
        private const val STATUS_REFRESH_INTERVAL_MILLISECONDS = 2_500L
        private const val TRANSIENT_STATUS_MILLISECONDS = 2_000L
    }
}
