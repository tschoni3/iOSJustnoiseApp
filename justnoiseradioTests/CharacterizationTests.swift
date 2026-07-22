import XCTest
@testable import justnoiseradio

final class SignalModelCharacterizationTests: XCTestCase {
    func testSignalInsightNormalizesDuplicateSourcesAndThemes() throws {
        let firstCaptureID = try XCTUnwrap(UUID(uuidString: "11111111-1111-1111-1111-111111111111"))
        let secondCaptureID = try XCTUnwrap(UUID(uuidString: "22222222-2222-2222-2222-222222222222"))
        let createdAt = Date(timeIntervalSince1970: 1_700_000_000)

        let insight = SignalInsight(
            type: .action,
            text: "Protect the first hour.",
            createdAt: createdAt,
            sourceCaptureClipIDs: [firstCaptureID, firstCaptureID, secondCaptureID],
            themes: [" Focus ", "FOCUS", "Deep Work", "   "],
            origin: .manual
        )

        XCTAssertEqual(insight.sourceCaptureClipIDs, [firstCaptureID, secondCaptureID])
        XCTAssertEqual(insight.themes, ["focus", "deep work"])
        XCTAssertEqual(insight.revealState, .revealed)
        XCTAssertEqual(insight.revealedAt, createdAt)
    }

    func testLegacySignalInsightDecodingPreservesCurrentDefaults() throws {
        let json = """
        {
          "id": "33333333-3333-3333-3333-333333333333",
          "type": "gem",
          "text": "Morning focus is strongest.",
          "createdAt": 12345,
          "sourceCaptureClipIDs": [],
          "themes": ["Morning"]
        }
        """

        let insight = try JSONDecoder().decode(SignalInsight.self, from: Data(json.utf8))

        XCTAssertEqual(insight.origin, .automatic)
        XCTAssertEqual(insight.revealState, .revealed)
        XCTAssertEqual(insight.revealedAt, insight.createdAt)
        XCTAssertEqual(insight.themes, ["morning"])
    }

    func testThreadLinkStableIDIsIndependentOfDirection() {
        XCTAssertEqual(
            ThreadLink.stableID(sourceThreadId: "sleep", targetThreadId: "focus"),
            "focus::sleep"
        )
        XCTAssertEqual(
            ThreadLink.stableID(sourceThreadId: "focus", targetThreadId: "sleep"),
            "focus::sleep"
        )
    }
}

final class CaptureClipCharacterizationTests: XCTestCase {
    func testLegacyCaptureClipDecodingKeepsCurrentFallbacks() throws {
        let json = """
        {
          "id": "44444444-4444-4444-4444-444444444444",
          "createdAt": 12345,
          "duration": 90,
          "audioFileURL": "file:///tmp/legacy-capture.m4a",
          "sourceModeName": "Reading"
        }
        """

        let clip = try JSONDecoder().decode(CaptureClip.self, from: Data(json.utf8))

        XCTAssertEqual(clip.id.uuidString, "44444444-4444-4444-4444-444444444444")
        XCTAssertEqual(clip.createdAt, Date(timeIntervalSinceReferenceDate: 12_345))
        XCTAssertEqual(clip.duration, 90)
        XCTAssertEqual(clip.audioFileURL, URL(fileURLWithPath: "/tmp/legacy-capture.m4a"))
        XCTAssertEqual(clip.sourceModeName, "Reading")
        XCTAssertEqual(clip.analysisState, .pending)
        XCTAssertNil(clip.analysisUpdatedAt)
        XCTAssertNil(clip.retryAfter)
        XCTAssertNil(clip.lastErrorMessage)
        XCTAssertNil(clip.transcript)
        XCTAssertEqual(clip.themes, [])
        XCTAssertNil(clip.processingStatus)
        XCTAssertNil(clip.processedAt)
    }

    func testApplyingCapturePatchNormalizesValuesAndClearsFailureState() throws {
        let clipID = try XCTUnwrap(UUID(uuidString: "55555555-5555-5555-5555-555555555555"))
        let retryAfter = Date(timeIntervalSince1970: 1_800_000_000)
        let original = CaptureClip(
            id: clipID,
            createdAt: Date(timeIntervalSince1970: 1_700_000_000),
            duration: 75,
            audioFileURL: URL(fileURLWithPath: "/tmp/capture.m4a"),
            sourceModeName: "Original Mode",
            analysisState: .failed,
            analysisUpdatedAt: retryAfter.addingTimeInterval(-60),
            lastDecisionBlockReason: "old block",
            retryAfter: retryAfter,
            lastErrorMessage: "temporary failure"
        )
        let patchJSON = """
        {
          "transcript": "  First line\\r\\n\\r\\nSecond line  ",
          "sourceLanguage": "und",
          "summary": "  Clear signal  ",
          "themes": [" Focus ", "FOCUS", "Deep Work", "   "],
          "category": "   ",
          "emotion": " calm ",
          "intensity": 1.4,
          "tension": " pressure ",
          "desire": " rest ",
          "avoidedAction": " email ",
          "currentState": " distracted ",
          "processingStatus": " ready ",
          "processedAt": "2026-07-18T12:34:56Z",
          "strongQuote": " Do the thing ",
          "clarifiedTranscript": " clarified version ",
          "context": " morning context "
        }
        """
        let patch = try JSONDecoder().decode(BackendCapturePatch.self, from: Data(patchJSON.utf8))
        let processedAt = try XCTUnwrap(SignalMemoryDate.date(from: "2026-07-18T12:34:56Z"))

        let updated = original.applyingCapturePatch(
            patch,
            selectedModeName: "Deep Work",
            blockReason: "manual stop",
            updatedAt: Date(timeIntervalSince1970: 1_750_000_000)
        )

        XCTAssertEqual(updated.id, clipID)
        XCTAssertEqual(updated.sourceModeName, "Deep Work")
        XCTAssertEqual(updated.analysisState, .reviewed)
        XCTAssertEqual(updated.analysisUpdatedAt, processedAt)
        XCTAssertEqual(updated.processedAt, processedAt)
        XCTAssertEqual(updated.lastDecisionBlockReason, "manual stop")
        XCTAssertNil(updated.retryAfter)
        XCTAssertNil(updated.lastErrorMessage)
        XCTAssertEqual(updated.transcript, "First line\nSecond line")
        XCTAssertEqual(updated.summary, "Clear signal")
        XCTAssertEqual(updated.themes, ["focus", "deep work"])
        XCTAssertEqual(updated.sourceLanguage, "und")
        XCTAssertNil(updated.category)
        XCTAssertEqual(updated.emotion, "calm")
        XCTAssertEqual(updated.intensity, 1.4)
        XCTAssertEqual(updated.tension, "pressure")
        XCTAssertEqual(updated.desire, "rest")
        XCTAssertEqual(updated.avoidedAction, "email")
        XCTAssertEqual(updated.currentState, "distracted")
        XCTAssertEqual(updated.processingStatus, "ready")

        let extraction = try XCTUnwrap(updated.extraction)
        XCTAssertNil(extraction.sourceLanguage)
        XCTAssertEqual(extraction.clarity, 1)
        XCTAssertEqual(extraction.noiseLevel, 0)
        XCTAssertEqual(extraction.strongQuote, "Do the thing")
        XCTAssertEqual(extraction.highlight, "Do the thing")
        XCTAssertEqual(extraction.clarifiedTranscript, "clarified version")
        XCTAssertEqual(extraction.context, "morning context")
    }

    func testFailureLifecycleReturnsPatchedCaptureToReviewedState() throws {
        let original = CaptureClip(
            duration: 45,
            audioFileURL: URL(fileURLWithPath: "/tmp/failure-lifecycle.m4a")
        )
        let patch = try JSONDecoder().decode(
            BackendCapturePatch.self,
            from: Data("{\"summary\":\"A useful signal\"}".utf8)
        )
        let reviewed = original.applyingCapturePatch(
            patch,
            selectedModeName: nil,
            blockReason: nil,
            updatedAt: Date(timeIntervalSince1970: 1_700_000_000)
        )
        let retryAfter = Date(timeIntervalSince1970: 1_700_003_600)
        let failed = reviewed.markingAnalysisFailed(
            retryAfter: retryAfter,
            message: "Try again",
            updatedAt: Date(timeIntervalSince1970: 1_700_000_100)
        )

        XCTAssertEqual(failed.analysisState, .failed)
        XCTAssertEqual(failed.retryAfter, retryAfter)
        XCTAssertEqual(failed.lastErrorMessage, "Try again")

        let recovered = failed.clearingFailure()

        XCTAssertEqual(recovered.analysisState, .reviewed)
        XCTAssertNil(recovered.retryAfter)
        XCTAssertNil(recovered.lastErrorMessage)
        XCTAssertEqual(recovered.extraction, reviewed.extraction)
    }
}

final class CaptureAudioFileStoreTests: XCTestCase {
    private var rootDirectory: URL!
    private var draftDirectory: URL!
    private var clipsDirectory: URL!
    private var store: CaptureAudioFileStore!
    private var userDefaults: UserDefaults!
    private var userDefaultsSuiteName: String!

    override func setUpWithError() throws {
        try super.setUpWithError()
        rootDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("CaptureAudioFileStoreTests-\(UUID().uuidString)", isDirectory: true)
        draftDirectory = rootDirectory.appendingPathComponent("drafts", isDirectory: true)
        clipsDirectory = rootDirectory.appendingPathComponent("clips", isDirectory: true)
        try FileManager.default.createDirectory(at: rootDirectory, withIntermediateDirectories: true)
        store = CaptureAudioFileStore(
            draftDirectory: draftDirectory,
            clipsDirectory: clipsDirectory
        )
        userDefaultsSuiteName = "CaptureAudioFileStoreTests-\(UUID().uuidString)"
        userDefaults = try XCTUnwrap(UserDefaults(suiteName: userDefaultsSuiteName))
    }

    override func tearDownWithError() throws {
        if let rootDirectory {
            try? FileManager.default.removeItem(at: rootDirectory)
        }
        if let userDefaultsSuiteName {
            UserDefaults.standard.removePersistentDomain(forName: userDefaultsSuiteName)
        }
        userDefaults = nil
        userDefaultsSuiteName = nil
        store = nil
        clipsDirectory = nil
        draftDirectory = nil
        rootDirectory = nil
        try super.tearDownWithError()
    }

    func testCommitMovesDraftWithoutLeavingSourceDuplicate() throws {
        let clipID = try XCTUnwrap(UUID(uuidString: "CCCCCCCC-1111-1111-1111-111111111111"))
        let draftURL = try writeDraft(named: "recording.m4a", contents: Data("audio".utf8))

        let destinationURL = try store.commitDraft(at: draftURL, clipID: clipID)

        XCTAssertFalse(FileManager.default.fileExists(atPath: draftURL.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: destinationURL.path))
        XCTAssertEqual(destinationURL.lastPathComponent, "\(clipID.uuidString).m4a")
        XCTAssertEqual(try Data(contentsOf: destinationURL), Data("audio".utf8))
    }

    func testCommitFailureLeavesDraftOwnedByCallerAndDoesNotAppendMetadata() throws {
        let clipID = try XCTUnwrap(UUID(uuidString: "DDDDDDDD-1111-1111-1111-111111111111"))
        let draftURL = try writeDraft(named: "recording.m4a", contents: Data("draft".utf8))
        try FileManager.default.createDirectory(at: clipsDirectory, withIntermediateDirectories: true)
        let occupiedDestination = clipsDirectory.appendingPathComponent("\(clipID.uuidString).m4a")
        try Data("existing".utf8).write(to: occupiedDestination)
        let signalStore = SignalStore(
            captureAudioFileStore: store,
            captureIDProvider: { clipID },
            userDefaults: userDefaults
        )

        XCTAssertThrowsError(
            try signalStore.saveCaptureClip(
                audioURL: draftURL,
                duration: 1,
                selectedModeName: "Focus"
            )
        )
        XCTAssertTrue(FileManager.default.fileExists(atPath: draftURL.path))
        XCTAssertEqual(try Data(contentsOf: occupiedDestination), Data("existing".utf8))
        XCTAssertTrue(signalStore.captureClips.isEmpty)
        XCTAssertTrue(store.pendingCommits().isEmpty)
    }

    func testInterruptedCommitRecoversMetadataOnNextLoad() throws {
        let clipID = try XCTUnwrap(UUID(uuidString: "EEEEEEEE-1111-1111-1111-111111111111"))
        let createdAt = Date(timeIntervalSince1970: 1_800_000_000)
        let draftURL = try writeDraft(named: "recover.m4a", contents: Data("audio".utf8))
        let destinationURL = try store.commitDraft(at: draftURL, clipID: clipID)
        let pending = PendingCaptureCommit(
            clipID: clipID,
            createdAt: createdAt,
            duration: 4.2,
            sourceModeName: "Focus",
            draftFileName: draftURL.lastPathComponent
        )
        try store.writePendingCommit(pending)
        let signalStore = SignalStore(
            captureAudioFileStore: store,
            userDefaults: userDefaults
        )

        signalStore.loadCaptureClips()

        let recovered = try XCTUnwrap(signalStore.captureClips.first)
        XCTAssertEqual(signalStore.captureClips.count, 1)
        XCTAssertEqual(recovered.id, clipID)
        XCTAssertEqual(recovered.createdAt, createdAt)
        XCTAssertEqual(recovered.duration, 4.2, accuracy: 0.001)
        XCTAssertEqual(recovered.audioFileURL, destinationURL)
        XCTAssertEqual(recovered.sourceModeName, "Focus")
        XCTAssertEqual(store.pendingCommits(), [pending])

        let nextLaunchStore = SignalStore(
            captureAudioFileStore: store,
            userDefaults: userDefaults
        )
        nextLaunchStore.loadCaptureClips()
        XCTAssertTrue(store.pendingCommits().isEmpty)
    }

    func testInterruptedCommitBeforeMoveRecoversTheNamedDraft() throws {
        let clipID = try XCTUnwrap(UUID(uuidString: "FFFFFFFF-1111-1111-1111-111111111111"))
        let draftURL = try writeDraft(named: "before-move.m4a", contents: Data("audio".utf8))
        let pending = PendingCaptureCommit(
            clipID: clipID,
            createdAt: Date(timeIntervalSince1970: 1_800_000_100),
            duration: 2.5,
            sourceModeName: nil,
            draftFileName: draftURL.lastPathComponent
        )
        try store.writePendingCommit(pending)
        let signalStore = SignalStore(
            captureAudioFileStore: store,
            userDefaults: userDefaults
        )

        signalStore.loadCaptureClips()

        let recovered = try XCTUnwrap(signalStore.captureClips.first)
        XCTAssertEqual(recovered.id, clipID)
        XCTAssertFalse(FileManager.default.fileExists(atPath: draftURL.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: recovered.audioFileURL.path))
        XCTAssertEqual(store.pendingCommits(), [pending])
    }

    func testDiscardDraftIsIdempotentAndCannotDeleteOutsideDraftDirectory() throws {
        let draftURL = try writeDraft(named: "discard.m4a")
        let outsideURL = rootDirectory.appendingPathComponent("legacy.m4a")
        try Data("legacy".utf8).write(to: outsideURL)

        store.discardDraft(at: draftURL)
        store.discardDraft(at: draftURL)
        store.discardDraft(at: outsideURL)

        XCTAssertFalse(FileManager.default.fileExists(atPath: draftURL.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: outsideURL.path))
    }

    func testPendingRecoveryRejectsAPathInsteadOfARelativeDraftName() throws {
        let draftURL = try writeDraft(named: "safe.m4a")

        XCTAssertNil(store.recoverableDraftURL(named: "../safe.m4a"))
        XCTAssertEqual(store.recoverableDraftURL(named: draftURL.lastPathComponent), draftURL)
    }

    func testStaleDraftCleanupTouchesOnlyCaptureDraftDirectory() throws {
        let referenceDate = Date(timeIntervalSince1970: 1_800_000_000)
        let oldDraft = try writeDraft(named: "old.m4a")
        let freshDraft = try writeDraft(named: "fresh.m4a")
        let outsideURL = rootDirectory.appendingPathComponent("old-legacy.m4a")
        try Data("legacy".utf8).write(to: outsideURL)
        try setModificationDate(referenceDate.addingTimeInterval(-101), for: oldDraft)
        try setModificationDate(referenceDate.addingTimeInterval(-99), for: freshDraft)
        try setModificationDate(referenceDate.addingTimeInterval(-101), for: outsideURL)

        try store.pruneStaleDrafts(referenceDate: referenceDate, maximumAge: 100)

        XCTAssertFalse(FileManager.default.fileExists(atPath: oldDraft.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: freshDraft.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: outsideURL.path))
    }

    func testDraftURLIsNamespacedAndUsesM4AExtension() throws {
        let draftURL = try store.makeDraftURL()

        XCTAssertEqual(draftURL.deletingLastPathComponent(), draftDirectory)
        XCTAssertEqual(draftURL.pathExtension, "m4a")
    }

    private func writeDraft(
        named name: String,
        contents: Data = Data("draft".utf8)
    ) throws -> URL {
        try FileManager.default.createDirectory(at: draftDirectory, withIntermediateDirectories: true)
        let url = draftDirectory.appendingPathComponent(name)
        try contents.write(to: url)
        return url
    }

    private func setModificationDate(_ date: Date, for url: URL) throws {
        try FileManager.default.setAttributes([.modificationDate: date], ofItemAtPath: url.path)
    }
}

private final class FakeCaptureAudioRecording: CaptureAudioRecording {
    var isMeteringEnabled = false
    var shouldStart = true
    var prepareCount = 0
    var recordCount = 0
    var stopCount = 0

    func prepareToRecord() -> Bool {
        prepareCount += 1
        return true
    }

    func record() -> Bool {
        recordCount += 1
        return shouldStart
    }

    func stop() {
        stopCount += 1
    }

    func updateMeters() {}
    func averagePower(forChannel channelNumber: Int) -> Float { -20 }
    func peakPower(forChannel channelNumber: Int) -> Float { -10 }
}

final class AudioRecorderLifecycleTests: XCTestCase {
    private var rootDirectory: URL!
    private var draftDirectory: URL!
    private var clipsDirectory: URL!
    private var store: CaptureAudioFileStore!

    override func setUpWithError() throws {
        try super.setUpWithError()
        rootDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("AudioRecorderLifecycleTests-\(UUID().uuidString)", isDirectory: true)
        draftDirectory = rootDirectory.appendingPathComponent("drafts", isDirectory: true)
        clipsDirectory = rootDirectory.appendingPathComponent("clips", isDirectory: true)
        try FileManager.default.createDirectory(at: rootDirectory, withIntermediateDirectories: true)
        store = CaptureAudioFileStore(
            draftDirectory: draftDirectory,
            clipsDirectory: clipsDirectory
        )
    }

    override func tearDownWithError() throws {
        if let rootDirectory {
            try? FileManager.default.removeItem(at: rootDirectory)
        }
        store = nil
        clipsDirectory = nil
        draftDirectory = nil
        rootDirectory = nil
        try super.tearDownWithError()
    }

    func testFailedStartDeletesDraftAndReleasesAudioSession() throws {
        let fakeRecording = FakeCaptureAudioRecording()
        fakeRecording.shouldStart = false
        var activationCount = 0
        var deactivationCount = 0
        var candidateURL: URL?
        let recorder = AudioRecorder(
            audioFileStore: store,
            permissionDenied: { false },
            activateAudioSession: { activationCount += 1 },
            deactivateAudioSession: { deactivationCount += 1 },
            recorderFactory: { url, _ in
                candidateURL = url
                try Data("audio".utf8).write(to: url)
                return fakeRecording
            }
        )

        XCTAssertThrowsError(try recorder.startRecording()) { error in
            guard case AudioRecorderError.failedToStart = error else {
                return XCTFail("Expected failedToStart, received \(error)")
            }
        }

        XCTAssertEqual(activationCount, 1)
        XCTAssertEqual(deactivationCount, 1)
        XCTAssertEqual(fakeRecording.prepareCount, 1)
        XCTAssertEqual(fakeRecording.recordCount, 1)
        XCTAssertFalse(FileManager.default.fileExists(atPath: try XCTUnwrap(candidateURL).path))
    }

    func testStopHandsDraftOwnershipToCallerExactlyOnce() throws {
        let fakeRecording = FakeCaptureAudioRecording()
        var deactivationCount = 0
        let recorder = AudioRecorder(
            audioFileStore: store,
            permissionDenied: { false },
            activateAudioSession: {},
            deactivateAudioSession: { deactivationCount += 1 },
            recorderFactory: { url, _ in
                try Data("audio".utf8).write(to: url)
                return fakeRecording
            }
        )
        try recorder.startRecording()

        var result: Result<URL, Error>?
        recorder.stopRecording { result = $0 }
        let handedOffURL = try XCTUnwrap(result).get()

        XCTAssertTrue(FileManager.default.fileExists(atPath: handedOffURL.path))
        XCTAssertEqual(fakeRecording.stopCount, 1)
        XCTAssertEqual(deactivationCount, 1)

        recorder.stopRecording { result = $0 }
        XCTAssertThrowsError(try XCTUnwrap(result).get())
        XCTAssertEqual(deactivationCount, 1)

        recorder.discardRecording(at: handedOffURL)
        XCTAssertFalse(FileManager.default.fileExists(atPath: handedOffURL.path))
    }

    func testCancellationDeletesOwnedDraftAndSecondCancelIsNoOp() throws {
        let fakeRecording = FakeCaptureAudioRecording()
        var deactivationCount = 0
        var draftURL: URL?
        let recorder = AudioRecorder(
            audioFileStore: store,
            permissionDenied: { false },
            activateAudioSession: {},
            deactivateAudioSession: { deactivationCount += 1 },
            recorderFactory: { url, _ in
                draftURL = url
                try Data("audio".utf8).write(to: url)
                return fakeRecording
            }
        )
        try recorder.startRecording()

        recorder.cancelRecording()
        recorder.cancelRecording()

        XCTAssertFalse(FileManager.default.fileExists(atPath: try XCTUnwrap(draftURL).path))
        XCTAssertEqual(fakeRecording.stopCount, 1)
        XCTAssertEqual(deactivationCount, 1)
    }
}

final class SignalTopicThreadCharacterizationTests: XCTestCase {
    func testThreadBuilderGroupsThemesAndAveragesIntensity() throws {
        let captures = [
            capture(
                id: "capture-1",
                createdAt: "2026-07-13T08:00:00Z",
                summary: "Protected the first hour.",
                themes: ["Deep Work"],
                quote: "The quiet hour mattered.",
                strength: 0.8
            ),
            capture(
                id: "capture-2",
                createdAt: "2026-07-15T09:00:00Z",
                summary: "Found the same rhythm again.",
                themes: ["Deep Work", "Morning"],
                quote: "Starting early made it easier.",
                strength: 0.6
            ),
            capture(
                id: "capture-3",
                createdAt: "2026-07-14T20:00:00Z",
                summary: "Recovery helped.",
                themes: ["Rest"],
                quote: nil,
                strength: nil
            )
        ]

        let threads = SignalTopicThreadEngine.build(from: captures)
        let deepWork = try XCTUnwrap(threads.first)

        XCTAssertEqual(threads.map(\.topicKey), ["deep work", "rest"])
        XCTAssertEqual(deepWork.occurrenceCount, 2)
        XCTAssertEqual(deepWork.themes, ["Deep Work", "Morning"])
        XCTAssertEqual(deepWork.intensity ?? 0, 0.7, accuracy: 0.000_001)
        XCTAssertEqual(deepWork.firstSeen, "2026-07-13T08:00:00Z")
        XCTAssertEqual(deepWork.lastSeen, "2026-07-15T09:00:00Z")
        XCTAssertEqual(deepWork.captureIds, ["capture-1", "capture-2"])
        XCTAssertEqual(deepWork.evidenceQuotes.map(\.quote), [
            "The quiet hour mattered.",
            "Starting early made it easier."
        ])
    }

    private func capture(
        id: String,
        createdAt: String,
        summary: String,
        themes: [String],
        quote: String?,
        strength: Double?
    ) -> SignalCaptureContextPayload {
        SignalCaptureContextPayload(
            capture_id: id,
            created_at: createdAt,
            summary: summary,
            themes: themes,
            sourceLanguage: nil,
            emotion: nil,
            strongQuote: quote,
            transcript: nil,
            clarifiedTranscript: nil,
            context: nil,
            tension: nil,
            highlight: nil,
            signalStrength: strength,
            emotionalIntensity: strength
        )
    }
}

final class NoiseRewindCharacterizationTests: XCTestCase {
    func testSundayEligibilityUsesPreviousWeekAndCurrentThresholds() throws {
        let calendar = utcCalendar()
        let referenceDate = try date(2026, 7, 19, 10, calendar: calendar)
        let previousWeekStart = try date(2026, 7, 12, 0, calendar: calendar)
        let sessions = [
            Session(
                startDate: try date(2026, 7, 13, 8, calendar: calendar),
                duration: 20 * 60,
                modeName: "Deep Work"
            ),
            Session(
                startDate: try date(2026, 7, 15, 9, calendar: calendar),
                duration: 25 * 60,
                modeName: "Deep Work"
            )
        ]

        XCTAssertTrue(
            NoiseRewindWeeklyInsightGenerator.shouldShowNoiseRewind(
                sessions: sessions,
                referenceDate: referenceDate,
                calendar: calendar,
                lastSeenWeekStart: nil,
                isSessionActive: false
            )
        )
        XCTAssertFalse(
            NoiseRewindWeeklyInsightGenerator.shouldShowNoiseRewind(
                sessions: sessions,
                referenceDate: referenceDate,
                calendar: calendar,
                lastSeenWeekStart: previousWeekStart,
                isSessionActive: false
            )
        )
        XCTAssertFalse(
            NoiseRewindWeeklyInsightGenerator.shouldShowNoiseRewind(
                sessions: sessions,
                referenceDate: referenceDate,
                calendar: calendar,
                lastSeenWeekStart: nil,
                isSessionActive: true
            )
        )
    }

    func testPatternInsightKeepsTwoHourBucketBehavior() throws {
        let calendar = utcCalendar()
        let sessions = [
            Session(
                startDate: try date(2026, 7, 13, 8, 30, calendar: calendar),
                duration: 90 * 60,
                modeName: "Deep Work"
            ),
            Session(
                startDate: try date(2026, 7, 14, 20, 0, calendar: calendar),
                duration: 30 * 60,
                modeName: "Reading"
            )
        ]

        let pattern = NoiseRewindWeeklyInsightGenerator.generatePatternInsight(
            sessions: sessions,
            calendar: calendar
        )

        XCTAssertEqual(pattern.title, "Your signal peaked around 08:00–10:00.")
        XCTAssertEqual(
            pattern.subtitle,
            "This window carried your strongest signal this week. Protect it next week."
        )
    }

    private func utcCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "en_US_POSIX")
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        calendar.firstWeekday = 1
        return calendar
    }

    private func date(
        _ year: Int,
        _ month: Int,
        _ day: Int,
        _ hour: Int,
        _ minute: Int = 0,
        calendar: Calendar
    ) throws -> Date {
        try XCTUnwrap(
            calendar.date(
                from: DateComponents(
                    timeZone: calendar.timeZone,
                    year: year,
                    month: month,
                    day: day,
                    hour: hour,
                    minute: minute
                )
            )
        )
    }
}

final class ScheduleStoreCharacterizationTests: XCTestCase {
    private var sharedDefaults: UserDefaults!
    private var legacyDefaults: UserDefaults!
    private var sharedSuiteName: String!
    private var legacySuiteName: String!

    override func setUp() {
        super.setUp()
        sharedSuiteName = "ScheduleStoreTests.shared.\(UUID().uuidString)"
        legacySuiteName = "ScheduleStoreTests.legacy.\(UUID().uuidString)"
        sharedDefaults = UserDefaults(suiteName: sharedSuiteName)
        legacyDefaults = UserDefaults(suiteName: legacySuiteName)
        sharedDefaults.removePersistentDomain(forName: sharedSuiteName)
        legacyDefaults.removePersistentDomain(forName: legacySuiteName)
    }

    override func tearDown() {
        sharedDefaults.removePersistentDomain(forName: sharedSuiteName)
        legacyDefaults.removePersistentDomain(forName: legacySuiteName)
        sharedDefaults = nil
        legacyDefaults = nil
        sharedSuiteName = nil
        legacySuiteName = nil
        super.tearDown()
    }

    func testLegacySchedulesMigrateToTheSharedSourceOfTruth() throws {
        let schedule = Schedule(
            id: try XCTUnwrap(UUID(uuidString: "66666666-6666-6666-6666-666666666666")),
            name: "Deep Work",
            modeId: try XCTUnwrap(UUID(uuidString: "77777777-7777-7777-7777-777777777777")),
            date: Date(timeIntervalSinceReferenceDate: 12_345),
            repeatWeekdays: [2, 4, 6]
        )
        legacyDefaults.set(
            try JSONEncoder().encode([schedule]),
            forKey: SharedKeys.legacySchedulesKey
        )

        let store = ScheduleStore(
            sharedDefaults: sharedDefaults,
            legacyDefaults: legacyDefaults
        )
        let loaded = store.load()

        XCTAssertEqual(loaded, [schedule])
        XCTAssertNil(legacyDefaults.data(forKey: SharedKeys.legacySchedulesKey))
        XCTAssertNotNil(sharedDefaults.data(forKey: SharedKeys.allSchedulesKey))
        XCTAssertEqual(store.load(), [schedule])
    }

    func testSharedSchedulesTakePrecedenceOverStaleLegacyData() throws {
        let sharedSchedule = Schedule(
            id: try XCTUnwrap(UUID(uuidString: "88888888-8888-8888-8888-888888888888")),
            name: "Shared",
            modeId: try XCTUnwrap(UUID(uuidString: "99999999-9999-9999-9999-999999999999")),
            date: Date(timeIntervalSinceReferenceDate: 20_000)
        )
        let staleSchedule = Schedule(
            id: try XCTUnwrap(UUID(uuidString: "AAAAAAAA-1111-1111-1111-111111111111")),
            name: "Legacy",
            modeId: try XCTUnwrap(UUID(uuidString: "BBBBBBBB-1111-1111-1111-111111111111")),
            date: Date(timeIntervalSinceReferenceDate: 10_000)
        )
        let store = ScheduleStore(
            sharedDefaults: sharedDefaults,
            legacyDefaults: legacyDefaults
        )

        store.save([sharedSchedule])
        legacyDefaults.set(
            try JSONEncoder().encode([staleSchedule]),
            forKey: SharedKeys.legacySchedulesKey
        )

        XCTAssertEqual(store.load(), [sharedSchedule])
        XCTAssertNotNil(legacyDefaults.data(forKey: SharedKeys.legacySchedulesKey))
    }
}
