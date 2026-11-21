package com.justnoise.gate0.platform.nfc

import java.security.MessageDigest

/** Shipped iOS compatibility labels, not cryptographic proof of genuine hardware. */
object ZapCompatibilityLabels {
    private val recognizedDigests = setOf(
        "6ae5313f83a60a156c78ee7dea6251759ee552d82055ba990b0983a2a9d77dec",
        "51639b6095e35be9471aeafe5e6d9c6668a5fcc10351ebe2efbb3e8b7057d72d",
        "8fad2d4ae45e105490d4f5d06c5475e9c1ab76007233edce3a353506a83e69b8",
    )

    fun isRecognized(canonicalRecord: ByteArray): Boolean {
        if (canonicalRecord.size < 4) return false
        val payload = canonicalRecord.copyOfRange(2, canonicalRecord.size)
        val validated = NdefTextPayloadParser.parseCanonicalStrictUtf8Record(
            canonicalRecord[0].toInt(), byteArrayOf(canonicalRecord[1]), payload,
        )
        try {
            if (validated == null) return false
            val start = 1 + (payload[0].toInt() and 0x3F)
            val digest = MessageDigest.getInstance("SHA-256").apply {
                update(payload, start, payload.size - start)
            }.digest()
            return try {
                digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) } in recognizedDigests
            } finally { digest.fill(0) }
        } finally {
            payload.fill(0)
            validated?.fill(0)
        }
    }

    fun allowsSessionRead(recognized: Boolean, paired: Boolean, sessionRequested: Boolean): Boolean =
        paired && (recognized || sessionRequested)
}
