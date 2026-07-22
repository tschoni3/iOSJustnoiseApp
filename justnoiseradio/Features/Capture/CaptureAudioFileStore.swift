import Foundation

enum CaptureAudioFileStoreError: Error {
    case sourceOutsideDraftDirectory
}

struct PendingCaptureCommit: Codable, Equatable {
    let clipID: UUID
    let createdAt: Date
    let duration: TimeInterval
    let sourceModeName: String?
    let draftFileName: String
}

/// Owns the transition from a short-lived recording draft to a persisted capture.
///
/// Draft cleanup is deliberately restricted to `CaptureDrafts`. Historical audio in
/// Documents may still be referenced by older journal/session records and is never
/// scanned or removed here.
struct CaptureAudioFileStore {
    static let draftDirectoryName = "CaptureDrafts"
    static let clipsDirectoryName = "CaptureClips"
    static let staleDraftAge: TimeInterval = 24 * 60 * 60
    private static let pendingCommitPrefix = ".pending-capture-"
    private static let pendingCommitSuffix = ".json"

    private let fileManager: FileManager
    let draftDirectory: URL
    let clipsDirectory: URL

    init(
        fileManager: FileManager = .default,
        draftDirectory: URL? = nil,
        clipsDirectory: URL? = nil
    ) {
        self.fileManager = fileManager
        self.draftDirectory = draftDirectory
            ?? fileManager.temporaryDirectory
                .appendingPathComponent(Self.draftDirectoryName, isDirectory: true)

        let documentsDirectory = fileManager.urls(
            for: .documentDirectory,
            in: .userDomainMask
        )[0]
        self.clipsDirectory = clipsDirectory
            ?? documentsDirectory
                .appendingPathComponent(Self.clipsDirectoryName, isDirectory: true)
    }

    func makeDraftURL(now: Date = .now) throws -> URL {
        try fileManager.createDirectory(
            at: draftDirectory,
            withIntermediateDirectories: true
        )
        try? pruneStaleDrafts(referenceDate: now)
        return draftDirectory.appendingPathComponent("\(UUID().uuidString).m4a")
    }

    func commitDraft(at draftURL: URL, clipID: UUID) throws -> URL {
        guard containsDraft(draftURL) else {
            throw CaptureAudioFileStoreError.sourceOutsideDraftDirectory
        }

        try fileManager.createDirectory(
            at: clipsDirectory,
            withIntermediateDirectories: true
        )

        let destinationURL = persistedURL(for: clipID)
        try fileManager.moveItem(at: draftURL, to: destinationURL)
        return destinationURL
    }

    func persistedURL(for clipID: UUID) -> URL {
        clipsDirectory.appendingPathComponent("\(clipID.uuidString).m4a")
    }

    func writePendingCommit(_ pending: PendingCaptureCommit) throws {
        try fileManager.createDirectory(
            at: clipsDirectory,
            withIntermediateDirectories: true
        )
        let data = try JSONEncoder().encode(pending)
        try data.write(to: pendingCommitURL(for: pending.clipID), options: .atomic)
    }

    func pendingCommits() -> [PendingCaptureCommit] {
        guard let urls = try? fileManager.contentsOfDirectory(
            at: clipsDirectory,
            includingPropertiesForKeys: nil
        ) else {
            return []
        }

        return urls
            .filter {
                $0.lastPathComponent.hasPrefix(Self.pendingCommitPrefix)
                    && $0.lastPathComponent.hasSuffix(Self.pendingCommitSuffix)
            }
            .compactMap { url in
                guard let data = try? Data(contentsOf: url) else { return nil }
                return try? JSONDecoder().decode(PendingCaptureCommit.self, from: data)
            }
            .sorted { $0.createdAt < $1.createdAt }
    }

    func clearPendingCommit(for clipID: UUID) {
        try? fileManager.removeItem(at: pendingCommitURL(for: clipID))
    }

    func recoverableDraftURL(named fileName: String) -> URL? {
        guard fileName == URL(fileURLWithPath: fileName).lastPathComponent,
              fileName.hasSuffix(".m4a") else {
            return nil
        }

        let url = draftDirectory.appendingPathComponent(fileName)
        guard containsDraft(url), fileManager.fileExists(atPath: url.path) else { return nil }
        return url
    }

    func discardDraft(at draftURL: URL) {
        guard containsDraft(draftURL) else { return }
        guard fileManager.fileExists(atPath: draftURL.path) else { return }
        try? fileManager.removeItem(at: draftURL)
    }

    func pruneStaleDrafts(
        referenceDate: Date = .now,
        maximumAge: TimeInterval = Self.staleDraftAge
    ) throws {
        guard fileManager.fileExists(atPath: draftDirectory.path) else { return }

        let draftURLs = try fileManager.contentsOfDirectory(
            at: draftDirectory,
            includingPropertiesForKeys: [.contentModificationDateKey, .isRegularFileKey],
            options: [.skipsHiddenFiles]
        )
        let cutoff = referenceDate.addingTimeInterval(-maximumAge)

        for draftURL in draftURLs where containsDraft(draftURL) {
            let values = try draftURL.resourceValues(
                forKeys: [.contentModificationDateKey, .isRegularFileKey]
            )
            guard values.isRegularFile == true,
                  let modifiedAt = values.contentModificationDate,
                  modifiedAt < cutoff else {
                continue
            }
            try fileManager.removeItem(at: draftURL)
        }
    }

    private func containsDraft(_ url: URL) -> Bool {
        let directoryPath = draftDirectory.standardizedFileURL.path
        let candidatePath = url.standardizedFileURL.path
        return candidatePath.hasPrefix(directoryPath + "/")
    }

    private func pendingCommitURL(for clipID: UUID) -> URL {
        clipsDirectory.appendingPathComponent(
            "\(Self.pendingCommitPrefix)\(clipID.uuidString)\(Self.pendingCommitSuffix)"
        )
    }
}
