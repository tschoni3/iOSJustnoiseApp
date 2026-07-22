package com.justnoise.gate0.data

import android.content.Context
import android.content.SharedPreferences
import com.justnoise.gate0.domain.SessionStateWriter

data class StoredGate0State(
    val acceptedDisclosureVersion: String?,
    val acceptedDisclosureEpochMilliseconds: Long,
    val selectedPackage: String?,
    val zapDigest: String?,
    val desiredActive: Boolean,
    val activeSinceEpochMilliseconds: Long,
    val sessionRevision: Long,
    val recoveryRequired: Boolean,
    val serviceConnected: Boolean,
    val serviceHeartbeatElapsedMilliseconds: Long,
)

class Gate0Preferences(context: Context) : SessionStateWriter {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    fun snapshot(): StoredGate0State = StoredGate0State(
        acceptedDisclosureVersion = preferences.getString(KEY_DISCLOSURE_VERSION, null),
        acceptedDisclosureEpochMilliseconds = preferences.getLong(
            KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS,
            0L,
        ),
        selectedPackage = preferences.getString(KEY_SELECTED_PACKAGE, null),
        zapDigest = preferences.getString(KEY_ZAP_DIGEST, null),
        desiredActive = preferences.getBoolean(KEY_DESIRED_ACTIVE, false),
        activeSinceEpochMilliseconds = preferences.getLong(KEY_ACTIVE_SINCE_EPOCH_MILLIS, 0L),
        sessionRevision = preferences.getLong(KEY_SESSION_REVISION, 0L),
        recoveryRequired = preferences.getBoolean(KEY_RECOVERY_REQUIRED, false),
        serviceConnected = preferences.getBoolean(KEY_SERVICE_CONNECTED, false),
        serviceHeartbeatElapsedMilliseconds = preferences.getLong(
            KEY_SERVICE_HEARTBEAT_ELAPSED_MILLIS,
            0L,
        ),
    )

    fun acceptDisclosure(
        version: String,
        epochMilliseconds: Long = System.currentTimeMillis(),
    ): Boolean = preferences.edit()
        .putString(KEY_DISCLOSURE_VERSION, version)
        .putLong(KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS, epochMilliseconds)
        .commit()

    @Synchronized
    fun setSelectedPackage(packageName: String?): Boolean {
        val editor = preferences.edit()
            .putBoolean(KEY_DESIRED_ACTIVE, false)
            .remove(KEY_ACTIVE_SINCE_EPOCH_MILLIS)
            .remove(KEY_RECOVERY_REQUIRED)
            .putLong(KEY_SESSION_REVISION, nextSessionRevision())
        if (packageName.isNullOrBlank()) {
            editor.remove(KEY_SELECTED_PACKAGE)
        } else {
            editor.putString(KEY_SELECTED_PACKAGE, packageName)
        }
        return editor.commit()
    }

    fun setZapDigest(encodedDigest: String): Boolean = preferences.edit()
        .putString(KEY_ZAP_DIGEST, encodedDigest)
        .commit()

    fun clearZapDigest(): Boolean = preferences.edit()
        .remove(KEY_ZAP_DIGEST)
        .commit()

    @Synchronized
    override fun startSession(epochMilliseconds: Long): Boolean = preferences.edit()
        .putBoolean(KEY_DESIRED_ACTIVE, true)
        .putLong(KEY_ACTIVE_SINCE_EPOCH_MILLIS, epochMilliseconds)
        .putLong(KEY_SESSION_REVISION, nextSessionRevision())
        .remove(KEY_RECOVERY_REQUIRED)
        .commit()

    @Synchronized
    override fun stopSession(): Boolean = preferences.edit()
        .putBoolean(KEY_DESIRED_ACTIVE, false)
        .remove(KEY_ACTIVE_SINCE_EPOCH_MILLIS)
        .putLong(KEY_SESSION_REVISION, nextSessionRevision())
        .remove(KEY_RECOVERY_REQUIRED)
        .commit()

    override fun markRecoveryRequired() {
        // apply() updates the process-local map immediately even when a preceding durable
        // commit failed. Both UI and service must fail closed for the rest of this process.
        preferences.edit().putBoolean(KEY_RECOVERY_REQUIRED, true).apply()
    }

    fun updateServiceHeartbeat(elapsedMilliseconds: Long): Boolean = preferences.edit()
        .putBoolean(KEY_SERVICE_CONNECTED, true)
        .putLong(KEY_SERVICE_HEARTBEAT_ELAPSED_MILLIS, elapsedMilliseconds)
        .commit()

    fun markServiceDisconnected(): Boolean = preferences.edit()
        .remove(KEY_SERVICE_CONNECTED)
        .remove(KEY_SERVICE_HEARTBEAT_ELAPSED_MILLIS)
        .commit()

    fun clearForWithdrawal(): Boolean = preferences.edit().clear().commit()

    private fun nextSessionRevision(): Long =
        preferences.getLong(KEY_SESSION_REVISION, 0L) + 1L

    companion object {
        const val CURRENT_DISCLOSURE_VERSION = "gate0-accessibility-v1"

        private const val FILE_NAME = "gate0_local_state"
        private const val KEY_DISCLOSURE_VERSION = "accepted_disclosure_version"
        private const val KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS =
            "accepted_disclosure_epoch_millis"
        private const val KEY_SELECTED_PACKAGE = "selected_package"
        private const val KEY_ZAP_DIGEST = "zap_hmac_digest"
        private const val KEY_DESIRED_ACTIVE = "desired_active"
        private const val KEY_ACTIVE_SINCE_EPOCH_MILLIS = "active_since_epoch_millis"
        private const val KEY_SESSION_REVISION = "session_revision"
        private const val KEY_RECOVERY_REQUIRED = "recovery_required"
        private const val KEY_SERVICE_CONNECTED = "service_connected"
        private const val KEY_SERVICE_HEARTBEAT_ELAPSED_MILLIS =
            "service_heartbeat_elapsed_millis"
    }
}
