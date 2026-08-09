enum PortableWeekday: String, Codable, CaseIterable {
    case monday
    case tuesday
    case wednesday
    case thursday
    case friday
    case saturday
    case sunday
}

enum PortableScheduleKind: String, Codable, Equatable {
    case oneTime
    case repeating
}

enum PortableScheduleEligibilityReason: String, Codable, Equatable {
    case eligible
    case disabled
    case modeUnavailable
    case targetSelectionEmpty
}

struct PortableScheduleEligibilityDecision: Equatable {
    let kind: PortableScheduleKind
    let isEligible: Bool
    let reason: PortableScheduleEligibilityReason
}

enum PortableScheduleRules {
    static func evaluate(
        isEnabled: Bool,
        modeResolvable: Bool,
        targetSelectionValid: Bool,
        kind: PortableScheduleKind
    ) -> PortableScheduleEligibilityDecision {
        guard isEnabled else {
            return PortableScheduleEligibilityDecision(
                kind: kind,
                isEligible: false,
                reason: .disabled
            )
        }
        guard modeResolvable else {
            return PortableScheduleEligibilityDecision(
                kind: kind,
                isEligible: false,
                reason: .modeUnavailable
            )
        }
        guard targetSelectionValid else {
            return PortableScheduleEligibilityDecision(
                kind: kind,
                isEligible: false,
                reason: .targetSelectionEmpty
            )
        }
        return PortableScheduleEligibilityDecision(
            kind: kind,
            isEligible: true,
            reason: .eligible
        )
    }
}
