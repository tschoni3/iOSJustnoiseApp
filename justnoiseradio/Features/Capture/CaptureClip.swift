import Foundation

enum CaptureAnalysisState: String, Codable, Hashable {
    case pending
    case reviewed
    case failed
}

struct CaptureClip: Identifiable, Codable, Hashable {
    let id: UUID
    let createdAt: Date
    let duration: TimeInterval
    let audioFileURL: URL
    let sourceModeName: String?
    let extraction: SignalExtraction?
    let analysisState: CaptureAnalysisState
    let analysisUpdatedAt: Date?
    let lastDecisionBlockReason: String?
    let retryAfter: Date?
    let lastErrorMessage: String?
    let transcript: String?
    let sourceLanguage: String?
    let summary: String?
    let themes: [String]
    let category: String?
    let emotion: String?
    let intensity: Double?
    let tension: String?
    let desire: String?
    let avoidedAction: String?
    let currentState: String?
    let processingStatus: String?
    let processedAt: Date?

    init(
        id: UUID = UUID(),
        createdAt: Date = Date(),
        duration: TimeInterval,
        audioFileURL: URL,
        sourceModeName: String? = nil,
        extraction: SignalExtraction? = nil,
        analysisState: CaptureAnalysisState = .pending,
        analysisUpdatedAt: Date? = nil,
        lastDecisionBlockReason: String? = nil,
        retryAfter: Date? = nil,
        lastErrorMessage: String? = nil,
        transcript: String? = nil,
        sourceLanguage: String? = nil,
        summary: String? = nil,
        themes: [String] = [],
        category: String? = nil,
        emotion: String? = nil,
        intensity: Double? = nil,
        tension: String? = nil,
        desire: String? = nil,
        avoidedAction: String? = nil,
        currentState: String? = nil,
        processingStatus: String? = nil,
        processedAt: Date? = nil
    ) {
        self.id = id
        self.createdAt = createdAt
        self.duration = duration
        self.audioFileURL = audioFileURL
        self.sourceModeName = sourceModeName
        self.extraction = extraction
        self.analysisState = analysisState
        self.analysisUpdatedAt = analysisUpdatedAt
        self.lastDecisionBlockReason = lastDecisionBlockReason
        self.retryAfter = retryAfter
        self.lastErrorMessage = lastErrorMessage
        self.transcript = transcript
        self.sourceLanguage = sourceLanguage
        self.summary = summary
        self.themes = themes
        self.category = category
        self.emotion = emotion
        self.intensity = intensity
        self.tension = tension
        self.desire = desire
        self.avoidedAction = avoidedAction
        self.currentState = currentState
        self.processingStatus = processingStatus
        self.processedAt = processedAt
    }

    enum CodingKeys: String, CodingKey {
        case id
        case createdAt
        case duration
        case audioFileURL
        case sourceModeName
        case extraction
        case analysisState
        case analysisUpdatedAt
        case lastDecisionBlockReason
        case retryAfter
        case lastErrorMessage
        case transcript
        case sourceLanguage
        case summary
        case themes
        case category
        case emotion
        case intensity
        case tension
        case desire
        case avoidedAction
        case currentState
        case processingStatus
        case processedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let id = try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        let createdAt = try container.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
        let duration = try container.decode(TimeInterval.self, forKey: .duration)
        let audioFileURL = try container.decode(URL.self, forKey: .audioFileURL)
        let sourceModeName = try container.decodeIfPresent(String.self, forKey: .sourceModeName)
        let extraction = try container.decodeIfPresent(SignalExtraction.self, forKey: .extraction)
        let analysisState = try container.decodeIfPresent(CaptureAnalysisState.self, forKey: .analysisState)
            ?? (extraction == nil ? .pending : .reviewed)
        let analysisUpdatedAt = try container.decodeIfPresent(Date.self, forKey: .analysisUpdatedAt)
        let lastDecisionBlockReason = try container.decodeIfPresent(String.self, forKey: .lastDecisionBlockReason)
        let retryAfter = try container.decodeIfPresent(Date.self, forKey: .retryAfter)
        let lastErrorMessage = try container.decodeIfPresent(String.self, forKey: .lastErrorMessage)
        let transcript = try container.decodeIfPresent(String.self, forKey: .transcript)
        let sourceLanguage = try container.decodeIfPresent(String.self, forKey: .sourceLanguage)
        let summary = try container.decodeIfPresent(String.self, forKey: .summary)
        let themes = try container.decodeIfPresent([String].self, forKey: .themes) ?? extraction?.themes ?? []
        let category = try container.decodeIfPresent(String.self, forKey: .category)
        let emotion = try container.decodeIfPresent(String.self, forKey: .emotion)
        let intensity = try container.decodeIfPresent(Double.self, forKey: .intensity)
        let tension = try container.decodeIfPresent(String.self, forKey: .tension)
        let desire = try container.decodeIfPresent(String.self, forKey: .desire)
        let avoidedAction = try container.decodeIfPresent(String.self, forKey: .avoidedAction)
        let currentState = try container.decodeIfPresent(String.self, forKey: .currentState)
        let processingStatus = try container.decodeIfPresent(String.self, forKey: .processingStatus)
        let processedAt = try container.decodeIfPresent(Date.self, forKey: .processedAt)

        self.init(
            id: id,
            createdAt: createdAt,
            duration: duration,
            audioFileURL: audioFileURL,
            sourceModeName: sourceModeName,
            extraction: extraction,
            analysisState: analysisState,
            analysisUpdatedAt: analysisUpdatedAt,
            lastDecisionBlockReason: lastDecisionBlockReason,
            retryAfter: retryAfter,
            lastErrorMessage: lastErrorMessage,
            transcript: transcript,
            sourceLanguage: sourceLanguage,
            summary: summary,
            themes: themes,
            category: category,
            emotion: emotion,
            intensity: intensity,
            tension: tension,
            desire: desire,
            avoidedAction: avoidedAction,
            currentState: currentState,
            processingStatus: processingStatus,
            processedAt: processedAt
        )
    }

    func updatingAnalysis(
        extraction: SignalExtraction,
        sourceModeName: String?,
        blockReason: String?,
        updatedAt: Date = Date()
    ) -> CaptureClip {
        CaptureClip(
            id: id,
            createdAt: createdAt,
            duration: duration,
            audioFileURL: audioFileURL,
            sourceModeName: sourceModeName ?? self.sourceModeName,
            extraction: extraction,
            analysisState: .reviewed,
            analysisUpdatedAt: updatedAt,
            lastDecisionBlockReason: blockReason,
            retryAfter: nil,
            lastErrorMessage: nil,
            transcript: transcript,
            sourceLanguage: sourceLanguage,
            summary: summary,
            themes: themes,
            category: category,
            emotion: emotion,
            intensity: intensity,
            tension: tension,
            desire: desire,
            avoidedAction: avoidedAction,
            currentState: currentState,
            processingStatus: processingStatus,
            processedAt: processedAt
        )
    }

    func applyingCapturePatch(
        _ patch: BackendCapturePatch,
        selectedModeName: String?,
        blockReason: String?,
        updatedAt: Date = Date()
    ) -> CaptureClip {
        let resolvedTranscript = cleaned(patch.transcript ?? transcript ?? extraction?.transcript ?? "")
        let resolvedSummary = cleaned(patch.summary ?? summary ?? extraction?.overview ?? "")
        let resolvedThemes = uniqueCleanedThemes(patch.themes ?? themes)
        let resolvedEmotion = cleaned(patch.emotion ?? emotion ?? extraction?.emotion ?? "neutral")
        let resolvedSourceLanguage = cleaned(patch.sourceLanguage ?? sourceLanguage ?? "")
        let resolvedProcessedAt = SignalMemoryDate.date(from: patch.processedAt) ?? processedAt ?? updatedAt
        let resolvedIntensity = patch.intensity ?? intensity ?? extraction?.signalStrength
        let resolvedTension = cleaned(patch.tension ?? tension ?? extraction?.tension ?? "")
        let resolvedStrongQuote = cleaned(patch.strongQuote ?? extraction?.strongQuote ?? "")
        let resolvedHighlight = cleaned(patch.highlight ?? extraction?.highlight ?? resolvedStrongQuote)
        let resolvedContext = cleaned(patch.context ?? extraction?.context ?? "")
        let resolvedClarifiedTranscript = cleaned(patch.clarifiedTranscript ?? extraction?.clarifiedTranscript ?? "")
        let clarity = clamped(resolvedIntensity ?? extraction?.clarity ?? 0.5)

        let updatedExtraction = SignalExtraction(
            captureClipID: id,
            createdAt: resolvedProcessedAt,
            sourceModeName: selectedModeName ?? sourceModeName,
            sourceLanguage: resolvedSourceLanguage.isEmpty || resolvedSourceLanguage == "und"
                ? nil
                : resolvedSourceLanguage,
            title: resolvedSummary.isEmpty
                ? (resolvedThemes.first?.capitalized ?? "Signal")
                : resolvedSummary,
            overview: resolvedSummary.isEmpty ? resolvedTranscript : resolvedSummary,
            actionSteps: "",
            challenges: "",
            transcript: resolvedTranscript,
            emotion: resolvedEmotion.isEmpty ? "neutral" : resolvedEmotion,
            themes: resolvedThemes,
            clarity: clarity,
            noiseLevel: clamped(1 - clarity),
            actionPotential: 0,
            reflectionPotential: clarity,
            confidence: nil,
            strongQuote: resolvedStrongQuote.isEmpty ? nil : resolvedStrongQuote,
            clarifiedTranscript: resolvedClarifiedTranscript.isEmpty ? nil : resolvedClarifiedTranscript,
            context: resolvedContext.isEmpty ? nil : resolvedContext,
            tension: resolvedTension.isEmpty ? nil : resolvedTension,
            highlight: resolvedHighlight.isEmpty ? nil : resolvedHighlight,
            signalStrength: resolvedIntensity
        )

        return CaptureClip(
            id: id,
            createdAt: createdAt,
            duration: duration,
            audioFileURL: audioFileURL,
            sourceModeName: selectedModeName ?? sourceModeName,
            extraction: updatedExtraction,
            analysisState: .reviewed,
            analysisUpdatedAt: resolvedProcessedAt,
            lastDecisionBlockReason: blockReason,
            retryAfter: nil,
            lastErrorMessage: nil,
            transcript: resolvedTranscript.isEmpty ? nil : resolvedTranscript,
            sourceLanguage: resolvedSourceLanguage.isEmpty ? nil : resolvedSourceLanguage,
            summary: resolvedSummary.isEmpty ? nil : resolvedSummary,
            themes: resolvedThemes,
            category: cleaned(patch.category ?? category ?? "").nilIfEmpty,
            emotion: resolvedEmotion.isEmpty ? nil : resolvedEmotion,
            intensity: resolvedIntensity,
            tension: resolvedTension.isEmpty ? nil : resolvedTension,
            desire: cleaned(patch.desire ?? desire ?? "").nilIfEmpty,
            avoidedAction: cleaned(patch.avoidedAction ?? avoidedAction ?? "").nilIfEmpty,
            currentState: cleaned(patch.currentState ?? currentState ?? "").nilIfEmpty,
            processingStatus: cleaned(patch.processingStatus ?? processingStatus ?? "processed"),
            processedAt: resolvedProcessedAt
        )
    }

    func markingReviewed(
        blockReason: String?,
        updatedAt: Date = Date()
    ) -> CaptureClip {
        CaptureClip(
            id: id,
            createdAt: createdAt,
            duration: duration,
            audioFileURL: audioFileURL,
            sourceModeName: sourceModeName,
            extraction: extraction,
            analysisState: .reviewed,
            analysisUpdatedAt: updatedAt,
            lastDecisionBlockReason: blockReason,
            retryAfter: nil,
            lastErrorMessage: nil,
            transcript: transcript,
            sourceLanguage: sourceLanguage,
            summary: summary,
            themes: themes,
            category: category,
            emotion: emotion,
            intensity: intensity,
            tension: tension,
            desire: desire,
            avoidedAction: avoidedAction,
            currentState: currentState,
            processingStatus: processingStatus ?? "processed",
            processedAt: processedAt ?? updatedAt
        )
    }

    func markingAnalysisFailed(
        retryAfter: Date,
        message: String,
        sourceModeName: String? = nil,
        updatedAt: Date = Date()
    ) -> CaptureClip {
        CaptureClip(
            id: id,
            createdAt: createdAt,
            duration: duration,
            audioFileURL: audioFileURL,
            sourceModeName: sourceModeName ?? self.sourceModeName,
            extraction: extraction,
            analysisState: .failed,
            analysisUpdatedAt: updatedAt,
            lastDecisionBlockReason: lastDecisionBlockReason,
            retryAfter: retryAfter,
            lastErrorMessage: message,
            transcript: transcript,
            sourceLanguage: sourceLanguage,
            summary: summary,
            themes: themes,
            category: category,
            emotion: emotion,
            intensity: intensity,
            tension: tension,
            desire: desire,
            avoidedAction: avoidedAction,
            currentState: currentState,
            processingStatus: processingStatus,
            processedAt: processedAt
        )
    }

    func clearingFailure() -> CaptureClip {
        CaptureClip(
            id: id,
            createdAt: createdAt,
            duration: duration,
            audioFileURL: audioFileURL,
            sourceModeName: sourceModeName,
            extraction: extraction,
            analysisState: extraction == nil ? .pending : .reviewed,
            analysisUpdatedAt: analysisUpdatedAt,
            lastDecisionBlockReason: lastDecisionBlockReason,
            retryAfter: nil,
            lastErrorMessage: nil,
            transcript: transcript,
            sourceLanguage: sourceLanguage,
            summary: summary,
            themes: themes,
            category: category,
            emotion: emotion,
            intensity: intensity,
            tension: tension,
            desire: desire,
            avoidedAction: avoidedAction,
            currentState: currentState,
            processingStatus: processingStatus,
            processedAt: processedAt
        )
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

    private func uniqueCleanedThemes(_ values: [String]) -> [String] {
        var result: [String] = []

        for value in values {
            let normalized = cleaned(value).lowercased()
            guard normalized.isEmpty == false else { continue }
            guard result.contains(normalized) == false else { continue }
            result.append(normalized)
        }

        return result
    }

    private func clamped(_ value: Double) -> Double {
        min(max(value, 0), 1)
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
