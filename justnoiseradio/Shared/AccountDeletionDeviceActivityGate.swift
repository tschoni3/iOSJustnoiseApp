import Foundation

struct AccountDeletionDeviceActivitySentinel {
    let defaults: UserDefaults

    var isEstablished: Bool {
        defaults.bool(forKey: SharedKeys.accountDeletionQuiescenceKey)
    }

    func establish() {
        defaults.set(true, forKey: SharedKeys.accountDeletionQuiescenceKey)
        defaults.synchronize()
    }

    func clearForAuthenticatedResume() {
        defaults.removeObject(forKey: SharedKeys.accountDeletionQuiescenceKey)
        defaults.synchronize()
    }
}

/// A cross-process deletion gate shared by the app and DeviceActivity extension.
///
/// The post-mutation check is intentional: an extension callback may pass its first
/// check immediately before the app establishes the deletion sentinel. If that race
/// occurs, the callback removes everything it just wrote and clears all shields.
struct AccountDeletionDeviceActivityGate {
    let defaults: UserDefaults
    let clearShields: () -> Void

    var isQuiesced: Bool {
        AccountDeletionDeviceActivitySentinel(defaults: defaults).isEstablished
    }

    @discardableResult
    func continueIfActive() -> Bool {
        guard isQuiesced == false else {
            clearDeletedAccountRuntimeState()
            return false
        }
        return true
    }

    @discardableResult
    func performMutationIfActive(_ mutation: () -> Void) -> Bool {
        guard continueIfActive() else { return false }
        mutation()

        guard isQuiesced == false else {
            clearDeletedAccountRuntimeState()
            return false
        }
        return true
    }

    @discardableResult
    func setIfActive(_ value: Any, forKey key: String) -> Bool {
        performMutationIfActive {
            defaults.set(value, forKey: key)
        }
    }

    @discardableResult
    func removeIfActive(_ key: String) -> Bool {
        performMutationIfActive {
            defaults.removeObject(forKey: key)
        }
    }

    private func clearDeletedAccountRuntimeState() {
        clearShields()
        for key in SharedKeys.accountOwnedTransientKeys {
            defaults.removeObject(forKey: key)
        }
        // This is a process-boundary latch, so make the extension's cleanup visible
        // to the app immediately. The deletion sentinel is deliberately retained.
        defaults.synchronize()
    }
}
