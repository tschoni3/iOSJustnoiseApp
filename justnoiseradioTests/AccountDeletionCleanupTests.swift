import XCTest
import ActivityKit
@testable import justnoiseradio

@MainActor
final class AccountDeletionCoordinatorTests: XCTestCase {
    func testRemoteFailurePreservesEveryLocalSurface() async throws {
        let fixture = try CleanerFixture()
        defer { fixture.tearDown() }
        fixture.populateDefaults()
        try fixture.populateFiles()

        let remote = RemoteDeletionSpy(error: TestFailure.remoteDeletion)
        let effects = SystemEffectsSpy()
        let identity = IdentityResetSpy()
        let auth = AuthSignOutSpy()
        var signedOut = false
        let coordinator = fixture.makeCoordinator(
            remote: remote,
            effects: effects,
            identity: identity,
            auth: auth,
            setSignedOut: { signedOut = true }
        )

        do {
            try await coordinator.deleteAccount(accessToken: "token")
            XCTFail("Expected remote deletion to fail")
        } catch TestFailure.remoteDeletion {
            // Expected.
        }

        for key in LocalAccountDataCleaner.accountOwnedStandardDefaultsKeys {
            XCTAssertEqual(fixture.standardDefaults.string(forKey: key), "value")
        }
        for key in LocalAccountDataCleaner.accountOwnedSharedDefaultsKeys {
            XCTAssertEqual(fixture.sharedDefaults.string(forKey: key), "value")
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.legacyAudio.path))
        XCTAssertFalse(fixture.cleaner.hasPendingCleanup)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.cleaner.cleanupMarkerURL.path))
        XCTAssertEqual(effects.totalCallCount, 0)
        XCTAssertEqual(identity.callCount, 0)
        XCTAssertEqual(auth.callCount, 0)
        XCTAssertFalse(signedOut)
    }

    func testConfirmedDeletionPurgesAccountDataAndPreservesInstallationState() async throws {
        let fixture = try CleanerFixture()
        defer { fixture.tearDown() }
        fixture.populateDefaults()
        try fixture.populateFiles()

        let remote = RemoteDeletionSpy()
        let effects = SystemEffectsSpy()
        effects.onQuiesce = {
            XCTAssertTrue(
                fixture.cleaner.hasDurableCleanupMarker,
                "Atomic retry marker must precede mutation"
            )
            XCTAssertTrue(
                fixture.makeCleaner().hasDurableCleanupMarker,
                "A fresh cleaner must observe the durable marker"
            )
        }
        let identity = IdentityResetSpy()
        let auth = AuthSignOutSpy()
        var signedOut = false
        let coordinator = fixture.makeCoordinator(
            remote: remote,
            effects: effects,
            identity: identity,
            auth: auth,
            setSignedOut: { signedOut = true }
        )

        try await coordinator.deleteAccount(accessToken: "token")

        for key in LocalAccountDataCleaner.accountOwnedStandardDefaultsKeys {
            XCTAssertNil(fixture.standardDefaults.object(forKey: key), "Expected \(key) to be removed")
        }
        for key in LocalAccountDataCleaner.accountOwnedSharedDefaultsKeys {
            XCTAssertNil(fixture.sharedDefaults.object(forKey: key), "Expected \(key) to be removed")
        }

        XCTAssertTrue(fixture.standardDefaults.bool(forKey: "hasCompletedOnboarding"))
        XCTAssertTrue(fixture.standardDefaults.bool(forKey: "hasCompletedCoachMarks"))
        XCTAssertTrue(fixture.standardDefaults.bool(forKey: SharedKeys.activationKey))
        XCTAssertEqual(
            fixture.standardDefaults.integer(forKey: SharedKeys.emergencyUnzapKey),
            3
        )
        XCTAssertTrue(fixture.sharedDefaults.bool(forKey: SharedKeys.activationKey))

        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.captureClipsDirectory.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.captureDraftsDirectory.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.legacyAudio.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.unrelatedDocument.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.nestedUnrelatedAudio.path))

        XCTAssertFalse(fixture.cleaner.hasPendingCleanup)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.cleaner.cleanupMarkerURL.path))
        XCTAssertEqual(remote.callCount, 1)
        XCTAssertEqual(effects.quiesceCallCount, 1)
        XCTAssertEqual(effects.clearRestrictionsCallCount, 1)
        XCTAssertEqual(effects.endActivitiesCallCount, 1)
        XCTAssertEqual(effects.clearNotificationsCallCount, 1)
        XCTAssertEqual(effects.resetStateCallCount, 1)
        XCTAssertEqual(identity.callCount, 1)
        XCTAssertEqual(auth.callCount, 1)
        XCTAssertTrue(signedOut)
    }

    func testCleanerIsIdempotentWhenDataIsAlreadyAbsent() throws {
        let fixture = try CleanerFixture()
        defer { fixture.tearDown() }
        fixture.populateDefaults()
        try fixture.populateFiles()

        try fixture.cleaner.markCleanupPending()
        try fixture.cleaner.purgePersistedAccountData()
        try fixture.cleaner.purgePersistedAccountData()
        try fixture.cleaner.clearCleanupMarker()
        try fixture.cleaner.clearCleanupMarker()

        XCTAssertFalse(fixture.cleaner.hasPendingCleanup)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.captureClipsDirectory.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.captureDraftsDirectory.path))
    }

    func testFailedFileRemovalKeepsMarkerAndRetryCompletesCleanup() async throws {
        let fixture = try CleanerFixture()
        defer { fixture.tearDown() }
        fixture.populateDefaults()
        try fixture.populateFiles()

        let liveFileSystem = AccountDeletionFileSystem.live
        var shouldFailRemoval = true
        let failingFileSystem = AccountDeletionFileSystem(
            fileExists: liveFileSystem.fileExists,
            contentsOfDirectory: liveFileSystem.contentsOfDirectory,
            isRegularFile: liveFileSystem.isRegularFile,
            removeItem: { url in
                if shouldFailRemoval, url.standardizedFileURL == fixture.captureClipsDirectory.standardizedFileURL {
                    shouldFailRemoval = false
                    throw TestFailure.fileRemoval
                }
                try liveFileSystem.removeItem(url)
            }
        )
        let failingCleaner = fixture.makeCleaner(fileSystem: failingFileSystem)
        let effects = SystemEffectsSpy()
        let identity = IdentityResetSpy()
        let auth = AuthSignOutSpy()
        var signedOut = false
        let firstCoordinator = AccountDeletionCoordinator(
            remoteDeleter: RemoteDeletionSpy(),
            cleaner: failingCleaner,
            systemEffects: effects,
            identityResetter: identity,
            authSignOut: auth,
            setSignedOut: { signedOut = true }
        )

        do {
            try await firstCoordinator.deleteAccount(accessToken: "token")
            XCTFail("Expected local cleanup to remain pending")
        } catch is AccountDeletionCoordinationError {
            // Expected: the remote account is gone, but retry remains pending.
        }

        XCTAssertTrue(failingCleaner.hasPendingCleanup)
        XCTAssertTrue(failingCleaner.hasDurableCleanupMarker)
        XCTAssertTrue(signedOut)
        XCTAssertEqual(identity.callCount, 1)
        XCTAssertEqual(auth.callCount, 1)

        let retryCoordinator = fixture.makeCoordinator(
            remote: RemoteDeletionSpy(error: TestFailure.remoteDeletion),
            effects: SystemEffectsSpy(),
            identity: IdentityResetSpy(),
            auth: AuthSignOutSpy(),
            setSignedOut: {}
        )

        let didRetryCleanup = try await retryCoordinator.retryPendingLocalCleanupIfNeeded()
        XCTAssertTrue(didRetryCleanup)
        XCTAssertFalse(fixture.cleaner.hasPendingCleanup)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.captureClipsDirectory.path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.captureDraftsDirectory.path))
    }

    func testMarkerWriteFailureStillPurgesAndSignsOutWhenCleanupSucceeds() async throws {
        let fixture = try CleanerFixture()
        defer { fixture.tearDown() }
        fixture.populateDefaults()
        try fixture.populateFiles()

        let liveMarkerFileSystem = AccountDeletionMarkerFileSystem.live
        let failingMarkerFileSystem = AccountDeletionMarkerFileSystem(
            fileExists: liveMarkerFileSystem.fileExists,
            createDirectory: liveMarkerFileSystem.createDirectory,
            writeAtomically: { _, _ in throw TestFailure.markerWrite },
            removeItem: liveMarkerFileSystem.removeItem
        )
        let cleaner = fixture.makeCleaner(markerFileSystem: failingMarkerFileSystem)
        let effects = SystemEffectsSpy()
        let identity = IdentityResetSpy()
        let auth = AuthSignOutSpy()
        var signedOut = false
        let coordinator = AccountDeletionCoordinator(
            remoteDeleter: RemoteDeletionSpy(),
            cleaner: cleaner,
            systemEffects: effects,
            identityResetter: identity,
            authSignOut: auth,
            setSignedOut: { signedOut = true }
        )

        try await coordinator.deleteAccount(accessToken: "token")

        XCTAssertFalse(cleaner.hasPendingCleanup)
        XCTAssertFalse(cleaner.hasDurableCleanupMarker)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.captureClipsDirectory.path))
        XCTAssertEqual(effects.resetStateCallCount, 1)
        XCTAssertEqual(identity.callCount, 1)
        XCTAssertEqual(auth.callCount, 1)
        XCTAssertTrue(signedOut)
    }

    func testMarkerAndPurgeFailureLeavesFailClosedFallbackForRetry() async throws {
        let fixture = try CleanerFixture()
        defer { fixture.tearDown() }
        fixture.populateDefaults()
        try fixture.populateFiles()

        let liveMarkerFileSystem = AccountDeletionMarkerFileSystem.live
        let failingMarkerFileSystem = AccountDeletionMarkerFileSystem(
            fileExists: liveMarkerFileSystem.fileExists,
            createDirectory: liveMarkerFileSystem.createDirectory,
            writeAtomically: { _, _ in throw TestFailure.markerWrite },
            removeItem: liveMarkerFileSystem.removeItem
        )
        let liveFileSystem = AccountDeletionFileSystem.live
        let failingFileSystem = AccountDeletionFileSystem(
            fileExists: liveFileSystem.fileExists,
            contentsOfDirectory: liveFileSystem.contentsOfDirectory,
            isRegularFile: liveFileSystem.isRegularFile,
            removeItem: { url in
                if url.standardizedFileURL == fixture.captureClipsDirectory.standardizedFileURL {
                    throw TestFailure.fileRemoval
                }
                try liveFileSystem.removeItem(url)
            }
        )
        let cleaner = fixture.makeCleaner(
            fileSystem: failingFileSystem,
            markerFileSystem: failingMarkerFileSystem
        )
        let effects = SystemEffectsSpy()
        let identity = IdentityResetSpy()
        let auth = AuthSignOutSpy()
        var signedOut = false
        let coordinator = AccountDeletionCoordinator(
            remoteDeleter: RemoteDeletionSpy(),
            cleaner: cleaner,
            systemEffects: effects,
            identityResetter: identity,
            authSignOut: auth,
            setSignedOut: { signedOut = true }
        )

        do {
            try await coordinator.deleteAccount(accessToken: "token")
            XCTFail("Expected cleanup to remain blocked")
        } catch is AccountDeletionCoordinationError {
            // The remote identity is gone, and the fallback keeps launch fail-closed.
        }

        XCTAssertFalse(cleaner.hasDurableCleanupMarker)
        XCTAssertTrue(
            fixture.standardDefaults.bool(
                forKey: LocalAccountDataCleaner.cleanupFallbackDefaultsKey
            )
        )
        XCTAssertTrue(cleaner.hasPendingCleanup)
        XCTAssertEqual(effects.resetStateCallCount, 1)
        XCTAssertEqual(identity.callCount, 1)
        XCTAssertEqual(auth.callCount, 1)
        XCTAssertTrue(signedOut)

        let retryRemote = RemoteDeletionSpy(error: TestFailure.remoteDeletion)
        let retryCoordinator = fixture.makeCoordinator(
            remote: retryRemote,
            effects: SystemEffectsSpy(),
            identity: IdentityResetSpy(),
            auth: AuthSignOutSpy(),
            setSignedOut: {}
        )
        let didRetry = try await retryCoordinator.retryPendingLocalCleanupIfNeeded()

        XCTAssertTrue(didRetry)
        XCTAssertEqual(retryRemote.callCount, 0)
        XCTAssertFalse(fixture.cleaner.hasPendingCleanup)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.captureClipsDirectory.path))
    }

    func testTraversalOutsideConfiguredRootsIsRejectedBeforeAnyMutation() throws {
        let fixture = try CleanerFixture()
        defer { fixture.tearDown() }
        fixture.populateDefaults()

        let outsideDirectory = fixture.rootDirectory
            .appendingPathComponent("outside", isDirectory: true)
            .appendingPathComponent(CaptureAudioFileStore.clipsDirectoryName, isDirectory: true)
        try FileManager.default.createDirectory(at: outsideDirectory, withIntermediateDirectories: true)
        let outsideFile = outsideDirectory.appendingPathComponent("must-survive.m4a")
        try Data("outside".utf8).write(to: outsideFile)

        let unsafeCleaner = fixture.makeCleaner(captureClipsDirectory: outsideDirectory)
        try unsafeCleaner.markCleanupPending()

        XCTAssertThrowsError(try unsafeCleaner.purgePersistedAccountData()) { error in
            XCTAssertEqual(error as? LocalAccountDataCleanupError, .unsafePath(outsideDirectory))
        }

        XCTAssertEqual(fixture.standardDefaults.string(forKey: "sessionHistory"), "value")
        XCTAssertTrue(FileManager.default.fileExists(atPath: outsideFile.path))
        XCTAssertTrue(unsafeCleaner.hasPendingCleanup)
    }

    func testCleanerPreservesDeviceActivitySentinelUntilAuthenticatedResume() throws {
        let fixture = try CleanerFixture()
        defer { fixture.tearDown() }
        fixture.populateDefaults()

        let sentinel = AccountDeletionDeviceActivitySentinel(
            defaults: fixture.sharedDefaults
        )
        sentinel.establish()

        try fixture.cleaner.purgePersistedAccountData()

        XCTAssertTrue(sentinel.isEstablished)
        XCTAssertNil(fixture.sharedDefaults.object(forKey: SharedKeys.selectionDataKey))

        sentinel.clearForAuthenticatedResume()
        XCTAssertFalse(sentinel.isEstablished)
    }

    func testColdLaunchCleanupRetryRestoresOnlyPreservedInstallationState() async throws {
        let fixture = try CleanerFixture()
        defer { fixture.tearDown() }
        fixture.populateDefaults()
        try fixture.populateFiles()
        try fixture.cleaner.markCleanupPending()

        let installationState = NFCInstallationStateProvider(
            isActivated: {
                fixture.sharedDefaults.bool(forKey: SharedKeys.activationKey)
                    || fixture.standardDefaults.bool(forKey: SharedKeys.activationKey)
            },
            emergencyUnzapCount: {
                fixture.standardDefaults.integer(forKey: SharedKeys.emergencyUnzapKey)
            }
        )
        let liveActivityClient = SessionLiveActivityClient(
            areActivitiesEnabled: { false },
            existingActivities: { [] },
            request: { _, _ in throw TestFailure.unexpectedLiveActivityRequest }
        )
        let freshNFCViewModel = NFCViewModel(
            installationStateProvider: installationState,
            liveActivityClient: liveActivityClient
        )
        XCTAssertFalse(freshNFCViewModel.isHydrated)
        XCTAssertFalse(freshNFCViewModel.isActivated)

        let effects = ColdLaunchNFCSystemEffects(nfcViewModel: freshNFCViewModel)
        let remote = RemoteDeletionSpy(error: TestFailure.remoteDeletion)
        let coordinator = AccountDeletionCoordinator(
            remoteDeleter: remote,
            cleaner: fixture.cleaner,
            systemEffects: effects,
            identityResetter: IdentityResetSpy(),
            authSignOut: AuthSignOutSpy(),
            setSignedOut: {}
        )

        let didRetry = try await coordinator.retryPendingLocalCleanupIfNeeded()

        XCTAssertTrue(didRetry)
        XCTAssertEqual(remote.callCount, 0)
        XCTAssertTrue(freshNFCViewModel.isHydrated)
        XCTAssertTrue(freshNFCViewModel.isActivated)
        XCTAssertEqual(freshNFCViewModel.emergencyUnzapCount, 3)
        XCTAssertEqual(freshNFCViewModel.modes.map(\.name), ["Noise", "Focus", "Sleep"])
        XCTAssertEqual(freshNFCViewModel.selectedMode?.id, freshNFCViewModel.modes.first?.id)
        XCTAssertTrue(freshNFCViewModel.schedules.isEmpty)
        XCTAssertTrue(freshNFCViewModel.sessionHistory.isEmpty)
        XCTAssertTrue(freshNFCViewModel.journalHistory.isEmpty)
        XCTAssertNil(fixture.standardDefaults.object(forKey: "modes"))
        XCTAssertTrue(fixture.standardDefaults.bool(forKey: SharedKeys.activationKey))
        XCTAssertTrue(fixture.sharedDefaults.bool(forKey: SharedKeys.activationKey))
    }
}

@MainActor
final class SignalAccountBoundaryTests: XCTestCase {
    func testLateSignalResultIsRejectedByCancellationAndGenerationBoundaries() async throws {
        try await assertLateSignalResultIsRejected(cancelTask: true)
        try await assertLateSignalResultIsRejected(cancelTask: false)
    }

    private func assertLateSignalResultIsRejected(cancelTask: Bool) async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SignalAccountBoundary-\(UUID().uuidString)", isDirectory: true)
        let documents = root.appendingPathComponent("Documents", isDirectory: true)
        let temporary = root.appendingPathComponent("tmp", isDirectory: true)
        let applicationSupport = root.appendingPathComponent(
            "Application Support",
            isDirectory: true
        )
        let clips = documents.appendingPathComponent(
            CaptureAudioFileStore.clipsDirectoryName,
            isDirectory: true
        )
        let drafts = temporary.appendingPathComponent(
            CaptureAudioFileStore.draftDirectoryName,
            isDirectory: true
        )
        try FileManager.default.createDirectory(at: drafts, withIntermediateDirectories: true)

        let standardDefaults = try XCTUnwrap(
            UserDefaults(suiteName: "SignalBoundaryStandard-\(UUID().uuidString)")
        )
        let sharedDefaults = try XCTUnwrap(
            UserDefaults(suiteName: "SignalBoundaryShared-\(UUID().uuidString)")
        )
        defer {
            try? FileManager.default.removeItem(at: root)
            for key in standardDefaults.dictionaryRepresentation().keys {
                standardDefaults.removeObject(forKey: key)
            }
            for key in sharedDefaults.dictionaryRepresentation().keys {
                sharedDefaults.removeObject(forKey: key)
            }
        }

        let analysisStarted = expectation(description: "analysis started")
        let analysisTaskFinished = expectation(description: "analysis task finished")
        let analyzer = DeferredSignalAnalyzer(onStart: { analysisStarted.fulfill() })
        let clipID = UUID()
        let audioStore = CaptureAudioFileStore(
            draftDirectory: drafts,
            clipsDirectory: clips
        )
        let signalStore = SignalStore(
            analysisClient: analyzer,
            captureAudioFileStore: audioStore,
            captureIDProvider: { clipID },
            userDefaults: standardDefaults,
            analysisTaskCanceller: { task in
                if cancelTask {
                    task.cancel()
                }
            },
            analysisTaskDidFinish: { analysisTaskFinished.fulfill() }
        )

        let draftURL = drafts.appendingPathComponent("recording.m4a")
        try Data("audio".utf8).write(to: draftURL)
        let clip = try signalStore.saveCaptureClip(
            audioURL: draftURL,
            duration: 4,
            selectedModeName: "Focus"
        )
        signalStore.beginSignalAnalysis(for: clip, selectedModeName: "Focus")
        await fulfillment(of: [analysisStarted], timeout: 2)

        signalStore.quiesceForAccountDeletion()
        signalStore.resetInMemoryStateForAccountDeletion()
        let cleaner = LocalAccountDataCleaner(
            standardDefaults: standardDefaults,
            sharedDefaults: sharedDefaults,
            documentsDirectory: documents,
            temporaryDirectory: temporary,
            applicationSupportDirectory: applicationSupport,
            captureClipsDirectory: clips,
            captureDraftsDirectory: drafts
        )
        try cleaner.purgePersistedAccountData()

        analyzer.resolve(with: try makeLateSignalResponse(clipID: clipID))
        await fulfillment(of: [analysisTaskFinished], timeout: 2)

        XCTAssertTrue(signalStore.captureClips.isEmpty)
        XCTAssertEqual(signalStore.signalMemoryState, SignalMemoryState())
        XCTAssertTrue(signalStore.signalAnalysisClipIDsInFlight.isEmpty)
        XCTAssertNil(standardDefaults.object(forKey: "jn_capture_clips_v1"))
        XCTAssertNil(standardDefaults.object(forKey: "jn_signal_memory_state_v1"))
        XCTAssertFalse(FileManager.default.fileExists(atPath: clips.path))
    }

    private func makeLateSignalResponse(clipID: UUID) throws -> BackendCaptureResponse {
        let json = """
        {
          "contractVersion": 1,
          "engineVersion": "account-boundary-test",
          "operationId": "late-old-account-operation",
          "baseMemoryRevision": 0,
          "nextMemoryRevision": 1,
          "requiresExactBaseRevision": true,
          "capturePatch": {
            "captureId": "\(clipID.uuidString)",
            "transcript": "must not survive account deletion"
          },
          "memoryPatch": { "operations": [] },
          "commentDecision": { "shouldShow": false },
          "safety": { "isSafe": true }
        }
        """
        return try JSONDecoder().decode(
            BackendCaptureResponse.self,
            from: Data(json.utf8)
        )
    }
}

final class DeviceActivityAccountBoundaryTests: XCTestCase {
    func testCallbackAlreadyInFlightSelfClearsAfterSentinelWinsRace() throws {
        let defaults = try makeDefaults()
        defer { clear(defaults) }
        var shieldsAreApplied = false
        let gate = AccountDeletionDeviceActivityGate(defaults: defaults) {
            shieldsAreApplied = false
        }
        let sentinel = AccountDeletionDeviceActivitySentinel(defaults: defaults)

        let didCommit = gate.performMutationIfActive {
            shieldsAreApplied = true
            defaults.set("old-account", forKey: SharedKeys.activeModeIdKey)
            sentinel.establish()
        }

        XCTAssertFalse(didCommit)
        XCTAssertFalse(shieldsAreApplied)
        XCTAssertTrue(sentinel.isEstablished)
        for key in SharedKeys.accountOwnedTransientKeys {
            XCTAssertNil(defaults.object(forKey: key), "Expected late state \(key) to self-clear")
        }
    }

    func testCallbackEnteringAfterSentinelSelfClearsWithoutMutating() throws {
        let defaults = try makeDefaults()
        defer { clear(defaults) }
        for key in SharedKeys.accountOwnedTransientKeys {
            defaults.set("old-account", forKey: key)
        }
        var shieldsAreApplied = true
        let sentinel = AccountDeletionDeviceActivitySentinel(defaults: defaults)
        sentinel.establish()
        let gate = AccountDeletionDeviceActivityGate(defaults: defaults) {
            shieldsAreApplied = false
        }
        var mutationRan = false

        let didCommit = gate.performMutationIfActive {
            mutationRan = true
        }

        XCTAssertFalse(didCommit)
        XCTAssertFalse(mutationRan)
        XCTAssertFalse(shieldsAreApplied)
        XCTAssertTrue(sentinel.isEstablished)
        for key in SharedKeys.accountOwnedTransientKeys {
            XCTAssertNil(defaults.object(forKey: key), "Expected stale state \(key) to self-clear")
        }
    }

    private func makeDefaults() throws -> UserDefaults {
        try XCTUnwrap(
            UserDefaults(suiteName: "DeviceActivityBoundary-\(UUID().uuidString)")
        )
    }

    private func clear(_ defaults: UserDefaults) {
        for key in defaults.dictionaryRepresentation().keys {
            defaults.removeObject(forKey: key)
        }
    }
}

@MainActor
final class NFCLiveActivityAccountBoundaryTests: XCTestCase {
    func testLateCreationAfterDeletionIsEndedAndNeverAdopted() async throws {
        let requestStarted = expectation(description: "Live Activity request started")
        let staleActivityEnded = expectation(description: "Late Live Activity ended")
        let deferredRequest = DeferredLiveActivityRequest(
            onStart: { requestStarted.fulfill() }
        )
        let client = SessionLiveActivityClient(
            areActivitiesEnabled: { true },
            existingActivities: { [] },
            request: { attributes, content in
                try await deferredRequest.request(attributes: attributes, content: content)
            }
        )
        let viewModel = NFCViewModel(
            installationStateProvider: NFCInstallationStateProvider(
                isActivated: { true },
                emergencyUnzapCount: { 3 }
            ),
            liveActivityClient: client
        )

        viewModel.startLiveActivity()
        await fulfillment(of: [requestStarted], timeout: 2)
        viewModel.quiesceForAccountDeletion()
        await viewModel.endLiveActivitiesForAccountDeletion()

        deferredRequest.resolve(
            with: SessionLiveActivityHandle(
                id: "late-old-account-activity",
                activity: nil,
                endImmediately: { staleActivityEnded.fulfill() }
            )
        )
        await fulfillment(of: [staleActivityEnded], timeout: 2)
        await Task.yield()

        XCTAssertNil(viewModel.currentLiveActivityHandleID)
        XCTAssertNil(viewModel.liveActivity)
    }
}

@MainActor
final class PostHogAccountBoundaryTests: XCTestCase {
    func testMarkerPresentAccountCleanupLaunchNeverStartsPostHog() throws {
        let fixture = try PostHogFixture()
        defer { fixture.tearDown() }
        try fixture.populateAnalyticsData()
        fixture.cleaner.establishCleanupMarker()
        var setupCallCount = 0
        let runtime = JustNoiseAnalyticsRuntime(
            cleaner: fixture.cleaner,
            client: PostHogRuntimeClient(
                setup: { setupCallCount += 1 },
                close: {},
                capture: { _, _ in },
                identify: { _, _ in }
            )
        )

        let outcome = runtime.prepareColdLaunch(accountCleanupPending: true)

        XCTAssertEqual(outcome, .gatedByAccountCleanup)
        XCTAssertEqual(setupCallCount, 0)
        XCTAssertTrue(fixture.cleaner.hasPendingCleanup)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.apiKeyDirectory.path))
    }

    func testTwoPhaseClosePurgeAndColdVerificationPrecedeSetup() throws {
        let fixture = try PostHogFixture()
        defer { fixture.tearDown() }
        var events: [String] = []
        let liveFileSystem = AccountDeletionFileSystem.live
        let tracingCleaner = fixture.makeCleaner(
            fileSystem: AccountDeletionFileSystem(
                fileExists: liveFileSystem.fileExists,
                contentsOfDirectory: liveFileSystem.contentsOfDirectory,
                isRegularFile: liveFileSystem.isRegularFile,
                removeItem: { url in
                    events.append("remove:\(url.lastPathComponent)")
                    try liveFileSystem.removeItem(url)
                }
            )
        )
        var capturedAfterClose = 0
        let firstRuntime = JustNoiseAnalyticsRuntime(
            cleaner: tracingCleaner,
            client: PostHogRuntimeClient(
                setup: { events.append("setup:first") },
                close: { events.append("close") },
                capture: { _, _ in capturedAfterClose += 1 },
                identify: { _, _ in }
            )
        )

        XCTAssertEqual(
            firstRuntime.prepareColdLaunch(accountCleanupPending: false),
            .started
        )
        try fixture.populateAnalyticsData()
        try firstRuntime.quiesceAndPurgeForAccountDeletion()
        firstRuntime.capture("must_not_be_queued")

        let closeIndex = try XCTUnwrap(events.firstIndex(of: "close"))
        let firstRemovalIndex = try XCTUnwrap(
            events.firstIndex(where: { $0.hasPrefix("remove:") })
        )
        XCTAssertLessThan(closeIndex, firstRemovalIndex)
        XCTAssertEqual(capturedAfterClose, 0)
        XCTAssertTrue(tracingCleaner.hasPendingCleanup)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.apiKeyDirectory.path))

        // Model a PostHog request callback that outlives close() and writes to disk.
        try fixture.writeLateCallbackData()
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.lateCallbackFile.path))

        var secondSetupCallCount = 0
        let secondRuntime = JustNoiseAnalyticsRuntime(
            cleaner: tracingCleaner,
            client: PostHogRuntimeClient(
                setup: {
                    XCTAssertFalse(
                        FileManager.default.fileExists(atPath: fixture.apiKeyDirectory.path)
                    )
                    XCTAssertFalse(tracingCleaner.hasPendingCleanup)
                    secondSetupCallCount += 1
                },
                close: {},
                capture: { _, _ in },
                identify: { _, _ in }
            )
        )

        XCTAssertEqual(
            secondRuntime.prepareColdLaunch(accountCleanupPending: false),
            .started
        )
        XCTAssertEqual(secondSetupCallCount, 1)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.lateCallbackFile.path))
    }

    func testPurgeRemovesCurrentAndLegacyDataButPreservesSiblingsAndLifecycleDefaults() throws {
        let fixture = try PostHogFixture()
        defer { fixture.tearDown() }
        try fixture.populateAnalyticsData()
        fixture.standardDefaults.set("1.2.3", forKey: "PHGVersionKey")
        fixture.standardDefaults.set("456", forKey: "PHGBuildKeyV2")

        try fixture.cleaner.purgePersistedData()

        XCTAssertFalse(FileManager.default.fileExists(atPath: fixture.apiKeyDirectory.path))
        for name in PostHogAccountDataCleaner.legacyStorageNames {
            XCTAssertFalse(
                FileManager.default.fileExists(
                    atPath: fixture.storageBaseDirectory.appendingPathComponent(name).path
                ),
                "Expected legacy item \(name) to be removed"
            )
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.siblingAPIKeyFile.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.unrelatedBaseFile.path))
        XCTAssertEqual(fixture.standardDefaults.string(forKey: "PHGVersionKey"), "1.2.3")
        XCTAssertEqual(fixture.standardDefaults.string(forKey: "PHGBuildKeyV2"), "456")
    }

    func testTraversalIsRejectedBeforeAnyAnalyticsMutation() throws {
        let fixture = try PostHogFixture()
        defer { fixture.tearDown() }
        try fixture.populateAnalyticsData()
        let outsideDirectory = fixture.applicationSupportDirectory
            .appendingPathComponent("Outside", isDirectory: true)
        try FileManager.default.createDirectory(
            at: outsideDirectory,
            withIntermediateDirectories: true
        )
        let outsideFile = outsideDirectory.appendingPathComponent("must-survive")
        try Data("outside".utf8).write(to: outsideFile)
        let unsafeCleaner = fixture.makeCleaner(apiKey: "../Outside")

        XCTAssertThrowsError(try unsafeCleaner.purgePersistedData()) { error in
            XCTAssertTrue(error is PostHogAccountDataCleanupError)
        }

        XCTAssertTrue(FileManager.default.fileExists(atPath: outsideFile.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.apiKeyDirectory.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: fixture.unrelatedBaseFile.path))
    }
}

private enum TestFailure: Error {
    case remoteDeletion
    case fileRemoval
    case markerWrite
    case unexpectedLiveActivityRequest
}

private final class DeferredSignalAnalyzer: SignalAnalyzing, @unchecked Sendable {
    private let lock = NSLock()
    private let onStart: @Sendable () -> Void
    private var continuation: CheckedContinuation<BackendCaptureResponse, Error>?

    init(onStart: @escaping @Sendable () -> Void) {
        self.onStart = onStart
    }

    func analyzeCaptureClip(
        _ clip: CaptureClip,
        selectedModeName: String?,
        context: SignalAnalysisContext
    ) async throws -> BackendCaptureResponse {
        try await withCheckedThrowingContinuation { continuation in
            lock.lock()
            self.continuation = continuation
            lock.unlock()
            onStart()
        }
    }

    func resolve(with response: BackendCaptureResponse) {
        lock.lock()
        let continuation = continuation
        self.continuation = nil
        lock.unlock()
        continuation?.resume(returning: response)
    }
}

@MainActor
private final class DeferredLiveActivityRequest {
    private let onStart: () -> Void
    private var continuation: CheckedContinuation<SessionLiveActivityHandle, Error>?

    init(onStart: @escaping () -> Void) {
        self.onStart = onStart
    }

    func request(
        attributes: SessionAttributes,
        content: ActivityContent<SessionAttributes.ContentState>
    ) async throws -> SessionLiveActivityHandle {
        _ = attributes
        _ = content
        onStart()
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
        }
    }

    func resolve(with handle: SessionLiveActivityHandle) {
        let continuation = continuation
        self.continuation = nil
        continuation?.resume(returning: handle)
    }
}

private final class RemoteDeletionSpy: AccountDeleting {
    private(set) var callCount = 0
    private let error: Error?

    init(error: Error? = nil) {
        self.error = error
    }

    func deleteAccount(accessToken: String?) async throws {
        callCount += 1
        if let error { throw error }
    }
}

@MainActor
private final class SystemEffectsSpy: AccountDeletionSystemEffecting {
    var onQuiesce: (() -> Void)?
    private(set) var quiesceCallCount = 0
    private(set) var clearRestrictionsCallCount = 0
    private(set) var endActivitiesCallCount = 0
    private(set) var clearNotificationsCallCount = 0
    private(set) var resetStateCallCount = 0

    var totalCallCount: Int {
        quiesceCallCount
            + clearRestrictionsCallCount
            + endActivitiesCallCount
            + clearNotificationsCallCount
            + resetStateCallCount
    }

    func quiesceRuntime() {
        quiesceCallCount += 1
        onQuiesce?()
    }

    func clearRestrictionsAndMonitoring() {
        clearRestrictionsCallCount += 1
    }

    func endLiveActivities() async {
        endActivitiesCallCount += 1
    }

    func clearNotifications() {
        clearNotificationsCallCount += 1
    }

    func resetInMemoryState() {
        resetStateCallCount += 1
    }
}

@MainActor
private final class ColdLaunchNFCSystemEffects: AccountDeletionSystemEffecting {
    private let nfcViewModel: NFCViewModel

    init(nfcViewModel: NFCViewModel) {
        self.nfcViewModel = nfcViewModel
    }

    func quiesceRuntime() {
        nfcViewModel.quiesceForAccountDeletion()
    }

    func clearRestrictionsAndMonitoring() {}

    func endLiveActivities() async {
        await nfcViewModel.endLiveActivitiesForAccountDeletion()
    }

    func clearNotifications() {}

    func resetInMemoryState() {
        nfcViewModel.resetInMemoryStateForAccountDeletion()
    }
}

@MainActor
private final class IdentityResetSpy: AccountDeletionIdentityResetting {
    private(set) var callCount = 0

    func resetIdentity() {
        callCount += 1
    }
}

@MainActor
private final class AuthSignOutSpy: AccountDeletionAuthSigningOut {
    private(set) var callCount = 0

    func signOutLocally() async {
        callCount += 1
    }
}

@MainActor
private final class CleanerFixture {
    let rootDirectory: URL
    let documentsDirectory: URL
    let temporaryDirectory: URL
    let applicationSupportDirectory: URL
    let captureClipsDirectory: URL
    let captureDraftsDirectory: URL
    let legacyAudio: URL
    let unrelatedDocument: URL
    let nestedUnrelatedAudio: URL
    let standardDefaults: UserDefaults
    let sharedDefaults: UserDefaults
    let cleaner: LocalAccountDataCleaner

    private let standardSuiteName: String
    private let sharedSuiteName: String

    init() throws {
        rootDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("AccountDeletionTests-\(UUID().uuidString)", isDirectory: true)
        documentsDirectory = rootDirectory.appendingPathComponent("Documents", isDirectory: true)
        temporaryDirectory = rootDirectory.appendingPathComponent("tmp", isDirectory: true)
        applicationSupportDirectory = rootDirectory.appendingPathComponent(
            "Application Support",
            isDirectory: true
        )
        captureClipsDirectory = documentsDirectory.appendingPathComponent(
            CaptureAudioFileStore.clipsDirectoryName,
            isDirectory: true
        )
        captureDraftsDirectory = temporaryDirectory.appendingPathComponent(
            CaptureAudioFileStore.draftDirectoryName,
            isDirectory: true
        )
        legacyAudio = documentsDirectory.appendingPathComponent("legacy.m4a")
        unrelatedDocument = documentsDirectory.appendingPathComponent("keep.txt")
        nestedUnrelatedAudio = documentsDirectory
            .appendingPathComponent("Unrelated", isDirectory: true)
            .appendingPathComponent("nested.m4a")

        try FileManager.default.createDirectory(at: documentsDirectory, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: temporaryDirectory, withIntermediateDirectories: true)

        standardSuiteName = "AccountDeletionStandard-\(UUID().uuidString)"
        sharedSuiteName = "AccountDeletionShared-\(UUID().uuidString)"
        standardDefaults = try XCTUnwrap(UserDefaults(suiteName: standardSuiteName))
        sharedDefaults = try XCTUnwrap(UserDefaults(suiteName: sharedSuiteName))
        cleaner = LocalAccountDataCleaner(
            standardDefaults: standardDefaults,
            sharedDefaults: sharedDefaults,
            documentsDirectory: documentsDirectory,
            temporaryDirectory: temporaryDirectory,
            applicationSupportDirectory: applicationSupportDirectory,
            captureClipsDirectory: captureClipsDirectory,
            captureDraftsDirectory: captureDraftsDirectory
        )
    }

    func populateDefaults() {
        for key in LocalAccountDataCleaner.accountOwnedStandardDefaultsKeys {
            standardDefaults.set("value", forKey: key)
        }
        for key in LocalAccountDataCleaner.accountOwnedSharedDefaultsKeys {
            sharedDefaults.set("value", forKey: key)
        }

        standardDefaults.set(true, forKey: "hasCompletedOnboarding")
        standardDefaults.set(true, forKey: "hasCompletedCoachMarks")
        standardDefaults.set(true, forKey: SharedKeys.activationKey)
        standardDefaults.set(3, forKey: SharedKeys.emergencyUnzapKey)
        sharedDefaults.set(true, forKey: SharedKeys.activationKey)
    }

    func populateFiles() throws {
        try FileManager.default.createDirectory(at: captureClipsDirectory, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: captureDraftsDirectory, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(
            at: nestedUnrelatedAudio.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )

        try Data("clip".utf8).write(
            to: captureClipsDirectory.appendingPathComponent("clip.m4a")
        )
        try Data("marker".utf8).write(
            to: captureClipsDirectory.appendingPathComponent(".pending-capture-test.json")
        )
        try Data("draft".utf8).write(
            to: captureDraftsDirectory.appendingPathComponent("draft.m4a")
        )
        try Data("legacy".utf8).write(to: legacyAudio)
        try Data("keep".utf8).write(to: unrelatedDocument)
        try Data("nested".utf8).write(to: nestedUnrelatedAudio)
    }

    func makeCleaner(
        fileSystem: AccountDeletionFileSystem = .live,
        markerFileSystem: AccountDeletionMarkerFileSystem = .live,
        captureClipsDirectory: URL? = nil
    ) -> LocalAccountDataCleaner {
        LocalAccountDataCleaner(
            standardDefaults: standardDefaults,
            sharedDefaults: sharedDefaults,
            fileSystem: fileSystem,
            markerFileSystem: markerFileSystem,
            documentsDirectory: documentsDirectory,
            temporaryDirectory: temporaryDirectory,
            applicationSupportDirectory: applicationSupportDirectory,
            captureClipsDirectory: captureClipsDirectory ?? self.captureClipsDirectory,
            captureDraftsDirectory: captureDraftsDirectory
        )
    }

    func makeCoordinator(
        remote: RemoteDeletionSpy,
        effects: SystemEffectsSpy,
        identity: IdentityResetSpy,
        auth: AuthSignOutSpy,
        setSignedOut: @escaping () -> Void
    ) -> AccountDeletionCoordinator {
        AccountDeletionCoordinator(
            remoteDeleter: remote,
            cleaner: cleaner,
            systemEffects: effects,
            identityResetter: identity,
            authSignOut: auth,
            setSignedOut: setSignedOut
        )
    }

    func tearDown() {
        try? FileManager.default.removeItem(at: rootDirectory)
        clear(defaults: standardDefaults)
        clear(defaults: sharedDefaults)
    }

    private func clear(defaults: UserDefaults) {
        for key in defaults.dictionaryRepresentation().keys {
            defaults.removeObject(forKey: key)
        }
    }
}

@MainActor
private final class PostHogFixture {
    static let bundleIdentifier = "stilltschoni.justnoiseradio"
    static let apiKey = "test-api-key"

    let rootDirectory: URL
    let applicationSupportDirectory: URL
    let storageBaseDirectory: URL
    let apiKeyDirectory: URL
    let siblingAPIKeyDirectory: URL
    let siblingAPIKeyFile: URL
    let unrelatedBaseFile: URL
    let lateCallbackFile: URL
    let standardDefaults: UserDefaults
    let cleaner: PostHogAccountDataCleaner

    init() throws {
        rootDirectory = FileManager.default.temporaryDirectory.appendingPathComponent(
            "PostHogBoundary-\(UUID().uuidString)",
            isDirectory: true
        )
        applicationSupportDirectory = rootDirectory.appendingPathComponent(
            "Application Support",
            isDirectory: true
        )
        storageBaseDirectory = applicationSupportDirectory.appendingPathComponent(
            Self.bundleIdentifier,
            isDirectory: true
        )
        apiKeyDirectory = storageBaseDirectory.appendingPathComponent(
            Self.apiKey,
            isDirectory: true
        )
        siblingAPIKeyDirectory = storageBaseDirectory.appendingPathComponent(
            "another-api-key",
            isDirectory: true
        )
        siblingAPIKeyFile = siblingAPIKeyDirectory.appendingPathComponent("keep")
        unrelatedBaseFile = storageBaseDirectory.appendingPathComponent("unrelated.keep")
        lateCallbackFile = apiKeyDirectory
            .appendingPathComponent("posthog.queueFolder", isDirectory: true)
            .appendingPathComponent("late-event")

        try FileManager.default.createDirectory(
            at: applicationSupportDirectory,
            withIntermediateDirectories: true
        )
        standardDefaults = try XCTUnwrap(
            UserDefaults(suiteName: "PostHogBoundaryDefaults-\(UUID().uuidString)")
        )
        cleaner = PostHogAccountDataCleaner(
            standardDefaults: standardDefaults,
            applicationSupportDirectory: applicationSupportDirectory,
            bundleIdentifier: Self.bundleIdentifier,
            apiKey: Self.apiKey
        )
    }

    func makeCleaner(
        fileSystem: AccountDeletionFileSystem = .live,
        apiKey: String = PostHogFixture.apiKey
    ) -> PostHogAccountDataCleaner {
        PostHogAccountDataCleaner(
            standardDefaults: standardDefaults,
            fileSystem: fileSystem,
            applicationSupportDirectory: applicationSupportDirectory,
            bundleIdentifier: Self.bundleIdentifier,
            apiKey: apiKey
        )
    }

    func populateAnalyticsData() throws {
        let eventQueue = apiKeyDirectory.appendingPathComponent(
            "posthog.queueFolder",
            isDirectory: true
        )
        let replayQueue = apiKeyDirectory.appendingPathComponent(
            "posthog.replayFolder",
            isDirectory: true
        )
        try FileManager.default.createDirectory(at: eventQueue, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: replayQueue, withIntermediateDirectories: true)
        try Data("event".utf8).write(to: eventQueue.appendingPathComponent("event"))
        try Data("replay".utf8).write(to: replayQueue.appendingPathComponent("replay"))
        try Data("identity".utf8).write(
            to: apiKeyDirectory.appendingPathComponent("posthog.distinctId")
        )

        try FileManager.default.createDirectory(
            at: siblingAPIKeyDirectory,
            withIntermediateDirectories: true
        )
        try Data("sibling".utf8).write(to: siblingAPIKeyFile)
        try Data("unrelated".utf8).write(to: unrelatedBaseFile)

        for name in PostHogAccountDataCleaner.legacyStorageNames {
            try Data("legacy".utf8).write(
                to: storageBaseDirectory.appendingPathComponent(name)
            )
        }
    }

    func writeLateCallbackData() throws {
        try FileManager.default.createDirectory(
            at: lateCallbackFile.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try Data("late".utf8).write(to: lateCallbackFile)
    }

    func tearDown() {
        try? FileManager.default.removeItem(at: rootDirectory)
        for key in standardDefaults.dictionaryRepresentation().keys {
            standardDefaults.removeObject(forKey: key)
        }
    }
}
