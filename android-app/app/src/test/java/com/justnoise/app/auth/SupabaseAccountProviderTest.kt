package com.justnoise.app.auth

import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real pinned SDK and request serialization, intercepted by a no-network Ktor engine. */
class SupabaseAccountProviderTest {
    @Test fun loginUsesExistingProjectAndDoesNotTrimPassword() = runBlocking {
        var requests = 0
        val provider = provider { request ->
            requests++
            assertEquals("zusnyjctxdnnxghpbhki.supabase.co", request.url.host)
            assertEquals("https", request.url.protocol.name)
            assertEquals("/auth/v1/token", request.url.encodedPath)
            assertEquals("password", request.url.parameters["grant_type"])
            val body = JSONObject((request.body as TextContent).text)
            assertEquals("synthetic@example.invalid", body.getString("email"))
            assertEquals("  synthetic-password  ", body.getString("password"))
            assertNotNull(request.headers["apikey"])
            TOKEN to HttpStatusCode.OK
        }
        val result = provider.signIn(" synthetic@example.invalid ", "  synthetic-password  ")
        assertEquals(A, result.accountId)
        assertEquals("synthetic-access", result.accessToken)
        assertEquals(1, requests)
    }

    @Test fun signupWithConfirmationIsNotAnAuthenticatedSession() = runBlocking {
        val cache = MemoryCodeVerifierCache()
        val flow = AuthFlowHandle(A, AuthFlowPurpose.REGISTRATION)
        val result = provider { request ->
            assertEquals("/auth/v1/signup", request.url.encodedPath)
            assertEquals(flow.callbackUrl, request.url.parameters["redirect_to"])
            val body = JSONObject((request.body as TextContent).text)
            assertEquals("s256", body.getString("code_challenge_method"))
            assertTrue(body.getString("code_challenge").isNotBlank())
            USER to HttpStatusCode.OK
        }.register("synthetic@example.invalid", "synthetic-password", cache, flow)
        assertTrue(result.confirmationRequired)
        assertNull(result.session)
        assertFalse(cache.loadCodeVerifier().isNullOrBlank())
    }

    @Test fun signupWithoutConfirmationReturnsSessionForCoordinatorOnly() = runBlocking {
        val result = provider { TOKEN to HttpStatusCode.OK }.register("synthetic@example.invalid", "synthetic-password",
            MemoryCodeVerifierCache(), AuthFlowHandle(A, AuthFlowPurpose.REGISTRATION))
        assertFalse(result.confirmationRequired)
        assertEquals(A, result.session!!.accountId)
    }

    @Test fun refreshRejectsUnexpectedAccountOwner() = runBlocking {
        val failure = failure {
            provider { TOKEN.replace(A, B) to HttpStatusCode.OK }.refresh(stored())
        }
        assertEquals(ProviderFailure.INVALID_RESPONSE, failure.failure)
    }

    @Test fun sdkErrorBodyCannotLeakIntoUiException() = runBlocking {
        val result = failure {
            provider { "{\"error_code\":\"invalid_credentials\",\"msg\":\"private-token synthetic@example.invalid\"}" to HttpStatusCode.BadRequest }
                .signIn("synthetic@example.invalid", "synthetic-password")
        }
        assertEquals(ProviderFailure.REJECTED, result.failure)
        assertNull(result.cause)
        assertFalse(result.toString().contains("private-token"))
        assertFalse(result.toString().contains("example.invalid"))
    }

    @Test fun invalidInputDoesNotConstructClientOrSendRequest() = runBlocking {
        var constructed = false
        val provider = SupabaseAccountProvider { constructed = true; error("Must not construct") }
        assertEquals(ProviderFailure.INVALID_INPUT, failure { provider.signIn("bad", "secret") }.failure)
        assertFalse(constructed)
    }

    @Test fun signupServerFailureIsSanitizedAndDistinguishedFromRejection() = runBlocking {
        val result = failure {
            provider { "{\"error_code\":\"unexpected_failure\",\"msg\":\"private mail-provider credential synthetic@example.invalid\"}" to HttpStatusCode.InternalServerError }
                .register("synthetic@example.invalid", "synthetic-password", MemoryCodeVerifierCache(),
                    AuthFlowHandle(A, AuthFlowPurpose.REGISTRATION))
        }
        assertEquals(ProviderFailure.SERVER_ERROR, result.failure)
        assertNull(result.cause)
        assertFalse(result.toString().contains("credential"))
        assertFalse(result.toString().contains("example.invalid"))
    }

    @Test fun signupRateLimitHasItsOwnSafeFailure() = runBlocking {
        val result = failure {
            provider { "{\"error_code\":\"over_email_send_rate_limit\",\"msg\":\"private details\"}" to HttpStatusCode.TooManyRequests }
                .register("synthetic@example.invalid", "synthetic-password", MemoryCodeVerifierCache(),
                    AuthFlowHandle(A, AuthFlowPurpose.REGISTRATION))
        }
        assertEquals(ProviderFailure.RATE_LIMITED, result.failure)
        assertFalse(result.toString().contains("private details"))
    }

    @Test fun nativeClientCannotAutomaticallyPersistOrRefreshSessions() = runBlocking {
        val engine = MockEngine { error("Construction must not perform network requests") }
        val client = accountSupabaseClient(engine)
        try {
            assertFalse(client.auth.config.autoLoadFromStorage)
            assertFalse(client.auth.config.autoSaveToStorage)
            assertFalse(client.auth.config.alwaysAutoRefresh)
            assertFalse(client.auth.config.enableLifecycleCallbacks)
            assertTrue(client.auth.sessionManager is MemorySessionManager)
            assertFalse(client.auth.isAutoRefreshRunning)
        } finally { client.close() }
    }

    @Test fun recoveryUsesPkceAndRetainsVerifierForLaterCallback() = runBlocking {
        val cache = MemoryCodeVerifierCache()
        val flow = AuthFlowHandle(A, AuthFlowPurpose.RECOVERY)
        val provider = provider { request ->
            assertEquals("/auth/v1/recover", request.url.encodedPath)
            assertEquals(flow.callbackUrl, request.url.parameters["redirect_to"])
            val body = JSONObject((request.body as TextContent).text)
            assertEquals("s256", body.getString("code_challenge_method"))
            assertTrue(body.getString("code_challenge").isNotBlank())
            "{}" to HttpStatusCode.OK
        }
        provider.requestRecovery("synthetic@example.invalid", cache, flow)
        assertFalse(cache.loadCodeVerifier().isNullOrBlank())
    }

    @Test fun googleUrlUsesPkceAndBoundRedirectWithoutOpeningBrowserOrNetwork() = runBlocking {
        val cache = MemoryCodeVerifierCache()
        val flow = AuthFlowHandle(A, AuthFlowPurpose.GOOGLE)
        val provider = provider { error("Building OAuth URL must not send a request") }
        val url = io.ktor.http.Url(provider.googleSignInUrl(cache, flow))
        assertEquals("https", url.protocol.name)
        assertEquals("zusnyjctxdnnxghpbhki.supabase.co", url.host)
        assertEquals("/auth/v1/authorize", url.encodedPath)
        assertEquals("google", url.parameters["provider"])
        assertEquals(flow.callbackUrl, url.parameters["redirect_to"])
        assertEquals("s256", url.parameters["code_challenge_method"])
        assertFalse(url.parameters["code_challenge"].isNullOrBlank())
        assertFalse(cache.loadCodeVerifier().isNullOrBlank())
        assertFalse(url.toString().contains(cache.loadCodeVerifier()!!))
    }

    @Test fun codeExchangeSuppliesVerifierAndDoesNotAcceptRawAccessToken() = runBlocking {
        val cache = MemoryCodeVerifierCache("synthetic-verifier")
        val provider = provider { request ->
            assertEquals("pkce", request.url.parameters["grant_type"])
            val body = JSONObject((request.body as TextContent).text)
            assertEquals("synthetic-verifier", body.getString("code_verifier"))
            assertEquals(A, body.getString("auth_code"))
            TOKEN to HttpStatusCode.OK
        }
        assertEquals(A, provider.exchangeCode(A, cache).accountId)
        assertEquals(ProviderFailure.INVALID_INPUT, failure { provider.exchangeCode("access-token", cache) }.failure)
    }

    @Test fun googleUrlWaitsForDurableVerifierWriteBeforeReturning() = runBlocking {
        val writing = CompletableDeferred<Unit>()
        val allowWrite = CompletableDeferred<Unit>()
        var stored: String? = null
        val cache = object : CodeVerifierCache {
            override suspend fun saveCodeVerifier(codeVerifier: String) {
                writing.complete(Unit)
                allowWrite.await()
                stored = codeVerifier
            }
            override suspend fun loadCodeVerifier() = stored
            override suspend fun deleteCodeVerifier() { error("Not the SDK's data to delete") }
        }
        val result = async {
            provider { error("No network") }.googleSignInUrl(cache, AuthFlowHandle(A, AuthFlowPurpose.GOOGLE))
        }
        withTimeout(5_000) { writing.await() }
        assertFalse(result.isCompleted)
        allowWrite.complete(Unit)
        assertTrue(withTimeout(5_000) { result.await() }.contains("code_challenge="))
        assertFalse(stored.isNullOrBlank())
    }

    @Test fun failedVerifierStorageNeverPublishesGoogleUrlOrRecoverySuccess() = runBlocking {
        val cache = object : CodeVerifierCache {
            override suspend fun saveCodeVerifier(codeVerifier: String) { error("Synthetic disk failure") }
            override suspend fun loadCodeVerifier(): String? = null
            override suspend fun deleteCodeVerifier() = Unit
        }
        val provider = provider { "{}" to HttpStatusCode.OK }
        assertEquals(ProviderFailure.UNAVAILABLE, failure {
            provider.googleSignInUrl(cache, AuthFlowHandle(A, AuthFlowPurpose.GOOGLE))
        }.failure)
        assertEquals(ProviderFailure.UNAVAILABLE, failure {
            provider.requestRecovery("synthetic@example.invalid", cache, AuthFlowHandle(A, AuthFlowPurpose.RECOVERY))
        }.failure)
    }

    @Test fun passwordUpdateUsesUserEndpointOnlyAfterVerifiedIdentity() = runBlocking {
        val paths = mutableListOf<String>()
        val provider = provider { request ->
            paths += request.method.value + " " + request.url.encodedPath
            assertEquals("Bearer synthetic-access", request.headers["Authorization"])
            if (request.method.value == "PUT") {
                assertEquals("new-password", JSONObject((request.body as TextContent).text).getString("password"))
            }
            USER to HttpStatusCode.OK
        }
        provider.updatePassword(stored(), "new-password")
        assertEquals(listOf("GET /auth/v1/user", "PUT /auth/v1/user"), paths)
    }

    @Test fun wrongIdentityNeverSendsPasswordUpdateOrDeletion() = runBlocking {
        var requests = 0
        val provider = provider { request ->
            requests++
            assertEquals("GET", request.method.value)
            USER.replace(A, B) to HttpStatusCode.OK
        }
        assertEquals(ProviderFailure.INVALID_RESPONSE, failure { provider.updatePassword(stored(), "new-password") }.failure)
        assertEquals(ProviderFailure.INVALID_RESPONSE, failure { provider.deleteAccount(stored()) }.failure)
        assertEquals(2, requests)
    }

    @Test fun deletionRequiresExactSuccessfulFunctionResponse() = runBlocking {
        val paths = mutableListOf<String>()
        val provider = provider { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath == "/auth/v1/user") USER to HttpStatusCode.OK
            else {
                assertEquals("POST", request.method.value)
                assertEquals("Bearer synthetic-access", request.headers["Authorization"])
                "{}" to HttpStatusCode.OK
            }
        }
        provider.deleteAccount(stored())
        assertEquals(listOf("/auth/v1/user", "/functions/v1/delete-account"), paths)
        val failure = failure {
            provider { request ->
                if (request.url.encodedPath == "/auth/v1/user") USER to HttpStatusCode.OK
                else "{}" to HttpStatusCode.Accepted
            }.deleteAccount(stored())
        }
        assertEquals(ProviderFailure.INVALID_RESPONSE, failure.failure)
    }

    private fun provider(handler: (io.ktor.client.request.HttpRequestData) -> Pair<String, HttpStatusCode>) =
        SupabaseAccountProvider { cache ->
            accountSupabaseClient(MockEngine { request ->
                val (body, status) = handler(request)
                respond(body, status, headersOf("Content-Type", "application/json"))
            }, cache)
        }
    private suspend fun failure(action: suspend () -> Unit): AccountProviderException {
        try { action() } catch (failure: AccountProviderException) { return failure }
        throw AssertionError("Expected AccountProviderException")
    }
    private fun stored() = StoredAuthSession(A, "synthetic@example.invalid", "synthetic-access", "synthetic-refresh", 0)
    companion object {
        private const val A = "11111111-1111-4111-8111-111111111111"
        private const val B = "22222222-2222-4222-8222-222222222222"
        private const val USER = "{\"id\":\"$A\",\"email\":\"synthetic@example.invalid\",\"aud\":\"authenticated\",\"role\":\"authenticated\",\"created_at\":\"2026-09-09T00:00:00Z\"}"
        private const val TOKEN = "{\"access_token\":\"synthetic-access\",\"refresh_token\":\"synthetic-refresh\",\"token_type\":\"bearer\",\"expires_in\":3600,\"user\":$USER}"
    }
}
