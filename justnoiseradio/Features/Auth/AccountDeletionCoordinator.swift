import DeviceActivity
import Foundation
import GoogleSignIn
import ManagedSettings
import UserNotifications

protocol AccountDeleting {
    func deleteAccount(accessToken: String?) async throws
}

extension AccountDeletionService: AccountDeleting {}

@MainActor
protocol AccountDeletionSystemEffecting: AnyObject {
    func quiesceRuntime()
    func clearRestrictionsAndMonitoring()
    func endLiveActivities() async
    func clearNotifications()
    func resetInMemoryState()
}

@MainActor
protocol AccountDeletionIdentityResetting: AnyObject {
    func resetIdentity() throws
}

@MainActor
protocol AccountDeletionAuthSigningOut: AnyObject {
    func signOutLocally() async
}

enum AccountDeletionCoordinationError: LocalizedError {
    case localCleanupIncomplete(markerCreationError: Error?, cleanupError: Error)
    case cleanupMarkerRemovalIncomplete(Error)

    var errorDescription: String? {
        switch self {
        case .localCleanupIncomplete:
            return "Your account was deleted, but some local files could not be removed yet. JustNoise will retry automatically."
        case .cleanupMarkerRemovalIncomplete:
            return "Your account data was removed, but cleanup could not be finalized yet. JustNoise will retry automatically."
        }
    }
}

/// Enforces the remote-first boundary for destructive account deletion.
///
/// No local state changes before the remote service confirms deletion. Once confirmed,
/// cleanup is deliberately fail-closed: the user is signed out and a durable marker remains
/// until every persisted account-owned file and preference has been removed.
@MainActor
final class AccountDeletionCoordinator {
    private let remoteDeleter: any AccountDeleting
    private let cleaner: LocalAccountDataCleaner
    private let systemEffects: any AccountDeletionSystemEffecting
    private let identityResetter: any AccountDeletionIdentityResetting
    private let authSignOut: any AccountDeletionAuthSigningOut
    private let setSignedOut: () -> Void

    init(
        remoteDeleter: any AccountDeleting,
        cleaner: LocalAccountDataCleaner,
        systemEffects: any AccountDeletionSystemEffecting,
        identityResetter: any AccountDeletionIdentityResetting,
        authSignOut: any AccountDeletionAuthSigningOut,
        setSignedOut: @escaping () -> Void
    ) {
        self.remoteDeleter = remoteDeleter
        self.cleaner = cleaner
        self.systemEffects = systemEffects
        self.identityResetter = identityResetter
        self.authSignOut = authSignOut
        self.setSignedOut = setSignedOut
    }

    func deleteAccount(accessToken: String?) async throws {
        // This must remain the first mutation boundary. Failed remote deletion preserves
        // every local preference, file, session, and active product state.
        try await remoteDeleter.deleteAccount(accessToken: accessToken)

        let markerCreationError = establishRetryMarker()
        try await completePendingLocalCleanup(markerCreationError: markerCreationError)
    }

    @discardableResult
    func retryPendingLocalCleanupIfNeeded() async throws -> Bool {
        guard cleaner.hasPendingCleanup else { return false }
        let markerCreationError = establishRetryMarker()
        try await completePendingLocalCleanup(markerCreationError: markerCreationError)
        return true
    }

    /// Returns an error only when no authoritative atomic marker could be established.
    /// A redundant defaults gate is set so launch still refuses to hydrate account data.
    private func establishRetryMarker() -> Error? {
        do {
            try cleaner.markCleanupPending()
            return nil
        } catch {
            cleaner.markFallbackCleanupPending()
            return error
        }
    }

    private func completePendingLocalCleanup(markerCreationError: Error?) async throws {
        systemEffects.quiesceRuntime()

        // Stop analytics before any persisted cleanup. PostHog's public close() does not
        // flush, and the separate marker keeps its disk purge fail-closed until a cold
        // pre-setup verification pass.
        var identityCleanupError: Error?
        do {
            try identityResetter.resetIdentity()
        } catch {
            identityCleanupError = error
        }

        systemEffects.clearRestrictionsAndMonitoring()
        await systemEffects.endLiveActivities()
        systemEffects.clearNotifications()
        systemEffects.resetInMemoryState()

        let cleanupResult: Result<Void, Error>
        do {
            try cleaner.purgePersistedAccountData()
            cleanupResult = .success(())
        } catch {
            cleanupResult = .failure(error)
        }

        await authSignOut.signOutLocally()
        setSignedOut()

        switch (cleanupResult, identityCleanupError) {
        case (.success, nil):
            do {
                try cleaner.clearCleanupMarker()
            } catch {
                cleaner.markFallbackCleanupPending()
                throw AccountDeletionCoordinationError.cleanupMarkerRemovalIncomplete(error)
            }
        case (.failure(let error), _):
            cleaner.markFallbackCleanupPending()
            throw AccountDeletionCoordinationError.localCleanupIncomplete(
                markerCreationError: markerCreationError,
                cleanupError: error
            )
        case (.success, .some(let error)):
            cleaner.markFallbackCleanupPending()
            throw AccountDeletionCoordinationError.localCleanupIncomplete(
                markerCreationError: markerCreationError,
                cleanupError: error
            )
        }
    }
}

@MainActor
final class LiveAccountDeletionSystemEffects: AccountDeletionSystemEffecting {
    private let nfcViewModel: NFCViewModel
    private let signalStore: SignalStore

    init(nfcViewModel: NFCViewModel, signalStore: SignalStore) {
        self.nfcViewModel = nfcViewModel
        self.signalStore = signalStore
    }

    func quiesceRuntime() {
        nfcViewModel.quiesceForAccountDeletion()
        signalStore.quiesceForAccountDeletion()
    }

    func clearRestrictionsAndMonitoring() {
        DeviceActivityBridge.quiesceForAccountDeletion()
        ManagedSettingsStore().clearAllSettings()
    }

    func endLiveActivities() async {
        await nfcViewModel.endLiveActivitiesForAccountDeletion()
    }

    func clearNotifications() {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }

    func resetInMemoryState() {
        nfcViewModel.resetInMemoryStateForAccountDeletion()
        signalStore.resetInMemoryStateForAccountDeletion()
    }
}

@MainActor
final class LiveAccountDeletionIdentityResetter: AccountDeletionIdentityResetting {
    func resetIdentity() throws {
        GIDSignIn.sharedInstance.signOut()
        try JustNoiseAnalyticsRuntime.shared.quiesceAndPurgeForAccountDeletion()
    }
}

@MainActor
final class LiveAccountDeletionAuthSignOut: AccountDeletionAuthSigningOut {
    func signOutLocally() async {
        // Supabase removes its local Keychain session before attempting the logout request.
        // The account is already remotely deleted, so a follow-up network error must not
        // keep the deleted account authenticated in the UI.
        try? await SupabaseManager.shared.client.auth.signOut(scope: .local)
    }
}
