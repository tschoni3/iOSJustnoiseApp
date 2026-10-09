package com.justnoise.app.auth

import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class AuthPendingFlowTest {
    private val authStorage = FlowTestAuthStore()
    private val journal = AuthSessionJournal(authStorage)
    private val blobs = FlowTestBlobs()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val keys = object : AuthEncryptionKey {
        override fun existing() = key
        override fun loadOrCreate() = key
    }
    private val cipher = AuthRecordCipher(keys, "com.justnoise.app", AuthCipherPurpose.PENDING_FLOW)
    private val storage = EncryptedAuthFlowStore(blobs, cipher)
    private var now = 100_000L
    private val flows = AuthPendingFlow(journal, storage) { now }

    @Test fun encryptedFlowRetainsVerifierAcrossRestartWithoutPlaintextCredentials() = runBlocking {
        val handle = ready()
        assertFalse(blobs.bytes!!.toString(Charsets.UTF_8).contains(VERIFIER))
        assertFalse(blobs.bytes!!.toString(Charsets.UTF_8).contains(handle.id))
        val restarted = AuthPendingFlow(AuthSessionJournal(authStorage), EncryptedAuthFlowStore(blobs, cipher)) { now }
        assertEquals(VERIFIER, restarted.cache(handle).loadCodeVerifier())
        val claim = restarted.claimCallback(callback(handle))
        assertTrue(restarted.validateClaim(claim))
        assertEquals(CODE, claim.code)
        assertEquals(AuthFlowPurpose.RECOVERY, claim.purpose)
    }

    @Test fun flowAndSessionCiphertextsCannotBeSubstitutedForEachOther() = runBlocking {
        ready()
        val wrongCipher = AuthRecordCipher(keys, "com.justnoise.app")
        assertThrows(Exception::class.java) { wrongCipher.decrypt(blobs.bytes!!) }
        val wrongApp = AuthRecordCipher(keys, "com.justnoise.androiddev", AuthCipherPurpose.PENDING_FLOW)
        assertThrows(Exception::class.java) { wrongApp.decrypt(blobs.bytes!!) }
        Unit
    }

    @Test fun malformedOriginsDuplicatesAndTokenFragmentsCannotClaimFlow() = runBlocking {
        val handle = ready()
        val valid = callback(handle)
        val invalid = listOf(
            valid.replace("shop.justnoise.app:", "https:"),
            valid.replace("//auth/", "//auth.evil/"),
            valid.replace("//auth/", "//evil@auth/"),
            valid.replace("//auth/", "//auth:443/"),
            valid.replace("/callback?", "/callback/extra?"),
            valid.replace("/callback?", "/%63allback?"),
            "$valid&state=${handle.id}", "$valid&code=$CODE", "$valid&%63ode=$CODE",
            "$valid&access_token=private", "$valid#access_token=private",
            "$valid&error=denied", "$valid&unexpected=x",
            valid.replace(CODE, "invalid-code"),
            valid.replace("state=", "state=%00"),
        )
        invalid.forEach { raw ->
            assertThrows(AuthFlowException::class.java) { flows.claimCallback(raw) }
            assertNull(storage.read()!!.claimId)
        }
        assertTrue(flows.validateClaim(flows.claimCallback(valid)))
    }

    @Test fun finishingActivityTargetsLatestCallbackBeforeItsFirstRenderedFrame() = runBlocking {
        val old = ready()
        val latest = ready()
        assertEquals(latest.id, AuthPendingFlow.recoveryCleanupId(callback(latest), old.id))
        assertEquals(old.id, AuthPendingFlow.recoveryCleanupId(null, old.id))
        assertEquals(old.id, AuthPendingFlow.recoveryCleanupId(callback(latest).replace("//auth/", "//wrong/"), old.id))
        assertNull(AuthPendingFlow.recoveryCleanupId("invalid", "invalid"))
        assertNull(storage.read()!!.claimId) // Parsing has no authority to claim or mutate.
    }

    @Test fun replacedFlowRejectsOldLinksCachesAndCancellation() = runBlocking {
        val old = ready()
        val oldCache = flows.cache(old)
        val newer = ready()
        assertThrows(AuthFlowException::class.java) { flows.claimCallback(callback(old)) }
        assertFalse(flows.cancel(old.id))
        assertThrows(AuthFlowException::class.java) { runBlocking { oldCache.saveCodeVerifier(VERIFIER) } }
        assertTrue(flows.validateClaim(flows.claimCallback(callback(newer))))
    }

    @Test fun duplicateCallbackRejectedAndResumeInvalidatesOldWorkerClaim() = runBlocking {
        val handle = ready()
        val first = flows.claimCallback(callback(handle))
        assertThrows(AuthFlowException::class.java) { flows.claimCallback(callback(handle)) }
        val resumed = AuthPendingFlow(journal, storage) { now }.resumeClaim()
        assertFalse(flows.validateClaim(first))
        assertFalse(flows.consume(first))
        assertTrue(flows.validateClaim(resumed))
        assertTrue(flows.consume(resumed))
        assertNull(storage.read())
        assertFalse(flows.consume(resumed))
    }

    @Test fun clockRollbackAndExpiryRejectAndPurgeOldSecrets() = runBlocking {
        val handle = ready()
        now -= 1
        assertThrows(AuthFlowException::class.java) { flows.claimCallback(callback(handle)) }
        assertTrue(flows.purgeStale())
        assertNull(storage.read())
        val new = ready()
        now += AuthFlowRecord.LIFETIME_MILLIS
        assertThrows(AuthFlowException::class.java) { flows.claimCallback(callback(new)) }
        assertTrue(flows.purgeStale())
        assertNull(storage.read())
    }

    @Test fun changedAuthGenerationCannotRestoreOrExposeOldFlow() = runBlocking {
        val handle = ready()
        val claim = flows.claimCallback(callback(handle))
        assertTrue(journal.signOut(journal.snapshot().operationToken))
        assertFalse(flows.validateClaim(claim))
        assertThrows(AuthFlowException::class.java) { flows.resumeClaim() }
        assertTrue(flows.purgeStale())
        assertNull(storage.read())
    }

    @Test fun confirmedSignInCanConsumeMatchingFlowAfterAuthGenerationChanges() = runBlocking {
        val handle = ready()
        val claim = flows.claimCallback(callback(handle))
        assertTrue(journal.acceptSignIn(claim.authToken, StoredAuthSession(CODE, "synthetic@example.invalid", "access", "refresh", 100)))
        assertFalse(flows.validateClaim(claim))
        assertTrue(flows.consume(claim))
        assertNull(storage.read())
        assertThrows(AuthFlowException::class.java) { flows.begin(AuthFlowPurpose.GOOGLE) }
        Unit
    }

    @Test fun realGoogleSdkPersistsVerifierIntoEncryptedGenerationBoundFlow() = runBlocking {
        val handle = flows.begin(AuthFlowPurpose.GOOGLE)
        val provider = SupabaseAccountProvider { cache ->
            accountSupabaseClient(MockEngine { error("No network allowed") }, cache)
        }
        val url = provider.googleSignInUrl(flows.cache(handle), handle)
        assertTrue(url.startsWith("https://zusnyjctxdnnxghpbhki.supabase.co/auth/v1/authorize?"))
        val saved = storage.read()!!
        assertFalse(saved.verifier.isNullOrBlank())
        assertFalse(blobs.bytes!!.toString(Charsets.UTF_8).contains(saved.verifier!!))
        assertTrue(flows.validateClaim(flows.claimCallback(callback(handle))))
    }

    @Test fun failedWritesDoNotClaimOrConsumeSuccessfulState() = runBlocking {
        val handle = ready()
        blobs.failNext = true
        assertThrows(AuthStorageException::class.java) { flows.claimCallback(callback(handle)) }
        assertNull(storage.read()!!.claimId)
        val claim = flows.claimCallback(callback(handle))
        blobs.failNext = true
        assertThrows(AuthStorageException::class.java) { flows.consume(claim) }
        assertTrue(flows.validateClaim(claim))
        assertTrue(flows.consume(claim))
    }

    @Test fun providerCancellationRequiresMatchingStateAndDoesNotExposeErrorDescription() = runBlocking {
        val handle = ready()
        val badState = "${SupabaseAccountProvider.CALLBACK_URL}?state=$CODE&error=denied"
        assertThrows(AuthFlowException::class.java) { flows.claimCallback(badState) }
        assertNotNull(storage.read())
        val failure = assertThrows(AuthFlowException::class.java) {
            flows.claimCallback("${handle.callbackUrl}&error=denied&error_description=private-secret")
        }
        assertEquals(AuthFlowFailure.CANCELLED, failure.failure)
        assertFalse(failure.toString().contains("private-secret"))
        assertNull(storage.read())
    }

    @Test fun unknownVersionAndMissingFlowAreNotTreatedAsSignedOut() {
        listOf("{\"version\":999,\"flow\":null}", "{\"version\":1}").forEach {
            blobs.bytes = cipher.encrypt(it.toByteArray())
            assertThrows(AuthStorageException::class.java) { storage.read() }
        }
    }

    @Test fun reauthenticationFlowIsBoundToExistingAccountGeneration() = runBlocking {
        assertThrows(AuthFlowException::class.java) { flows.begin(AuthFlowPurpose.REAUTH_GOOGLE) }
        assertTrue(journal.acceptSignIn(journal.snapshot().operationToken,
            StoredAuthSession(CODE, "synthetic@example.invalid", "access", "refresh", 100)))
        val handle = flows.begin(AuthFlowPurpose.REAUTH_GOOGLE)
        flows.cache(handle).saveCodeVerifier(VERIFIER)
        val claim = flows.claimCallback(callback(handle))
        assertTrue(flows.validateClaim(claim))
        assertTrue(journal.signOut(journal.snapshot().operationToken))
        assertFalse(flows.validateClaim(claim))
        flows.purgeStale()
        assertNull(storage.read())
    }

    @Test fun deletionPendingMayReauthenticateButCannotUseSignedOutFlows() = runBlocking {
        assertTrue(journal.acceptSignIn(journal.snapshot().operationToken,
            StoredAuthSession(CODE, "synthetic@example.invalid", "access", "refresh", 100)))
        assertTrue(journal.beginDeletion(journal.snapshot().operationToken))
        assertThrows(AuthFlowException::class.java) { flows.begin(AuthFlowPurpose.RECOVERY) }
        val handle = flows.begin(AuthFlowPurpose.REAUTH_RECOVERY)
        flows.cache(handle).saveCodeVerifier(VERIFIER)
        assertTrue(flows.validateClaim(flows.claimCallback(callback(handle))))
        assertTrue(journal.acknowledgeRemoteDeletion(journal.snapshot().operationToken))
        flows.purgeStale()
        assertNull(storage.read())
    }

    @Test fun cacheCannotPretendToBeAnotherFlowPurpose() = runBlocking {
        val handle = ready()
        val wrong = flows.cache(AuthFlowHandle(handle.id, AuthFlowPurpose.GOOGLE))
        assertThrows(AuthFlowException::class.java) { runBlocking { wrong.loadCodeVerifier() } }
        Unit
    }

    private suspend fun ready(): AuthFlowHandle = flows.begin(AuthFlowPurpose.RECOVERY).also {
        flows.cache(it).saveCodeVerifier(VERIFIER)
    }
    private fun callback(handle: AuthFlowHandle) = "${handle.callbackUrl}&code=$CODE"
    companion object {
        private const val CODE = "11111111-1111-4111-8111-111111111111"
        private const val VERIFIER = "synthetic-verifier-abcdefghijklmnopqrstuvwxyz0123456789"
    }
}

private class FlowTestAuthStore : AuthRecordStore {
    private var record: AuthRecord? = null
    override fun read() = record
    override fun write(record: AuthRecord) { this.record = record }
}
private class FlowTestBlobs : AuthBlobStorage {
    var bytes: ByteArray? = null
    var failNext = false
    override fun read() = bytes?.copyOf()
    override fun write(bytes: ByteArray) {
        if (failNext) { failNext = false; error("Synthetic disk error") }
        this.bytes = bytes.copyOf()
    }
}
