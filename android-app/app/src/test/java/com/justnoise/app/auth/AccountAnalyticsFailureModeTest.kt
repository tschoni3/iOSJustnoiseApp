package com.justnoise.app.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import com.justnoise.gate0.data.MemoryPreferences

/** Isolated failure-mode checks. No real HTTP, Android storage or owner device. */
class AccountAnalyticsFailureModeTest {
    @Test fun bothAllowedEventsUseTheSameMinimalPayloadContract() = runBlocking {
        val delivered = mutableListOf<String>()
        val transport = PostHogCaptureTransport(MockEngine { request ->
            val payload = JSONObject((request.body as TextContent).text)
            assertEquals(setOf("api_key", "event", "distinct_id", "properties"),
                payload.keys().asSequence().toSet())
            assertEquals(ID, payload.getString("distinct_id"))
            val properties = payload.getJSONObject("properties")
            assertEquals(setOf("\$process_person_profile"), properties.keys().asSequence().toSet())
            assertFalse(properties.getBoolean("\$process_person_profile"))
            assertNull(request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
            delivered += payload.getString("event")
            respond("{}", HttpStatusCode.OK)
        })
        try {
            ProductAnalyticsEvent.entries.forEach { transport.send(it, ID) }
            assertEquals(listOf("focus_session_started", "focus_session_ended"), delivered)
        } finally { transport.close() }
    }

    @Test fun optOutCancelsAnActualKtorMockRequestBeforeReturning() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val transport = PostHogCaptureTransport(MockEngine {
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        })
        val analytics = AccountAnalytics(TestAnalyticsConsent(), transport)
        try {
            assertTrue(analytics.optIn())
            analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, analytics.generation())
            withTimeout(5_000) { started.await() }
            assertTrue(analytics.closeAndPurge())
            assertTrue(cancelled.isCompleted)
            assertFalse(analytics.isEnabled())
        } finally { analytics.closeAndPurge(); transport.close() }
    }

    @Test fun saturatedCaptureDropsExcessWithoutLeavingQueuedWork() = runBlocking {
        val entered = AtomicInteger()
        val finished = AtomicInteger()
        val allEntered = CompletableDeferred<Unit>()
        val analytics = AccountAnalytics(TestAnalyticsConsent()) { _, _ ->
            if (entered.incrementAndGet() == 8) allEntered.complete(Unit)
            try { awaitCancellation() } finally { finished.incrementAndGet() }
        }
        try {
            assertTrue(analytics.optIn())
            val generation = analytics.generation()
            repeat(8) { analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, generation) }
            withTimeout(5_000) { allEntered.await() }
            repeat(32) { analytics.capture(ProductAnalyticsEvent.SESSION_STOPPED, generation) }
            assertEquals(8, entered.get())
            assertTrue(analytics.closeAndPurge())
            assertEquals(8, finished.get())
            assertEquals(8, entered.get())
            analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, generation)
            assertEquals(8, entered.get())
        } finally { analytics.closeAndPurge() }
    }

    @Test fun httpRejectionsAreFailuresAndAreNotRetried() = runBlocking {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.TooManyRequests,
            HttpStatusCode.InternalServerError)) {
            val calls = AtomicInteger()
            val transport = PostHogCaptureTransport(MockEngine {
                calls.incrementAndGet()
                respond("{}", status)
            })
            try {
                assertTrue("Expected HTTP $status to fail", runCatching {
                    transport.send(ProductAnalyticsEvent.SESSION_STARTED, ID)
                }.isFailure)
                assertEquals(1, calls.get())
            } finally { transport.close() }
        }
    }

    @Test fun offlineTransportFailureDoesNotEscapeCaptureOrRetryAfterCleanup() = runBlocking {
        val attempted = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val analytics = AccountAnalytics(TestAnalyticsConsent()) { _, _ ->
            calls.incrementAndGet()
            attempted.complete(Unit)
            throw IOException("Synthetic offline transport")
        }
        try {
            assertTrue(analytics.optIn())
            analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, analytics.generation())
            withTimeout(5_000) { attempted.await() }
            assertTrue(analytics.closeAndPurge())
            assertEquals(1, calls.get())
            assertFalse(analytics.isEnabled())
        } finally { analytics.closeAndPurge() }
    }

    @Test fun invalidIdentityIsRejectedBeforeTransport() = runBlocking {
        val calls = AtomicInteger()
        val transport = PostHogCaptureTransport(MockEngine {
            calls.incrementAndGet()
            respond("{}", HttpStatusCode.OK)
        })
        try {
            for (invalid in listOf("", "synthetic@example.invalid", "not-a-uuid")) {
                val error = runCatching {
                    transport.send(ProductAnalyticsEvent.SESSION_STARTED, invalid)
                }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException)
            }
            assertEquals(0, calls.get())
        } finally { transport.close() }
    }

    @Test fun unreadableConsentRestoresDisabled() {
        val calls = AtomicInteger()
        val store = object : AnalyticsConsentStore {
            override fun granted(): Boolean = throw IOException("Synthetic storage read failure")
            override fun write(granted: Boolean) = true
        }
        val analytics = AccountAnalytics(store) { _, _ -> calls.incrementAndGet() }
        try {
            assertTrue(analytics.restoreConsent())
            assertFalse(analytics.isEnabled())
            analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, analytics.generation())
            assertEquals(0, calls.get())
        } finally { analytics.closeAndPurge() }
    }

    @Test fun failedInitialOptInNeverEnablesCapture() {
        val calls = AtomicInteger()
        val store = TestAnalyticsConsent().apply { fail = true }
        val analytics = AccountAnalytics(store) { _, _ -> calls.incrementAndGet() }
        try {
            assertFalse(analytics.optIn())
            assertFalse(analytics.isEnabled())
            analytics.capture(ProductAnalyticsEvent.SESSION_STARTED, analytics.generation())
            assertEquals(0, calls.get())
        } finally { store.fail = false; analytics.closeAndPurge() }
    }

    @Test fun successfulRevocationRemainsOffInNewProcessInstance() {
        val store = TestAnalyticsConsent()
        val first = AccountAnalytics(store) { _, _ -> error("No capture requested") }
        assertTrue(first.optIn())
        assertTrue(first.closeAndPurge())
        val restarted = AccountAnalytics(store) { _, _ -> error("No capture requested") }
        try {
            assertTrue(restarted.restoreConsent())
            assertFalse(restarted.isEnabled())
        } finally { restarted.closeAndPurge() }
    }

    @Test fun processRestartRequiresNewConsentAndRotatesIdentity() = runBlocking {
        val preferences = MemoryPreferences()
        val store = ProcessBoundAnalyticsConsent(preferences, ID)
        val firstId = CompletableDeferred<String>()
        val secondId = CompletableDeferred<String>()
        val first = AccountAnalytics(store) { _, id -> firstId.complete(id) }
        assertTrue(first.optIn())
        first.capture(ProductAnalyticsEvent.SESSION_STARTED, first.generation())
        val before = withTimeout(5_000) { firstId.await() }
        // A new instance sharing the durable store models process reconstruction.
        // Do not revoke first: process death is not an explicit user opt-out.
        val restarted = AccountAnalytics(ProcessBoundAnalyticsConsent(preferences, NEXT_PROCESS)) { _, id -> secondId.complete(id) }
        try {
            assertTrue(restarted.restoreConsent())
            assertFalse(restarted.isEnabled())
            restarted.capture(ProductAnalyticsEvent.SESSION_STOPPED, restarted.generation())
            assertFalse(secondId.isCompleted)
            assertTrue(restarted.optIn())
            restarted.capture(ProductAnalyticsEvent.SESSION_STOPPED, restarted.generation())
            val after = withTimeout(5_000) { secondId.await() }
            assertNotEquals(before, after)
        } finally { first.closeAndPurge(); restarted.closeAndPurge() }
    }

    @Test fun failedRevocationCannotRestoreOldConsentAfterProcessRestart() {
        val preferences = MemoryPreferences()
        val store = ProcessBoundAnalyticsConsent(preferences, ID)
        val first = AccountAnalytics(store) { _, _ -> error("No capture requested") }
        assertTrue(first.optIn())
        // Snapshot durable content before the simulated failed disk commit.
        // SharedPreferences may update memory even when its disk write fails.
        val durableGrant = preferences.getString("process_grant_v2", null)
        preferences.failNextCommit = true
        assertFalse(first.closeAndPurge())
        assertFalse(first.isEnabled())
        val reopenedDisk = MemoryPreferences().apply {
            edit().putString("process_grant_v2", durableGrant).commit()
        }
        val restarted = AccountAnalytics(ProcessBoundAnalyticsConsent(reopenedDisk, NEXT_PROCESS)) { _, _ ->
            error("Old consent must not authorize capture")
        }
        try {
            assertTrue(restarted.restoreConsent())
            assertFalse(restarted.isEnabled())
            restarted.capture(ProductAnalyticsEvent.SESSION_STARTED, restarted.generation())
        } finally {
            first.closeAndPurge()
            restarted.closeAndPurge()
        }
    }

    companion object {
        private const val ID = "11111111-1111-4111-8111-111111111111"
        private const val NEXT_PROCESS = "22222222-2222-4222-8222-222222222222"
    }
}
