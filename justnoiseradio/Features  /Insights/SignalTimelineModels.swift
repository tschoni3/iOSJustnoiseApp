import Foundation

enum SignalInsightType: String, Codable, Hashable {
    case gem
    case action
    case question
    case pattern
    case contradiction

}

enum SignalInsightOrigin: String, Codable, Hashable {
    case automatic
    case manual
}

enum SignalInsightRevealState: String, Codable, Hashable {
    case unseen
    case revealed
}

enum SignalReviewNotice: Equatable {
    case paused(until: Date)
    case retrying(until: Date)
}

enum SignalControlIndicatorState: Equatable {
    case idle
    case reviewing
    case delayed
    case ready(count: Int)
}

struct SignalExtraction: Identifiable, Codable, Hashable {
    let id: UUID
    let captureClipID: UUID
    let createdAt: Date
    let sourceModeName: String?
    let sourceLanguage: String?
    let title: String
    let overview: String
    let actionSteps: String
    let challenges: String
    let transcript: String
    let emotion: String
    let themes: [String]
    let clarity: Double
    let noiseLevel: Double
    let actionPotential: Double
    let reflectionPotential: Double
    let confidence: Double?
    let strongQuote: String?
    let clarifiedTranscript: String?
    let context: String?
    let tension: String?
    let highlight: String?
    let signalStrength: Double?

    init(
        id: UUID = UUID(),
        captureClipID: UUID,
        createdAt: Date = Date(),
        sourceModeName: String? = nil,
        sourceLanguage: String? = nil,
        title: String,
        overview: String,
        actionSteps: String,
        challenges: String,
        transcript: String,
        emotion: String,
        themes: [String],
        clarity: Double,
        noiseLevel: Double,
        actionPotential: Double,
        reflectionPotential: Double,
        confidence: Double? = nil,
        strongQuote: String?,
        clarifiedTranscript: String? = nil,
        context: String? = nil,
        tension: String? = nil,
        highlight: String? = nil,
        signalStrength: Double? = nil
    ) {
        self.id = id
        self.captureClipID = captureClipID
        self.createdAt = createdAt
        self.sourceModeName = sourceModeName
        self.sourceLanguage = sourceLanguage
        self.title = title
        self.overview = overview
        self.actionSteps = actionSteps
        self.challenges = challenges
        self.transcript = transcript
        self.emotion = emotion
        self.themes = themes
        self.clarity = clarity
        self.noiseLevel = noiseLevel
        self.actionPotential = actionPotential
        self.reflectionPotential = reflectionPotential
        self.confidence = confidence
        self.strongQuote = strongQuote
        self.clarifiedTranscript = clarifiedTranscript
        self.context = context
        self.tension = tension
        self.highlight = highlight
        self.signalStrength = signalStrength
    }
}

struct SignalInsight: Identifiable, Codable, Hashable {
    let id: UUID
    let type: SignalInsightType
    let text: String
    let createdAt: Date
    let sourceCaptureClipIDs: [UUID]
    let sourceModeName: String?
    let themes: [String]
    let origin: SignalInsightOrigin
    let revealState: SignalInsightRevealState
    let revealedAt: Date?

    init(
        id: UUID = UUID(),
        type: SignalInsightType,
        text: String,
        createdAt: Date = Date(),
        sourceCaptureClipIDs: [UUID],
        sourceModeName: String? = nil,
        themes: [String],
        origin: SignalInsightOrigin = .automatic,
        revealState: SignalInsightRevealState? = nil,
        revealedAt: Date? = nil
    ) {
        var uniqueCaptureIDs: [UUID] = []
        for captureID in sourceCaptureClipIDs where uniqueCaptureIDs.contains(captureID) == false {
            uniqueCaptureIDs.append(captureID)
        }

        var uniqueThemes: [String] = []
        for theme in themes {
            let normalized = theme
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased()

            guard normalized.isEmpty == false else { continue }
            guard uniqueThemes.contains(normalized) == false else { continue }
            uniqueThemes.append(normalized)
        }

        let resolvedRevealState = revealState ?? (origin == .manual ? .revealed : .unseen)
        let resolvedRevealedAt: Date?
        switch resolvedRevealState {
        case .revealed:
            resolvedRevealedAt = revealedAt ?? createdAt
        case .unseen:
            resolvedRevealedAt = nil
        }

        self.id = id
        self.type = type
        self.text = text
        self.createdAt = createdAt
        self.sourceCaptureClipIDs = uniqueCaptureIDs
        self.sourceModeName = sourceModeName
        self.themes = uniqueThemes
        self.origin = origin
        self.revealState = resolvedRevealState
        self.revealedAt = resolvedRevealedAt
    }

    enum CodingKeys: String, CodingKey {
        case id
        case type
        case text
        case createdAt
        case sourceCaptureClipIDs
        case sourceModeName
        case themes
        case origin
        case revealState
        case revealedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let id = try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        let type = try container.decode(SignalInsightType.self, forKey: .type)
        let text = try container.decode(String.self, forKey: .text)
        let createdAt = try container.decode(Date.self, forKey: .createdAt)
        let sourceCaptureClipIDs = try container.decodeIfPresent([UUID].self, forKey: .sourceCaptureClipIDs) ?? []
        let sourceModeName = try container.decodeIfPresent(String.self, forKey: .sourceModeName)
        let themes = try container.decodeIfPresent([String].self, forKey: .themes) ?? []
        let origin = try container.decodeIfPresent(SignalInsightOrigin.self, forKey: .origin) ?? .automatic
        let revealState = try container.decodeIfPresent(SignalInsightRevealState.self, forKey: .revealState) ?? .revealed
        let revealedAt = try container.decodeIfPresent(Date.self, forKey: .revealedAt)

        self.init(
            id: id,
            type: type,
            text: text,
            createdAt: createdAt,
            sourceCaptureClipIDs: sourceCaptureClipIDs,
            sourceModeName: sourceModeName,
            themes: themes,
            origin: origin,
            revealState: revealState,
            revealedAt: revealedAt
        )
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(type, forKey: .type)
        try container.encode(text, forKey: .text)
        try container.encode(createdAt, forKey: .createdAt)
        try container.encode(sourceCaptureClipIDs, forKey: .sourceCaptureClipIDs)
        try container.encodeIfPresent(sourceModeName, forKey: .sourceModeName)
        try container.encode(themes, forKey: .themes)
        try container.encode(origin, forKey: .origin)
        try container.encode(revealState, forKey: .revealState)
        try container.encodeIfPresent(revealedAt, forKey: .revealedAt)
    }

}

enum SignalAnalysisFailureKind: String, Codable, Hashable {
    case quotaExceeded
    case transient
}

struct SignalAnalysisFailureRecord: Identifiable, Codable, Hashable {
    var id: UUID { captureClipID }
    let captureClipID: UUID
    let failedAt: Date
    let retryAfter: Date
    let kind: SignalAnalysisFailureKind
    let message: String
}
