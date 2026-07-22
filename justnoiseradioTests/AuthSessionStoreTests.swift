import XCTest
@testable import justnoiseradio

@MainActor
final class AuthSessionStoreTests: XCTestCase {
    func testInitialSessionIsTheOnlyColdLaunchRoutingAuthority() async {
        let stream = AsyncStream<AuthSessionEvent>.makeStream()
        let store = AuthSessionStore(source: AuthSessionEventSource(events: stream.stream))

        XCTAssertEqual(store.state, .resolving)

        stream.continuation.yield(
            AuthSessionEvent(kind: .initialSession, account: nil)
        )
        await waitUntil { store.state == .signedOut }

        XCTAssertEqual(store.state, .signedOut)
        stream.continuation.finish()
    }

    func testSignInSignOutAndAccountSwitchFollowOrderedEvents() async {
        let first = AuthenticatedAccount(id: UUID(), email: "one@example.com")
        let second = AuthenticatedAccount(id: UUID(), email: "two@example.com")
        let stream = AsyncStream<AuthSessionEvent>.makeStream()
        let store = AuthSessionStore(source: AuthSessionEventSource(events: stream.stream))

        stream.continuation.yield(
            AuthSessionEvent(kind: .initialSession, account: first)
        )
        await waitUntil { store.state == .signedIn(first) }

        stream.continuation.yield(
            AuthSessionEvent(kind: .signedIn, account: second)
        )
        await waitUntil { store.state == .signedIn(second) }

        stream.continuation.yield(
            AuthSessionEvent(kind: .signedOut, account: nil)
        )
        await waitUntil { store.state == .signedOut }

        XCTAssertEqual(store.state, .signedOut)
        stream.continuation.finish()
    }

    func testConfirmedDeletionRejectsLateAuthenticatedEventsUntilLocalSignOut() async {
        let deleted = AuthenticatedAccount(id: UUID(), email: "deleted@example.com")
        let next = AuthenticatedAccount(id: UUID(), email: "next@example.com")
        let stream = AsyncStream<AuthSessionEvent>.makeStream()
        let store = AuthSessionStore(source: AuthSessionEventSource(events: stream.stream))

        stream.continuation.yield(
            AuthSessionEvent(kind: .initialSession, account: deleted)
        )
        await waitUntil { store.state == .signedIn(deleted) }

        store.transitionToSignedOutAfterConfirmedDeletion()
        stream.continuation.yield(
            AuthSessionEvent(kind: .tokenRefreshed, account: deleted)
        )
        await Task.yield()
        XCTAssertEqual(store.state, .signedOut)

        stream.continuation.yield(
            AuthSessionEvent(kind: .signedOut, account: nil)
        )
        await waitUntil { store.state == .signedOut }

        stream.continuation.yield(
            AuthSessionEvent(kind: .signedIn, account: next)
        )
        await waitUntil { store.state == .signedIn(next) }

        XCTAssertEqual(store.state, .signedIn(next))
        stream.continuation.finish()
    }

    private func waitUntil(
        _ predicate: @escaping @MainActor () -> Bool,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async {
        for _ in 0..<100 where predicate() == false {
            await Task.yield()
        }
        XCTAssertTrue(predicate(), file: file, line: line)
    }
}
