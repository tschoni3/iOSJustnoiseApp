package com.justnoise.app.auth

import java.util.UUID

/** Secrets deliberately have no generated data-class string/component methods. */
internal class StoredAuthSession(
    val accountId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
) {
    init {
        require(canonicalAccountId(accountId)) { "Invalid account identity" }
        require(email.length <= 320 && email.none(Char::isISOControl)) { "Invalid account email" }
        require(listOf(accessToken, refreshToken).all { token ->
            token.length in 1..16_384 && token.all { it.code in 33..126 }
        }) { "Invalid session credentials" }
        require(expiresAtEpochSeconds >= 0) { "Invalid session expiry" }
    }

    override fun toString() = "StoredAuthSession([redacted])"
}

internal fun canonicalAccountId(value: String): Boolean = runCatching {
    value.length == 36 && UUID.fromString(value).toString() == value
}.getOrDefault(false)

internal enum class AuthPhase { SIGNED_OUT, SIGNED_IN, DELETE_REMOTE_PENDING, DELETE_LOCAL_PENDING }

internal class AuthRecord(
    val generation: String,
    val phase: AuthPhase,
    val session: StoredAuthSession? = null,
    val cleanupAccountId: String? = null,
) {
    init {
        require(canonicalAccountId(generation)) { "Invalid auth generation" }
        require(when (phase) {
            AuthPhase.SIGNED_OUT -> session == null && cleanupAccountId == null
            AuthPhase.SIGNED_IN -> session != null && cleanupAccountId == null
            AuthPhase.DELETE_REMOTE_PENDING -> session != null && cleanupAccountId == session.accountId
            AuthPhase.DELETE_LOCAL_PENDING -> session == null && cleanupAccountId?.let(::canonicalAccountId) == true
        }) { "Invalid auth journal state" }
    }

    override fun toString() = "AuthRecord($phase, [redacted])"
}

internal data class AuthOperationToken(val generation: String)

/** UI gets this projection, never credentials. Expiry does not gate local Zap stops. */
internal class AuthSnapshot(record: AuthRecord) {
    val operationToken = AuthOperationToken(record.generation)
    val phase = record.phase
    val accountId = record.session?.accountId ?: record.cleanupAccountId
    val email = record.session?.email
    override fun toString() = "AuthSnapshot($phase, [redacted])"
}

internal interface AuthRecordStore {
    fun read(): AuthRecord?
    /** Return only after a complete durable replacement; never keep an in-memory success on failure. */
    fun write(record: AuthRecord)
}

internal class AuthStorageException : Exception("Secure account storage is unavailable")

/**
 * Worker-only, single-process journal. The SDK/coordinator must not persist tokens elsewhere.
 * This is not the account coordinator: do not publish signed-in UI or switch product stores
 * from a provider callback alone. Account/analytics cleanup must precede that publication.
 */
internal class AuthSessionJournal(private val store: AuthRecordStore) {
    /** Serialize a complete local account handoff against refresh/delete callbacks too.
     * Never perform network requests or wait for main-thread UI work inside this block. */
    fun <T> withAccountLock(action: () -> T): T = synchronized(LOCK, action)

    fun snapshot(): AuthSnapshot = synchronized(LOCK) { AuthSnapshot(readOrInitialize()) }

    fun credentials(token: AuthOperationToken): StoredAuthSession? = synchronized(LOCK) {
        val current = readOrInitialize()
        current.session.takeIf { current.generation == token.generation }
    }

    fun acceptSignIn(token: AuthOperationToken, session: StoredAuthSession): Boolean = mutate(token) {
        if (it.phase != AuthPhase.SIGNED_OUT) null else fresh(AuthPhase.SIGNED_IN, session)
    }

    fun acceptRefresh(token: AuthOperationToken, session: StoredAuthSession): Boolean = mutate(token) {
        if (it.phase !in setOf(AuthPhase.SIGNED_IN, AuthPhase.DELETE_REMOTE_PENDING) ||
            it.session?.accountId != session.accountId
        ) null else fresh(it.phase, session, it.cleanupAccountId)
    }

    /** Only the coordinator calls this, after safely closing the account-owned workspace. */
    fun signOut(token: AuthOperationToken): Boolean = mutate(token) {
        if (it.phase !in setOf(AuthPhase.SIGNED_OUT, AuthPhase.SIGNED_IN)) null
        else fresh(AuthPhase.SIGNED_OUT)
    }

    fun beginDeletion(token: AuthOperationToken): Boolean = mutate(token) {
        if (it.phase != AuthPhase.SIGNED_IN) null
        else fresh(AuthPhase.DELETE_REMOTE_PENDING, it.session, it.session!!.accountId)
    }

    /** Call only on verified remote deletion success, not timeout/transport failure. */
    fun acknowledgeRemoteDeletion(token: AuthOperationToken): Boolean = mutate(token) {
        if (it.phase != AuthPhase.DELETE_REMOTE_PENDING) null
        else fresh(AuthPhase.DELETE_LOCAL_PENDING, cleanupAccountId = it.cleanupAccountId)
    }

    /** Local product/analytics cleanup must be durable before this transition. */
    fun completeLocalDeletion(token: AuthOperationToken): Boolean = mutate(token) {
        if (it.phase != AuthPhase.DELETE_LOCAL_PENDING) null else fresh(AuthPhase.SIGNED_OUT)
    }

    private fun mutate(token: AuthOperationToken, change: (AuthRecord) -> AuthRecord?): Boolean = synchronized(LOCK) {
        val current = readOrInitialize()
        if (current.generation != token.generation) return@synchronized false
        val next = change(current) ?: return@synchronized false
        store.write(next)
        true
    }

    private fun readOrInitialize(): AuthRecord = store.read() ?: fresh(AuthPhase.SIGNED_OUT).also(store::write)

    private fun fresh(phase: AuthPhase, session: StoredAuthSession? = null, cleanupAccountId: String? = null) =
        AuthRecord(UUID.randomUUID().toString(), phase, session, cleanupAccountId)

    companion object {
        // Shared by every journal instance, including replacement UI/coordinator instances.
        private val LOCK = Any()
    }
}
