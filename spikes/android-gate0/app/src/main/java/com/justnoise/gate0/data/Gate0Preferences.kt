package com.justnoise.gate0.data

import android.content.Context
import android.content.SharedPreferences
import com.justnoise.gate0.domain.SessionStateWriter

data class StoredGate0State(
    val acceptedDisclosureVersion: String?,
    val acceptedDisclosureEpochMilliseconds: Long,
    val selectedPackages: Set<String>,
    val activePackages: Set<String>,
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

    fun snapshot(): StoredGate0State = synchronized(STATE_LOCK) {
        val selectedPackages = readSelectedPackages()
        val desiredActive = preferences.getBoolean(KEY_DESIRED_ACTIVE, false)
        StoredGate0State(
            acceptedDisclosureVersion = preferences.getString(KEY_DISCLOSURE_VERSION, null),
            acceptedDisclosureEpochMilliseconds = preferences.getLong(
                KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS,
                0L,
            ),
            selectedPackages = selectedPackages,
            activePackages = if (preferences.contains(KEY_ACTIVE_PACKAGES)) {
                preferences.getStringSet(KEY_ACTIVE_PACKAGES, emptySet()).orEmpty().toSet()
            } else if (desiredActive && !preferences.contains(KEY_SELECTED_PACKAGES)) {
                // Upgrade an existing single-target session without discarding its intent.
                selectedPackages
            } else {
                emptySet()
            },
            zapDigest = preferences.getString(KEY_ZAP_DIGEST, null),
            desiredActive = desiredActive,
            activeSinceEpochMilliseconds = preferences.getLong(KEY_ACTIVE_SINCE_EPOCH_MILLIS, 0L),
            sessionRevision = preferences.getLong(KEY_SESSION_REVISION, 0L),
            recoveryRequired = preferences.getBoolean(KEY_RECOVERY_REQUIRED, false),
            serviceConnected = preferences.getBoolean(KEY_SERVICE_CONNECTED, false),
            serviceHeartbeatElapsedMilliseconds = preferences.getLong(
                KEY_SERVICE_HEARTBEAT_ELAPSED_MILLIS,
                0L,
            ),
        )
    }

    fun acceptDisclosure(
        version: String,
        epochMilliseconds: Long = System.currentTimeMillis(),
    ): Boolean = preferences.edit()
        .putString(KEY_DISCLOSURE_VERSION, version)
        .putLong(KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS, epochMilliseconds)
        .commit()

    fun setSelectedPackages(packageNames: Set<String>): Boolean = synchronized(STATE_LOCK) {
        if (packageNames.any(String::isBlank)) return false
        val selection = packageNames.toSet()
        if (readSelectedPackages() == selection) return true
        // A target edit must never end a session or alter its enforced snapshot.
        if (preferences.getBoolean(KEY_DESIRED_ACTIVE, false)) return false

        preferences.edit()
            .putStringSet(KEY_SELECTED_PACKAGES, selection)
            .remove(KEY_SELECTED_PACKAGE)
            .putLong(KEY_SESSION_REVISION, nextSessionRevision())
            .commit()
    }

    fun setZapDigest(encodedDigest: String): Boolean = preferences.edit()
        .putString(KEY_ZAP_DIGEST, encodedDigest)
        .commit()

    fun clearZapDigest(): Boolean = preferences.edit()
        .remove(KEY_ZAP_DIGEST)
        .commit()

    override fun startSession(epochMilliseconds: Long): Boolean = synchronized(STATE_LOCK) {
        val selection = readSelectedPackages()
        preferences.edit()
            .putStringSet(KEY_SELECTED_PACKAGES, selection)
            .remove(KEY_SELECTED_PACKAGE)
            .putStringSet(KEY_ACTIVE_PACKAGES, selection)
            .putBoolean(KEY_DESIRED_ACTIVE, true)
            .putLong(KEY_ACTIVE_SINCE_EPOCH_MILLIS, epochMilliseconds)
            .putLong(KEY_SESSION_REVISION, nextSessionRevision())
            .remove(KEY_RECOVERY_REQUIRED)
            .commit()
    }

    override fun stopSession(): Boolean = synchronized(STATE_LOCK) {
        preferences.edit()
            .putBoolean(KEY_DESIRED_ACTIVE, false)
            .remove(KEY_ACTIVE_PACKAGES)
            .remove(KEY_ACTIVE_SINCE_EPOCH_MILLIS)
            .putLong(KEY_SESSION_REVISION, nextSessionRevision())
            .remove(KEY_RECOVERY_REQUIRED)
            .commit()
    }

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

    private fun readSelectedPackages(): Set<String> =
        if (preferences.contains(KEY_SELECTED_PACKAGES)) {
            // getStringSet exposes the preferences' collection; never return or mutate it.
            preferences.getStringSet(KEY_SELECTED_PACKAGES, emptySet()).orEmpty().toSet()
        } else {
            setOfNotNull(preferences.getString(KEY_SELECTED_PACKAGE, null)?.takeIf(String::isNotBlank))
        }

    companion object {
        const val CURRENT_DISCLOSURE_VERSION = "gate0-accessibility-v2"

        private val STATE_LOCK = Any()

        private const val FILE_NAME = "gate0_local_state"
        private const val KEY_DISCLOSURE_VERSION = "accepted_disclosure_version"
        private const val KEY_DISCLOSURE_ACCEPTED_EPOCH_MILLIS =
            "accepted_disclosure_epoch_millis"
        private const val KEY_SELECTED_PACKAGE = "selected_package"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_ACTIVE_PACKAGES = "active_packages"
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
