import Foundation

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

struct PortableScheduleTiming: Equatable {
    var oneTimeEpochMilliseconds: Int64? = nil
    var repeatWeekdays: Set<PortableWeekday> = []
    var hour: Int? = nil
    var minute: Int? = nil
}

struct PortableScheduleOccurrence: Equatable {
    let key: String
    let epochMilliseconds: Int64
    var localDate: String? = nil
}

/// Pure contract rules only; the shipped DeviceActivity scheduler is not wired here.
enum PortableScheduleTimingRules {
    static let maximumEpochMilliseconds: Int64 = 253_402_300_799_999

    private static let namedZoneIDs = Set(TimeZone.knownTimeZoneIdentifiers).union(["UTC"])
    private static let utcCalendar = gregorianCalendar(timeZone: TimeZone(secondsFromGMT: 0)!)
    private static let weekdays: [PortableWeekday] = [
        .sunday, .monday, .tuesday, .wednesday, .thursday, .friday, .saturday,
    ]

    static func isValid(_ timing: PortableScheduleTiming) -> Bool {
        if timing.repeatWeekdays.isEmpty {
            guard let epoch = timing.oneTimeEpochMilliseconds else { return false }
            return validEpoch(epoch) && timing.hour == nil && timing.minute == nil
        }
        guard let hour = timing.hour, let minute = timing.minute else { return false }
        return timing.oneTimeEpochMilliseconds == nil && validClock(hour: hour, minute: minute)
    }

    static func resolveOneTimeEpochMilliseconds(
        localDate: String,
        hour: Int,
        minute: Int,
        zoneId: String,
        nowEpochMilliseconds: Int64
    ) -> Int64? {
        guard validEpoch(nowEpochMilliseconds), validClock(hour: hour, minute: minute),
              let date = parseDate(localDate), let zone = namedZone(zoneId),
              let epoch = firstValidInstant(date: date, hour: hour, minute: minute, zone: zone),
              validEpoch(epoch), epoch > nowEpochMilliseconds else { return nil }
        return epoch
    }

    static func nextOccurrence(
        timing: PortableScheduleTiming,
        afterExclusiveEpochMilliseconds: Int64,
        zoneId: String,
        lastResolvedLocalDate: String? = nil
    ) -> PortableScheduleOccurrence? {
        guard isValid(timing), validEpoch(afterExclusiveEpochMilliseconds),
              let zone = namedZone(zoneId) else { return nil }
        if let epoch = timing.oneTimeEpochMilliseconds {
            // A device-zone change never shifts a saved one-time instant.
            return epoch > afterExclusiveEpochMilliseconds
                ? PortableScheduleOccurrence(key: "oneTime:\(epoch)", epochMilliseconds: epoch)
                : nil
        }

        let calendar = gregorianCalendar(timeZone: zone)
        let after = Date(timeIntervalSince1970: Double(afterExclusiveEpochMilliseconds) / 1_000)
        let components = calendar.dateComponents([.year, .month, .day], from: after)
        guard var date = utcCalendar.date(from: components),
              let hour = timing.hour, let minute = timing.minute else { return nil }
        if let lastResolvedLocalDate {
            guard let highWaterDate = parseDate(lastResolvedLocalDate) else { return nil }
            if date <= highWaterDate {
                guard let nextDate = utcCalendar.date(byAdding: .day, value: 1, to: highWaterDate)
                else { return nil }
                date = nextDate
            }
        }

        let clockKey = String(format: "%02d:%02d", hour, minute)
        while utcCalendar.component(.year, from: date) <= 9999 {
            let weekday = weekdays[utcCalendar.component(.weekday, from: date) - 1]
            if timing.repeatWeekdays.contains(weekday),
               let epoch = firstValidInstant(date: date, hour: hour, minute: minute, zone: zone),
               epoch > afterExclusiveEpochMilliseconds {
                guard validEpoch(epoch) else { return nil }
                let localDate = dateKey(date)
                return PortableScheduleOccurrence(
                    key: "repeating:\(localDate):\(clockKey)",
                    epochMilliseconds: epoch,
                    localDate: localDate
                )
            }
            guard let nextDate = utcCalendar.date(byAdding: .day, value: 1, to: date)
            else { return nil }
            date = nextDate
        }
        return nil
    }

    fileprivate static func validEpoch(_ epoch: Int64) -> Bool {
        (0...maximumEpochMilliseconds).contains(epoch)
    }

    private static func validClock(hour: Int, minute: Int) -> Bool {
        (0...23).contains(hour) && (0...59).contains(minute)
    }

    private static func gregorianCalendar(timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "en_US_POSIX")
        calendar.timeZone = timeZone
        return calendar
    }

    private static func namedZone(_ id: String) -> TimeZone? {
        // Foundation's advertised list omits resolvable IANA aliases such as US/Pacific.
        guard namedZoneIDs.contains(id) || id.contains("/") else { return nil }
        return TimeZone(identifier: id)
    }

    private static func parseDate(_ value: String) -> Date? {
        guard value.range(of: "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", options: .regularExpression) != nil
        else { return nil }
        let parts = value.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3, (1...9999).contains(parts[0]), (1...12).contains(parts[1])
        else { return nil }
        // Validate Gregorian dates explicitly; Foundation otherwise uses Julian
        // leap years before its historical cutover, even for old replay receipts.
        let year = parts[0]
        let leapYear = year.isMultiple(of: 4) && (!year.isMultiple(of: 100) || year.isMultiple(of: 400))
        let daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
        guard (1...daysInMonth[parts[1] - 1]).contains(parts[2]) else { return nil }
        let components = DateComponents(year: parts[0], month: parts[1], day: parts[2])
        return utcCalendar.date(from: components)
    }

    private static func dateKey(_ date: Date) -> String {
        let components = utcCalendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", components.year!, components.month!, components.day!)
    }

    private static func firstValidInstant(date: Date, hour: Int, minute: Int, zone: TimeZone) -> Int64? {
        let calendar = gregorianCalendar(timeZone: zone)
        var components = utcCalendar.dateComponents([.year, .month, .day], from: date)
        components.hour = hour
        components.minute = minute
        components.second = 0
        // Search before this civil day in every named zone, not after the caller's
        // instant: otherwise the second half of an overlap could be replayed.
        guard let instant = calendar.nextDate(
            after: date.addingTimeInterval(-2 * 86_400),
            matching: components,
            matchingPolicy: .strict,
            repeatedTimePolicy: .first,
            direction: .forward
        ), calendar.date(instant, matchesComponents: components) else { return nil }
        return Int64((instant.timeIntervalSince1970 * 1_000).rounded())
    }
}

enum PortableScheduleDeliveryDecision: String, Codable, Equatable {
    case ignoreStale
    case ignoreDuplicate
    case invalid
    case tooEarly
    case missed
    case skipActive
    case startCandidate
}

enum PortableScheduleDeliveryRules {
    static let graceMilliseconds: Int64 = 300_000

    /// A candidate still needs live capability, consent, targets and an atomic start.
    static func evaluate(
        currentRevisionEnabled: Bool,
        alreadyHandled: Bool,
        sessionRequested: Bool,
        scheduledAtEpochMilliseconds: Int64,
        nowEpochMilliseconds: Int64
    ) -> PortableScheduleDeliveryDecision {
        guard currentRevisionEnabled else { return .ignoreStale }
        guard !alreadyHandled else { return .ignoreDuplicate }
        guard PortableScheduleTimingRules.validEpoch(scheduledAtEpochMilliseconds),
              PortableScheduleTimingRules.validEpoch(nowEpochMilliseconds) else { return .invalid }
        guard nowEpochMilliseconds >= scheduledAtEpochMilliseconds else { return .tooEarly }
        guard nowEpochMilliseconds - scheduledAtEpochMilliseconds <= graceMilliseconds
        else { return .missed }
        return sessionRequested ? .skipActive : .startCandidate
    }
}
