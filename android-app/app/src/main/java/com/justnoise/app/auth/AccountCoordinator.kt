package com.justnoise.app.auth

import com.justnoise.gate0.data.Gate0Preferences
import com.justnoise.gate0.data.LocalWorkspaceId
import com.justnoise.gate0.data.WorkspaceReadToken
import com.justnoise.gate0.data.WorkspaceSwitchResult
import com.justnoise.gate0.data.LocalDataImportOffer
import com.justnoise.gate0.data.LocalDataImportResult
import com.justnoise.gate0.data.WorkspaceEditToken

/** Must durably stop delivery and purge old queued telemetry; never flush it on sign-out.
 * Runs on the account worker, outside the product-state lock. No main-thread waits. */
internal fun interface AccountTelemetryBoundary {
    fun closeAndPurge(): Boolean
}

/** An explicit choice, not an implicit migration of the owner's existing data. */
internal enum class LocalDataChoice { KEEP_SEPARATE }
internal enum class AccountOutcome { READY, DELETION_PENDING, STALE, ACTIVE_SESSION, RECOVERY_REQUIRED, STORAGE_ERROR, TELEMETRY_CLEANUP_REQUIRED }

internal class AccountView(val auth: AuthSnapshot, val workspace: WorkspaceReadToken) {
    override fun toString() = "AccountView(${auth.phase}, [redacted])"
}

internal class AccountResult(val outcome: AccountOutcome, val view: AccountView? = null) {
    override fun toString() = "AccountResult($outcome)"
}

/**
 * Production-only, synchronous WORKER API. Remote calls happen outside this class with
 * captured journal tokens. No provider callback can publish UI directly. Consumers must
 * discard retained product ViewModels/saved drafts at a workspace boundary. isCurrent
 * is also worker-only (it reads encrypted storage); UI delivery needs its own cancelled-
 * request/generation guard, not a blocking Keystore read on the main thread.
 *
 * A handoff writes product ownership first, with its durable barrier closed, then auth,
 * then releases the barrier. restore() reconciles a crash against the durable auth owner.
 * NFC/restriction code never takes the auth lock and never depends on network refresh.
 */
internal class AccountCoordinator(
    private val journal: AuthSessionJournal,
    private val products: Gate0Preferences,
    private val telemetry: AccountTelemetryBoundary,
) {
    fun closeProductAccess() = products.closeAccountSessionStarts()
    fun denyProductAccess() = products.denyAccountSessionStarts()

    fun restore(): AccountResult = guarded { adoptOnboardingLocalData(reconcile()) }

    fun localImportOffer(): LocalDataImportOffer = journal.withAccountLock {
        val auth = journal.snapshot()
        if (auth.phase != AuthPhase.SIGNED_IN) LocalDataImportOffer()
        else products.localDataImportOffer(LocalWorkspaceId.parse(requireNotNull(auth.accountId)))
    }

    fun deferLocalImport(expected: WorkspaceEditToken): Boolean = journal.withAccountLock {
        val auth = journal.snapshot()
        auth.phase == AuthPhase.SIGNED_IN && auth.accountId == expected.workspace.value &&
            products.deferLocalDataImport(expected)
    }

    fun importLocalData(expected: WorkspaceEditToken): LocalDataImportResult = journal.withAccountLock {
        val auth = journal.snapshot()
        if (auth.phase != AuthPhase.SIGNED_IN || auth.accountId != expected.workspace.value)
            return@withAccountLock LocalDataImportResult.CONFLICT
        products.closeAccountSessionStarts()
        products.importLocalData(expected)
    }

    fun acceptSignIn(expected: AuthOperationToken, session: StoredAuthSession, localData: LocalDataChoice): AccountResult = guarded {
        val current = journal.snapshot()
        if (current.operationToken != expected || current.phase != AuthPhase.SIGNED_OUT) return@guarded stale()
        check(localData == LocalDataChoice.KEEP_SEPARATE)
        // A previous failed operation must reconcile before accepting another provider response.
        if (products.workspaceTransitionPending() || products.currentWorkspace() != LocalWorkspaceId.LOCAL)
            return@guarded AccountResult(AccountOutcome.RECOVERY_REQUIRED)
        if (!telemetry.closeAndPurge()) return@guarded telemetryFailure()
        val staged = stage(LocalWorkspaceId.parse(session.accountId))
        if (staged != null) return@guarded staged
        if (!journal.acceptSignIn(expected, session)) return@guarded stale()
        adoptOnboardingLocalData(finish(journal.snapshot()))
    }

    fun signOut(expected: AuthOperationToken): AccountResult = guarded {
        val current = journal.snapshot()
        if (current.operationToken != expected || current.phase !in setOf(AuthPhase.SIGNED_IN, AuthPhase.SIGNED_OUT))
            return@guarded stale()
        if (!telemetry.closeAndPurge()) return@guarded telemetryFailure()
        val staged = stage(LocalWorkspaceId.LOCAL)
        if (staged != null) return@guarded staged
        if (!journal.signOut(expected)) return@guarded stale()
        finish(journal.snapshot())
    }

    fun acceptRefresh(expected: AuthOperationToken, session: StoredAuthSession): AccountResult = guarded {
        if (!journal.acceptRefresh(expected, session)) return@guarded stale()
        reconcile()
    }

    /** Persists retry intent before any remote deletion request. Does not erase local data. */
    fun beginDeletion(expected: AuthOperationToken): AccountResult = guarded {
        val current = journal.snapshot()
        if (current.operationToken != expected || current.phase != AuthPhase.SIGNED_IN) return@guarded stale()
        val blocked = idleFailure()
        if (blocked != null) return@guarded blocked
        if (!telemetry.closeAndPurge()) return@guarded telemetryFailure()
        if (!journal.beginDeletion(expected)) return@guarded stale()
        reconcile()
    }

    /** Provider adapter calls this only on verified remote success. Timeout is not success. */
    fun confirmRemoteDeletion(expected: AuthOperationToken): AccountResult = guarded {
        if (!journal.acknowledgeRemoteDeletion(expected)) return@guarded stale()
        // A session may have started while the request was in flight. In that case preserve
        // its workspace until the owner stops it; credentials are already durably removed.
        reconcile()
    }

    /** No credentials or private product data in a UI projection; a late result is rejected. */
    fun isCurrent(view: AccountView): Boolean = journal.withAccountLock {
        runCatching {
            journal.snapshot().operationToken == view.auth.operationToken && products.isWorkspaceCurrent(view.workspace)
        }.getOrDefault(false)
    }

    private fun reconcile(): AccountResult {
        val auth = journal.snapshot()
        val deleting = auth.phase == AuthPhase.DELETE_LOCAL_PENDING
        val target = if (auth.phase == AuthPhase.SIGNED_OUT || deleting) LocalWorkspaceId.LOCAL
            else LocalWorkspaceId.parse(requireNotNull(auth.accountId))
        if (!products.resumeLocalDataImport(target)) return storageFailure()
        val needsHandoff = deleting || products.workspaceTransitionPending() || products.currentWorkspace() != target
        if (needsHandoff || auth.phase == AuthPhase.DELETE_REMOTE_PENDING) {
            // A cleanup failure must not open the destination or silently enable telemetry.
            if (!telemetry.closeAndPurge()) return telemetryFailure()
        }
        if (needsHandoff) {
            val staged = stage(target, forceHandoff = deleting)
            if (staged != null) return staged
        }
        if (deleting) {
            val token = products.pendingWorkspaceSwitch() ?: return storageFailure()
            if (!products.eraseAccountWorkspace(token, LocalWorkspaceId.parse(requireNotNull(auth.accountId))))
                return storageFailure()
            if (!journal.completeLocalDeletion(auth.operationToken)) return stale()
        }
        return finish(journal.snapshot())
    }

    /** Returns an error, or null when the selected workspace is ready to finish. */
    private fun stage(target: LocalWorkspaceId, forceHandoff: Boolean = false): AccountResult? {
        val pending = products.pendingWorkspaceSwitch()
        if (pending != null && pending.workspace == target) return null
        if (pending == null && products.currentWorkspace() == target && !forceHandoff) return null
        val edit = pending ?: products.prepareWorkspaceSwitch() ?: return idleFailure() ?: storageFailure()
        return when (products.switchWorkspace(edit, target, forceHandoff)) {
            WorkspaceSwitchResult.SWITCHED_PENDING, WorkspaceSwitchResult.UNCHANGED -> null
            WorkspaceSwitchResult.ACTIVE_SESSION -> AccountResult(AccountOutcome.ACTIVE_SESSION)
            WorkspaceSwitchResult.RECOVERY_REQUIRED -> AccountResult(AccountOutcome.RECOVERY_REQUIRED)
            WorkspaceSwitchResult.CONFLICT -> stale()
            WorkspaceSwitchResult.STORAGE_FAILED -> storageFailure()
        }
    }

    private fun finish(auth: AuthSnapshot): AccountResult {
        val expectedOwner = if (auth.phase == AuthPhase.SIGNED_OUT) LocalWorkspaceId.LOCAL
            else LocalWorkspaceId.parse(requireNotNull(auth.accountId))
        if (auth.phase == AuthPhase.DELETE_LOCAL_PENDING || products.currentWorkspace() != expectedOwner) return storageFailure()
        if (products.workspaceTransitionPending()) {
            val pending = products.pendingWorkspaceSwitch() ?: return storageFailure()
            if (!products.completeWorkspaceSwitch(pending)) return storageFailure()
        }
        val read = products.workspaceReadToken() ?: return storageFailure()
        if (auth.phase == AuthPhase.SIGNED_IN && !products.authorizeAccountSessionStarts(expectedOwner))
            return storageFailure()
        if (auth.phase != AuthPhase.SIGNED_IN) products.denyAccountSessionStarts()
        return AccountResult(if (auth.phase == AuthPhase.DELETE_REMOTE_PENDING) AccountOutcome.DELETION_PENDING else AccountOutcome.READY,
            AccountView(auth, read))
    }

    private fun idleFailure(): AccountResult? {
        val state = products.snapshot()
        return when {
            state.desiredActive -> AccountResult(AccountOutcome.ACTIVE_SESSION)
            state.recoveryRequired -> AccountResult(AccountOutcome.RECOVERY_REQUIRED)
            else -> null
        }
    }

    /**
     * First-run modes are written to the LOCAL workspace before required sign-in.
     * KEEP_SEPARATE normally leaves them there until an explicit import, which made
     * the mode created in "Create your first mode" disappear from Home after login.
     * If onboarding already completed and the signed-in workspace is still empty,
     * adopt that local first mode (and its selection) automatically, like iOS.
     */
    private fun adoptOnboardingLocalData(result: AccountResult): AccountResult {
        if (result.outcome != AccountOutcome.READY || result.view == null) return result
        if (!products.hasCompletedOnboarding()) return result
        val catalog = runCatching { products.loadModes() }.getOrNull() ?: return result
        if (catalog.error || catalog.modes.isNotEmpty()) return result
        val auth = journal.snapshot()
        if (auth.phase != AuthPhase.SIGNED_IN) return result
        val offer = products.localDataImportOffer(LocalWorkspaceId.parse(requireNotNull(auth.accountId)))
        val token = offer.token ?: return result
        if (!offer.shouldPrompt) return result
        products.importLocalData(token)
        return finish(journal.snapshot())
    }

    private fun guarded(action: () -> AccountResult): AccountResult = journal.withAccountLock {
        products.closeAccountSessionStarts()
        val result = try { action() } catch (_: Exception) { storageFailure() }
        if (result.view == null) products.denyAccountSessionStarts()
        result
    }
    private fun stale() = AccountResult(AccountOutcome.STALE)
    private fun storageFailure() = AccountResult(AccountOutcome.STORAGE_ERROR)
    private fun telemetryFailure() = AccountResult(AccountOutcome.TELEMETRY_CLEANUP_REQUIRED)
}
