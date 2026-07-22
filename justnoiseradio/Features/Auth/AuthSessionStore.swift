import Foundation
import Supabase

struct AuthenticatedAccount: Equatable, Sendable {
    let id: UUID
    let email: String?
}

enum JustNoiseAuthState: Equatable, Sendable {
    case resolving
    case signedOut
    case signedIn(AuthenticatedAccount)

    var authenticatedAccount: AuthenticatedAccount? {
        guard case .signedIn(let account) = self else { return nil }
        return account
    }
}

struct AuthSessionEvent: Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        case initialSession
        case passwordRecovery
        case signedIn
        case signedOut
        case tokenRefreshed
        case userUpdated
        case userDeleted
        case mfaChallengeVerified
    }

    let kind: Kind
    let account: AuthenticatedAccount?
}

struct AuthSessionEventSource {
    let events: AsyncStream<AuthSessionEvent>

    static func live(auth: AuthClient) -> AuthSessionEventSource {
        AuthSessionEventSource(
            events: AsyncStream { continuation in
                let task = Task {
                    for await change in auth.authStateChanges {
                        guard Task.isCancelled == false else { return }
                        let account = change.session.map {
                            AuthenticatedAccount(
                                id: $0.user.id,
                                email: $0.user.email
                            )
                        }
                        continuation.yield(
                            AuthSessionEvent(
                                kind: AuthSessionEvent.Kind(change.event),
                                account: account
                            )
                        )
                    }
                    continuation.finish()
                }

                continuation.onTermination = { _ in
                    task.cancel()
                }
            }
        )
    }

    static func constant(_ event: AuthSessionEvent) -> AuthSessionEventSource {
        AuthSessionEventSource(
            events: AsyncStream { continuation in
                continuation.yield(event)
                continuation.finish()
            }
        )
    }
}

private extension AuthSessionEvent.Kind {
    init(_ event: AuthChangeEvent) {
        switch event {
        case .initialSession:
            self = .initialSession
        case .passwordRecovery:
            self = .passwordRecovery
        case .signedIn:
            self = .signedIn
        case .signedOut:
            self = .signedOut
        case .tokenRefreshed:
            self = .tokenRefreshed
        case .userUpdated:
            self = .userUpdated
        case .userDeleted:
            self = .userDeleted
        case .mfaChallengeVerified:
            self = .mfaChallengeVerified
        }
    }
}

/// The only UI-facing authentication source of truth.
///
/// Supabase owns persisted session state. Views perform authentication operations, while
/// this store alone translates the SDK's ordered event stream into app routing state.
@MainActor
final class AuthSessionStore: ObservableObject {
    @Published private(set) var state: JustNoiseAuthState = .resolving

    private var observationTask: Task<Void, Never>?
    private var waitsForDeletionSignOutEvent = false

    init(source: AuthSessionEventSource) {
        observationTask = Task { [weak self] in
            for await event in source.events {
                guard Task.isCancelled == false else { return }
                self?.apply(event)
            }
        }
    }

    convenience init(auth: AuthClient = SupabaseManager.shared.client.auth) {
        self.init(source: .live(auth: auth))
    }

    deinit {
        observationTask?.cancel()
    }

    /// Remote account deletion is an authoritative signed-out outcome. Ignore any late
    /// authenticated SDK callback until its local sign-out event crosses the same boundary.
    func transitionToSignedOutAfterConfirmedDeletion() {
        waitsForDeletionSignOutEvent = true
        state = .signedOut
    }

    private func apply(_ event: AuthSessionEvent) {
        switch event.kind {
        case .signedOut, .userDeleted:
            waitsForDeletionSignOutEvent = false
            state = .signedOut

        case .initialSession:
            guard waitsForDeletionSignOutEvent == false else { return }
            state = event.account.map(JustNoiseAuthState.signedIn) ?? .signedOut

        case .signedIn:
            guard waitsForDeletionSignOutEvent == false else { return }
            state = event.account.map(JustNoiseAuthState.signedIn) ?? .signedOut

        case .passwordRecovery, .tokenRefreshed, .userUpdated, .mfaChallengeVerified:
            guard waitsForDeletionSignOutEvent == false,
                  let account = event.account else { return }
            state = .signedIn(account)
        }
    }
}
