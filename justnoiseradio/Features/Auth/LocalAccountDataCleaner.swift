import Foundation

enum LocalAccountDataCleanupError: LocalizedError, Equatable {
    case unsafePath(URL)

    var errorDescription: String? {
        switch self {
        case .unsafePath(let url):
            return "Refusing to remove account data outside the app-owned storage boundary: \(url.path)"
        }
    }
}

struct AccountDeletionFileSystem {
    let fileExists: (_ path: String) -> Bool
    let contentsOfDirectory: (_ directory: URL) throws -> [URL]
    let isRegularFile: (_ url: URL) throws -> Bool
    let removeItem: (_ url: URL) throws -> Void

    static let live = AccountDeletionFileSystem(
        fileExists: { FileManager.default.fileExists(atPath: $0) },
        contentsOfDirectory: { directory in
            try FileManager.default.contentsOfDirectory(
                at: directory,
                includingPropertiesForKeys: [.isRegularFileKey],
                options: []
            )
        },
        isRegularFile: { url in
            try url.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile == true
        },
        removeItem: { try FileManager.default.removeItem(at: $0) }
    )
}

struct AccountDeletionMarkerFileSystem {
    let fileExists: (_ path: String) -> Bool
    let createDirectory: (_ directory: URL) throws -> Void
    let writeAtomically: (_ data: Data, _ destination: URL) throws -> Void
    let removeItem: (_ url: URL) throws -> Void

    static let live = AccountDeletionMarkerFileSystem(
        fileExists: { FileManager.default.fileExists(atPath: $0) },
        createDirectory: { directory in
            try FileManager.default.createDirectory(
                at: directory,
                withIntermediateDirectories: true
            )
        },
        writeAtomically: { data, destination in
            try data.write(to: destination, options: [.atomic])
        },
        removeItem: { try FileManager.default.removeItem(at: $0) }
    )
}

struct AccountDeletionRetryMarkerStore {
    static let markerFileName = "pending-local-account-deletion-cleanup-v1"

    let markerURL: URL
    private let fileSystem: AccountDeletionMarkerFileSystem

    init(
        applicationSupportDirectory: URL,
        markerFileName: String = Self.markerFileName,
        fileSystem: AccountDeletionMarkerFileSystem = .live
    ) {
        markerURL = applicationSupportDirectory.appendingPathComponent(
            markerFileName,
            isDirectory: false
        )
        self.fileSystem = fileSystem
    }

    var exists: Bool {
        fileSystem.fileExists(markerURL.path)
    }

    func create() throws {
        try fileSystem.createDirectory(markerURL.deletingLastPathComponent())
        try fileSystem.writeAtomically(
            Data("justnoise-account-deletion-cleanup-v1\n".utf8),
            markerURL
        )
    }

    func remove() throws {
        guard exists else { return }
        try fileSystem.removeItem(markerURL)
    }
}

/// The destructive, local-only half of confirmed account deletion.
///
/// This type intentionally removes an explicit allowlist of account-owned values rather
/// than clearing a defaults domain. Installation state such as activation, onboarding,
/// emergency credits, permissions, and StoreKit state must survive account deletion.
struct LocalAccountDataCleaner {
    /// A redundant recovery gate used only when the atomic file marker cannot be written,
    /// or when cleanup remains incomplete. The Application Support file is authoritative.
    static let cleanupFallbackDefaultsKey = "jn_pending_local_account_deletion_cleanup_fallback_v1"

    static let accountOwnedStandardDefaultsKeys: Set<String> = [
        "isSignedIn",
        "userName",
        "userLanguage",
        "showPostSessionJournalPrompt",
        "modes",
        "modeNames",
        "selectedModeID",
        "selectedModeName",
        SharedKeys.legacySchedulesKey,
        "sessionHistory",
        "transcriptionHistory",
        "jn_journal_history_v1",
        "jn_last_noise_rewind_seen_week_start",
        "jn_capture_clips_v1",
        "jn_signal_extractions_v1",
        "jn_signal_insights_v1",
        "jn_signal_memory_state_v1",
        "jn_signal_analysis_failures_v1",
        "jn_signal_analysis_paused_until_v1",
        "jn_seen_signal_comment_ids_v1",
        "preferredSessionTime",
        "preSessionLeadMinutes",
        "lastNotificationSentYYYYMMDD",
    ]

    static let accountOwnedSharedDefaultsKeys = SharedKeys.accountOwnedTransientKeys

    private let standardDefaults: UserDefaults
    private let sharedDefaults: UserDefaults
    private let fileSystem: AccountDeletionFileSystem
    private let retryMarkerStore: AccountDeletionRetryMarkerStore
    private let documentsDirectory: URL
    private let temporaryDirectory: URL
    private let captureClipsDirectory: URL
    private let captureDraftsDirectory: URL

    init(
        standardDefaults: UserDefaults = .standard,
        sharedDefaults: UserDefaults = JNShared.suite,
        fileSystem: AccountDeletionFileSystem = .live,
        markerFileSystem: AccountDeletionMarkerFileSystem = .live,
        documentsDirectory: URL? = nil,
        temporaryDirectory: URL? = nil,
        applicationSupportDirectory: URL? = nil,
        captureClipsDirectory: URL? = nil,
        captureDraftsDirectory: URL? = nil
    ) {
        let fileManager = FileManager.default
        let resolvedDocumentsDirectory = documentsDirectory
            ?? fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let resolvedTemporaryDirectory = temporaryDirectory ?? fileManager.temporaryDirectory
        let resolvedApplicationSupportDirectory = applicationSupportDirectory
            ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]

        self.standardDefaults = standardDefaults
        self.sharedDefaults = sharedDefaults
        self.fileSystem = fileSystem
        self.retryMarkerStore = AccountDeletionRetryMarkerStore(
            applicationSupportDirectory: resolvedApplicationSupportDirectory,
            fileSystem: markerFileSystem
        )
        self.documentsDirectory = resolvedDocumentsDirectory
        self.temporaryDirectory = resolvedTemporaryDirectory
        self.captureClipsDirectory = captureClipsDirectory
            ?? resolvedDocumentsDirectory.appendingPathComponent(
                CaptureAudioFileStore.clipsDirectoryName,
                isDirectory: true
            )
        self.captureDraftsDirectory = captureDraftsDirectory
            ?? resolvedTemporaryDirectory.appendingPathComponent(
                CaptureAudioFileStore.draftDirectoryName,
                isDirectory: true
            )
    }

    var hasPendingCleanup: Bool {
        hasDurableCleanupMarker
            || standardDefaults.bool(forKey: Self.cleanupFallbackDefaultsKey)
    }

    var hasDurableCleanupMarker: Bool {
        retryMarkerStore.exists
    }

    var cleanupMarkerURL: URL {
        retryMarkerStore.markerURL
    }

    func markCleanupPending() throws {
        try retryMarkerStore.create()
    }

    func markFallbackCleanupPending() {
        standardDefaults.set(true, forKey: Self.cleanupFallbackDefaultsKey)
    }

    func clearCleanupMarker() throws {
        // Remove the authoritative marker first. If that fails, retain the fallback
        // so launch remains fail-closed and another retry can finish the operation.
        try retryMarkerStore.remove()
        standardDefaults.removeObject(forKey: Self.cleanupFallbackDefaultsKey)
    }

    /// Idempotently removes all known account-owned local data while retaining the retry marker.
    func purgePersistedAccountData() throws {
        try validateFileBoundaries()

        for key in Self.accountOwnedStandardDefaultsKeys {
            standardDefaults.removeObject(forKey: key)
        }
        for key in Self.accountOwnedSharedDefaultsKeys {
            sharedDefaults.removeObject(forKey: key)
        }
        try removeDirectoryIfPresent(captureClipsDirectory)
        try removeDirectoryIfPresent(captureDraftsDirectory)
        try removeLegacyRootAudioFiles()
    }

    private func validateFileBoundaries() throws {
        try requireImmediateChild(
            captureClipsDirectory,
            named: CaptureAudioFileStore.clipsDirectoryName,
            of: documentsDirectory
        )
        try requireImmediateChild(
            captureDraftsDirectory,
            named: CaptureAudioFileStore.draftDirectoryName,
            of: temporaryDirectory
        )
    }

    private func requireImmediateChild(
        _ candidate: URL,
        named expectedName: String,
        of parent: URL
    ) throws {
        let resolvedParent = parent.resolvingSymlinksInPath().standardizedFileURL
        let resolvedCandidate = candidate.resolvingSymlinksInPath().standardizedFileURL

        guard resolvedCandidate != resolvedParent,
              resolvedCandidate.lastPathComponent == expectedName,
              resolvedCandidate.deletingLastPathComponent() == resolvedParent else {
            throw LocalAccountDataCleanupError.unsafePath(candidate)
        }
    }

    private func removeDirectoryIfPresent(_ directory: URL) throws {
        guard fileSystem.fileExists(directory.path) else { return }
        try fileSystem.removeItem(directory)
    }

    private func removeLegacyRootAudioFiles() throws {
        guard fileSystem.fileExists(documentsDirectory.path) else { return }

        for url in try fileSystem.contentsOfDirectory(documentsDirectory) {
            let standardizedURL = url.standardizedFileURL
            guard standardizedURL.deletingLastPathComponent() == documentsDirectory.standardizedFileURL,
                  standardizedURL.pathExtension.lowercased() == "m4a",
                  try fileSystem.isRegularFile(standardizedURL) else {
                continue
            }

            try fileSystem.removeItem(standardizedURL)
        }
    }
}
