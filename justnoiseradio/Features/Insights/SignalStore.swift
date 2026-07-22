import Combine
import Foundation
import OSLog
import UIKit

final class SignalStore: NSObject, ObservableObject {
    @Published var isHydrated: Bool = false
    // Capture clips are the only persisted source of truth for Signal memory on device.
    @Published var captureClips: [CaptureClip] = []
    @Published var signalMemoryState = SignalMemoryState()
    @Published var signalAnalysisClipIDsInFlight: Set<UUID> = []
    @Published private var seenSignalCommentIDs: Set<String> = []
    private var legacySignalInsights: [SignalInsight] = []

    private let captureClipsKey = "jn_capture_clips_v1"
    private let legacySignalExtractionsKey = "jn_signal_extractions_v1"
    private let legacySignalInsightsKey = "jn_signal_insights_v1"
    private let signalMemoryStateKey = "jn_signal_memory_state_v1"
    private let signalAnalysisFailuresKey = "jn_signal_analysis_failures_v1"
    private let signalAnalysisPausedUntilKey = "jn_signal_analysis_paused_until_v1"
    private let seenSignalCommentIDsKey = "jn_seen_signal_comment_ids_v1"

    private let logger = Logger(subsystem: "com.stilltschoni.justnoiseradioapp", category: "SignalStore")
    private let analysisClient: any SignalAnalyzing
    private let captureAudioFileStore: CaptureAudioFileStore
    private let captureIDProvider: () -> UUID
    private let userDefaults: UserDefaults
    private let analysisTaskCanceller: @Sendable (Task<Void, Never>) -> Void
    private let analysisTaskDidFinish: @Sendable () -> Void
    private var signalAnalysisPausedUntil: Date?
    // Failed analyses stay local and retry later instead of blocking capture creation.
    private var signalAnalysisFailuresByClipID: [UUID: SignalAnalysisFailureRecord] = [:]
    private var isWaitingForProtectedData: Bool = false
    private var pendingSignalAnalysisJobs: [SignalAnalysisJob] = []
    private var isProcessingSignalAnalysisQueue = false
    private var hasWrittenCaptureClipsThisProcess = false
    private var accountBoundaryGeneration = UUID()
    private var activeSignalAnalysisTask: Task<Void, Never>?

    init(
        analysisClient: any SignalAnalyzing = LiveSignalAnalysisClient(),
        captureAudioFileStore: CaptureAudioFileStore = CaptureAudioFileStore(),
        captureIDProvider: @escaping () -> UUID = { UUID() },
        userDefaults: UserDefaults = .standard,
        analysisTaskCanceller: @escaping @Sendable (Task<Void, Never>) -> Void = { $0.cancel() },
        analysisTaskDidFinish: @escaping @Sendable () -> Void = {}
    ) {
        self.analysisClient = analysisClient
        self.captureAudioFileStore = captureAudioFileStore
        self.captureIDProvider = captureIDProvider
        self.userDefaults = userDefaults
        self.analysisTaskCanceller = analysisTaskCanceller
        self.analysisTaskDidFinish = analysisTaskDidFinish
        super.init()
    }

    var orderedSignalComments: [SignalComment] {
        signalMemoryState.comments.sorted { $0.createdAt > $1.createdAt }
    }

    var unseenSignalCommentCount: Int {
        signalMemoryState.comments.reduce(into: 0) { count, comment in
            if seenSignalCommentIDs.contains(comment.id) == false {
                count += 1
            }
        }
    }

    var hasPendingSignalAnalysis: Bool {
        signalAnalysisClipIDsInFlight.isEmpty == false || pendingSignalAnalysisJobs.isEmpty == false
    }

    var signalReviewNotice: SignalReviewNotice? {
        guard hasPendingSignalAnalysis == false else { return nil }

        if let pausedUntil = activeSignalAnalysisPauseUntil {
            return .paused(until: pausedUntil)
        }

        if let retryAfter = nextSignalAnalysisRetryAfter {
            return .retrying(until: retryAfter)
        }

        return nil
    }

    var signalControlIndicatorState: SignalControlIndicatorState {
        if hasPendingSignalAnalysis {
            return .reviewing
        }

        if unseenSignalCommentCount > 0 {
            return .ready(count: unseenSignalCommentCount)
        }

        if signalReviewNotice != nil {
            return .delayed
        }

        return .idle
    }

    func hydrateOnLaunch() {
        guard isHydrated == false else { return }

        if !(UIApplication.shared.isProtectedDataAvailable) {
            guard isWaitingForProtectedData == false else { return }
            isWaitingForProtectedData = true
            logger.warning("Protected data not available yet. Waiting for unlock…")
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(_protectedDataReady),
                name: UIApplication.protectedDataDidBecomeAvailableNotification,
                object: nil
            )
            return
        }

        _performHydration()
    }

    @MainActor
    func quiesceForAccountDeletion() {
        accountBoundaryGeneration = UUID()
        if let activeSignalAnalysisTask {
            analysisTaskCanceller(activeSignalAnalysisTask)
        }
        activeSignalAnalysisTask = nil
        pendingSignalAnalysisJobs.removeAll()
        signalAnalysisClipIDsInFlight.removeAll()
        isProcessingSignalAnalysisQueue = false
        isWaitingForProtectedData = false
        NotificationCenter.default.removeObserver(
            self,
            name: UIApplication.protectedDataDidBecomeAvailableNotification,
            object: nil
        )
    }

    @MainActor
    func resetInMemoryStateForAccountDeletion() {
        quiesceForAccountDeletion()
        captureClips = []
        signalMemoryState = SignalMemoryState()
        seenSignalCommentIDs = []
        legacySignalInsights = []
        signalAnalysisPausedUntil = nil
        signalAnalysisFailuresByClipID = [:]
        hasWrittenCaptureClipsThisProcess = false
        isHydrated = true
    }

    @objc private func _protectedDataReady() {
        isWaitingForProtectedData = false
        NotificationCenter.default.removeObserver(
            self,
            name: UIApplication.protectedDataDidBecomeAvailableNotification,
            object: nil
        )
        _performHydration()
    }

    private func _performHydration() {
        loadCaptureClips()
        loadLegacySignalInsights()
        loadSignalMemoryState()
        loadSignalCommentReadState()
        loadSignalAnalysisRetryState()
        isHydrated = true
    }

    func loadCaptureClips() {
        let legacyExtractions = loadLegacySignalExtractions()
        let legacyExtractionsByClipID = Dictionary(
            uniqueKeysWithValues: legacyExtractions.map { ($0.captureClipID, $0) }
        )

        if let data = userDefaults.data(forKey: captureClipsKey),
           let saved = try? JSONDecoder().decode([CaptureClip].self, from: data) {
            captureClips = saved
                .filter { FileManager.default.fileExists(atPath: $0.audioFileURL.path) }
                .map { clip in
                    guard clip.extraction == nil,
                          let extraction = legacyExtractionsByClipID[clip.id] else {
                        return clip
                    }

                    return clip.updatingAnalysis(
                        extraction: extraction,
                        sourceModeName: clip.sourceModeName ?? extraction.sourceModeName,
                        blockReason: clip.lastDecisionBlockReason,
                        updatedAt: extraction.createdAt
                    )
                }
                .sorted { $0.createdAt < $1.createdAt }
        } else {
            captureClips = []
        }

        recoverPendingCaptureCommits(
            canAcknowledgePersistedMetadata: hasWrittenCaptureClipsThisProcess == false
        )
        saveCaptureClips()
        userDefaults.removeObject(forKey: legacySignalExtractionsKey)
    }

    func saveCaptureClips() {
        refreshDerivedCaptureData()

        if let data = try? JSONEncoder().encode(captureClips) {
            userDefaults.set(data, forKey: captureClipsKey)
            hasWrittenCaptureClipsThisProcess = true
        }
    }

    private func recoverPendingCaptureCommits(canAcknowledgePersistedMetadata: Bool) {
        let persistedClipIDs = Set(captureClips.map(\.id))

        for pending in captureAudioFileStore.pendingCommits() {
            if persistedClipIDs.contains(pending.clipID) {
                if canAcknowledgePersistedMetadata {
                    captureAudioFileStore.clearPendingCommit(for: pending.clipID)
                }
                continue
            }

            var audioURL = captureAudioFileStore.persistedURL(for: pending.clipID)
            if FileManager.default.fileExists(atPath: audioURL.path) == false {
                guard let draftURL = captureAudioFileStore.recoverableDraftURL(
                    named: pending.draftFileName
                ) else {
                    logger.error("Pending Capture commit has no recoverable audio file.")
                    continue
                }

                do {
                    audioURL = try captureAudioFileStore.commitDraft(
                        at: draftURL,
                        clipID: pending.clipID
                    )
                } catch {
                    logger.error("Pending Capture file move could not be recovered: \(error.localizedDescription)")
                    continue
                }
            }

            captureClips.append(
                CaptureClip(
                    id: pending.clipID,
                    createdAt: pending.createdAt,
                    duration: max(0, pending.duration),
                    audioFileURL: audioURL,
                    sourceModeName: pending.sourceModeName
                )
            )
        }

        captureClips.sort { $0.createdAt < $1.createdAt }
        if Set(captureClips.map(\.id)) != persistedClipIDs {
            logger.warning("Recovered Capture metadata after an interrupted file commit.")
        }
    }

    private func loadLegacySignalInsights() {
        if let data = userDefaults.data(forKey: legacySignalInsightsKey),
           let saved = try? JSONDecoder().decode([SignalInsight].self, from: data) {
            legacySignalInsights = saved.sorted { $0.createdAt > $1.createdAt }
        } else {
            legacySignalInsights = []
        }
    }

    func loadSignalMemoryState() {
        if let data = userDefaults.data(forKey: signalMemoryStateKey),
           let saved = try? JSONDecoder().decode(SignalMemoryState.self, from: data) {
            signalMemoryState = saved
        } else {
            signalMemoryState = SignalMemoryState()
        }
    }

    private func saveSignalMemoryState() {
        if let data = try? JSONEncoder().encode(signalMemoryState) {
            userDefaults.set(data, forKey: signalMemoryStateKey)
        }
    }

    private func loadSignalCommentReadState() {
        let currentCommentIDs = Set(signalMemoryState.comments.map(\.id))

        if let savedIDs = userDefaults.stringArray(forKey: seenSignalCommentIDsKey) {
            seenSignalCommentIDs = Set(savedIDs).intersection(currentCommentIDs)
        } else {
            // Existing comments predate unread tracking and should not create a migration badge.
            seenSignalCommentIDs = currentCommentIDs
        }

        saveSignalCommentReadState()
    }

    private func saveSignalCommentReadState() {
        userDefaults.set(seenSignalCommentIDs.sorted(), forKey: seenSignalCommentIDsKey)
    }

    func loadSignalAnalysisRetryState() {
        if let pauseDate = userDefaults.object(forKey: signalAnalysisPausedUntilKey) as? Date,
           pauseDate > .now {
            signalAnalysisPausedUntil = pauseDate
        } else {
            signalAnalysisPausedUntil = nil
            userDefaults.removeObject(forKey: signalAnalysisPausedUntilKey)
        }

        if let data = userDefaults.data(forKey: signalAnalysisFailuresKey),
           let saved = try? JSONDecoder().decode([SignalAnalysisFailureRecord].self, from: data) {
            signalAnalysisFailuresByClipID = Dictionary(
                uniqueKeysWithValues: saved
                    .filter { $0.retryAfter > .now }
                    .map { ($0.captureClipID, $0) }
            )
        } else {
            signalAnalysisFailuresByClipID = [:]
        }

        saveSignalAnalysisFailures()
    }

    private func saveSignalAnalysisFailures() {
        let failures = signalAnalysisFailuresByClipID.values.sorted {
            $0.failedAt < $1.failedAt
        }

        if let data = try? JSONEncoder().encode(failures) {
            userDefaults.set(data, forKey: signalAnalysisFailuresKey)
        }
    }

    @discardableResult
    func saveCaptureClip(
        audioURL: URL,
        duration: TimeInterval,
        selectedModeName: String?
    ) throws -> CaptureClip {
        let clipID = captureIDProvider()
        let pending = PendingCaptureCommit(
            clipID: clipID,
            createdAt: Date(),
            duration: max(0, duration),
            sourceModeName: selectedModeName,
            draftFileName: audioURL.lastPathComponent
        )
        try captureAudioFileStore.writePendingCommit(pending)

        let destinationURL: URL
        do {
            destinationURL = try captureAudioFileStore.commitDraft(
                at: audioURL,
                clipID: clipID
            )
        } catch {
            captureAudioFileStore.clearPendingCommit(for: clipID)
            throw error
        }

        let clip = CaptureClip(
            id: clipID,
            createdAt: pending.createdAt,
            duration: pending.duration,
            audioFileURL: destinationURL,
            sourceModeName: selectedModeName
        )

        captureClips.append(clip)
        saveCaptureClips()

        return clip
    }

    func beginSignalAnalysis(for clip: CaptureClip, selectedModeName: String?) {
        guard FileManager.default.fileExists(atPath: clip.audioFileURL.path) else { return }
        guard canAttemptSignalAnalysis(for: clip.id) else { return }
        guard clip.analysisState != .reviewed else { return }
        guard signalAnalysisClipIDsInFlight.contains(clip.id) == false else { return }
        guard pendingSignalAnalysisJobs.contains(where: { $0.clipID == clip.id }) == false else { return }

        logger.info("Enqueued automatic signal analysis for clip \(clip.id.uuidString, privacy: .public)")
        enqueueSignalAnalysisJob(
            clipID: clip.id,
            selectedModeName: selectedModeName
        )
    }

    func ensureRecentCaptureSignalAnalyses(selectedModeName: String?, limit: Int = 6) {
        let missingRecentClips = captureClips
            .sorted { $0.createdAt > $1.createdAt }
            .filter { clip in
                clip.analysisState != .reviewed
                    && signalAnalysisClipIDsInFlight.contains(clip.id) == false
                    && pendingSignalAnalysisJobs.contains(where: { $0.clipID == clip.id }) == false
                    && canAttemptSignalAnalysis(for: clip.id)
            }
            .prefix(limit)

        for clip in missingRecentClips {
            beginSignalAnalysis(for: clip, selectedModeName: selectedModeName)
        }
    }

    func signalTimelineDidAppear() {
        let currentCommentIDs = Set(signalMemoryState.comments.map(\.id))
        if seenSignalCommentIDs != currentCommentIDs {
            seenSignalCommentIDs = currentCommentIDs
            saveSignalCommentReadState()
        }
    }

    func captureSurfaceDidAppear(selectedModeName: String?) {
        ensureRecentCaptureSignalAnalyses(selectedModeName: selectedModeName)
    }

    func captureReferenceText(for comment: SignalComment) -> String? {
        let orderedClips = captureClips.sorted { $0.createdAt < $1.createdAt }
        let positions = comment.sourceCaptureIds
            .compactMap { captureID in
                orderedClips.firstIndex(where: { $0.id == captureID }).map { $0 + 1 }
            }
            .sorted()

        guard positions.isEmpty == false else { return nil }

        if positions.count == 1, let position = positions.first {
            return String(format: "Capture %03d", position)
        }

        if positions.count == 2,
           let first = positions.first,
           let last = positions.last {
            return String(format: "Captures %03d + %03d", first, last)
        }

        return "\(positions.count) captures"
    }

    func captureReferenceDate(for comment: SignalComment) -> Date? {
        comment.sourceCaptureIds
            .compactMap { captureClip(for: $0)?.createdAt }
            .max()
    }

    private func enqueueSignalAnalysisJob(
        clipID: UUID,
        selectedModeName: String?
    ) {
        guard pendingSignalAnalysisJobs.contains(where: { $0.clipID == clipID }) == false else { return }
        guard signalAnalysisClipIDsInFlight.contains(clipID) == false else { return }

        pendingSignalAnalysisJobs.append(
            SignalAnalysisJob(
                clipID: clipID,
                selectedModeName: selectedModeName
            )
        )
        processNextSignalAnalysisJob()
    }

    private func processNextSignalAnalysisJob() {
        guard isProcessingSignalAnalysisQueue == false else { return }

        while let job = pendingSignalAnalysisJobs.first {
            guard let clip = captureClip(for: job.clipID),
                  FileManager.default.fileExists(atPath: clip.audioFileURL.path),
                  canAttemptSignalAnalysis(for: clip.id),
                  clip.analysisState != .reviewed else {
                pendingSignalAnalysisJobs.removeFirst()
                continue
            }

            isProcessingSignalAnalysisQueue = true
            signalAnalysisClipIDsInFlight.insert(clip.id)

            let analysisContext = SignalContextBuilder.build(
                currentClipID: clip.id,
                captureClips: captureClips,
                signalInsights: legacySignalInsights,
                memoryState: signalMemoryState
            )
            let analysisClient = self.analysisClient
            let generation = accountBoundaryGeneration
            let taskDidFinish = analysisTaskDidFinish

            logger.info("Starting queued signal analysis for clip \(clip.id.uuidString, privacy: .public) at memory revision \(self.signalMemoryState.memoryRevision, privacy: .public)")

            activeSignalAnalysisTask = Task(priority: .utility) { [weak self] in
                defer { taskDidFinish() }
                do {
                    let response = try await analysisClient.analyzeCaptureClip(
                        clip,
                        selectedModeName: job.selectedModeName,
                        context: analysisContext
                    )

                    guard Task.isCancelled == false else { return }
                    guard let self else { return }
                    await MainActor.run {
                        guard self.accountBoundaryGeneration == generation else { return }
                        self.handleSignalAnalysisSuccess(job: job, response: response)
                    }
                } catch {
                    guard Task.isCancelled == false else { return }
                    guard let self else { return }
                    await MainActor.run {
                        guard self.accountBoundaryGeneration == generation else { return }
                        self.handleSignalAnalysisFailure(job: job, error: error)
                    }
                }
            }

            return
        }
    }

    private func handleSignalAnalysisSuccess(
        job: SignalAnalysisJob,
        response: BackendCaptureResponse
    ) {
        activeSignalAnalysisTask = nil
        signalAnalysisClipIDsInFlight.remove(job.clipID)
        clearSignalAnalysisFailure(for: job.clipID)

        let applyResult = applyBackendCaptureResponse(
            response,
            clipID: job.clipID,
            selectedModeName: job.selectedModeName
        )

        finishCurrentSignalAnalysisJob(job)

        switch applyResult {
        case .applied(let storedComment):
            if storedComment == false {
                logger.info("Signal analysis applied without a visible comment for clip \(job.clipID.uuidString, privacy: .public)")
            }
        case .duplicate:
            logger.info("Skipped duplicate signal operation \(response.operationId, privacy: .public)")
        case .revisionMismatch:
            logger.info("Requeued signal analysis for clip \(job.clipID.uuidString, privacy: .public) because memory revision changed.")
            pendingSignalAnalysisJobs.append(job.requeued())
        }

        processNextSignalAnalysisJob()
    }

    private func handleSignalAnalysisFailure(job: SignalAnalysisJob, error: Error) {
        activeSignalAnalysisTask = nil
        signalAnalysisClipIDsInFlight.remove(job.clipID)
        finishCurrentSignalAnalysisJob(job)
        recordSignalAnalysisFailure(for: job.clipID, error: error)

        if (error as? SignalAnalysisError) != .quotaExceeded {
            logger.error("Signal analysis failed: \(error.localizedDescription)")
        }

        processNextSignalAnalysisJob()
    }

    private func finishCurrentSignalAnalysisJob(_ job: SignalAnalysisJob) {
        if pendingSignalAnalysisJobs.first?.id == job.id {
            pendingSignalAnalysisJobs.removeFirst()
        } else {
            pendingSignalAnalysisJobs.removeAll { $0.id == job.id }
        }

        isProcessingSignalAnalysisQueue = false
    }

    private func applyBackendCaptureResponse(
        _ response: BackendCaptureResponse,
        clipID: UUID,
        selectedModeName: String?
    ) -> SignalPatchApplyResult {
        guard signalMemoryState.appliedOperationIds.contains(response.operationId) == false else {
            updateCapture(
                withID: clipID,
                sortAfterUpdate: false
            ) { clip in
                clip.markingReviewed(
                    blockReason: response.commentDecision.blockReason ?? response.commentDecision.reason
                )
            }
            return .duplicate
        }

        guard response.baseMemoryRevision == signalMemoryState.memoryRevision else {
            return .revisionMismatch
        }

        var updatedCaptureClips = captureClips
        var updatedMemoryState = signalMemoryState
        let blockReason = response.commentDecision.blockReason ?? response.commentDecision.reason
        let allowsNormalComments = response.safety.hold != true

        applyCapturePatch(
            response.capturePatch,
            fallbackClipID: clipID,
            selectedModeName: selectedModeName,
            blockReason: blockReason,
            captureClips: &updatedCaptureClips
        )

        var storedMemoryPatchComment = false
        for operation in response.memoryPatch.operations {
            if applyMemoryOperation(
                operation,
                selectedModeName: selectedModeName,
                blockReason: blockReason,
                allowsNormalComments: allowsNormalComments,
                captureClips: &updatedCaptureClips,
                memoryState: &updatedMemoryState
            ) {
                storedMemoryPatchComment = true
            }
        }

        let storedResponseComment: Bool
        if allowsNormalComments,
           let comment = makeSignalComment(from: response.comment) {
            upsertComment(comment, into: &updatedMemoryState)
            storedResponseComment = true
        } else {
            storedResponseComment = false
        }

        updatedMemoryState.appliedOperationIds.insert(response.operationId)
        updatedMemoryState.appliedOperations.append(
            AppliedOperationRecord(
                operationId: response.operationId,
                appliedAt: Date(),
                baseMemoryRevision: response.baseMemoryRevision,
                nextMemoryRevision: response.nextMemoryRevision,
                captureId: clipID
            )
        )
        updatedMemoryState.memoryRevision = response.nextMemoryRevision
        updatedMemoryState.appliedOperations = Array(updatedMemoryState.appliedOperations.suffix(200))

        captureClips = updatedCaptureClips
        signalMemoryState = updatedMemoryState
        saveCaptureClips()
        saveSignalMemoryState()

        return .applied(storedComment: storedResponseComment || storedMemoryPatchComment)
    }

    private func applyCapturePatch(
        _ patch: BackendCapturePatch,
        fallbackClipID: UUID,
        selectedModeName: String?,
        blockReason: String?,
        captureClips: inout [CaptureClip]
    ) {
        let targetClipID = patch.captureId.flatMap(UUID.init(uuidString:)) ?? fallbackClipID
        guard let index = captureClips.firstIndex(where: { $0.id == targetClipID }) else { return }

        captureClips[index] = captureClips[index].applyingCapturePatch(
            patch,
            selectedModeName: selectedModeName,
            blockReason: blockReason
        )
    }

    private func applyMemoryOperation(
        _ operation: BackendMemoryOperation,
        selectedModeName: String?,
        blockReason: String?,
        allowsNormalComments: Bool,
        captureClips: inout [CaptureClip],
        memoryState: inout SignalMemoryState
    ) -> Bool {
        switch operation.type {
        case .updateCapture:
            guard let patch = operation.capturePatch else { return false }
            guard let captureID = operation.captureId.flatMap(UUID.init(uuidString:))
                ?? patch.captureId.flatMap(UUID.init(uuidString:)) else { return false }
            applyCapturePatch(
                patch,
                fallbackClipID: captureID,
                selectedModeName: selectedModeName,
                blockReason: blockReason,
                captureClips: &captureClips
            )
            return false
        case .createThread:
            if let thread = operation.thread {
                upsertThread(thread, into: &memoryState)
            } else if let thread = makeThread(from: operation) {
                upsertThread(thread, into: &memoryState)
            }
            return false
        case .appendCaptureToThread:
            guard let threadId = operation.threadId,
                  let captureId = operation.captureId else { return false }
            appendCapture(captureId, toThreadID: threadId, in: &memoryState)
            return false
        case .updateThreadState:
            guard let threadId = operation.threadId else { return false }
            updateThread(threadId, patch: operation.threadPatch, state: operation.state, in: &memoryState)
            return false
        case .createThreadLink:
            if let link = operation.link {
                upsertThreadLink(link, into: &memoryState)
            } else if let link = makeThreadLink(from: operation) {
                upsertThreadLink(link, into: &memoryState)
            }
            return false
        case .removeThreadLink:
            removeThreadLink(from: operation, in: &memoryState)
            return false
        case .updateThreadCommentState:
            guard let threadId = operation.threadId else { return false }
            updateThread(threadId, patch: operation.threadPatch, state: operation.state, commentState: operation.commentState, in: &memoryState)
            return false
        case .createComment:
            guard allowsNormalComments else { return false }
            if let comment = makeSignalComment(from: operation.comment) {
                upsertComment(comment, into: &memoryState)
                return true
            } else if let comment = makeSignalComment(from: operation) {
                upsertComment(comment, into: &memoryState)
                return true
            }
            return false
        case .unknown:
            logger.info("Skipped unknown signal memory operation.")
            return false
        }
    }

    private func makeThread(from operation: BackendMemoryOperation) -> TopicThread? {
        guard let threadId = operation.threadId else { return nil }
        let label = cleaned(operation.text ?? threadId)

        return TopicThread(
            id: threadId,
            label: label.isEmpty ? threadId : label,
            topicKey: threadId,
            occurrenceCount: 0,
            captureIds: operation.captureId.map { [$0] } ?? [],
            state: operation.state,
            commentState: operation.commentState
        )
    }

    private func upsertThread(_ thread: TopicThread, into memoryState: inout SignalMemoryState) {
        guard thread.id.isEmpty == false else { return }

        if let index = memoryState.threads.firstIndex(where: { $0.id == thread.id }) {
            memoryState.threads[index] = mergedThread(
                existing: memoryState.threads[index],
                incoming: thread
            )
        } else {
            memoryState.threads.append(thread)
        }
    }

    private func mergedThread(existing: TopicThread, incoming: TopicThread) -> TopicThread {
        TopicThread(
            id: existing.id,
            label: incoming.label.isEmpty ? existing.label : incoming.label,
            topicKey: incoming.topicKey.isEmpty ? existing.topicKey : incoming.topicKey,
            category: incoming.category ?? existing.category,
            themes: uniqueStrings(existing.themes + incoming.themes),
            occurrenceCount: max(existing.occurrenceCount, incoming.occurrenceCount),
            intensity: incoming.intensity ?? existing.intensity,
            intensityTrend: incoming.intensityTrend ?? existing.intensityTrend,
            firstSeen: minDateString(existing.firstSeen, incoming.firstSeen),
            lastSeen: maxDateString(existing.lastSeen, incoming.lastSeen),
            captureIds: uniqueStrings(existing.captureIds + incoming.captureIds),
            evidenceQuotes: uniqueEvidenceQuotes(existing.evidenceQuotes + incoming.evidenceQuotes),
            state: incoming.state ?? existing.state,
            commentState: incoming.commentState ?? existing.commentState
        )
    }

    private func appendCapture(
        _ captureId: String,
        toThreadID threadId: String,
        in memoryState: inout SignalMemoryState
    ) {
        guard captureId.isEmpty == false else { return }

        if let index = memoryState.threads.firstIndex(where: { $0.id == threadId }) {
            var thread = memoryState.threads[index]
            if thread.captureIds.contains(captureId) == false {
                thread.captureIds.append(captureId)
            }
            thread.occurrenceCount = max(thread.occurrenceCount, thread.captureIds.count)
            memoryState.threads[index] = thread
        } else {
            memoryState.threads.append(
                TopicThread(
                    id: threadId,
                    label: threadId,
                    topicKey: threadId,
                    occurrenceCount: 1,
                    captureIds: [captureId]
                )
            )
        }
    }

    private func updateThread(
        _ threadId: String,
        patch: BackendThreadPatch?,
        state: String?,
        commentState: String? = nil,
        in memoryState: inout SignalMemoryState
    ) {
        guard let index = memoryState.threads.firstIndex(where: { $0.id == threadId }) else { return }
        var thread = memoryState.threads[index]

        if let patch {
            thread.label = patch.label ?? thread.label
            thread.topicKey = patch.topicKey ?? thread.topicKey
            thread.category = patch.category ?? thread.category
            thread.themes = patch.themes.map { uniqueStrings($0) } ?? thread.themes
            thread.occurrenceCount = patch.occurrenceCount ?? thread.occurrenceCount
            thread.intensity = patch.intensity ?? thread.intensity
            thread.intensityTrend = patch.intensityTrend ?? thread.intensityTrend
            thread.firstSeen = patch.firstSeen ?? thread.firstSeen
            thread.lastSeen = patch.lastSeen ?? thread.lastSeen
            thread.captureIds = patch.captureIds.map { uniqueStrings($0) } ?? thread.captureIds
            thread.evidenceQuotes = patch.evidenceQuotes.map { uniqueEvidenceQuotes($0) } ?? thread.evidenceQuotes
            thread.state = patch.state ?? thread.state
            thread.commentState = patch.commentState ?? thread.commentState
        }

        thread.state = state ?? thread.state
        thread.commentState = commentState ?? thread.commentState
        memoryState.threads[index] = thread
    }

    private func makeThreadLink(from operation: BackendMemoryOperation) -> ThreadLink? {
        guard let sourceThreadId = operation.sourceThreadId ?? operation.threadId,
              let targetThreadId = operation.targetThreadId else { return nil }

        return ThreadLink(
            id: operation.linkId ?? ThreadLink.stableID(
                sourceThreadId: sourceThreadId,
                targetThreadId: targetThreadId
            ),
            sourceThreadId: sourceThreadId,
            targetThreadId: targetThreadId,
            relationship: operation.state,
            createdAt: operation.createdAt,
            state: operation.state
        )
    }

    private func upsertThreadLink(_ link: ThreadLink, into memoryState: inout SignalMemoryState) {
        guard link.sourceThreadId.isEmpty == false,
              link.targetThreadId.isEmpty == false else { return }

        if let index = memoryState.threadLinks.firstIndex(where: { $0.id == link.id }) {
            memoryState.threadLinks[index] = link
        } else {
            memoryState.threadLinks.append(link)
        }
    }

    private func removeThreadLink(
        from operation: BackendMemoryOperation,
        in memoryState: inout SignalMemoryState
    ) {
        if let linkId = operation.linkId {
            memoryState.threadLinks.removeAll { $0.id == linkId }
            return
        }

        guard let sourceThreadId = operation.sourceThreadId ?? operation.threadId,
              let targetThreadId = operation.targetThreadId else { return }
        let stableID = ThreadLink.stableID(
            sourceThreadId: sourceThreadId,
            targetThreadId: targetThreadId
        )

        memoryState.threadLinks.removeAll {
            $0.id == stableID
                || ($0.sourceThreadId == sourceThreadId && $0.targetThreadId == targetThreadId)
                || ($0.sourceThreadId == targetThreadId && $0.targetThreadId == sourceThreadId)
        }
    }

    private func makeSignalComment(from backendComment: BackendSignalComment?) -> SignalComment? {
        guard let backendComment else { return nil }
        guard let anchorCaptureId = UUID(uuidString: backendComment.anchorCaptureId) else { return nil }
        let text = cleaned(backendComment.text)
        guard text.isEmpty == false else { return nil }

        let sourceCaptureIds = backendComment.sourceCaptureIds
            .compactMap(UUID.init(uuidString:))
        let id = backendComment.id ?? stableCommentID(anchorCaptureId: anchorCaptureId, text: text)

        return SignalComment(
            id: id,
            anchorCaptureId: anchorCaptureId,
            text: text,
            hat: cleaned(backendComment.hat ?? "").nilIfEmpty,
            path: cleaned(backendComment.path ?? "").nilIfEmpty,
            createdAt: SignalMemoryDate.date(from: backendComment.createdAt) ?? Date(),
            threadIds: uniqueStrings(backendComment.threadIds),
            sourceCaptureIds: sourceCaptureIds.isEmpty ? [anchorCaptureId] : sourceCaptureIds,
            state: backendComment.state
        )
    }

    private func makeSignalComment(from operation: BackendMemoryOperation) -> SignalComment? {
        guard let anchorCaptureIdString = operation.anchorCaptureId ?? operation.captureId,
              let anchorCaptureId = UUID(uuidString: anchorCaptureIdString) else { return nil }
        let text = cleaned(operation.text ?? "")
        guard text.isEmpty == false else { return nil }
        let sourceCaptureIds = (operation.sourceCaptureIds ?? [anchorCaptureIdString])
            .compactMap(UUID.init(uuidString:))

        return SignalComment(
            id: operation.commentId ?? stableCommentID(anchorCaptureId: anchorCaptureId, text: text),
            anchorCaptureId: anchorCaptureId,
            text: text,
            hat: cleaned(operation.hat ?? "").nilIfEmpty,
            path: cleaned(operation.path ?? "").nilIfEmpty,
            createdAt: SignalMemoryDate.date(from: operation.createdAt) ?? Date(),
            threadIds: uniqueStrings(operation.threadIds ?? []),
            sourceCaptureIds: sourceCaptureIds.isEmpty ? [anchorCaptureId] : sourceCaptureIds,
            state: operation.state
        )
    }

    private func upsertComment(_ comment: SignalComment, into memoryState: inout SignalMemoryState) {
        if let index = memoryState.comments.firstIndex(where: { existing in
            existing.id == comment.id
                || (
                    existing.anchorCaptureId == comment.anchorCaptureId
                        && existing.normalizedTextSignature == comment.normalizedTextSignature
                )
        }) {
            memoryState.comments[index] = comment
        } else {
            memoryState.comments.append(comment)
        }

        memoryState.comments.sort { $0.createdAt > $1.createdAt }
    }

    private func stableCommentID(anchorCaptureId: UUID, text: String) -> String {
        let signature = text
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { $0.isEmpty == false }
            .joined(separator: "-")

        return "comment-\(anchorCaptureId.uuidString)-\(signature)"
    }

    private func uniqueStrings(_ values: [String]) -> [String] {
        var result: [String] = []

        for value in values {
            let cleanedValue = cleaned(value)
            guard cleanedValue.isEmpty == false else { continue }
            guard result.contains(cleanedValue) == false else { continue }
            result.append(cleanedValue)
        }

        return result
    }

    private func uniqueEvidenceQuotes(
        _ values: [SignalMemoryEvidenceQuotePayload]
    ) -> [SignalMemoryEvidenceQuotePayload] {
        var seen: Set<String> = []
        var result: [SignalMemoryEvidenceQuotePayload] = []

        for value in values {
            let signature = "\(value.captureId)::\(value.quote)"
            guard seen.contains(signature) == false else { continue }
            seen.insert(signature)
            result.append(value)
        }

        return result
    }

    private func minDateString(_ lhs: String?, _ rhs: String?) -> String? {
        switch (lhs, rhs) {
        case let (left?, right?):
            return min(left, right)
        case let (left?, nil):
            return left
        case let (nil, right?):
            return right
        case (nil, nil):
            return nil
        }
    }

    private func maxDateString(_ lhs: String?, _ rhs: String?) -> String? {
        switch (lhs, rhs) {
        case let (left?, right?):
            return max(left, right)
        case let (left?, nil):
            return left
        case let (nil, right?):
            return right
        case (nil, nil):
            return nil
        }
    }

    private func cleaned(_ text: String) -> String {
        text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.isEmpty == false }
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func canAttemptSignalAnalysis(for clipID: UUID) -> Bool {
        if let signalAnalysisPausedUntil {
            if signalAnalysisPausedUntil > .now {
                return false
            }

            self.signalAnalysisPausedUntil = nil
            userDefaults.removeObject(forKey: signalAnalysisPausedUntilKey)
        }

        guard let failure = signalAnalysisFailuresByClipID[clipID] else {
            return true
        }

        if failure.retryAfter > .now {
            return false
        }

        signalAnalysisFailuresByClipID.removeValue(forKey: clipID)
        saveSignalAnalysisFailures()
        return true
    }

    private var activeSignalAnalysisPauseUntil: Date? {
        guard let signalAnalysisPausedUntil else { return nil }
        return signalAnalysisPausedUntil > .now ? signalAnalysisPausedUntil : nil
    }

    private var nextSignalAnalysisRetryAfter: Date? {
        let blockedClipIDs = Set(
            captureClips
                .filter { clip in
                    FileManager.default.fileExists(atPath: clip.audioFileURL.path)
                        && clip.analysisState != .reviewed
                }
                .map(\.id)
        )

        return signalAnalysisFailuresByClipID.values
            .filter { blockedClipIDs.contains($0.captureClipID) && $0.retryAfter > .now }
            .map(\.retryAfter)
            .min()
    }

    private func recordSignalAnalysisFailure(for clipID: UUID, error: Error) {
        let failedAt = Date()
        let kind: SignalAnalysisFailureKind
        let retryAfter: Date

        if let signalError = error as? SignalAnalysisError,
           case .quotaExceeded = signalError {
            kind = .quotaExceeded
            retryAfter = failedAt.addingTimeInterval(60 * 60)
            signalAnalysisPausedUntil = retryAfter
            userDefaults.set(retryAfter, forKey: signalAnalysisPausedUntilKey)
            logger.info("Signal analysis paused because the analysis quota is exhausted.")
        } else {
            kind = .transient
            retryAfter = failedAt.addingTimeInterval(15 * 60)
        }

        signalAnalysisFailuresByClipID[clipID] = SignalAnalysisFailureRecord(
            captureClipID: clipID,
            failedAt: failedAt,
            retryAfter: retryAfter,
            kind: kind,
            message: error.localizedDescription
        )
        updateCapture(
            withID: clipID,
            sortAfterUpdate: false
        ) { clip in
            clip.markingAnalysisFailed(
                retryAfter: retryAfter,
                message: error.localizedDescription
            )
        }
        saveSignalAnalysisFailures()
        logger.info("Signal analysis retry scheduled for clip \(clipID.uuidString, privacy: .public) at \(retryAfter.formatted(.dateTime.hour().minute()), privacy: .public)")
    }

    private func clearSignalAnalysisFailure(for clipID: UUID) {
        guard signalAnalysisFailuresByClipID.removeValue(forKey: clipID) != nil else { return }
        updateCapture(
            withID: clipID,
            sortAfterUpdate: false
        ) { clip in
            clip.clearingFailure()
        }
        saveSignalAnalysisFailures()
    }

    private func captureClip(for id: UUID) -> CaptureClip? {
        captureClips.first(where: { $0.id == id })
    }

    private func loadLegacySignalExtractions() -> [SignalExtraction] {
        if let data = userDefaults.data(forKey: legacySignalExtractionsKey),
           let saved = try? JSONDecoder().decode([SignalExtraction].self, from: data) {
            return saved.sorted { $0.createdAt < $1.createdAt }
        }

        return []
    }

    private func refreshDerivedCaptureData() {
        captureClips = captureClips
            .filter { FileManager.default.fileExists(atPath: $0.audioFileURL.path) }
            .sorted { $0.createdAt < $1.createdAt }
    }

    private func updateCapture(
        withID captureID: UUID,
        sortAfterUpdate: Bool = true,
        update: (CaptureClip) -> CaptureClip
    ) {
        guard let existingIndex = captureClips.firstIndex(where: { $0.id == captureID }) else { return }

        captureClips[existingIndex] = update(captureClips[existingIndex])

        if sortAfterUpdate {
            captureClips.sort { $0.createdAt < $1.createdAt }
        }

        saveCaptureClips()
    }
}

private struct SignalAnalysisJob: Identifiable, Equatable {
    let id: UUID
    let clipID: UUID
    let selectedModeName: String?
    let attempt: Int

    init(
        id: UUID = UUID(),
        clipID: UUID,
        selectedModeName: String?,
        attempt: Int = 0
    ) {
        self.id = id
        self.clipID = clipID
        self.selectedModeName = selectedModeName
        self.attempt = attempt
    }

    func requeued() -> SignalAnalysisJob {
        SignalAnalysisJob(
            clipID: clipID,
            selectedModeName: selectedModeName,
            attempt: attempt + 1
        )
    }
}

private enum SignalPatchApplyResult: Equatable {
    case applied(storedComment: Bool)
    case duplicate
    case revisionMismatch
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
