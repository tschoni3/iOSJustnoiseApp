package com.justnoise.app.auth

import android.app.Activity
import android.content.Intent
import com.justnoise.gate0.app.Gate0Application
import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.SessionStartAccess
import com.justnoise.gate0.data.SessionStartAccessHost
import com.justnoise.gate0.data.LocalDataImportOffer
import com.justnoise.gate0.data.LocalDataImportResult
import com.justnoise.gate0.feature.FocusSessionEvents
import com.justnoise.integrated.IntegratedAccountAccess
import com.justnoise.integrated.IntegratedAccountHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class AccountApplication : Gate0Application(), IntegratedAccountHost, FocusSessionEvents, SessionStartAccessHost {
    override val sessionStartAccess = SessionStartAccess(accountRequired = true)
    internal val accounts by lazy {
        val journal = androidAuthJournal(this)
        val analytics = AccountAnalytics(androidAnalyticsConsent(this), PostHogCaptureTransport())
        AccountRuntime(journal, AccountCoordinator(journal, Gate0Preferences(this), analytics),
            AuthPendingFlow(journal, androidAuthFlowStore(this)), SupabaseAccountProvider(), analytics)
    }
    override val accountAccess: StateFlow<IntegratedAccountAccess> get() = accounts.access
    override fun onCreate() { super.onCreate(); accounts.restore() }
    override fun openAccount(activity: Activity) { activity.startActivity(Intent(activity, AccountActivity::class.java)) }
    override fun focusEventGeneration() = accounts.analyticsGeneration()
    override fun recordFocusEvent(generation: Long, started: Boolean) = accounts.recordSession(generation, started)
}

internal enum class AccountPage { SIGN_IN, REGISTER, RECOVERY, UPDATE_PASSWORD, ACCOUNT, REAUTHENTICATE }
internal data class AccountScreenState(
    val busy: Boolean = true,
    val page: AccountPage = AccountPage.SIGN_IN,
    val email: String = "",
    val message: String? = null,
    val deletionPending: Boolean = false,
    val browserUrl: String? = null,
    val analyticsEnabled: Boolean = false,
    val reauthenticating: Boolean = false,
    val canRetryLink: Boolean = false,
    val recoveryFlowId: String? = null,
    val localImport: LocalDataImportOffer = LocalDataImportOffer(),
) {
    override fun toString() = "AccountScreenState($page, busy=$busy, [redacted])"
}

/** One application-owned account worker; Compose receives no credentials. Network work
 * never runs under the journal lock. Product UI is invalidated before a local handoff. */
internal class AccountRuntime(
    private val journal: AuthSessionJournal,
    private val coordinator: AccountCoordinator,
    private val flows: AuthPendingFlow,
    private val provider: SupabaseAccountProvider,
    private val analytics: AccountAnalytics,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AccountScreenState())
    val state = mutableState.asStateFlow()
    private val mutableAccess = MutableStateFlow(IntegratedAccountAccess())
    val access = mutableAccess.asStateFlow()
    private var recovery: Pair<AuthFlowClaim, StoredAuthSession>? = null

    fun restore() = operation {
        closeProductView()
        flows.purgeStale()
        val result = coordinator.restore()
        publish(result)
        if (result.outcome == AccountOutcome.READY && !analytics.restoreConsent()) {
            mutableState.value = mutableState.value.copy(message = ANALYTICS_SAVE_FAILURE)
        }
        mutableState.value = mutableState.value.copy(analyticsEnabled = analytics.isEnabled())
    }

    fun navigate(page: AccountPage) = operation {
        abandonRecoveryNow()
        val auth = journal.snapshot()
        when {
            auth.phase == AuthPhase.SIGNED_OUT && page in setOf(AccountPage.SIGN_IN, AccountPage.REGISTER, AccountPage.RECOVERY) ->
                mutableState.value = mutableState.value.copy(page = page, message = null, recoveryFlowId = null, reauthenticating = false)
            auth.phase in setOf(AuthPhase.SIGNED_IN, AuthPhase.DELETE_REMOTE_PENDING) &&
                page in setOf(AccountPage.REAUTHENTICATE, AccountPage.RECOVERY) ->
                mutableState.value = mutableState.value.copy(page = page, email = auth.email.orEmpty(),
                    recoveryFlowId = null, reauthenticating = true, message = null)
            else -> publish(coordinator.restore())
        }
    }

    /** A finishing Activity may cancel only the recovery form it actually displayed,
     * never another Activity's newer flow. Rotation does not call this. */
    fun abandonRecovery(expectedFlowId: String) = operation(enqueue = true) {
        if (recovery?.first?.flowId == expectedFlowId) {
            abandonRecoveryNow()
            publish(coordinator.restore())
        }
    }

    private fun abandonRecoveryNow() {
        val pending = recovery
        recovery = null // Forget transient credentials even if the tombstone write fails.
        pending?.let { flows.cancel(it.first.flowId) }
    }

    fun analyticsGeneration() = analytics.generation()

    fun deferLocalImport(expected: LocalDataImportOffer) = operation {
        val token = expected.token ?: return@operation
        val saved = coordinator.deferLocalImport(token)
        publish(coordinator.restore())
        if (!saved) mutableState.value = mutableState.value.copy(message = "This data choice could not be saved. Reload account state and try again.")
    }

    fun importLocalData(expected: LocalDataImportOffer) = operation {
        val token = expected.token ?: return@operation
        closeProductView()
        val imported = coordinator.importLocalData(token)
        publish(coordinator.restore())
        mutableState.value = mutableState.value.copy(message = when (imported) {
            LocalDataImportResult.IMPORTED -> null
            LocalDataImportResult.ACTIVE_SESSION -> "Stop your session with the Zap before importing local data."
            LocalDataImportResult.RECOVERY_REQUIRED -> "Finish app recovery before importing local data."
            LocalDataImportResult.CAPACITY -> "There is not enough mode or schedule capacity to merge everything. Nothing was imported. Free space in your account's lists, then retry."
            LocalDataImportResult.CONFLICT -> "Your account or data changed. Reload account state and confirm the import again."
            LocalDataImportResult.HISTORY_CONFLICT -> "A local session conflicts with an existing account record. Nothing was imported or overwritten; both records are kept. Contact support before retrying."
            LocalDataImportResult.UNAVAILABLE -> "These local data are no longer available to import."
            LocalDataImportResult.STORAGE_FAILED -> "The transfer could not finish. Do not clear app data. Reload account state to retry safely; nothing was reported as completed."
        })
    }
    fun recordSession(generation: Long, started: Boolean) = analytics.capture(
        if (started) ProductAnalyticsEvent.SESSION_STARTED else ProductAnalyticsEvent.SESSION_STOPPED, generation)
    fun setAnalytics(enabled: Boolean) = operation {
        // A stale confirmation from another Activity must not rewrite an already
        // granted choice or rotate its identity a second time.
        if (enabled && analytics.isEnabled()) return@operation
        if (enabled) require(mutableAccess.value.ready && journal.snapshot().phase in setOf(AuthPhase.SIGNED_OUT, AuthPhase.SIGNED_IN))
        val saved = if (enabled) analytics.optIn() else analytics.closeAndPurge()
        mutableState.value = mutableState.value.copy(analyticsEnabled = analytics.isEnabled(),
            message = if (saved) null else ANALYTICS_SAVE_FAILURE)
    }

    fun passwordSignIn(email: String, password: String, register: Boolean) = operation {
        abandonRecoveryNow()
        val snapshot = journal.snapshot()
        require(snapshot.phase == AuthPhase.SIGNED_OUT)
        val session = if (register) {
            val flow = flows.begin(AuthFlowPurpose.REGISTRATION)
            provider.register(email, password, flows.cache(flow), flow).session
        } else provider.signIn(email, password)
        if (session == null) {
            mutableState.value = AccountScreenState(page = AccountPage.SIGN_IN,
                message = "Check your email to confirm your account. Open the link on this phone within 30 minutes to return to Justnoise; otherwise confirm your email and sign in again.")
        } else {
            closeProductView()
            val result = coordinator.acceptSignIn(snapshot.operationToken, session, LocalDataChoice.KEEP_SEPARATE)
            flows.purgeStale()
            publish(result)
        }
    }

    fun requestRecovery(email: String) = operation {
        abandonRecoveryNow()
        val auth = journal.snapshot()
        val reauth = auth.phase != AuthPhase.SIGNED_OUT
        val flow = flows.begin(if (reauth) AuthFlowPurpose.REAUTH_RECOVERY else AuthFlowPurpose.RECOVERY)
        provider.requestRecovery(if (reauth) auth.email.orEmpty() else email, flows.cache(flow), flow)
        mutableState.value = mutableState.value.copy(message =
            "If this email has an account, a recovery link has been requested. Open the link on this phone within 30 minutes.")
    }

    fun google() = operation {
        abandonRecoveryNow()
        val flow = flows.begin(if (journal.snapshot().phase == AuthPhase.SIGNED_OUT) AuthFlowPurpose.GOOGLE else AuthFlowPurpose.REAUTH_GOOGLE)
        val url = provider.googleSignInUrl(flows.cache(flow), flow)
        mutableState.value = mutableState.value.copy(browserUrl = url)
    }

    fun browserHandled(url: String, opened: Boolean) {
        if (mutableState.value.browserUrl == url) mutableState.value = mutableState.value.copy(
            browserUrl = null, message = if (opened) "Complete sign-in in your browser." else "No browser could open sign-in. Please try again.")
    }

    fun callback(uri: String) = operation(enqueue = true) {
        val claim = flows.claimCallback(uri)
        exchangeClaim(claim)
    }

    fun retryPendingLink() = operation {
        abandonRecoveryNow()
        exchangeClaim(flows.resumeClaim())
    }

    private suspend fun exchangeClaim(claim: AuthFlowClaim) {
        val session = provider.exchangeCode(claim.code, flows.cache(AuthFlowHandle(claim.flowId, claim.purpose)))
        require(validClaimOwner(claim, session))
        if (claim.purpose.recovery) {
            // Recovery credentials stay memory-only until the user chooses a new password.
            recovery = claim to session
            mutableState.value = AccountScreenState(page = AccountPage.UPDATE_PASSWORD,
                reauthenticating = claim.purpose.reauthentication, recoveryFlowId = claim.flowId,
                deletionPending = journal.snapshot().phase == AuthPhase.DELETE_REMOTE_PENDING)
        } else acceptClaim(claim, session)
    }

    fun updatePassword(password: String) = operation {
        val (claim, session) = requireNotNull(recovery)
        require(validClaimOwner(claim, session))
        provider.updatePassword(session, password)
        require(validClaimOwner(claim, session))
        acceptClaim(claim, session)
    }

    private fun validClaimOwner(claim: AuthFlowClaim, session: StoredAuthSession): Boolean = journal.withAccountLock {
        flows.validateClaim(claim) && (!claim.purpose.reauthentication || journal.snapshot().accountId == session.accountId)
    }

    fun reauthenticate(password: String) = operation {
        abandonRecoveryNow()
        val snapshot = journal.snapshot()
        require(snapshot.phase in setOf(AuthPhase.SIGNED_IN, AuthPhase.DELETE_REMOTE_PENDING))
        val session = provider.signIn(snapshot.email.orEmpty(), password)
        publish(coordinator.acceptRefresh(snapshot.operationToken, session))
        flows.purgeStale()
    }

    private fun acceptClaim(claim: AuthFlowClaim, session: StoredAuthSession) {
        if (!claim.purpose.reauthentication) closeProductView()
        val result = journal.withAccountLock {
            if (!validClaimOwner(claim, session)) AccountResult(AccountOutcome.STALE)
            else if (claim.purpose.reauthentication) coordinator.acceptRefresh(claim.authToken, session)
            else coordinator.acceptSignIn(claim.authToken, session, LocalDataChoice.KEEP_SEPARATE)
        }
        if (result.outcome in setOf(AccountOutcome.READY, AccountOutcome.DELETION_PENDING)) {
            recovery = null
            publish(result)
            flows.consume(claim)
        } else {
            publish(result)
        }
    }

    fun signOut() = operation {
        closeProductView()
        val result = coordinator.signOut(journal.snapshot().operationToken)
        flows.purgeStale()
        recovery = null
        publish(result)
    }

    /** Explicit confirmation in the Activity precedes this call; retries do not start
     * a new deletion. Remote timeout never advances the journal to local erasure. */
    fun deleteAccount() = operation {
        var snapshot = journal.snapshot()
        if (snapshot.phase == AuthPhase.SIGNED_IN) {
            val begun = coordinator.beginDeletion(snapshot.operationToken)
            if (begun.outcome != AccountOutcome.DELETION_PENDING) { publish(begun); return@operation }
            closeProductView()
            publish(begun)
            snapshot = journal.snapshot()
        }
        if (snapshot.phase == AuthPhase.DELETE_REMOTE_PENDING) {
            var credentials = requireNotNull(journal.credentials(snapshot.operationToken))
            try {
                if (credentials.expiresAtEpochSeconds <= nowEpochSeconds() + 60) {
                    val renewed = provider.refresh(credentials)
                    val accepted = coordinator.acceptRefresh(snapshot.operationToken, renewed)
                    if (accepted.outcome != AccountOutcome.DELETION_PENDING) { publish(accepted); return@operation }
                    snapshot = journal.snapshot()
                    credentials = requireNotNull(journal.credentials(snapshot.operationToken))
                }
                provider.deleteAccount(credentials)
            } catch (failure: AccountProviderException) {
                if (failure.failure != ProviderFailure.REJECTED) throw failure
                publish(coordinator.restore())
                mutableState.value = mutableState.value.copy(page = AccountPage.REAUTHENTICATE, reauthenticating = true,
                    message = "Please confirm this same account again, then retry deletion. If it was already deleted, contact hello@justnoise.shop; a failed request is not confirmation of deletion.")
                return@operation
            }
            closeProductView()
            publish(coordinator.confirmRemoteDeletion(snapshot.operationToken))
        } else {
            closeProductView()
            publish(coordinator.restore())
        }
        flows.purgeStale()
        recovery = null
    }

    private fun closeProductView() {
        coordinator.closeProductAccess()
        mutableAccess.value = IntegratedAccountAccess(false, mutableAccess.value.generation + 1)
    }

    private fun publish(result: AccountResult) {
        val snapshot = result.view?.auth ?: journal.snapshot()
        if (result.outcome == AccountOutcome.READY) recovery = null
        mutableState.value = AccountScreenState(
            page = if (snapshot.phase == AuthPhase.SIGNED_OUT) AccountPage.SIGN_IN else AccountPage.ACCOUNT,
            email = snapshot.email.orEmpty(),
            deletionPending = snapshot.phase in setOf(AuthPhase.DELETE_REMOTE_PENDING, AuthPhase.DELETE_LOCAL_PENDING),
            analyticsEnabled = analytics.isEnabled(),
            canRetryLink = flows.hasPendingClaim(),
            localImport = if (result.view != null) coordinator.localImportOffer() else LocalDataImportOffer(),
            message = when (result.outcome) {
                AccountOutcome.READY -> null
                AccountOutcome.DELETION_PENDING -> "Account deletion is pending. Retry to finish it. New sessions are paused; an existing session can still be stopped with your Zap."
                AccountOutcome.ACTIVE_SESSION -> "Stop your session with the Zap before changing accounts, then try again."
                AccountOutcome.RECOVERY_REQUIRED -> "Finish app recovery in Setup before changing accounts."
                AccountOutcome.STALE -> "This account request is no longer current. Please try again."
                AccountOutcome.STORAGE_ERROR -> "Secure storage is unavailable. Nothing was reported as completed. Retry recovery."
                AccountOutcome.TELEMETRY_CLEANUP_REQUIRED -> "Pending analytics could not be cleared. Retry before changing accounts."
            },
        )
        if (result.view != null) mutableAccess.value = mutableAccess.value.copy(ready = true,
            signedIn = result.view.auth.phase == AuthPhase.SIGNED_IN)
        else {
            // A rejected idle-only handoff may leave the original profile valid. Reconcile
            // that profile without accepting a failed network operation as successful.
            val restored = coordinator.restore()
            mutableAccess.value = mutableAccess.value.copy(ready = restored.view != null,
                signedIn = restored.view?.auth?.phase == AuthPhase.SIGNED_IN)
        }
    }

    private fun operation(enqueue: Boolean = false, action: suspend () -> Unit): Job {
        return scope.launch {
            if (enqueue) mutex.lock() else if (!mutex.tryLock()) return@launch
            try {
                mutableState.value = mutableState.value.copy(busy = true, message = null)
                action()
            } catch (_: Exception) {
                if (!mutableAccess.value.ready) coordinator.denyProductAccess()
                mutableState.value = mutableState.value.copy(message =
                    "The request could not be completed. Check your connection or request a new link, then retry. Your offline Zap is unchanged.")
            } finally {
                mutableState.value = mutableState.value.copy(busy = false, analyticsEnabled = analytics.isEnabled(),
                    canRetryLink = recovery == null && flows.hasPendingClaim())
                mutex.unlock()
            }
        }
    }

    companion object {
        private const val ANALYTICS_SAVE_FAILURE = "Analytics preference could not be saved. Capture stays off for this process. Use Reload account state to retry saving it."
    }
}
