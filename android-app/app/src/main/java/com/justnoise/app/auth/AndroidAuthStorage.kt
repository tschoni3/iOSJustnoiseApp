package com.justnoise.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Production adapter; construction alone does not read/write credentials or change pairing. */
internal fun androidAuthJournal(context: Context): AuthSessionJournal = AuthSessionJournal(
    EncryptedAuthRecordStore(
        AtomicAuthBlobStorage(File(context.applicationContext.noBackupFilesDir, "auth-session-v1.bin")),
        AuthRecordCipher(AndroidAuthEncryptionKey(), context.packageName),
    ),
)

internal fun androidAuthFlowStore(context: Context): AuthFlowStore = EncryptedAuthFlowStore(
    AtomicAuthBlobStorage(File(context.applicationContext.noBackupFilesDir, "auth-flow-v1.bin")),
    AuthRecordCipher(AndroidAuthEncryptionKey(), context.packageName, AuthCipherPurpose.PENDING_FLOW),
)

private class AtomicAuthBlobStorage(file: File) : AuthBlobStorage {
    private val atomic = AtomicFile(file)

    override fun read(): ByteArray? = try {
        atomic.openRead().use { stream ->
            val buffer = ByteArray(AuthRecordCipher.MAX_BLOB_BYTES + 1)
            var count = 0
            while (count < buffer.size) {
                val read = stream.read(buffer, count, buffer.size - count)
                if (read < 0) break
                if (read == 0) throw AuthStorageException()
                count += read
            }
            check(count <= AuthRecordCipher.MAX_BLOB_BYTES)
            buffer.copyOf(count)
        }
    } catch (_: FileNotFoundException) {
        // Only genuine absence is a signed-out initialization, not unreadable state.
        if (atomic.baseFile.exists() || File(atomic.baseFile.path + ".bak").exists()) throw AuthStorageException()
        null
    }

    override fun write(bytes: ByteArray) {
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
        check(read()?.let { MessageDigest.isEqual(it, bytes) } == true)
    }
}

private class AndroidAuthEncryptionKey : AuthEncryptionKey {
    override fun existing(): SecretKey? = synchronized(KEY_LOCK) {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(ALIAS, null) as? SecretKey
    }

    override fun loadOrCreate(): SecretKey = synchronized(KEY_LOCK) {
        existing() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build())
            generateKey()
        }
    }

    companion object {
        private val KEY_LOCK = Any()
        private const val ALIAS = "justnoise_auth_session_aes_v1"
    }
}
