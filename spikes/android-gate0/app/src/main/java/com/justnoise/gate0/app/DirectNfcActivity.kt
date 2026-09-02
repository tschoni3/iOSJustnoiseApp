package com.justnoise.gate0.app

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import com.justnoise.gate0.R
import com.justnoise.gate0.domain.SessionTransition
import com.justnoise.gate0.domain.ZapReadOutcome
import com.justnoise.gate0.feature.Gate0Controller
import com.justnoise.gate0.feature.Gate0Truth
import com.justnoise.gate0.platform.nfc.DirectNfcDispatchRules
import com.justnoise.gate0.platform.nfc.ProcessDirectNfcMutationGate
import com.justnoise.gate0.platform.nfc.NdefReadFailure
import com.justnoise.gate0.platform.nfc.NdefReadResult
import com.justnoise.gate0.platform.nfc.NdefTagReader
import com.justnoise.gate0.platform.nfc.OneShotScanCoordinator
import java.util.concurrent.Executors

/**
 * Handles an unlocked Android NDEF dispatch without trusting the message copied into the Intent.
 * A usable live Tag is re-read before an authorized session mutation can occur.
 */
class DirectNfcActivity : Activity() {
    private val coordinator = OneShotScanCoordinator<Unit>()
    private val readerExecutor = Executors.newSingleThreadExecutor()
    private lateinit var controller: Gate0Controller
    private var intentConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = Gate0Controller(this)
        consumeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!intentConsumed) consumeIntent(intent)
    }

    override fun onStop() {
        coordinator.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        coordinator.cancel()
        readerExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun consumeIntent(candidate: Intent) {
        intentConsumed = true
        val liveTag = try {
            candidate.liveNfcTag()
        } catch (_: RuntimeException) {
            null
        }
        if (!DirectNfcDispatchRules.accepts(candidate.action, candidate.type, liveTag != null)) {
            finish()
            return
        }

        val token = coordinator.begin(Unit)
        readerExecutor.execute {
            if (!coordinator.claim(token)) return@execute
            val result = NdefTagReader.readSingleStrictTextRecord(requireNotNull(liveTag))
            if (result is NdefReadResult.Accepted) awaitFreshServiceHandshake(token)
            runOnUiThread { complete(token, result) }
        }
    }

    private fun awaitFreshServiceHandshake(token: OneShotScanCoordinator.Token<Unit>) {
        if (controller.truth() != Gate0Truth.RECOVERY_REQUIRED) return
        val deadline = SystemClock.elapsedRealtime() + SERVICE_HANDSHAKE_TIMEOUT_MILLISECONDS
        while (
            coordinator.isClaimActive(token) &&
            controller.truth() == Gate0Truth.RECOVERY_REQUIRED &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            SystemClock.sleep(SERVICE_HANDSHAKE_POLL_MILLISECONDS)
        }
    }

    private fun complete(
        token: OneShotScanCoordinator.Token<Unit>,
        result: NdefReadResult,
    ) {
        if (!coordinator.isClaimActive(token)) {
            result.clearSensitiveData()
            return
        }

        try {
            if (result is NdefReadResult.Rejected) {
                finishWithStatus(result.statusResource())
                return
            }

            val transientCanonicalRecord =
                (result as NdefReadResult.Accepted).transientCanonicalRecord
            if (!ProcessDirectNfcMutationGate.tryClaim(SystemClock.elapsedRealtime())) {
                finishWithStatus(R.string.status_direct_duplicate_ignored)
                return
            }
            val interaction = try {
                controller.handleZap(
                    transientCanonicalRecord = transientCanonicalRecord,
                    acceptedReadAlreadyHandled = false,
                )
            } catch (_: Exception) {
                null
            }
            val status = when {
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
            finishWithStatus(status)
        } finally {
            result.clearSensitiveData()
            coordinator.finish(token)
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

    private fun Intent.liveNfcTag(): Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }

    private fun finishWithStatus(messageResource: Int) {
        Toast.makeText(applicationContext, messageResource, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun NdefReadResult.clearSensitiveData() {
        if (this is NdefReadResult.Accepted) transientCanonicalRecord.fill(0)
    }

    companion object {
        private const val SERVICE_HANDSHAKE_TIMEOUT_MILLISECONDS = 1_500L
        private const val SERVICE_HANDSHAKE_POLL_MILLISECONDS = 50L
    }
}
