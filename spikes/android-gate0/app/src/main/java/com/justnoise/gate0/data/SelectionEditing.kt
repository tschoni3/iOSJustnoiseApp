package com.justnoise.gate0.data

/**
 * Transient compare-and-set token. It deliberately is not Parcelable/Serializable or persistent:
 * a restored editor must re-read current state, never replay a prior process's pending save.
 */
class SelectionEditToken internal constructor(
    internal val processNonce: String,
    internal val consentGeneration: Long,
    internal val sessionRevision: Long,
    internal val consentVersion: String?,
    internal val consentEpochMilliseconds: Long,
) {
    override fun equals(other: Any?): Boolean = other is SelectionEditToken &&
        processNonce == other.processNonce && consentGeneration == other.consentGeneration &&
        sessionRevision == other.sessionRevision && consentVersion == other.consentVersion &&
        consentEpochMilliseconds == other.consentEpochMilliseconds

    override fun hashCode(): Int {
        var result = processNonce.hashCode()
        result = 31 * result + consentGeneration.hashCode()
        result = 31 * result + sessionRevision.hashCode()
        result = 31 * result + (consentVersion?.hashCode() ?: 0)
        return 31 * result + consentEpochMilliseconds.hashCode()
    }

    override fun toString(): String = "SelectionEditToken(opaque)"
}

data class SelectionEditSnapshot(
    val selectedPackages: Set<String> = emptySet(),
    val sessionRequested: Boolean = false,
    val recoveryRequired: Boolean = false,
    val error: Boolean = false,
    val token: SelectionEditToken? = null,
)

enum class SelectionEditResult {
    SAVED,
    UNCHANGED,
    ACTIVE_SESSION,
    CONFLICT,
    RECOVERY_REQUIRED,
    INVALID_SELECTION,
    STORAGE_FAILED,
}
