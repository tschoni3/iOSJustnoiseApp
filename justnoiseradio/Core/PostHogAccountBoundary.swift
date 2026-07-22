import Foundation
import PostHog

enum PostHogAccountDataCleanupError: LocalizedError, Equatable {
    case unsafePath(URL)

    var errorDescription: String? {
        switch self {
        case .unsafePath(let url):
            return "Refusing to remove analytics data outside its app-owned boundary: \(url.path)"
        }
    }
}

/// File-backed cleanup for the storage layout used by the pinned PostHog iOS 3.34 SDK.
/// PostHog has no public queue-clear API, so this adapter removes only its validated
/// API-key directory and an exact allowlist of legacy root-level storage names.
struct PostHogAccountDataCleaner {
    static let cleanupFallbackDefaultsKey = "jn_pending_posthog_account_cleanup_fallback_v1"
    static let cleanupMarkerFileName = "pending-posthog-account-cleanup-v1"

    static let legacyStorageNames: Set<String> = [
        "posthog.distinctId",
        "posthog.anonymousId",
        "posthog.queueFolder",
        "posthog.queue.plist",
        "posthog.replayFolder",
        "posthog.enabledFeatureFlags",
        "posthog.enabledFeatureFlagPayloads",
        "posthog.flags",
        "posthog.groups",
        "posthog.registerProperties",
        "posthog.optOut",
        "posthog.sessionReplay",
        "posthog.isIdentified",
        "posthog.enabledPersonProcessing",
        "posthog.remoteConfig",
        "posthog.surveySeen",
        "posthog.requestId",
        "posthog.personPropertiesForFlags",
        "posthog.groupPropertiesForFlags",
    ]

    private let standardDefaults: UserDefaults
    private let fileSystem: AccountDeletionFileSystem
    private let markerStore: AccountDeletionRetryMarkerStore
    private let applicationSupportDirectory: URL
    private let bundleIdentifier: String
    private let apiKey: String

    init(
        standardDefaults: UserDefaults = .standard,
        fileSystem: AccountDeletionFileSystem = .live,
        markerFileSystem: AccountDeletionMarkerFileSystem = .live,
        applicationSupportDirectory: URL? = nil,
        bundleIdentifier: String? = nil,
        apiKey: String
    ) {
        let resolvedApplicationSupport = applicationSupportDirectory
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let resolvedBundleIdentifier = bundleIdentifier
            ?? Bundle.main.bundleIdentifier
            ?? "stilltschoni.justnoiseradio"

        self.standardDefaults = standardDefaults
        self.fileSystem = fileSystem
        markerStore = AccountDeletionRetryMarkerStore(
            applicationSupportDirectory: resolvedApplicationSupport,
            markerFileName: Self.cleanupMarkerFileName,
            fileSystem: markerFileSystem
        )
        self.applicationSupportDirectory = resolvedApplicationSupport
        self.bundleIdentifier = resolvedBundleIdentifier
        self.apiKey = apiKey
    }

    var hasPendingCleanup: Bool {
        markerStore.exists
            || standardDefaults.bool(forKey: Self.cleanupFallbackDefaultsKey)
    }

    var cleanupMarkerURL: URL {
        markerStore.markerURL
    }

    var storageBaseDirectory: URL {
        applicationSupportDirectory.appendingPathComponent(
            bundleIdentifier,
            isDirectory: true
        )
    }

    var apiKeyDirectory: URL {
        storageBaseDirectory.appendingPathComponent(apiKey, isDirectory: true)
    }

    /// Establish a second, analytics-specific marker. It remains after same-process
    /// deletion so a later cold launch can remove files from any late SDK callback
    /// before PostHog is set up again.
    func establishCleanupMarker() {
        do {
            try markerStore.create()
        } catch {
            standardDefaults.set(true, forKey: Self.cleanupFallbackDefaultsKey)
        }
    }

    func clearCleanupMarker() throws {
        try markerStore.remove()
        standardDefaults.removeObject(forKey: Self.cleanupFallbackDefaultsKey)
    }

    func purgePersistedData() throws {
        try validateBoundaries()

        if fileSystem.fileExists(apiKeyDirectory.path) {
            try fileSystem.removeItem(apiKeyDirectory)
        }

        for name in Self.legacyStorageNames.sorted() {
            let item = storageBaseDirectory.appendingPathComponent(name)
            if fileSystem.fileExists(item.path) {
                try fileSystem.removeItem(item)
            }
        }
    }

    private func validateBoundaries() throws {
        try requireImmediateChild(
            storageBaseDirectory,
            named: bundleIdentifier,
            of: applicationSupportDirectory
        )
        try requireImmediateChild(
            apiKeyDirectory,
            named: apiKey,
            of: storageBaseDirectory
        )
        for name in Self.legacyStorageNames {
            try requireImmediateChild(
                storageBaseDirectory.appendingPathComponent(name),
                named: name,
                of: storageBaseDirectory
            )
        }
    }

    private func requireImmediateChild(
        _ candidate: URL,
        named expectedName: String,
        of parent: URL
    ) throws {
        let resolvedParent = parent.resolvingSymlinksInPath().standardizedFileURL
        let resolvedCandidate = candidate.resolvingSymlinksInPath().standardizedFileURL

        guard expectedName.isEmpty == false,
              expectedName.contains("/") == false,
              resolvedCandidate != resolvedParent,
              candidate.lastPathComponent == expectedName,
              resolvedCandidate.deletingLastPathComponent() == resolvedParent else {
            throw PostHogAccountDataCleanupError.unsafePath(candidate)
        }
    }
}

@MainActor
struct PostHogRuntimeClient {
    let setup: () -> Void
    let close: () -> Void
    let capture: (_ event: String, _ properties: [String: Any]?) -> Void
    let identify: (_ distinctID: String, _ userProperties: [String: Any]?) -> Void

    static func live(config: PostHogConfig) -> PostHogRuntimeClient {
        PostHogRuntimeClient(
            setup: {
                PostHogSDK.shared.setup(config)
            },
            close: {
                // close() stops timers/reachability and does not flush. reset() must not
                // be used here because it leaves queues and sends a flags request.
                PostHogSDK.shared.close()
            },
            capture: { event, properties in
                PostHogSDK.shared.capture(event, properties: properties)
            },
            identify: { distinctID, userProperties in
                PostHogSDK.shared.identify(
                    distinctID,
                    userProperties: userProperties
                )
            }
        )
    }
}

enum PostHogColdLaunchOutcome: Equatable {
    case started
    case gatedByAccountCleanup
    case disabledAfterCleanupFailure
}

/// Owns all PostHog lifecycle calls so setup cannot race a pending account cleanup.
@MainActor
final class JustNoiseAnalyticsRuntime {
    private static var configuredShared: JustNoiseAnalyticsRuntime?

    static var shared: JustNoiseAnalyticsRuntime {
        guard let configuredShared else {
            preconditionFailure("Analytics runtime must be configured during app initialization")
        }
        return configuredShared
    }

    @discardableResult
    static func prepareSharedColdLaunch(
        config: PostHogConfig,
        accountCleanupPending: Bool
    ) -> PostHogColdLaunchOutcome {
        let runtime: JustNoiseAnalyticsRuntime
        if let configuredShared {
            runtime = configuredShared
        } else {
            runtime = JustNoiseAnalyticsRuntime(
                cleaner: PostHogAccountDataCleaner(apiKey: config.apiKey),
                client: .live(config: config)
            )
            configuredShared = runtime
        }
        return runtime.prepareColdLaunch(accountCleanupPending: accountCleanupPending)
    }

    private let cleaner: PostHogAccountDataCleaner
    private let client: PostHogRuntimeClient
    private(set) var isRunning = false
    private var isQuiescedForProcess = false
    private var didPrepareColdLaunch = false

    init(
        cleaner: PostHogAccountDataCleaner,
        client: PostHogRuntimeClient
    ) {
        self.cleaner = cleaner
        self.client = client
    }

    @discardableResult
    func prepareColdLaunch(accountCleanupPending: Bool) -> PostHogColdLaunchOutcome {
        guard didPrepareColdLaunch == false else {
            return isRunning ? .started : .gatedByAccountCleanup
        }
        didPrepareColdLaunch = true

        if cleaner.hasPendingCleanup {
            do {
                try cleaner.purgePersistedData()
            } catch {
                isQuiescedForProcess = true
                return .disabledAfterCleanupFailure
            }
        }

        guard accountCleanupPending == false else {
            // Keep the analytics marker for the next clean process. The current launch
            // must never set up an SDK while product cleanup is pending.
            isQuiescedForProcess = true
            return .gatedByAccountCleanup
        }

        if cleaner.hasPendingCleanup {
            do {
                try cleaner.clearCleanupMarker()
            } catch {
                isQuiescedForProcess = true
                return .disabledAfterCleanupFailure
            }
        }

        client.setup()
        isRunning = true
        return .started
    }

    /// Called only after remote account deletion is confirmed and the durable product
    /// marker exists. PostHog stays closed for the rest of this process.
    func quiesceAndPurgeForAccountDeletion() throws {
        cleaner.establishCleanupMarker()
        isQuiescedForProcess = true
        client.close()
        isRunning = false
        try cleaner.purgePersistedData()
        // Deliberately retain the marker. A cold pre-setup pass is the only deterministic
        // way to remove output from SDK requests that were already in flight at close().
    }

    func capture(_ event: String, properties: [String: Any]? = nil) {
        guard isRunning, isQuiescedForProcess == false else { return }
        client.capture(event, properties)
    }

    func identify(_ distinctID: String, userProperties: [String: Any]? = nil) {
        guard isRunning, isQuiescedForProcess == false else { return }
        client.identify(distinctID, userProperties)
    }
}
