package com.justnoise.gate0.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

class HmacZapStore(
    private val preferences: Gate0Preferences,
) {
    fun pair(transientCanonicalRecord: ByteArray): Boolean {
        if (transientCanonicalRecord.isEmpty()) return false
        val digest = computeDigest(transientCanonicalRecord)
        return try {
            preferences.setZapDigest(Base64.encodeToString(digest, Base64.NO_WRAP))
        } finally {
            digest.fill(0)
        }
    }

    fun isAuthorized(transientCanonicalRecord: ByteArray): Boolean {
        val encodedExpected = preferences.snapshot().zapDigest ?: return false
        val expected = try {
            Base64.decode(encodedExpected, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val candidate = computeDigest(transientCanonicalRecord)
        return try {
            MessageDigest.isEqual(expected, candidate)
        } finally {
            expected.fill(0)
            candidate.fill(0)
        }
    }

    fun clear(): Boolean {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
        return preferences.clearZapDigest()
    }

    private fun computeDigest(transientCanonicalRecord: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(loadOrCreateKey())
        return mac.doFinal(transientCanonicalRecord)
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(HMAC_ALGORITHM, ANDROID_KEY_STORE)
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(specification)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val HMAC_ALGORITHM = KeyProperties.KEY_ALGORITHM_HMAC_SHA256
        private const val KEY_ALIAS = "justnoise_gate0_zap_hmac_v1"
    }
}
