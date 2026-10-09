package com.justnoise.app.auth

import android.content.Context
import android.content.SharedPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID

/** Existing iOS event names selected by the owner; deliberately no arbitrary names/properties. */
internal enum class ProductAnalyticsEvent(val wireName: String) {
    SESSION_STARTED("focus_session_started"), SESSION_STOPPED("focus_session_ended"),
}

internal interface AnalyticsConsentStore {
    fun granted(): Boolean
    fun write(granted: Boolean): Boolean
}

// One epoch per OS process, never restored from disk and never transmitted.
private val analyticsProcessEpoch = UUID.randomUUID().toString()

internal fun androidAnalyticsConsent(context: Context): AnalyticsConsentStore = object : AnalyticsConsentStore {
    private val store by lazy {
        ProcessBoundAnalyticsConsent(
            context.getSharedPreferences("product_analytics_consent_v1", Context.MODE_PRIVATE),
            analyticsProcessEpoch,
        )
    }
    override fun granted() = store.granted()
    override fun write(granted: Boolean) = store.write(granted)
}

/** A failed opt-out may leave an old durable grant. A new process must never accept
 * it, even if storage is still unwritable. Activity recreation retains the epoch;
 * account changes use AccountAnalytics' existing in-process revocation barrier. */
internal class ProcessBoundAnalyticsConsent(
    private val preferences: SharedPreferences,
    private val processEpoch: String,
) : AnalyticsConsentStore {
    init { require(canonicalAccountId(processEpoch)) }
    override fun granted() = preferences.getString("process_grant_v2", null) == processEpoch
    override fun write(granted: Boolean) = preferences.edit()
        .remove("granted") // Never migrate the old, unscoped boolean into a grant.
        .putString("process_grant_v2", if (granted) processEpoch else null)
        .commit()
}

internal fun interface AnalyticsTransport {
    suspend fun send(event: ProductAnalyticsEvent, anonymousId: String)
}

/** No SDK autocapture, disk queue, automatic retries or person/identity calls. Uses
 * PostHog's documented capture endpoint with the existing public project token. */
internal class PostHogCaptureTransport(engine: HttpClientEngine? = null) : AnalyticsTransport {
    private val clientDelegate = lazy {
        if (engine == null) HttpClient(Android) { configure() } else HttpClient(engine) { configure() }
    }
    private val client by clientDelegate

    override suspend fun send(event: ProductAnalyticsEvent, anonymousId: String) {
        require(canonicalAccountId(anonymousId))
        client.post("https://us.i.posthog.com/i/v0/e/") {
            contentType(ContentType.Application.Json)
            setBody(JSONObject().put("api_key", PROJECT_TOKEN).put("event", event.wireName)
                .put("distinct_id", anonymousId)
                .put("properties", JSONObject().put("\$process_person_profile", false)).toString())
        }
    }

    private fun io.ktor.client.HttpClientConfig<*>.configure() {
        followRedirects = false
        expectSuccess = true
        install(HttpTimeout) { requestTimeoutMillis = 5_000; connectTimeoutMillis = 3_000; socketTimeoutMillis = 5_000 }
    }
    internal fun close() { if (clientDelegate.isInitialized()) client.close() }
    companion object {
        private const val PROJECT_TOKEN = "phc_getaoYoT7aGkEbiDW8oRlFijWJhLIIgJhYjquD6Be3e"
    }
}

/** Production account boundary, not a no-op. It cancels and joins every in-flight
 * capture before acknowledging an account change. No event payload survives process
 * death. Already delivered requests cannot be recalled. Calls that read/write consent
 * or close the boundary are worker-only; capture itself never blocks a session. */
internal class AccountAnalytics(
    private val consent: AnalyticsConsentStore,
    private val transport: AnalyticsTransport,
) : AccountTelemetryBoundary {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableSetOf<Job>()
    private var enabled = false
    private var restorationAllowed = true
    private var revocationPending = false
    private var anonymousId = UUID.randomUUID().toString()
    private var generation = 0L

    fun isEnabled(): Boolean = synchronized(lock) { enabled }
    fun generation(): Long = synchronized(lock) { generation }

    /** Only after successful account reconciliation; construction never starts capture. */
    fun restoreConsent(): Boolean = synchronized(lock) {
        if (revocationPending) {
            // Never resurrect the older disk opt-in after a failed opt-out write.
            // A user-requested account reload retries durability while capture stays off.
            enabled = false
            revocationPending = !runCatching { consent.write(false) }.getOrDefault(false)
            return@synchronized !revocationPending
        }
        if (restorationAllowed) {
            restorationAllowed = false
            enabled = runCatching { consent.granted() }.getOrDefault(false)
        }
        true
    }

    fun optIn(): Boolean = synchronized(lock) {
        restorationAllowed = false
        if (!runCatching { consent.write(true) }.getOrDefault(false)) return@synchronized false
        revocationPending = false
        anonymousId = UUID.randomUUID().toString()
        generation++
        enabled = true
        true
    }

    fun capture(event: ProductAnalyticsEvent, expectedGeneration: Long) = synchronized(lock) {
        if (!enabled || generation != expectedGeneration || jobs.size >= 8) return@synchronized
        val id = anonymousId
        val job = scope.launch(start = CoroutineStart.LAZY) {
            // Network failures are intentionally dropped; never replay across an account.
            try { transport.send(event, id) } catch (_: Exception) { /* no logs/payload persistence */ }
        }
        jobs += job
        job.invokeOnCompletion { synchronized(lock) { jobs -= job } }
        job.start()
    }

    override fun closeAndPurge(): Boolean {
        val pending: List<Job>
        val stored: Boolean
        synchronized(lock) {
            enabled = false
            restorationAllowed = false
            anonymousId = UUID.randomUUID().toString()
            generation++
            stored = runCatching { consent.write(false) }.getOrDefault(false)
            revocationPending = !stored
            pending = jobs.toList()
            pending.forEach { it.cancel() }
        }
        val stopped = runBlocking {
            withTimeoutOrNull(6_000) { pending.forEach { it.cancelAndJoin() }; true } ?: false
        }
        return stored && stopped
    }
}
