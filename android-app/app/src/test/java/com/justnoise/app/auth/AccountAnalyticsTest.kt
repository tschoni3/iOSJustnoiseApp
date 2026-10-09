package com.justnoise.app.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class AccountAnalyticsTest {
    @Test fun noCaptureBeforeExplicitConsentOrFromOldGeneration() = runBlocking {
        val delivered = CopyOnWriteArrayList<String>()
        val ready = CompletableDeferred<Unit>()
        val store = TestAnalyticsConsent()
        val analytics = AccountAnalytics(store) { event, _ -> delivered += event.wireName; ready.complete(Unit) }
        val old = analytics.generation()
        analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, old)
        assertTrue(analytics.optIn())
        analytics.capture(ProductAnalyticsEvent.SESSION_STOPPED, old)
        analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, analytics.generation())
        withTimeout(5_000) { ready.await() }
        assertTrue(analytics.closeAndPurge())
        assertEquals(listOf("focus_session_started"), delivered.toList())
        assertFalse(store.enabled)
    }

    @Test fun accountBoundaryCancelsAndJoinsInFlightRequestsBeforeReturning() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()
        val analytics = AccountAnalytics(TestAnalyticsConsent()) { _, _ ->
            started.complete(Unit)
            try { awaitCancellation() } finally { ended.complete(Unit) }
        }
        assertTrue(analytics.optIn())
        analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, analytics.generation())
        withTimeout(5_000) { started.await() }
        assertTrue(analytics.closeAndPurge())
        assertTrue(ended.isCompleted)
        assertFalse(analytics.isEnabled())
    }

    @Test fun consentWriteFailureIsNotReportedAsSuccessfulCleanup() {
        val store = TestAnalyticsConsent()
        val analytics = AccountAnalytics(store) { _, _ -> error("No event expected") }
        assertTrue(analytics.optIn())
        store.fail = true
        assertFalse(analytics.closeAndPurge())
        assertFalse(analytics.isEnabled())
        assertFalse(analytics.optIn())
        store.fail = false
        assertTrue(analytics.closeAndPurge())
    }

    @Test fun failedRevocationCannotBeUndoneByRepeatedConsentRestoration() {
        val store = TestAnalyticsConsent()
        val analytics = AccountAnalytics(store) { _, _ -> error("Revoked capture must remain off") }
        assertTrue(analytics.optIn())
        store.fail = true
        assertFalse(analytics.closeAndPurge())
        assertTrue(store.enabled) // Older durable consent survived the simulated failure.
        repeat(2) {
            assertFalse(analytics.restoreConsent())
            assertFalse(analytics.isEnabled())
            analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, analytics.generation())
        }
        store.fail = false
        assertTrue(analytics.restoreConsent())
        assertFalse(store.enabled)
        assertFalse(analytics.isEnabled())
        assertTrue(analytics.restoreConsent())
        assertFalse(analytics.isEnabled())
        assertTrue(analytics.optIn()) // Only a new explicit successful opt-in may enable.
        assertTrue(analytics.isEnabled())
        assertTrue(analytics.closeAndPurge())
    }

    @Test fun newAccountConsentDoesNotReusePreviousAnonymousIdentity() = runBlocking {
        val ids = CopyOnWriteArrayList<String>()
        var received = CompletableDeferred<Unit>()
        val analytics = AccountAnalytics(TestAnalyticsConsent()) { _, id -> ids += id; received.complete(Unit) }
        assertTrue(analytics.optIn())
        analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, analytics.generation())
        withTimeout(5_000) { received.await() }
        assertTrue(analytics.closeAndPurge())
        received = CompletableDeferred()
        assertTrue(analytics.optIn())
        analytics.capture(ProductAnalyticsEvent.SESSION_STOPPED, analytics.generation())
        withTimeout(5_000) { received.await() }
        assertTrue(analytics.closeAndPurge())
        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
    }

    @Test fun restoredConsentRequiresExplicitRuntimeReconciliation() {
        val store = TestAnalyticsConsent().apply { enabled = true }
        val analytics = AccountAnalytics(store) { _, _ -> error("No event expected") }
        assertFalse(analytics.isEnabled())
        analytics.restoreConsent()
        assertTrue(analytics.isEnabled())
        assertTrue(analytics.closeAndPurge())
    }

    @Test fun postHogPayloadContainsOnlyExistingEventAndUnidentifiedRandomId() = runBlocking {
        val transport = PostHogCaptureTransport(MockEngine { request ->
            assertEquals("https://us.i.posthog.com/i/v0/e/", request.url.toString())
            assertEquals("POST", request.method.value)
            val payload = JSONObject((request.body as TextContent).text)
            assertEquals(setOf("api_key", "event", "distinct_id", "properties"), payload.keys().asSequence().toSet())
            assertEquals("focus_session_ended", payload.getString("event"))
            assertEquals(ID, payload.getString("distinct_id"))
            assertEquals(setOf("\$process_person_profile"), payload.getJSONObject("properties").keys().asSequence().toSet())
            assertFalse(payload.getJSONObject("properties").getBoolean("\$process_person_profile"))
            assertNull(request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
            respond("{}", HttpStatusCode.OK)
        })
        try { transport.send(ProductAnalyticsEvent.SESSION_STOPPED, ID) } finally { transport.close() }
    }

    @Test fun ingestionRedirectIsNotFollowed() = runBlocking {
        var calls = 0
        val transport = PostHogCaptureTransport(MockEngine {
            calls++
            respond("", HttpStatusCode.TemporaryRedirect, io.ktor.http.headersOf("Location", "https://example.invalid/collect"))
        })
        try { runCatching { transport.send(ProductAnalyticsEvent.SESSION_STARTED, ID) } } finally { transport.close() }
        assertEquals(1, calls)
    }
    companion object { private const val ID = "11111111-1111-4111-8111-111111111111" }
}

internal class TestAnalyticsConsent : AnalyticsConsentStore {
    var enabled = false
    var fail = false
    override fun granted() = enabled
    override fun write(granted: Boolean): Boolean {
        if (fail) return false
        enabled = granted
        return true
    }
}
