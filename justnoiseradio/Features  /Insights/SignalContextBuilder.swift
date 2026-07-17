// SignalContextBuilder.swift

import Foundation

struct SignalAnalysisContext: Sendable {
    let recentCaptures: [SignalCaptureContextPayload]
    let existingSignals: [SignalInsightContextPayload]
    let existingComments: [SignalCommentContextPayload]
    let memory: SignalMemoryContextPayload
}

struct SignalCaptureContextPayload: Codable, Hashable, Sendable {
    let capture_id: String
    let created_at: String
    let summary: String
    let themes: [String]
    let sourceLanguage: String?
    let emotion: String?
    let strongQuote: String?
    let transcript: String?
    let clarifiedTranscript: String?
    let context: String?
    let tension: String?
    let highlight: String?
    let signalStrength: Double?
    let emotionalIntensity: Double?
}

struct SignalInsightContextPayload: Codable, Hashable, Sendable {
    let id: String
    let type: String
    let text: String
    let created_at: String
    let source_capture_ids: [String]
    let themes: [String]
    let origin: String
    let reveal_state: String
    let revealed_at: String?
}

struct SignalCommentContextPayload: Codable, Hashable, Sendable {
    let id: String
    let anchor_capture_id: String
    let text: String
    let hat: String?
    let path: String?
    let created_at: String
    let thread_ids: [String]
    let source_capture_ids: [String]
    let state: String?
}

struct SignalMemoryContextPayload: Codable, Hashable, Sendable {
    let version: String
    let source: String
    let memoryRevision: Int
    let narrativeSummary: String?
    let threads: [SignalMemoryThreadPayload]
    let allKnownThreads: [SignalMemoryThreadPayload]
    let threadLinks: [ThreadLink]
    let existingComments: [SignalCommentContextPayload]
    let categories: [String]
}

struct SignalMemoryThreadPayload: Codable, Hashable, Sendable {
    let id: String
    let label: String
    let topicKey: String
    let category: String?
    let themes: [String]
    let occurrenceCount: Int
    let intensity: Double?
    let intensityTrend: String?
    let firstSeen: String?
    let lastSeen: String?
    let captureIds: [String]
    let evidenceQuotes: [SignalMemoryEvidenceQuotePayload]
}

struct SignalMemoryEvidenceQuotePayload: Codable, Hashable, Sendable {
    let captureId: String
    let quote: String
}

enum SignalContextBuilder {
    private static let recentCaptureLimit = 10
    private static let existingSignalLimit = 10
    private static let existingCommentLimit = 30
    private static let threadContextLimit = 30

    static func build(
        currentClipID: UUID,
        captureClips: [CaptureClip],
        signalInsights: [SignalInsight],
        memoryState: SignalMemoryState = SignalMemoryState()
    ) -> SignalAnalysisContext {
        let recentCaptureCandidates = captureClips
            .filter { $0.id != currentClipID }
            .sorted { $0.createdAt > $1.createdAt }
            .prefix(recentCaptureLimit)
            .sorted { $0.createdAt < $1.createdAt }

        let recentCaptures: [SignalCaptureContextPayload] = recentCaptureCandidates.compactMap { clip in
            guard let extraction = clip.extraction else { return nil }
            return buildRecentCapturePayload(clip: clip, extraction: extraction)
        }

        let existingSignals: [SignalInsightContextPayload] = signalInsights
            .sorted { $0.createdAt > $1.createdAt }
            .prefix(existingSignalLimit)
            .map { buildSignalPayload(from: $0) }

        let existingComments = memoryState.comments
            .sorted { $0.createdAt > $1.createdAt }
            .prefix(existingCommentLimit)
            .map { buildCommentPayload(from: $0) }

        let fallbackThreads = SignalTopicThreadEngine.build(from: recentCaptures)
        let storedThreads = memoryState.threads
            .sorted { threadRecency($0) > threadRecency($1) }
            .prefix(threadContextLimit)
            .map(buildThreadPayload)
        let threads = storedThreads.isEmpty ? fallbackThreads : storedThreads
        let allKnownThreads = Array(
            mergedThreadPayloads(primary: storedThreads, fallback: fallbackThreads)
                .prefix(threadContextLimit)
        )
        let categories = Array(Set(allKnownThreads.compactMap { $0.category })).sorted()
        let narrativeSummary = SignalNarrativeBuilder.build(
            recentCaptures: recentCaptures,
            threads: threads,
            existingSignals: existingSignals
        )

        return SignalAnalysisContext(
            recentCaptures: recentCaptures,
            existingSignals: existingSignals,
            existingComments: existingComments,
            memory: SignalMemoryContextPayload(
                version: "v1",
                source: "client_local_memory",
                memoryRevision: memoryState.memoryRevision,
                narrativeSummary: narrativeSummary,
                threads: threads,
                allKnownThreads: allKnownThreads,
                threadLinks: memoryState.threadLinks,
                existingComments: existingComments,
                categories: categories
            )
        )
    }

    private static func buildRecentCapturePayload(
        clip: CaptureClip,
        extraction: SignalExtraction
    ) -> SignalCaptureContextPayload {
        let transcript = cleaned(extraction.clarifiedTranscript ?? extraction.transcript)
        let highlight = cleaned(extraction.highlight ?? extraction.strongQuote ?? "")
        let context = cleaned(extraction.context ?? "")
        let tension = cleaned(extraction.tension ?? "")

        return SignalCaptureContextPayload(
            capture_id: clip.id.uuidString,
            created_at: isoString(from: clip.createdAt),
            summary: extraction.overview,
            themes: extraction.themes,
            sourceLanguage: extraction.sourceLanguage,
            emotion: extraction.emotion == "neutral" ? nil : extraction.emotion,
            strongQuote: extraction.strongQuote,
            transcript: transcript.isEmpty ? nil : transcript,
            clarifiedTranscript: transcript.isEmpty ? nil : transcript,
            context: context.isEmpty ? nil : context,
            tension: tension.isEmpty ? nil : tension,
            highlight: highlight.isEmpty ? nil : highlight,
            signalStrength: extraction.signalStrength,
            emotionalIntensity: extraction.signalStrength
        )
    }

    private static func buildSignalPayload(from insight: SignalInsight) -> SignalInsightContextPayload {
        SignalInsightContextPayload(
            id: insight.id.uuidString,
            type: insight.type.rawValue,
            text: insight.text,
            created_at: isoString(from: insight.createdAt),
            source_capture_ids: insight.sourceCaptureClipIDs.map(\.uuidString),
            themes: insight.themes,
            origin: insight.origin.rawValue,
            reveal_state: insight.revealState.rawValue,
            revealed_at: insight.revealedAt.map(isoString(from:))
        )
    }

    private static func buildCommentPayload(from comment: SignalComment) -> SignalCommentContextPayload {
        SignalCommentContextPayload(
            id: comment.id,
            anchor_capture_id: comment.anchorCaptureId.uuidString,
            text: comment.text,
            hat: comment.hat,
            path: comment.path,
            created_at: isoString(from: comment.createdAt),
            thread_ids: comment.threadIds,
            source_capture_ids: comment.sourceCaptureIds.map(\.uuidString),
            state: comment.state
        )
    }

    private static func buildThreadPayload(from thread: TopicThread) -> SignalMemoryThreadPayload {
        SignalMemoryThreadPayload(
            id: thread.id,
            label: thread.label,
            topicKey: thread.topicKey,
            category: thread.category,
            themes: thread.themes,
            occurrenceCount: thread.occurrenceCount,
            intensity: thread.intensity,
            intensityTrend: thread.intensityTrend,
            firstSeen: thread.firstSeen,
            lastSeen: thread.lastSeen,
            captureIds: thread.captureIds,
            evidenceQuotes: thread.evidenceQuotes
        )
    }

    private static func mergedThreadPayloads(
        primary: [SignalMemoryThreadPayload],
        fallback: [SignalMemoryThreadPayload]
    ) -> [SignalMemoryThreadPayload] {
        var seen: Set<String> = []
        var merged: [SignalMemoryThreadPayload] = []

        for thread in primary + fallback {
            guard seen.contains(thread.id) == false else { continue }
            seen.insert(thread.id)
            merged.append(thread)
        }

        return merged
    }

    private static func isoString(from date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }

    private static func threadRecency(_ thread: TopicThread) -> String {
        thread.lastSeen ?? thread.firstSeen ?? ""
    }

    private static func cleaned(_ text: String) -> String {
        text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.isEmpty == false }
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
