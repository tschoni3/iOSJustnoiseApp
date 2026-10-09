package com.justnoise.app.auth

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AuthSessionJournalTest {
    private val blobs = MemoryAuthBlobs()
    private val keys = MemoryAuthKey()
    private val store = EncryptedAuthRecordStore(blobs, AuthRecordCipher(keys, "com.justnoise.app"))
    private val journal = AuthSessionJournal(store)

    @Test fun initialSignedOutGenerationIsDurableAcrossInstances() {
        val first = journal.snapshot()
        assertEquals(AuthPhase.SIGNED_OUT, first.phase)
        assertNull(first.accountId)
        assertEquals(first.operationToken, AuthSessionJournal(store).snapshot().operationToken)
        assertNotNull(blobs.bytes)
    }

    @Test fun onlyOneOfTwoConcurrentLoginResponsesCanCommit() {
        val token = journal.snapshot().operationToken
        assertTrue(journal.acceptSignIn(token, session(ACCOUNT_A)))
        assertFalse(AuthSessionJournal(store).acceptSignIn(token, session(ACCOUNT_B)))
        assertEquals(ACCOUNT_A, journal.snapshot().accountId)
    }

    @Test fun signOutRejectsLateRefreshAndRetainsNoCredentials() {
        signIn()
        val old = journal.snapshot().operationToken
        assertTrue(journal.signOut(old))
        assertFalse(journal.acceptRefresh(old, session(ACCOUNT_A)))
        assertFalse(journal.acceptSignIn(old, session(ACCOUNT_A)))
        assertNull(journal.credentials(old))
        assertNull(journal.credentials(journal.snapshot().operationToken))
        assertEquals(AuthPhase.SIGNED_OUT, AuthSessionJournal(store).snapshot().phase)
    }

    @Test fun explicitSignedOutCancellationAlsoInvalidatesPendingLogin() {
        val pending = journal.snapshot().operationToken
        assertTrue(journal.signOut(pending))
        assertFalse(journal.acceptSignIn(pending, session(ACCOUNT_A)))
    }

    @Test fun refreshMustKeepTheSameAccountAndRotateGeneration() {
        signIn()
        val before = journal.snapshot().operationToken
        assertFalse(journal.acceptRefresh(before, session(ACCOUNT_B)))
        assertTrue(journal.acceptRefresh(before, session(ACCOUNT_A, "new-access", "new-refresh")))
        val after = journal.snapshot().operationToken
        assertNotEquals(before, after)
        assertNull(journal.credentials(before))
        assertEquals("new-refresh", journal.credentials(after)?.refreshToken)
    }

    @Test fun accountAResponseCannotReplaceAccountBAfterSignOutAndSignIn() {
        signIn()
        val a = journal.snapshot().operationToken
        assertTrue(journal.signOut(a))
        assertTrue(journal.acceptSignIn(journal.snapshot().operationToken, session(ACCOUNT_B)))
        assertFalse(journal.acceptRefresh(a, session(ACCOUNT_A)))
        assertFalse(journal.acceptSignIn(a, session(ACCOUNT_A)))
        assertEquals(ACCOUNT_B, journal.snapshot().accountId)
    }

    @Test fun deletionSurvivesRestartAndCannotBeBypassedBySignOutOrLateLogin() {
        signIn()
        val signedIn = journal.snapshot().operationToken
        assertTrue(journal.beginDeletion(signedIn))
        val restarted = AuthSessionJournal(store)
        val remote = restarted.snapshot()
        assertEquals(AuthPhase.DELETE_REMOTE_PENDING, remote.phase)
        assertEquals(ACCOUNT_A, remote.accountId)
        assertNotNull(restarted.credentials(remote.operationToken))
        assertFalse(restarted.signOut(remote.operationToken))
        assertFalse(restarted.acceptSignIn(remote.operationToken, session(ACCOUNT_B)))
        assertFalse(restarted.completeLocalDeletion(remote.operationToken))
        assertFalse(restarted.acceptRefresh(signedIn, session(ACCOUNT_A)))

        assertTrue(restarted.acknowledgeRemoteDeletion(remote.operationToken))
        val local = AuthSessionJournal(store).snapshot()
        assertEquals(AuthPhase.DELETE_LOCAL_PENDING, local.phase)
        assertEquals(ACCOUNT_A, local.accountId)
        assertNull(local.email)
        assertNull(restarted.credentials(local.operationToken))
        assertFalse(restarted.signOut(local.operationToken))
        assertFalse(restarted.acceptRefresh(local.operationToken, session(ACCOUNT_A)))
        assertFalse(restarted.acceptSignIn(local.operationToken, session(ACCOUNT_B)))
        assertTrue(restarted.completeLocalDeletion(local.operationToken))
        assertEquals(AuthPhase.SIGNED_OUT, restarted.snapshot().phase)
        assertNull(restarted.snapshot().accountId)
        assertFalse(restarted.completeLocalDeletion(local.operationToken))
    }

    @Test fun deletionCredentialRefreshCannotLoseTheCleanupOwner() {
        signIn()
        journal.beginDeletion(journal.snapshot().operationToken)
        val pending = journal.snapshot().operationToken
        assertFalse(journal.acceptRefresh(pending, session(ACCOUNT_B)))
        assertTrue(journal.acceptRefresh(pending, session(ACCOUNT_A, "rotated", "rotated-refresh")))
        assertEquals(AuthPhase.DELETE_REMOTE_PENDING, journal.snapshot().phase)
        assertEquals(ACCOUNT_A, journal.snapshot().accountId)
        assertFalse(journal.acknowledgeRemoteDeletion(pending))
    }

    @Test fun failedWriteCannotPublishASignedInState() {
        val token = journal.snapshot().operationToken
        blobs.failWrite = true
        assertThrows(AuthStorageException::class.java) { journal.acceptSignIn(token, session(ACCOUNT_A)) }
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertEquals(token, journal.snapshot().operationToken)
        blobs.failWrite = false
        assertTrue(journal.acceptSignIn(token, session(ACCOUNT_A)))
    }

    @Test fun failedSignOutAndCleanupWritesRemainRetryableInsteadOfClaimingSuccess() {
        signIn()
        val before = journal.snapshot().operationToken
        blobs.failWrite = true
        assertThrows(AuthStorageException::class.java) { journal.signOut(before) }
        assertEquals(AuthPhase.SIGNED_IN, journal.snapshot().phase)
        blobs.failWrite = false
        assertTrue(journal.beginDeletion(before))
        assertTrue(journal.acknowledgeRemoteDeletion(journal.snapshot().operationToken))
        val cleanup = journal.snapshot().operationToken
        blobs.failWrite = true
        assertThrows(AuthStorageException::class.java) { journal.completeLocalDeletion(cleanup) }
        assertEquals(AuthPhase.DELETE_LOCAL_PENDING, AuthSessionJournal(store).snapshot().phase)
        assertEquals(ACCOUNT_A, journal.snapshot().accountId)
    }

    @Test fun corruptedExistingStateIsNotReinitializedOrDeleted() {
        signIn()
        blobs.bytes!![blobs.bytes!!.lastIndex] = (blobs.bytes!!.last() + 1).toByte()
        val before = blobs.bytes!!.copyOf()
        assertThrows(AuthStorageException::class.java) { AuthSessionJournal(store).snapshot() }
        assertArrayEquals(before, blobs.bytes)
    }

    @Test fun diagnosticRepresentationsNeverContainIdentityOrCredentials() {
        val session = session(ACCOUNT_A)
        val record = AuthRecord(ACCOUNT_B, AuthPhase.SIGNED_IN, session)
        val diagnostic = "$session $record ${AuthSnapshot(record)}"
        listOf(ACCOUNT_A, "synthetic@example.invalid", "synthetic-access", "synthetic-refresh").forEach {
            assertFalse(diagnostic.contains(it))
        }
    }

    private fun signIn() { assertTrue(journal.acceptSignIn(journal.snapshot().operationToken, session(ACCOUNT_A))) }

    companion object {
        const val ACCOUNT_A = "11111111-1111-4111-8111-111111111111"
        const val ACCOUNT_B = "22222222-2222-4222-8222-222222222222"
        internal fun session(id: String, access: String = "synthetic-access", refresh: String = "synthetic-refresh") =
            StoredAuthSession(id, "synthetic@example.invalid", access, refresh, 10_000L)
    }
}

internal class MemoryAuthBlobs : AuthBlobStorage {
    var bytes: ByteArray? = null
    var failWrite = false
    override fun read() = bytes?.copyOf()
    override fun write(bytes: ByteArray) {
        if (failWrite) error("Synthetic disk failure")
        this.bytes = bytes.copyOf()
    }
}

internal class MemoryAuthKey : AuthEncryptionKey {
    var key: SecretKey? = null
    var creations = 0
    override fun existing() = key
    override fun loadOrCreate(): SecretKey = key ?: KeyGenerator.getInstance("AES").run {
        init(256)
        generateKey().also { key = it; creations++ }
    }
}
