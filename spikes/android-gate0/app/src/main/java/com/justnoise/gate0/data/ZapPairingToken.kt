package com.justnoise.gate0.data

/**
 * Short-lived authority for one user-confirmed pairing scan. Never persist or serialize this
 * token: a recreated setup screen must ask again against current session and consent state.
 */
class ZapPairingToken internal constructor(
    internal val stateToken: SelectionEditToken,
    internal val pairingGeneration: Long,
    internal val expectedDigest: String?,
    val replacesExistingZap: Boolean,
) {
    override fun toString(): String = "ZapPairingToken(opaque)"
}
