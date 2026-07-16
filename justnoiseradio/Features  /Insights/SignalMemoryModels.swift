import Foundation

struct SignalMemoryState: Codable, Hashable, Sendable {
    var memoryRevision: Int
    var appliedOperationIds: Set<String>
    var appliedOperations: [AppliedOperationRecord]
    var threads: [TopicThread]
    var comments: [SignalComment]
    var threadLinks: [ThreadLink]

    init(
        memoryRevision: Int = 0,
        appliedOperationIds: Set<String> = [],
        appliedOperations: [AppliedOperationRecord] = [],
        threads: [TopicThread] = [],
        comments: [SignalComment] = [],
        threadLinks: [ThreadLink] = []
    ) {
        self.memoryRevision = memoryRevision
        self.appliedOperationIds = appliedOperationIds
        self.appliedOperations = appliedOperations
        self.threads = threads
        self.comments = comments
        self.threadLinks = threadLinks
    }
}

struct TopicThread: Identifiable, Codable, Hashable, Sendable {
    var id: String
    var label: String
    var topicKey: String
    var category: String?
    var themes: [String]
    var occurrenceCount: Int
    var intensity: Double?
    var intensityTrend: String?
    var firstSeen: String?
    var lastSeen: String?
    var captureIds: [String]
    var evidenceQuotes: [SignalMemoryEvidenceQuotePayload]
    var state: String?
    var commentState: String?

    init(
        id: String,
        label: String,
        topicKey: String,
        category: String? = nil,
        themes: [String] = [],
        occurrenceCount: Int = 0,
        intensity: Double? = nil,
        intensityTrend: String? = nil,
        firstSeen: String? = nil,
        lastSeen: String? = nil,
        captureIds: [String] = [],
        evidenceQuotes: [SignalMemoryEvidenceQuotePayload] = [],
        state: String? = nil,
        commentState: String? = nil
    ) {
        self.id = id
        self.label = label
        self.topicKey = topicKey
        self.category = category
        self.themes = themes
        self.occurrenceCount = occurrenceCount
        self.intensity = intensity
        self.intensityTrend = intensityTrend
        self.firstSeen = firstSeen
        self.lastSeen = lastSeen
        self.captureIds = captureIds
        self.evidenceQuotes = evidenceQuotes
        self.state = state
        self.commentState = commentState
    }

    enum CodingKeys: String, CodingKey {
        case id
        case label
        case topicKey
        case category
        case themes
        case occurrenceCount
        case intensity
        case intensityTrend
        case firstSeen
        case lastSeen
        case captureIds
        case evidenceQuotes
        case state
        case commentState
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let id = try container.decodeIfPresent(String.self, forKey: .id) ?? UUID().uuidString
        let topicKey = try container.decodeIfPresent(String.self, forKey: .topicKey) ?? id
        let label = try container.decodeIfPresent(String.self, forKey: .label) ?? topicKey

        self.init(
            id: id,
            label: label,
            topicKey: topicKey,
            category: try container.decodeIfPresent(String.self, forKey: .category),
            themes: try container.decodeIfPresent([String].self, forKey: .themes) ?? [],
            occurrenceCount: try container.decodeIfPresent(Int.self, forKey: .occurrenceCount) ?? 0,
            intensity: try container.decodeIfPresent(Double.self, forKey: .intensity),
            intensityTrend: try container.decodeIfPresent(String.self, forKey: .intensityTrend),
            firstSeen: try container.decodeIfPresent(String.self, forKey: .firstSeen),
            lastSeen: try container.decodeIfPresent(String.self, forKey: .lastSeen),
            captureIds: try container.decodeIfPresent([String].self, forKey: .captureIds) ?? [],
            evidenceQuotes: try container.decodeIfPresent([SignalMemoryEvidenceQuotePayload].self, forKey: .evidenceQuotes) ?? [],
            state: try container.decodeIfPresent(String.self, forKey: .state),
            commentState: try container.decodeIfPresent(String.self, forKey: .commentState)
        )
    }
}

struct SignalComment: Identifiable, Codable, Hashable, Sendable {
    let id: String
    let anchorCaptureId: UUID
    let text: String
    let hat: String?
    let path: String?
    let createdAt: Date
    let threadIds: [String]
    let sourceCaptureIds: [UUID]
    let state: String?

    var normalizedTextSignature: String {
        text
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { $0.isEmpty == false }
            .joined(separator: " ")
    }
}

struct ThreadLink: Identifiable, Codable, Hashable, Sendable {
    var id: String
    var sourceThreadId: String
    var targetThreadId: String
    var relationship: String?
    var createdAt: String?
    var state: String?

    init(
        id: String,
        sourceThreadId: String,
        targetThreadId: String,
        relationship: String? = nil,
        createdAt: String? = nil,
        state: String? = nil
    ) {
        self.id = id
        self.sourceThreadId = sourceThreadId
        self.targetThreadId = targetThreadId
        self.relationship = relationship
        self.createdAt = createdAt
        self.state = state
    }

    enum CodingKeys: String, CodingKey {
        case id
        case sourceThreadId
        case targetThreadId
        case relationship
        case createdAt
        case state
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let sourceThreadId = try container.decodeIfPresent(String.self, forKey: .sourceThreadId) ?? ""
        let targetThreadId = try container.decodeIfPresent(String.self, forKey: .targetThreadId) ?? ""
        let id = try container.decodeIfPresent(String.self, forKey: .id)
            ?? ThreadLink.stableID(sourceThreadId: sourceThreadId, targetThreadId: targetThreadId)

        self.init(
            id: id,
            sourceThreadId: sourceThreadId,
            targetThreadId: targetThreadId,
            relationship: try container.decodeIfPresent(String.self, forKey: .relationship),
            createdAt: try container.decodeIfPresent(String.self, forKey: .createdAt),
            state: try container.decodeIfPresent(String.self, forKey: .state)
        )
    }

    static func stableID(sourceThreadId: String, targetThreadId: String) -> String {
        [sourceThreadId, targetThreadId].sorted().joined(separator: "::")
    }
}

struct AppliedOperationRecord: Identifiable, Codable, Hashable, Sendable {
    var id: String { operationId }

    let operationId: String
    let appliedAt: Date
    let baseMemoryRevision: Int
    let nextMemoryRevision: Int
    let captureId: UUID?
}

struct BackendCaptureResponse: Decodable, Equatable, Sendable {
    let contractVersion: Int
    let engineVersion: String
    let operationId: String
    let baseMemoryRevision: Int
    let nextMemoryRevision: Int
    let requiresExactBaseRevision: Bool
    let capturePatch: BackendCapturePatch
    let memoryPatch: BackendMemoryPatch
    let commentDecision: BackendCommentDecision
    let safety: BackendSafetyPayload
    let comment: BackendSignalComment?
    let serverStorage: String?
}

struct BackendCapturePatch: Decodable, Equatable, Sendable {
    let captureId: String?
    let transcript: String?
    let sourceLanguage: String?
    let summary: String?
    let themes: [String]?
    let category: String?
    let emotion: String?
    let intensity: Double?
    let tension: String?
    let desire: String?
    let avoidedAction: String?
    let currentState: String?
    let processingStatus: String?
    let processedAt: String?
    let strongQuote: String?
    let clarifiedTranscript: String?
    let context: String?
    let highlight: String?
    let signalStrength: Double?
}

struct BackendMemoryPatch: Decodable, Equatable, Sendable {
    let operations: [BackendMemoryOperation]

    init(operations: [BackendMemoryOperation] = []) {
        self.operations = operations
    }

    enum CodingKeys: String, CodingKey {
        case operations
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        operations = try container.decodeIfPresent([BackendMemoryOperation].self, forKey: .operations) ?? []
    }
}

enum BackendMemoryOperationType: Equatable, Sendable {
    case updateCapture
    case createThread
    case appendCaptureToThread
    case updateThreadState
    case createThreadLink
    case removeThreadLink
    case updateThreadCommentState
    case createComment
    case unknown(String)

    init(rawValue: String) {
        let normalized = rawValue
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "")
            .replacingOccurrences(of: "-", with: "")
            .lowercased()

        switch normalized {
        case "updatecapture":
            self = .updateCapture
        case "createthread":
            self = .createThread
        case "appendcapturetothread":
            self = .appendCaptureToThread
        case "updatethreadstate":
            self = .updateThreadState
        case "createthreadlink":
            self = .createThreadLink
        case "removethreadlink":
            self = .removeThreadLink
        case "updatethreadcommentstate":
            self = .updateThreadCommentState
        case "createcomment":
            self = .createComment
        default:
            self = .unknown(rawValue)
        }
    }
}

struct BackendMemoryOperation: Decodable, Equatable, Sendable {
    let type: BackendMemoryOperationType
    let operationId: String?
    let captureId: String?
    let threadId: String?
    let sourceThreadId: String?
    let targetThreadId: String?
    let linkId: String?
    let commentId: String?
    let state: String?
    let commentState: String?
    let capturePatch: BackendCapturePatch?
    let threadPatch: BackendThreadPatch?
    let thread: TopicThread?
    let link: ThreadLink?
    let comment: BackendSignalComment?
    let anchorCaptureId: String?
    let text: String?
    let hat: String?
    let path: String?
    let threadIds: [String]?
    let sourceCaptureIds: [String]?
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case type
        case operation
        case operationType
        case operationId
        case captureId
        case threadId
        case sourceThreadId
        case targetThreadId
        case linkId
        case commentId
        case state
        case commentState
        case capturePatch
        case threadPatch
        case patch
        case thread
        case link
        case comment
        case anchorCaptureId
        case text
        case hat
        case path
        case threadIds
        case sourceCaptureIds
        case createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let rawType = try container.decodeIfPresent(String.self, forKey: .type)
            ?? container.decodeIfPresent(String.self, forKey: .operationType)
            ?? container.decodeIfPresent(String.self, forKey: .operation)
            ?? "unknown"

        type = BackendMemoryOperationType(rawValue: rawType)
        operationId = try container.decodeIfPresent(String.self, forKey: .operationId)
        captureId = try container.decodeIfPresent(String.self, forKey: .captureId)
        threadId = try container.decodeIfPresent(String.self, forKey: .threadId)
        sourceThreadId = try container.decodeIfPresent(String.self, forKey: .sourceThreadId)
        targetThreadId = try container.decodeIfPresent(String.self, forKey: .targetThreadId)
        linkId = try container.decodeIfPresent(String.self, forKey: .linkId)
        commentId = try container.decodeIfPresent(String.self, forKey: .commentId)
        state = try container.decodeIfPresent(String.self, forKey: .state)
        commentState = try container.decodeIfPresent(String.self, forKey: .commentState)
        capturePatch = try container.decodeIfPresent(BackendCapturePatch.self, forKey: .capturePatch)
            ?? container.decodeIfPresent(BackendCapturePatch.self, forKey: .patch)
        threadPatch = try container.decodeIfPresent(BackendThreadPatch.self, forKey: .threadPatch)
            ?? container.decodeIfPresent(BackendThreadPatch.self, forKey: .patch)
        thread = try container.decodeIfPresent(TopicThread.self, forKey: .thread)
        link = try container.decodeIfPresent(ThreadLink.self, forKey: .link)
        comment = try container.decodeIfPresent(BackendSignalComment.self, forKey: .comment)
        anchorCaptureId = try container.decodeIfPresent(String.self, forKey: .anchorCaptureId)
        text = try container.decodeIfPresent(String.self, forKey: .text)
        hat = try container.decodeIfPresent(String.self, forKey: .hat)
        path = try container.decodeIfPresent(String.self, forKey: .path)
        threadIds = try container.decodeIfPresent([String].self, forKey: .threadIds)
        sourceCaptureIds = try container.decodeIfPresent([String].self, forKey: .sourceCaptureIds)
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt)
    }
}

struct BackendThreadPatch: Decodable, Equatable, Sendable {
    let label: String?
    let topicKey: String?
    let category: String?
    let themes: [String]?
    let occurrenceCount: Int?
    let intensity: Double?
    let intensityTrend: String?
    let firstSeen: String?
    let lastSeen: String?
    let captureIds: [String]?
    let evidenceQuotes: [SignalMemoryEvidenceQuotePayload]?
    let state: String?
    let commentState: String?
}

struct BackendCommentDecision: Decodable, Equatable, Sendable {
    let type: String?
    let shouldShow: Bool?
    let shouldCreate: Bool?
    let reason: String?
    let blockReason: String?
    let hat: String?
    let path: String?
}

struct BackendSafetyPayload: Decodable, Equatable, Sendable {
    let isSafe: Bool?
    let blocked: Bool?
    let hold: Bool?
    let reason: String?
    let categories: [String]?
}

struct BackendSignalComment: Decodable, Equatable, Sendable {
    let id: String?
    let anchorCaptureId: String
    let text: String
    let hat: String?
    let path: String?
    let createdAt: String?
    let threadIds: [String]
    let sourceCaptureIds: [String]
    let state: String?

    enum CodingKeys: String, CodingKey {
        case id
        case anchorCaptureId
        case text
        case hat
        case path
        case createdAt
        case threadIds
        case sourceCaptureIds
        case state
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        anchorCaptureId = try container.decode(String.self, forKey: .anchorCaptureId)
        text = try container.decode(String.self, forKey: .text)
        id = try container.decodeIfPresent(String.self, forKey: .id)
        hat = try container.decodeIfPresent(String.self, forKey: .hat)
        path = try container.decodeIfPresent(String.self, forKey: .path)
        createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt)
        threadIds = try container.decodeIfPresent([String].self, forKey: .threadIds) ?? []
        sourceCaptureIds = try container.decodeIfPresent([String].self, forKey: .sourceCaptureIds) ?? []
        state = try container.decodeIfPresent(String.self, forKey: .state)
    }
}

enum SignalMemoryDate {
    static func date(from string: String?) -> Date? {
        guard let string, string.isEmpty == false else { return nil }

        let fractionalFormatter = ISO8601DateFormatter()
        fractionalFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        if let date = fractionalFormatter.date(from: string) {
            return date
        }

        let standardFormatter = ISO8601DateFormatter()
        standardFormatter.formatOptions = [.withInternetDateTime]
        return standardFormatter.date(from: string)
    }

    static func isoString(from date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }
}
