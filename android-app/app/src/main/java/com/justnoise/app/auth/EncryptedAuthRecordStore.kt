package com.justnoise.app.auth

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface AuthBlobStorage {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

internal interface AuthEncryptionKey {
    fun existing(): SecretKey?
    fun loadOrCreate(): SecretKey
}

/** AES-GCM authenticates both stored credentials and the package/version binding. */
internal enum class AuthCipherPurpose(val context: String) { SESSION("auth-journal"), PENDING_FLOW("auth-flow") }

internal class AuthRecordCipher(private val key: AuthEncryptionKey, applicationId: String, purpose: AuthCipherPurpose = AuthCipherPurpose.SESSION) {
    private val associatedData = "Justnoise/${purpose.context}/v1/$applicationId".toByteArray(Charsets.UTF_8)

    fun encrypt(plain: ByteArray): ByteArray {
        require(plain.size <= MAX_BLOB_BYTES - 1 - IV_BYTES - TAG_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key.loadOrCreate())
        cipher.updateAAD(associatedData)
        check(cipher.iv.size == IV_BYTES)
        return byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(encoded: ByteArray): ByteArray {
        require(encoded.size in (1 + IV_BYTES + TAG_BYTES)..MAX_BLOB_BYTES && encoded[0] == VERSION)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Never replace a missing key while attempting to read existing credentials.
        cipher.init(Cipher.DECRYPT_MODE, key.existing() ?: throw AuthStorageException(),
            GCMParameterSpec(TAG_BYTES * 8, encoded.copyOfRange(1, 1 + IV_BYTES)))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(encoded, 1 + IV_BYTES, encoded.size - 1 - IV_BYTES)
    }

    companion object {
        const val MAX_BLOB_BYTES = 131_072
        private const val VERSION: Byte = 1
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
    }
}

internal class EncryptedAuthRecordStore(
    private val blobs: AuthBlobStorage,
    private val cipher: AuthRecordCipher,
) : AuthRecordStore {
    override fun read(): AuthRecord? = sanitized {
        val encoded = blobs.read() ?: return@sanitized null
        val plain = cipher.decrypt(encoded)
        try { AuthRecordCodec.decode(plain) } finally { plain.fill(0) }
    }

    override fun write(record: AuthRecord) = sanitized {
        val plain = AuthRecordCodec.encode(record)
        try { blobs.write(cipher.encrypt(plain)) } finally { plain.fill(0) }
    }

    private inline fun <T> sanitized(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        // Parser/provider errors must not put tokens or account contents into logs/UI.
        throw AuthStorageException()
    }
}

internal object AuthRecordCodec {
    fun encode(record: AuthRecord): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeUTF(record.generation)
            out.writeUTF(record.phase.name)
            out.writeBoolean(record.session != null)
            record.session?.let {
                out.writeUTF(it.accountId)
                out.writeUTF(it.email)
                out.writeUTF(it.accessToken)
                out.writeUTF(it.refreshToken)
                out.writeLong(it.expiresAtEpochSeconds)
            }
            out.writeBoolean(record.cleanupAccountId != null)
            record.cleanupAccountId?.let(out::writeUTF)
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): AuthRecord {
        require(bytes.size <= AuthRecordCipher.MAX_BLOB_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == 1)
            val generation = input.readUTF()
            val phase = AuthPhase.valueOf(input.readUTF())
            val session = if (input.readBoolean()) StoredAuthSession(
                input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readLong(),
            ) else null
            val cleanup = if (input.readBoolean()) input.readUTF() else null
            require(input.available() == 0)
            AuthRecord(generation, phase, session, cleanup)
        }
    }
}
