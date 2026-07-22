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

enum ZapReadPolicy {
    static func classify(
        record: ZapNDEFRecordInput,
        authorizedPayloads: Set<String>
    ) -> ZapPayloadClassification {
        guard
            record.typeNameFormat == .wellKnown,
            record.type == Data([0x54]),
            let text = decodeNDEFTextPayload(record.payload)
        else {
            return .malformed
        }

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

    private static func decodeNDEFTextPayload(_ payload: Data) -> String? {
        guard let statusByte = payload.first else { return nil }
        guard statusByte & 0xC0 == 0 else { return nil }

        let languageCodeLength = Int(statusByte & 0x3F)
        guard languageCodeLength > 0 else { return nil }
        let textStartIndex = 1 + languageCodeLength
        guard payload.count > textStartIndex else { return nil }

        let textData = payload.dropFirst(textStartIndex)
        guard let text = String(data: Data(textData), encoding: .utf8), !text.isEmpty else {
            return nil
        }

        return text
    }
}

enum AccidentalStopGuard {
    static let minimumElapsedSeconds: TimeInterval = 3

    static func allowsStop(elapsedSeconds: TimeInterval) -> Bool {
        elapsedSeconds >= minimumElapsedSeconds
    }
}
