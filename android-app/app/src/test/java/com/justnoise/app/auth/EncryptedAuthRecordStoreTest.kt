package com.justnoise.app.auth

import org.junit.Assert.*
import org.junit.Test
import com.justnoise.app.auth.AuthSessionJournalTest.Companion.ACCOUNT_A
import com.justnoise.app.auth.AuthSessionJournalTest.Companion.ACCOUNT_B
import com.justnoise.app.auth.AuthSessionJournalTest.Companion.session

class EncryptedAuthRecordStoreTest {
    private val blobs = MemoryAuthBlobs()
    private val key = MemoryAuthKey()
    private val cipher = AuthRecordCipher(key, "com.justnoise.app")
    private val store = EncryptedAuthRecordStore(blobs, cipher)
    private val record = AuthRecord(ACCOUNT_B, AuthPhase.SIGNED_IN, session(ACCOUNT_A))

    @Test fun encryptedRoundTripPreservesExactSessionWithoutPlaintextOnDisk() {
        store.write(record)
        val stored = blobs.bytes!!.toString(Charsets.ISO_8859_1)
        listOf(ACCOUNT_A, "synthetic@example.invalid", "synthetic-access", "synthetic-refresh").forEach {
            assertFalse(stored.contains(it))
        }
        val loaded = store.read()!!
        assertEquals(record.generation, loaded.generation)
        assertEquals(record.phase, loaded.phase)
        assertEquals(record.session!!.accountId, loaded.session!!.accountId)
        assertEquals(record.session.email, loaded.session.email)
        assertEquals(record.session.accessToken, loaded.session.accessToken)
        assertEquals(record.session.refreshToken, loaded.session.refreshToken)
        assertEquals(record.session.expiresAtEpochSeconds, loaded.session.expiresAtEpochSeconds)
    }

    @Test fun everyWriteHasFreshRandomNonce() {
        store.write(record)
        val first = blobs.bytes!!.copyOf()
        store.write(record)
        assertFalse(first.contentEquals(blobs.bytes!!))
        assertEquals(1, key.creations)
    }

    @Test fun changedCiphertextNonceOrFormatIsRejected() {
        store.write(record)
        val valid = blobs.bytes!!.copyOf()
        listOf(0, 1, 12, 13, valid.lastIndex).forEach { position ->
            blobs.bytes = valid.copyOf().also { it[position] = (it[position].toInt() xor 1).toByte() }
            assertThrows(AuthStorageException::class.java) { store.read() }
        }
    }

    @Test fun differentApplicationCannotDecryptCopiedRecord() {
        store.write(record)
        val other = EncryptedAuthRecordStore(blobs, AuthRecordCipher(key, "com.justnoise.androiddev"))
        assertThrows(AuthStorageException::class.java) { other.read() }
    }

    @Test fun missingKeyNeverCreatesReplacementOrErasesExistingRecord() {
        store.write(record)
        val before = blobs.bytes!!.copyOf()
        key.key = null
        assertThrows(AuthStorageException::class.java) { store.read() }
        assertEquals(1, key.creations)
        assertArrayEquals(before, blobs.bytes)
    }

    @Test fun unknownJournalVersionAndTrailingBytesAreRejected() {
        val plain = AuthRecordCodec.encode(record)
        blobs.bytes = cipher.encrypt(plain.copyOf().also { it[3] = 2 })
        assertThrows(AuthStorageException::class.java) { store.read() }
        blobs.bytes = cipher.encrypt(plain + byteArrayOf(0))
        assertThrows(AuthStorageException::class.java) { store.read() }
    }

    @Test fun oversizedAndTruncatedEnvelopesAreRejected() {
        listOf(ByteArray(0), ByteArray(28), ByteArray(AuthRecordCipher.MAX_BLOB_BYTES + 1)).forEach {
            blobs.bytes = it
            assertThrows(AuthStorageException::class.java) { store.read() }
        }
    }

    @Test fun storageErrorsAreSanitizedWithoutOriginalMessageOrCause() {
        val failing = object : AuthBlobStorage {
            override fun read(): ByteArray? = error("synthetic-access private@example.invalid")
            override fun write(bytes: ByteArray) = error("synthetic-refresh")
        }
        val secure = EncryptedAuthRecordStore(failing, cipher)
        val error = assertThrows(AuthStorageException::class.java) { secure.read() }
        assertEquals("Secure account storage is unavailable", error.message)
        assertNull(error.cause)
        val writeError = assertThrows(AuthStorageException::class.java) { secure.write(record) }
        assertNull(writeError.cause)
        assertFalse(writeError.toString().contains("synthetic-refresh"))
    }

    @Test fun invalidRecordShapesAndUnboundedCredentialsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { session("../../other-account") }
        assertThrows(IllegalArgumentException::class.java) { session("1-1-1-1-1") }
        assertThrows(IllegalArgumentException::class.java) { session(ACCOUNT_A, "token\nwith-newline") }
        assertThrows(IllegalArgumentException::class.java) { session(ACCOUNT_A, "x".repeat(16_385)) }
        assertThrows(IllegalArgumentException::class.java) { AuthRecord(ACCOUNT_B, AuthPhase.SIGNED_OUT, session(ACCOUNT_A)) }
        assertThrows(IllegalArgumentException::class.java) { AuthRecord(ACCOUNT_B, AuthPhase.DELETE_REMOTE_PENDING, session(ACCOUNT_A), ACCOUNT_B) }
        assertThrows(IllegalArgumentException::class.java) { AuthRecord(ACCOUNT_B, AuthPhase.DELETE_LOCAL_PENDING) }
    }
}
