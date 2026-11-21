package com.justnoise.gate0.platform.nfc

/**
 * Public Zap trust, identical to `justnoiseradio/Features/NFC/ZapTrust.json`.
 * Both values are meant to ship in apps: the Ed25519 verification key and the
 * AES-GCM content key. `scripts/verify-product-contracts.sh` rejects drift.
 */
object ZapTrust {
    const val VERIFICATION_KEY = "03jBKF+i4HsIb1yu4HKT7uSQ1gaZWX/ous24APaHXpY="
    const val CONTENT_KEY = "pgj2eC/qvO/3QCeRfauA+yh++yoPRONNk5WsWg05Rcw="
}
