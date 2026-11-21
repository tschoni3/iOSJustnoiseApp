package com.justnoise.app.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

internal enum class ProviderFailure { INVALID_INPUT, REJECTED, UNAVAILABLE, INVALID_RESPONSE, SERVER_ERROR, RATE_LIMITED }
internal class AccountProviderException(val failure: ProviderFailure) : Exception("Account request failed: $failure")

/** Only transient session data; no automatic account publication or credential writes. */
internal class RegistrationResult(val session: StoredAuthSession?) {
    val confirmationRequired get() = session == null
    override fun toString() = "RegistrationResult(confirmationRequired=$confirmationRequired)"
}

/**
 * Short-lived native SDK clients isolate concurrent requests. SDK persistence, automatic
 * refresh and logging are disabled. Only the account journal may persist a returned session.
 * No constructor or restore performs a network request; every call is user/workflow invoked.
 */
internal class SupabaseAccountProvider(
    private val clientFactory: (CodeVerifierCache) -> SupabaseClient = { accountSupabaseClient(verifierCache = it) },
) {
    suspend fun signIn(email: String, password: String): StoredAuthSession {
        validate(email, password)
        return request { client ->
            client.auth.signInWith(Email) { this.email = email.trim(); this.password = password }
            session(client.auth.currentSessionOrNull())
        }
    }

    suspend fun register(email: String, password: String, verifierCache: CodeVerifierCache,
                         flow: AuthFlowHandle): RegistrationResult {
        validate(email, password)
        if (flow.purpose != AuthFlowPurpose.REGISTRATION) throw AccountProviderException(ProviderFailure.INVALID_INPUT)
        val durable = AwaitedVerifierCache(verifierCache)
        return request(durable) { client ->
            // Confirmation may return a user but NO session. Never treat that as logged in.
            // Android must not inherit the shared Site URL (which belongs to existing
            // clients). Its confirmation uses the same tightly bound PKCE return route.
            client.auth.signUpWith(Email, redirectUrl = flow.callbackUrl) { this.email = email.trim(); this.password = password }
            durable.awaitSaved()
            RegistrationResult(client.auth.currentSessionOrNull()?.let(::session))
        }
    }

    suspend fun refresh(current: StoredAuthSession): StoredAuthSession = request { client ->
        val refreshed = session(client.auth.refreshSession(current.refreshToken))
        if (refreshed.accountId != current.accountId) throw AccountProviderException(ProviderFailure.INVALID_RESPONSE)
        refreshed
    }

    /** Caller supplies an encrypted, generation-bound pending-flow cache BEFORE sending.
     * UI must use a non-enumerating message; successful submission is not proof of delivery. */
    suspend fun requestRecovery(email: String, verifierCache: CodeVerifierCache, flow: AuthFlowHandle) {
        validate(email, "validation-only")
        if (!flow.purpose.recovery) throw AccountProviderException(ProviderFailure.INVALID_INPUT)
        val durable = AwaitedVerifierCache(verifierCache)
        request(durable) {
            it.auth.resetPasswordForEmail(email.trim(), redirectUrl = flow.callbackUrl)
            durable.awaitSaved()
        }
    }

    suspend fun googleSignInUrl(verifierCache: CodeVerifierCache, flow: AuthFlowHandle): String {
        if (flow.purpose !in setOf(AuthFlowPurpose.GOOGLE, AuthFlowPurpose.REAUTH_GOOGLE))
            throw AccountProviderException(ProviderFailure.INVALID_INPUT)
        val durable = AwaitedVerifierCache(verifierCache)
        return request(durable) {
            val url = it.auth.getOAuthUrl(Google, redirectUrl = flow.callbackUrl)
            durable.awaitSaved()
            url
        }
    }

    /** Raw provider operation only. The caller must first validate the callback URI,
     * pending user-initiated flow and auth generation; never accept arbitrary Intent tokens. */
    suspend fun exchangeCode(code: String, verifierCache: CodeVerifierCache): StoredAuthSession {
        if (!canonicalAccountId(code)) throw AccountProviderException(ProviderFailure.INVALID_INPUT)
        return request(verifierCache) { session(it.auth.exchangeCodeForSession(code, saveSession = false)) }
    }

    /** Used only after a verified recovery callback/session has passed the account boundary. */
    suspend fun updatePassword(current: StoredAuthSession, password: String) {
        validate(current.email, password)
        request { client ->
            importVerifiedIdentity(client, current)
            val user = client.auth.updateUser { this.password = password }
            if (user.id != current.accountId) throw AccountProviderException(ProviderFailure.INVALID_RESPONSE)
        }
    }

    /** Exact existing iOS edge-function contract: authenticated POST, HTTP 200 = success.
     * Timeout/401/404/5xx are NOT proof of deletion, including a retry after lost response. */
    suspend fun deleteAccount(current: StoredAuthSession) {
        request { client ->
            importVerifiedIdentity(client, current)
            val response = client.functions.invoke("delete-account")
            if (response.status != HttpStatusCode.OK) throw AccountProviderException(ProviderFailure.INVALID_RESPONSE)
        }
    }

    private suspend fun importVerifiedIdentity(client: SupabaseClient, current: StoredAuthSession) {
        client.auth.importAuthToken(current.accessToken, current.refreshToken, retrieveUser = true, autoRefresh = false)
        if (client.auth.currentUserOrNull()?.id != current.accountId)
            throw AccountProviderException(ProviderFailure.INVALID_RESPONSE)
    }

    private fun validate(email: String, password: String) {
        val normalized = email.trim()
        if (normalized.length !in 3..320 || normalized.count { it == '@' } != 1 ||
            normalized.any { it.isWhitespace() || it.isISOControl() } || password.length !in 1..4096)
            throw AccountProviderException(ProviderFailure.INVALID_INPUT)
    }

    private fun session(value: UserSession?): StoredAuthSession {
        val response = value ?: throw AccountProviderException(ProviderFailure.INVALID_RESPONSE)
        val user = response.user ?: throw AccountProviderException(ProviderFailure.INVALID_RESPONSE)
        return try {
            StoredAuthSession(user.id, user.email.orEmpty(), response.accessToken, response.refreshToken,
                response.expiresAt.epochSeconds)
        } catch (_: IllegalArgumentException) { throw AccountProviderException(ProviderFailure.INVALID_RESPONSE) }
    }

    private suspend fun <T> request(verifierCache: CodeVerifierCache = MemoryCodeVerifierCache(), action: suspend (SupabaseClient) -> T): T {
        var client: SupabaseClient? = null
        try {
            client = clientFactory(verifierCache)
            return action(client)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AccountProviderException) {
            throw failure
        } catch (failure: RestException) {
            // Classify only the HTTP status. SDK error bodies/headers can contain
            // email addresses, credentials or internal mail-provider configuration.
            throw AccountProviderException(when (failure.statusCode) {
                429 -> ProviderFailure.RATE_LIMITED
                in 500..599 -> ProviderFailure.SERVER_ERROR
                else -> ProviderFailure.REJECTED
            })
        } catch (_: Exception) {
            // Never expose SDK exception text/body/headers, which can contain credentials.
            throw AccountProviderException(ProviderFailure.UNAVAILABLE)
        } finally {
            withContext(NonCancellable) {
                client?.let {
                    try {
                        try { it.auth.clearSession() } finally { it.close() }
                    } catch (_: Exception) { throw AccountProviderException(ProviderFailure.UNAVAILABLE) }
                }
            }
        }
    }

    companion object {
        const val CALLBACK_URL = "shop.justnoise.app://auth/callback"
    }
}

/** Optional engine is used by mock-transport tests; production uses the native Android engine. */
// Pinned SDK's HTTP configuration hook is annotated internal; reviewed here solely
// to disable redirect following. No undocumented Android/Samsung API is involved.
@OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
internal fun accountSupabaseClient(engine: HttpClientEngine? = null, verifierCache: CodeVerifierCache = MemoryCodeVerifierCache()): SupabaseClient = createSupabaseClient(
    "https://zusnyjctxdnnxghpbhki.supabase.co",
    // Public anon key already shipped by iOS. Never substitute a service-role/admin key.
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp1c255amN0eGRubnhnaHBiaGtpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzg3NDczMDQsImV4cCI6MjA1NDMyMzMwNH0.3WP_jwfBagzxc9qGle68bPWL47ePA_dXaWsXG1lT1No",
) {
    httpEngine = engine
    defaultLogLevel = LogLevel.NONE
    requestTimeout = 15.seconds
    httpConfig { followRedirects = false }
    install(Auth) {
        alwaysAutoRefresh = false
        autoLoadFromStorage = false
        autoSaveToStorage = false
        enableLifecycleCallbacks = false
        sessionManager = MemorySessionManager()
        // The durable pending-flow owner, not a short-lived SDK session, decides when
        // a verifier expires/is consumed. SDK clearSession() also deletes its cache;
        // permitting that here would destroy recovery before the email link returns.
        codeVerifierCache = object : CodeVerifierCache {
            override suspend fun saveCodeVerifier(codeVerifier: String) = verifierCache.saveCodeVerifier(codeVerifier)
            override suspend fun loadCodeVerifier(): String? = verifierCache.loadCodeVerifier()
            override suspend fun deleteCodeVerifier() = Unit
        }
        flowType = FlowType.PKCE
    }
    install(Functions)
}

/** SDK 3.6 launches verifier writes asynchronously. Do not close that SDK client or
 * publish a usable URL before its exact verifier has reached the durable owner.
 * Capture disk failures here instead of throwing from an unobserved SDK coroutine. */
private class AwaitedVerifierCache(private val delegate: CodeVerifierCache) : CodeVerifierCache {
    private val saved = CompletableDeferred<Boolean>()
    override suspend fun saveCodeVerifier(codeVerifier: String) {
        try {
            delegate.saveCodeVerifier(codeVerifier)
            saved.complete(delegate.loadCodeVerifier() == codeVerifier)
        } catch (cancelled: CancellationException) {
            saved.complete(false)
            throw cancelled
        } catch (_: Exception) { saved.complete(false) }
    }
    override suspend fun loadCodeVerifier(): String? = delegate.loadCodeVerifier()
    override suspend fun deleteCodeVerifier() = Unit
    suspend fun awaitSaved() {
        if (!withTimeout(15_000) { saved.await() })
            throw AccountProviderException(ProviderFailure.UNAVAILABLE)
    }
}
