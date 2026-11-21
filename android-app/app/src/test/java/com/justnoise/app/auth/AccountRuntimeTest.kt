package com.justnoise.app.auth

import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.LocalWorkspaceId
import com.justnoise.gate0.data.MemoryPreferences
import com.justnoise.gate0.data.ModeMutationResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Real runtime/coordinator/product storage and SDK, but synthetic in-memory disk/HTTP. */
class AccountRuntimeTest {
    private val products = Gate0Preferences(MemoryPreferences(), AccountHistories()) { 10_000L }
    private val journal = AuthSessionJournal(AccountTestAuthStore())
    private val consent = TestAnalyticsConsent()
    private val analytics = AccountAnalytics(consent) { _, _ -> error("No automatic analytics") }
    private val flowStore = object : AuthFlowStore {
        private var record: AuthFlowRecord? = null
        override fun read() = record
        override fun write(record: AuthFlowRecord?) { this.record = record }
    }
    private val flows = AuthPendingFlow(journal, flowStore)
    private var response = TOKEN
    private var status = HttpStatusCode.OK
    private var handler: ((HttpRequestData) -> Pair<String, HttpStatusCode>)? = null
    private var beforeResponse: suspend (HttpRequestData) -> Unit = {}
    private val requests = CopyOnWriteArrayList<String>()
    private var clockSeconds = System.currentTimeMillis() / 1_000
    private val provider = SupabaseAccountProvider { cache -> accountSupabaseClient(MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath} ${request.url.parameters["grant_type"].orEmpty()}"
        beforeResponse(request)
        val result = handler?.invoke(request) ?: (response to status)
        respond(result.first, result.second, headersOf("Content-Type", "application/json"))
    }, cache) }
    private val runtime = AccountRuntime(journal, AccountCoordinator(journal, products, analytics), flows, provider, analytics) { clockSeconds }

    @Before fun prepare() {
        assertTrue(products.clearForWithdrawal())
        assertTrue(products.acceptDisclosure(Gate0Preferences.CURRENT_DISCLOSURE_VERSION, 1_000L))
        assertEquals(ModeMutationResult.SAVED,
            products.saveMode(products.loadModes().token!!, null, "Local sample", setOf("synthetic.local")))
    }
    @After fun clean() { analytics.closeAndPurge(); assertTrue(products.clearForWithdrawal()) }

    @Test fun successfulLoginOffersOptionalImportAndDeferralRemainsReachable() = runBlocking {
        runtime.restore().join()
        runtime.passwordSignIn("synthetic@example.invalid", "password", false).join()
        val first = runtime.state.value
        assertTrue(first.localImport.shouldPrompt)
        assertFalse(AccountEntryNavigation.returnToProduct(true, true, first))
        runtime.deferLocalImport(first.localImport).join()
        assertFalse(runtime.state.value.localImport.shouldPrompt)
        assertTrue(AccountEntryNavigation.returnToProduct(true, true, runtime.state.value))
        assertTrue(products.loadModes().modes.isEmpty())
        runtime.restore().join()
        assertEquals(com.justnoise.gate0.data.LocalDataImportStatus.AVAILABLE, runtime.state.value.localImport.status)
        assertFalse(runtime.state.value.localImport.shouldPrompt)
    }

    @Test fun confirmedImportPublishesNewProductGenerationWithoutNetworkOrAnalytics() = runBlocking {
        runtime.restore().join()
        runtime.passwordSignIn("synthetic@example.invalid", "password", false).join()
        val generation = runtime.access.value.generation
        val requestCount = requests.size
        runtime.importLocalData(runtime.state.value.localImport).join()
        assertEquals(com.justnoise.gate0.data.LocalDataImportStatus.COMPLETE, runtime.state.value.localImport.status)
        assertNull(runtime.state.value.message)
        assertEquals("Local sample", products.loadModes().modes.single().name)
        assertTrue(runtime.access.value.generation > generation)
        assertTrue(runtime.access.value.productAllowed)
        assertFalse(runtime.state.value.analyticsEnabled)
        assertEquals(requestCount, requests.size)
    }

    @Test fun emailSignInAndSignOutPublishRealIsolatedWorkspaces() = runBlocking {
        runtime.restore(); settled { runtime.access.value.ready }
        assertFalse(runtime.access.value.productAllowed)
        val localMode = products.loadModes().modes.single()
        val before = runtime.access.value.generation
        runtime.passwordSignIn("synthetic@example.invalid", "password", false)
        val account = settled { it.page == AccountPage.ACCOUNT }
        assertEquals("synthetic@example.invalid", account.email)
        assertEquals(LocalWorkspaceId.parse(A), products.currentWorkspace())
        assertTrue(products.loadModes().modes.isEmpty())
        assertTrue(runtime.access.value.generation > before)
        assertTrue(runtime.access.value.productAllowed)
        runtime.signOut(); settled { it.page == AccountPage.SIGN_IN }
        assertEquals(localMode, products.loadModes().modes.single())
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertFalse(runtime.access.value.productAllowed)
    }

    @Test fun accountAToBToARestoresOnlyEachWorkspaceAndKeepsGlobalPairing() = runBlocking {
        runtime.restore().join()
        val localMode = products.loadModes().modes.single()
        assertTrue(products.setZapDigest("synthetic-pairing"))

        runtime.passwordSignIn("synthetic@example.invalid", "password", false).join()
        assertEquals(A, journal.snapshot().accountId)
        assertTrue(products.loadModes().modes.isEmpty())
        assertEquals(ModeMutationResult.SAVED,
            products.saveMode(products.loadModes().token!!, null, "Account A", setOf("synthetic.a")))
        runtime.setAnalytics(true).join()
        assertTrue(runtime.state.value.analyticsEnabled)
        runtime.signOut().join()
        assertEquals(listOf(localMode), products.loadModes().modes)
        assertFalse(runtime.state.value.analyticsEnabled)

        response = TOKEN.replace(A, B).replace("synthetic@example.invalid", "other@example.invalid")
        runtime.passwordSignIn("other@example.invalid", "password", false).join()
        assertEquals(B, journal.snapshot().accountId)
        assertTrue(products.loadModes().modes.isEmpty())
        assertEquals(ModeMutationResult.SAVED,
            products.saveMode(products.loadModes().token!!, null, "Account B", setOf("synthetic.b")))
        runtime.signOut().join()
        assertEquals(listOf(localMode), products.loadModes().modes)

        response = TOKEN
        runtime.passwordSignIn("synthetic@example.invalid", "password", false).join()
        assertEquals(A, journal.snapshot().accountId)
        assertEquals(listOf("Account A"), products.loadModes().modes.map { it.name })
        assertEquals(setOf("synthetic.a"), products.loadModes().modes.single().selectedPackages)
        assertFalse(products.loadModes().modes.any { it.name == "Account B" })
        assertEquals("synthetic-pairing", products.snapshot().zapDigest)
        assertFalse(runtime.state.value.analyticsEnabled)
        assertTrue(runtime.access.value.productAllowed)
    }

    @Test fun activeSessionRejectsNewLoginWithoutLosingLocalStop() = runBlocking {
        runtime.restore(); settled { runtime.access.value.ready }
        val mode = products.loadModes().modes.single()
        assertEquals(ModeMutationResult.SAVED, products.selectMode(products.loadModes().token!!, mode.id))
        products.updateServiceHeartbeat(10_000L)
        assertTrue(products.startSession(100_000L))
        runtime.passwordSignIn("synthetic@example.invalid", "password", false)
        settled { it.message?.contains("Stop your session") == true }
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertTrue(products.snapshot().desiredActive)
        assertTrue(products.stopSession(105_000L))
    }

    @Test fun googleCallbackUsesPendingFlowAndRealCoordinator() = runBlocking {
        runtime.restore(); settled { runtime.access.value.ready }
        runtime.google()
        val browser = settled { it.browserUrl != null }.browserUrl!!
        val callback = Url(browser).parameters["redirect_to"]!!
        runtime.browserHandled(browser, true)
        runtime.callback("$callback&code=$A")
        settled { it.page == AccountPage.ACCOUNT }
        assertEquals(A, journal.snapshot().accountId)
        assertNull(flowStore.read())
    }

    @Test fun failedAuthenticationKeepsLocalWorkspaceAndSanitizesMessage() = runBlocking {
        runtime.restore(); settled { runtime.access.value.ready }
        response = "{\"msg\":\"private-server-error\",\"error_code\":\"invalid_credentials\"}"
        status = HttpStatusCode.BadRequest
        runtime.passwordSignIn("synthetic@example.invalid", "password", false)
        val state = settled { it.message != null }
        assertFalse(state.message!!.contains("private-server-error"))
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
        assertTrue(runtime.access.value.ready)
    }

    @Test fun signInRevokesPreviousWorkspaceAnalyticsConsent() = runBlocking {
        runtime.restore(); settled { runtime.access.value.ready }
        runtime.setAnalytics(true); settled { it.analyticsEnabled }
        assertTrue(consent.enabled)
        runtime.passwordSignIn("synthetic@example.invalid", "password", false)
        settled { it.page == AccountPage.ACCOUNT }
        assertFalse(consent.enabled)
        assertFalse(runtime.state.value.analyticsEnabled)
    }

    @Test fun signupMailServerFailureHasActionableCopyAndDoesNotGrantProductAccess() = runBlocking {
        runtime.restore().join()
        runtime.navigate(AccountPage.REGISTER)
        val savedModes = products.loadModes().modes
        response = "{\"error_code\":\"unexpected_failure\",\"msg\":\"private SMTP key/domain details\"}"
        status = HttpStatusCode.InternalServerError
        runtime.passwordSignIn("synthetic@example.invalid", "new-password", true).join()
        assertEquals(AccountPage.REGISTER, runtime.state.value.page)
        assertEquals("Account creation is temporarily unavailable. Please try again later.", runtime.state.value.message)
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertFalse(runtime.access.value.productAllowed)
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
        assertEquals(savedModes, products.loadModes().modes)
        assertFalse(runtime.state.value.busy)
        // The ordinary Create account action remains a valid retry after the fix.
        response = USER
        status = HttpStatusCode.OK
        runtime.passwordSignIn("synthetic@example.invalid", "new-password", true).join()
        assertTrue(runtime.state.value.message.orEmpty().contains("confirm your account"))
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
    }

    @Test fun signupFailuresNeverAskForANonexistentLinkOrMentionOfflineZap() {
        for (failure in ProviderFailure.entries) {
            val message = accountRequestFailureMessage(AccountPage.REGISTER, failure)
            assertFalse(message.contains("link", ignoreCase = true))
            assertFalse(message.contains("Zap", ignoreCase = true))
        }
        assertTrue(accountRequestFailureMessage(AccountPage.REGISTER, ProviderFailure.RATE_LIMITED).contains("wait"))
        assertFalse(accountRequestFailureMessage(AccountPage.REGISTER, ProviderFailure.UNAVAILABLE).contains("connection"))
        assertFalse(accountRequestFailureMessage(AccountPage.REGISTER, ProviderFailure.SERVER_ERROR).contains("connection"))
    }

    @Test fun accountReloadRetriesFailedAnalyticsOptOutWithoutReenablingCapture() = runBlocking {
        runtime.restore().join()
        runtime.setAnalytics(true).join()
        consent.fail = true
        runtime.setAnalytics(false).join()
        assertTrue(consent.enabled)
        assertFalse(runtime.state.value.analyticsEnabled)
        runtime.restore().join()
        assertFalse(runtime.state.value.analyticsEnabled)
        assertTrue(runtime.state.value.message.orEmpty().contains("Analytics stays off"))
        assertTrue(runtime.access.value.ready) // Offline app remains available.
        consent.fail = false
        runtime.restore().join()
        assertFalse(consent.enabled)
        assertFalse(runtime.state.value.analyticsEnabled)
        assertNull(runtime.state.value.message)
    }

    @Test fun repeatedOptInIsIdempotentEvenIfStorageLaterBecomesUnavailable() = runBlocking {
        runtime.restore().join()
        runtime.setAnalytics(true).join()
        val generation = runtime.analyticsGeneration()
        consent.fail = true
        runtime.setAnalytics(true).join()
        assertTrue(runtime.state.value.analyticsEnabled)
        assertEquals(generation, runtime.analyticsGeneration())
        assertNull(runtime.state.value.message) // Never falsely claims capture is off.
        consent.fail = false
    }

    @Test fun expiredDeletionCredentialsRefreshSameAccountThenFinishDeletion() = runBlocking {
        runtime.restore().join()
        response = TOKEN.replace("3600", "1")
        runtime.passwordSignIn("synthetic@example.invalid", "password", false).join()
        clockSeconds += 100
        handler = { request -> when (request.url.encodedPath) {
            "/auth/v1/token" -> TOKEN to HttpStatusCode.OK
            "/auth/v1/user" -> USER to HttpStatusCode.OK
            else -> "{}" to HttpStatusCode.OK
        } }
        requests.clear()
        withTimeout(5_000) { runtime.deleteAccount().join() }
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
        assertEquals(listOf("POST /auth/v1/token refresh_token", "GET /auth/v1/user ", "POST /functions/v1/delete-account "), requests.toList())
    }

    @Test fun rejectedRefreshOffersSameAccountReauthenticationWithoutErasingIntent() = runBlocking {
        runtime.restore().join()
        response = TOKEN.replace("3600", "1")
        runtime.passwordSignIn("synthetic@example.invalid", "password", false).join()
        clockSeconds += 100
        response = "{\"error_code\":\"refresh_token_not_found\",\"msg\":\"invalid\"}"
        status = HttpStatusCode.BadRequest
        withTimeout(5_000) { runtime.deleteAccount().join() }
        assertEquals(AccountPage.REAUTHENTICATE, runtime.state.value.page)
        assertTrue(runtime.state.value.deletionPending)
        assertEquals(AuthPhase.DELETE_REMOTE_PENDING, journal.snapshot().phase)
        assertEquals(A, journal.snapshot().accountId)
        status = HttpStatusCode.OK
        response = TOKEN.replace(A, B)
        runtime.reauthenticate("password").join()
        assertEquals(A, journal.snapshot().accountId)
        assertEquals(AuthPhase.DELETE_REMOTE_PENDING, journal.snapshot().phase)
        assertEquals(LocalWorkspaceId.parse(A), products.currentWorkspace())
        response = TOKEN
        runtime.reauthenticate("password").join()
        assertEquals(AccountPage.ACCOUNT, runtime.state.value.page)
        assertTrue(runtime.state.value.deletionPending)
        assertEquals(A, journal.snapshot().accountId)
    }

    @Test fun abandoningRecoveryForgetsTemporaryCredentialsAndCancelsOnlyThatFlow() = runBlocking {
        runtime.restore().join()
        handler = { request -> (if (request.url.encodedPath == "/auth/v1/token") TOKEN else "{}") to HttpStatusCode.OK }
        runtime.requestRecovery("synthetic@example.invalid").join()
        val id = flowStore.read()!!.id
        runtime.callback("${SupabaseAccountProvider.CALLBACK_URL}?state=$id&code=$A").join()
        assertEquals(AccountPage.UPDATE_PASSWORD, runtime.state.value.page)
        runtime.abandonRecovery(B).join() // An older/different Activity has no authority over this form.
        assertEquals(id, runtime.state.value.recoveryFlowId)
        runtime.abandonRecovery(id).join()
        assertNull(flowStore.read())
        assertEquals(AccountPage.SIGN_IN, runtime.state.value.page)
        val calls = requests.size
        runtime.updatePassword("new-password").join()
        assertEquals(calls, requests.size)
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
    }

    @Test fun closingActivityDuringExchangeDiscardsCredentialsWhenResponseArrives() = runBlocking {
        runtime.restore().join()
        handler = { request -> (if (request.url.encodedPath == "/auth/v1/token") TOKEN else "{}") to HttpStatusCode.OK }
        runtime.requestRecovery("synthetic@example.invalid").join()
        val id = flowStore.read()!!.id
        val started = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        beforeResponse = { request -> if (request.url.encodedPath == "/auth/v1/token") {
            started.complete(Unit)
            releaseResponse.await()
        } }
        val callback = "${SupabaseAccountProvider.CALLBACK_URL}?state=$id&code=$A"
        val exchange = runtime.callback(callback)
        withTimeout(5_000) { started.await() }
        assertNull(runtime.state.value.recoveryFlowId) // No rendered form yet.
        val cleanup = runtime.abandonRecovery(AuthPendingFlow.callbackFields(callback).getValue("state"))
        releaseResponse.complete(Unit)
        withTimeout(5_000) { exchange.join(); cleanup.join() }
        assertEquals(AccountPage.SIGN_IN, runtime.state.value.page)
        assertNull(runtime.state.value.recoveryFlowId)
        assertNull(flowStore.read())
        val calls = requests.size
        runtime.updatePassword("must-not-send").join()
        assertEquals(calls, requests.size)
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
    }

    @Test fun anotherLoginCannotRetainOldRecoveryCredentials() = runBlocking {
        runtime.restore().join()
        handler = { request -> (if (request.url.encodedPath == "/auth/v1/token") TOKEN else "{}") to HttpStatusCode.OK }
        runtime.requestRecovery("synthetic@example.invalid").join()
        val id = flowStore.read()!!.id
        runtime.callback("${SupabaseAccountProvider.CALLBACK_URL}?state=$id&code=$A").join()
        runtime.passwordSignIn("synthetic@example.invalid", "password", false).join()
        assertEquals(AccountPage.ACCOUNT, runtime.state.value.page)
        assertNull(flowStore.read())
        val calls = requests.size
        runtime.updatePassword("must-not-send").join()
        assertEquals(calls, requests.size)
    }

    @Test fun recoveryReauthenticationRejectsDifferentOwnerBeforePasswordMutation() = runBlocking {
        runtime.restore().join()
        runtime.passwordSignIn("synthetic@example.invalid", "password", false).join()
        handler = { request -> (if (request.url.encodedPath == "/auth/v1/token") TOKEN.replace(A, B) else "{}") to HttpStatusCode.OK }
        runtime.requestRecovery("ignored-other-email@example.invalid").join()
        val flow = flowStore.read()!!
        assertEquals(AuthFlowPurpose.REAUTH_RECOVERY, flow.purpose)
        runtime.callback("${SupabaseAccountProvider.CALLBACK_URL}?state=${flow.id}&code=$A").join()
        assertNotEquals(AccountPage.UPDATE_PASSWORD, runtime.state.value.page)
        val calls = requests.size
        runtime.updatePassword("must-not-send").join()
        assertEquals(calls, requests.size)
        assertEquals(A, journal.snapshot().accountId)
    }

    @Test fun claimedLinkNeedsExplicitRetryAfterRestart() = runBlocking {
        runtime.restore().join()
        val flow = flows.begin(AuthFlowPurpose.GOOGLE)
        flows.cache(flow).saveCodeVerifier("synthetic-verifier-abcdefghijklmnopqrstuvwxyz0123456789")
        flows.claimCallback("${flow.callbackUrl}&code=$A")
        requests.clear()
        val restarted = AccountRuntime(journal, AccountCoordinator(journal, products, analytics), flows, provider, analytics)
        restarted.restore().join()
        assertTrue(restarted.state.value.canRetryLink)
        assertTrue(requests.isEmpty())
        restarted.retryPendingLink().join()
        assertEquals(A, journal.snapshot().accountId)
        assertNull(flowStore.read())
    }

    @Test fun confirmedPasswordRecoveryUpdatesServerBeforeOpeningAccountWorkspace() = runBlocking {
        runtime.restore().join()
        handler = { request -> when (request.url.encodedPath) {
            "/auth/v1/token" -> TOKEN to HttpStatusCode.OK
            "/auth/v1/user" -> USER to HttpStatusCode.OK
            else -> "{}" to HttpStatusCode.OK
        } }
        runtime.requestRecovery("synthetic@example.invalid").join()
        val flow = flowStore.read()!!
        runtime.callback("${SupabaseAccountProvider.CALLBACK_URL}?state=${flow.id}&code=$A").join()
        assertEquals(AccountPage.UPDATE_PASSWORD, runtime.state.value.page)
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
        requests.clear()
        runtime.updatePassword("new-password").join()
        assertEquals(listOf("GET /auth/v1/user ", "PUT /auth/v1/user "), requests.toList())
        assertEquals(A, journal.snapshot().accountId)
        assertEquals(AccountPage.ACCOUNT, runtime.state.value.page)
        assertEquals(LocalWorkspaceId.parse(A), products.currentWorkspace())
        assertNull(flowStore.read())
        assertNull(runtime.state.value.recoveryFlowId)
        val calls = requests.size
        runtime.updatePassword("do-not-repeat").join()
        assertEquals(calls, requests.size)
    }

    @Test fun googleReauthenticationPreservesWorkspaceModesAndDeletionIntent() = runBlocking {
        runtime.restore().join()
        runtime.passwordSignIn("synthetic@example.invalid", "password", false).join()
        assertEquals(ModeMutationResult.SAVED,
            products.saveMode(products.loadModes().token!!, null, "Account sample", setOf("synthetic.account")))
        val savedModes = products.loadModes().modes
        status = HttpStatusCode.Unauthorized
        response = "{\"msg\":\"expired\"}"
        runtime.deleteAccount().join()
        assertEquals(AuthPhase.DELETE_REMOTE_PENDING, journal.snapshot().phase)
        runtime.google().join()
        val flow = flowStore.read()!!
        assertEquals(AuthFlowPurpose.REAUTH_GOOGLE, flow.purpose)
        response = TOKEN
        status = HttpStatusCode.OK
        runtime.callback("${SupabaseAccountProvider.CALLBACK_URL}?state=${flow.id}&code=$A").join()
        assertEquals(AuthPhase.DELETE_REMOTE_PENDING, journal.snapshot().phase)
        assertEquals(A, journal.snapshot().accountId)
        assertEquals(savedModes, products.loadModes().modes)
        assertTrue(runtime.state.value.deletionPending)
        assertTrue(runtime.access.value.ready)
        assertNull(flowStore.read())
    }

    @Test fun unconfirmedRegistrationNeverAssignsLocalDataOrPersistsSession() = runBlocking {
        runtime.restore().join()
        val savedModes = products.loadModes().modes
        response = USER
        runtime.passwordSignIn("synthetic@example.invalid", "new-password", true).join()
        assertEquals(AuthPhase.SIGNED_OUT, journal.snapshot().phase)
        assertEquals(AccountPage.SIGN_IN, runtime.state.value.page)
        assertTrue(runtime.state.value.message.orEmpty().contains("confirm your account"))
        assertEquals(LocalWorkspaceId.LOCAL, products.currentWorkspace())
        assertEquals(savedModes, products.loadModes().modes)
        assertTrue(runtime.access.value.ready)
        assertEquals(AuthFlowPurpose.REGISTRATION, flowStore.read()!!.purpose)
        assertNotNull(flowStore.read()!!.verifier)
    }

    @Test fun registrationConfirmationReturnsThroughBoundFlowAndAcceptsOnce() = runBlocking {
        runtime.restore().join()
        response = USER
        runtime.passwordSignIn("synthetic@example.invalid", "new-password", true).join()
        val flow = flowStore.read()!!
        val callback = "${SupabaseAccountProvider.CALLBACK_URL}?state=${flow.id}&code=$A"
        response = TOKEN
        runtime.callback(callback).join()
        assertEquals(A, journal.snapshot().accountId)
        assertEquals(AccountPage.ACCOUNT, runtime.state.value.page)
        assertEquals(LocalWorkspaceId.parse(A), products.currentWorkspace())
        assertNull(flowStore.read())
        val calls = requests.size
        runtime.callback(callback).join()
        assertEquals(calls, requests.size)
        assertEquals(A, journal.snapshot().accountId)
    }

    private suspend fun settled(predicate: (AccountScreenState) -> Boolean): AccountScreenState =
        withTimeout(5_000) { runtime.state.first { !it.busy && predicate(it) } }

    companion object {
        private const val A = "11111111-1111-4111-8111-111111111111"
        private const val B = "22222222-2222-4222-8222-222222222222"
        private val USER = org.json.JSONObject(TOKEN).getJSONObject("user").toString()
        private const val TOKEN = """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","token_type":"bearer","expires_in":3600,"user":{"id":"11111111-1111-4111-8111-111111111111","email":"synthetic@example.invalid","aud":"authenticated","created_at":"2026-01-01T00:00:00Z"}}"""
    }
}
