import FamilyControls
import Foundation

enum TargetSelectionRule {
    static func isValid(
        applicationCount: Int,
        categoryCount: Int,
        websiteCount: Int
    ) -> Bool {
        applicationCount > 0 || categoryCount > 0 || websiteCount > 0
    }
}

extension FamilyActivitySelection {
    var hasBlockingTargets: Bool {
        TargetSelectionRule.isValid(
            applicationCount: applicationTokens.count,
            categoryCount: categoryTokens.count,
            websiteCount: webDomainTokens.count
        )
    }
}

enum ZapPayloadClassification: String, Decodable, Equatable {
    case authorized
    case unauthorized
    case malformed
    case canceled
}

enum ZapNDEFTypeNameFormat: Equatable {
    case wellKnown
    case unsupported
}

struct ZapNDEFRecordInput: Equatable {
    let typeNameFormat: ZapNDEFTypeNameFormat
    let type: Data
    let payload: Data
}

enum ZapReadOutcome: String, Decodable, Equatable {
    case accepted
    case duplicateIgnored
    case unauthorized
    case invalid
    case canceled
}

struct ZapReadDecision: Equatable {
    let outcome: ZapReadOutcome
    let mutationEligible: Bool
}

enum ZapEntryModeAction: String, Decodable, Equatable {
    case stopActiveSession
    case startRecentMode
    case modeUnavailable
}

enum ZapEntryModeRules {
    static func resolve(
        sessionActive: Bool,
        recentModeExists: Bool,
        recentModeSelectionValid: Bool
    ) -> ZapEntryModeAction {
        if sessionActive {
            return .stopActiveSession
        }
        if recentModeExists && recentModeSelectionValid {
            return .startRecentMode
        }
        return .modeUnavailable
    }
}

enum ZapReadPolicy {
    static func classify(
        record: ZapNDEFRecordInput,
        authorizedPayloads: Set<String>
    ) -> ZapPayloadClassification {
        guard let text = decodedText(record) else { return .malformed }

        return authorizedPayloads.contains(text) ? .authorized : .unauthorized
    }

    static func decide(
        classification: ZapPayloadClassification,
        acceptedReadAlreadyHandled: Bool
    ) -> ZapReadDecision {
        switch classification {
        case .authorized where acceptedReadAlreadyHandled:
            return ZapReadDecision(outcome: .duplicateIgnored, mutationEligible: false)
        case .authorized:
            return ZapReadDecision(outcome: .accepted, mutationEligible: true)
        case .unauthorized:
            return ZapReadDecision(outcome: .unauthorized, mutationEligible: false)
        case .malformed:
            return ZapReadDecision(outcome: .invalid, mutationEligible: false)
        case .canceled:
            return ZapReadDecision(outcome: .canceled, mutationEligible: false)
        }
    }

    static func firstRelevantDecision(
        records: [ZapNDEFRecordInput],
        authorizedPayloads: Set<String>,
        acceptedReadAlreadyHandled: Bool
    ) -> ZapReadDecision {
        var firstFailure: ZapReadDecision?

        for record in records {
            let decision = decide(
                classification: classify(
                    record: record,
                    authorizedPayloads: authorizedPayloads
                ),
                acceptedReadAlreadyHandled: acceptedReadAlreadyHandled
            )

            if decision.mutationEligible {
                return decision
            }

            if firstFailure == nil {
                firstFailure = decision
            }
        }

        return firstFailure ?? ZapReadDecision(outcome: .invalid, mutationEligible: false)
    }

    static func decodedText(_ record: ZapNDEFRecordInput, allowsUTF16: Bool = false) -> String? {
        guard record.typeNameFormat == .wellKnown, record.type == ZapTextPayload.type,
              let text = ZapTextPayload.decode(record.payload, allowsUTF16: allowsUTF16),
              !text.isEmpty else { return nil }
        return text
    }
}

enum AccidentalStopGuard {
    static let minimumElapsedSeconds: TimeInterval = 3

    static func allowsStop(elapsedSeconds: TimeInterval) -> Bool {
        elapsedSeconds >= minimumElapsedSeconds
    }
}
