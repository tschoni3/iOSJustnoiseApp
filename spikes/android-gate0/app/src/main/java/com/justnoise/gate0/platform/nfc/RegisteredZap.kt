package com.justnoise.gate0.platform.nfc

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Android mirror of iOS `RegisteredZap.swift`: an issuer signature is the offline
 * registration for one physical chip UID. The AES key ships to readers; only the
 * separate factory signing key (never in this repository) can mint credentials.
 *
 * The UID is used only in memory for this check. It is never stored, logged or sent.
 */
class RegisteredZap(verificationKey: ByteArray, contentKey: ByteArray) {
    private val verificationKey = verificationKey.copyOf()
    private val contentKey = contentKey.copyOf()

    fun accepts(text: String, physicalUid: ByteArray): Boolean {
        if (physicalUid.size != UID_BYTES || contentKey.size != 32 || verificationKey.size != 32) {
            return false
        }
        if (text.toByteArray(Charsets.UTF_8).size > MAX_TEXT_UTF8_BYTES || !text.startsWith(PREFIX)) {
            return false
        }
        val envelope = strictBase64(text.substring(PREFIX.length)) ?: return false
        if (envelope.size != SIGNATURE_BYTES + SEALED_BYTES) return false

        val binding = BINDING_PREFIX + physicalUid
        val signature = envelope.copyOfRange(0, SIGNATURE_BYTES)
        val sealed = envelope.copyOfRange(SIGNATURE_BYTES, envelope.size)
        return try {
            val verifier = Ed25519Signer().apply {
                init(false, Ed25519PublicKeyParameters(verificationKey, 0))
                val signed = binding + sealed
                update(signed, 0, signed.size)
            }
            if (!verifier.verifySignature(signature)) return false
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(contentKey, "AES"),
                GCMParameterSpec(TAG_BYTES * 8, sealed, 0, NONCE_BYTES),
            )
            cipher.updateAAD(binding)
            val value = cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
            try { value.contentEquals(PRODUCT) } finally { value.fill(0) }
        } catch (_: Exception) {
            false
        } finally {
            binding.fill(0)
            envelope.fill(0)
            sealed.fill(0)
        }
    }

    companion object {
        const val PREFIX = "JNZ1."
        const val UID_BYTES = 7
        const val MAX_TEXT_UTF8_BYTES = 256
        private const val SIGNATURE_BYTES = 64
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private val PRODUCT = "justnoise.zap.activation.v1".toByteArray(Charsets.UTF_8)
        private val BINDING_PREFIX = "JustNoise/Zap/1".toByteArray(Charsets.UTF_8)
        private val SEALED_BYTES = NONCE_BYTES + PRODUCT.size + TAG_BYTES

        /** Production trust shipped with the app; must equal iOS `ZapTrust.json`. */
        val bundled: RegisteredZap by lazy {
            RegisteredZap(
                Base64.getDecoder().decode(ZapTrust.VERIFICATION_KEY),
                Base64.getDecoder().decode(ZapTrust.CONTENT_KEY),
            )
        }

        /** Matches Foundation `Data(base64Encoded:)`: standard alphabet, padding required. */
        internal fun strictBase64(value: String): ByteArray? {
            if (value.isEmpty() || value.length % 4 != 0) return null
            if (!value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }) {
                return null
            }
            val firstPad = value.indexOf('=')
            if (firstPad >= 0 && (firstPad < value.length - 2 || value.substring(firstPad).any { it != '=' })) {
                return null
            }
            return try { Base64.getDecoder().decode(value) } catch (_: IllegalArgumentException) { null }
        }
    }
}
