package com.justnoise.app.auth

import io.github.jan.supabase.auth.CodeVerifierCache
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

internal enum class AuthFlowPurpose {
    RECOVERY, GOOGLE, REGISTRATION, REAUTH_RECOVERY, REAUTH_GOOGLE;
    val recovery get() = this == RECOVERY || this == REAUTH_RECOVERY
    val reauthentication get() = this == REAUTH_RECOVERY || this == REAUTH_GOOGLE
    fun allows(phase: AuthPhase) = if (reauthentication)
        phase == AuthPhase.SIGNED_IN || phase == AuthPhase.DELETE_REMOTE_PENDING else phase == AuthPhase.SIGNED_OUT
}
internal enum class AuthFlowFailure { NO_PENDING_FLOW, EXPIRED, STALE, INVALID_CALLBACK, ALREADY_CLAIMED, CANCELLED }
internal class AuthFlowException(val failure: AuthFlowFailure) : Exception("Account link unavailable: $failure")

internal data class AuthFlowRecord(
    val id: String,
    val authGeneration: String,
    val purpose: AuthFlowPurpose,
    val createdAt: Long,
    val expiresAt: Long,
    val verifier: String? = null,
    val code: String? = null,
    val claimId: String? = null,
) {
    init {
        require(canonicalAccountId(id) && canonicalAccountId(authGeneration))
        require(createdAt >= 0 && expiresAt > createdAt && expiresAt - createdAt == LIFETIME_MILLIS)
        // The pinned SDK emits 64 random bytes as padded base64url (86 chars + ==).
        // Preserve its exact bytes: trimming padding would change the S256 challenge.
        require(verifier == null || verifier.matches(Regex("[A-Za-z0-9._~-]{43,128}")) ||
            verifier.matches(Regex("[A-Za-z0-9_-]{86}==")))
        require((code == null && claimId == null) ||
            (code != null && canonicalAccountId(code) && claimId != null && canonicalAccountId(claimId) && verifier != null))
    }
    override fun toString() = "AuthFlowRecord($purpose, [redacted])"
    companion object { const val LIFETIME_MILLIS = 30 * 60 * 1_000L }
}

internal class AuthFlowHandle(val id: String, val purpose: AuthFlowPurpose) {
    init { require(canonicalAccountId(id)) }
    val callbackUrl: String get() = "${SupabaseAccountProvider.CALLBACK_URL}?state=$id"
    override fun toString() = "AuthFlowHandle($purpose, [redacted])"
}

internal class AuthFlowClaim(val flowId: String, val claimId: String, val code: String, val authToken: AuthOperationToken, val purpose: AuthFlowPurpose) {
    override fun toString() = "AuthFlowClaim($purpose, [redacted])"
}

internal interface AuthFlowStore {
    fun read(): AuthFlowRecord?
    /** Null is a durable encrypted tombstone, not a failed/unconfirmed file removal. */
    fun write(record: AuthFlowRecord?)
}

/** Worker-only. Tokens/URI contents are never logged or passed to analytics. */
internal class AuthPendingFlow(
    private val journal: AuthSessionJournal,
    private val store: AuthFlowStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun begin(purpose: AuthFlowPurpose): AuthFlowHandle = locked {
        val auth = journal.snapshot()
        if (!purpose.allows(auth.phase)) fail(AuthFlowFailure.STALE)
        val created = now()
        val record = AuthFlowRecord(UUID.randomUUID().toString(), auth.operationToken.generation, purpose,
            created, Math.addExact(created, AuthFlowRecord.LIFETIME_MILLIS))
        store.write(record) // Replaces any older flow; its callback/cache can no longer mutate this one.
        AuthFlowHandle(record.id, purpose)
    }

    fun cache(handle: AuthFlowHandle): CodeVerifierCache = object : CodeVerifierCache {
        override suspend fun saveCodeVerifier(codeVerifier: String) = locked {
            val record = live(handle.id)
            if (record.purpose != handle.purpose) fail(AuthFlowFailure.STALE)
            if (record.claimId != null || record.verifier != null) fail(AuthFlowFailure.STALE)
            store.write(record.copy(verifier = codeVerifier))
        }
        override suspend fun loadCodeVerifier(): String? = locked {
            val record = live(handle.id)
            if (record.purpose != handle.purpose) fail(AuthFlowFailure.STALE)
            record.verifier
        }
        override suspend fun deleteCodeVerifier() { cancel(handle.id) }
    }

    fun claimCallback(rawUri: String): AuthFlowClaim = locked {
        val fields = callbackFields(rawUri)
        val record = live(fields["state"] ?: fail(AuthFlowFailure.INVALID_CALLBACK))
        if (record.claimId != null) fail(AuthFlowFailure.ALREADY_CLAIMED)
        if ("error" in fields) {
            store.write(null)
            fail(AuthFlowFailure.CANCELLED) // Never display remote error_description.
        }
        val code = fields["code"]?.takeIf(::canonicalAccountId) ?: fail(AuthFlowFailure.INVALID_CALLBACK)
        if (record.verifier == null) fail(AuthFlowFailure.NO_PENDING_FLOW)
        val claimed = record.copy(code = code, claimId = UUID.randomUUID().toString())
        store.write(claimed)
        claimed.claim()
    }

    /** Explicit retry after process loss. Rotating the claim rejects a previous worker result.
     * If the server already consumed the code before a lost response, request a new link. */
    fun resumeClaim(): AuthFlowClaim = locked {
        val current = store.read() ?: fail(AuthFlowFailure.NO_PENDING_FLOW)
        val record = live(current.id)
        if (record.code == null) fail(AuthFlowFailure.NO_PENDING_FLOW)
        val resumed = record.copy(claimId = UUID.randomUUID().toString())
        store.write(resumed)
        resumed.claim()
    }

    fun validateClaim(claim: AuthFlowClaim): Boolean = locked {
        runCatching {
            val current = live(claim.flowId)
            current.claimId == claim.claimId && current.code == claim.code &&
                current.authGeneration == claim.authToken.generation && current.purpose == claim.purpose
        }.getOrDefault(false)
    }

    fun hasPendingClaim(): Boolean = locked {
        runCatching { store.read()?.let { live(it.id).code != null } == true }.getOrDefault(false)
    }

    /** Called after the coordinator durably commits the result; the auth generation may
     * already have changed. Match the exact flow/claim, not the new signed-in generation. */
    fun consume(claim: AuthFlowClaim): Boolean = locked {
        val current = store.read()
        if (current?.id != claim.flowId || current.claimId != claim.claimId || current.code != claim.code ||
            current.authGeneration != claim.authToken.generation || current.purpose != claim.purpose) false
        else { store.write(null); true }
    }

    fun cancel(expectedId: String): Boolean = locked {
        if (store.read()?.id != expectedId) false else { store.write(null); true }
    }

    /** Required on sign-out/deletion and at startup before enabling account UI. */
    fun purgeStale(): Boolean = locked {
        val current = store.read() ?: return@locked true
        val auth = journal.snapshot()
        if (!current.purpose.allows(auth.phase) || auth.operationToken.generation != current.authGeneration ||
            now() !in current.createdAt until current.expiresAt) store.write(null)
        true
    }

    private fun live(id: String): AuthFlowRecord {
        val record = store.read() ?: fail(AuthFlowFailure.NO_PENDING_FLOW)
        if (record.id != id) fail(AuthFlowFailure.STALE)
        val auth = journal.snapshot()
        if (!record.purpose.allows(auth.phase) || auth.operationToken.generation != record.authGeneration) fail(AuthFlowFailure.STALE)
        if (now() !in record.createdAt until record.expiresAt) fail(AuthFlowFailure.EXPIRED)
        return record
    }

    private fun AuthFlowRecord.claim() = AuthFlowClaim(id, requireNotNull(claimId), requireNotNull(code), AuthOperationToken(authGeneration), purpose)
    private fun <T> locked(action: () -> T): T = journal.withAccountLock { synchronized(LOCK, action) }

    companion object {
        private val LOCK = Any()
        private fun fail(reason: AuthFlowFailure): Nothing = throw AuthFlowException(reason)

        /** Parsing only, not a claim. Runtime cleanup still checks exact ownership.
         * A newly delivered callback can precede the next Compose frame, so its ID
         * takes precedence over a previously displayed recovery form. */
        internal fun recoveryCleanupId(callback: String?, displayedId: String?): String? =
            callback?.let { runCatching { callbackFields(it)["state"] }.getOrNull() }
                ?: displayedId?.takeIf(::canonicalAccountId)

        internal fun callbackFields(raw: String): Map<String, String> {
            try {
                require(raw.length in 1..4096 && raw.none(Char::isISOControl))
                val uri = URI(raw)
                require(uri.scheme == "shop.justnoise.app" && uri.rawAuthority == "auth" &&
                    uri.rawPath == "/callback" && uri.rawFragment == null)
                val query = requireNotNull(uri.rawQuery)
                val pairs = query.split('&').map { part ->
                    val pieces = part.split('=', limit = 2)
                    require(pieces.size == 2)
                    val key = URLDecoder.decode(pieces[0], "UTF-8")
                    val value = URLDecoder.decode(pieces[1], "UTF-8")
                    require(key in setOf("state", "code", "error", "error_code", "error_description"))
                    require(value.none(Char::isISOControl))
                    key to value
                }
                require(pairs.map { it.first }.distinct().size == pairs.size)
                val values = pairs.toMap()
                require(values["state"]?.let(::canonicalAccountId) == true)
                require(("code" in values) xor ("error" in values))
                if ("code" in values) require(values.keys == setOf("state", "code"))
                return values
            } catch (_: Exception) { fail(AuthFlowFailure.INVALID_CALLBACK) }
        }
    }
}

internal class EncryptedAuthFlowStore(private val blobs: AuthBlobStorage, private val cipher: AuthRecordCipher) : AuthFlowStore {
    override fun read(): AuthFlowRecord? = sanitized {
        val encoded = blobs.read() ?: return@sanitized null
        val plain = cipher.decrypt(encoded)
        try {
            require(plain.size <= 4096)
            val json = JSONObject(plain.toString(Charsets.UTF_8))
            require(json.getInt("version") == 1 && json.has("flow"))
            if (json.isNull("flow")) null else json.getJSONObject("flow").let { flow ->
                fun string(key: String) = flow.get(key) as String
                fun optional(key: String) = if (flow.isNull(key)) null else string(key)
                AuthFlowRecord(string("id"), string("authGeneration"), AuthFlowPurpose.valueOf(string("purpose")),
                    flow.getLong("createdAt"), flow.getLong("expiresAt"), optional("verifier"), optional("code"), optional("claimId"))
            }
        } finally { plain.fill(0) }
    }

    override fun write(record: AuthFlowRecord?) = sanitized {
        val plain = JSONObject().put("version", 1).put("flow", record?.let {
            JSONObject().put("id", it.id).put("authGeneration", it.authGeneration).put("purpose", it.purpose.name)
                .put("createdAt", it.createdAt).put("expiresAt", it.expiresAt).put("verifier", it.verifier ?: JSONObject.NULL)
                .put("code", it.code ?: JSONObject.NULL).put("claimId", it.claimId ?: JSONObject.NULL)
        } ?: JSONObject.NULL).toString().toByteArray(Charsets.UTF_8)
        try { require(plain.size <= 4096); blobs.write(cipher.encrypt(plain)) } finally { plain.fill(0) }
    }

    private fun <T> sanitized(action: () -> T): T = try { action() } catch (_: Exception) { throw AuthStorageException() }
}
