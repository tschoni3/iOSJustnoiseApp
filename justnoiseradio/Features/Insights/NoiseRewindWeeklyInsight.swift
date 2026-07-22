//
//  NoiseRewindWeeklyInsight.swift
//  justnoise
//
//  Created by TJ on 21.05.26.
//

import Foundation

/// The dominant weekly attention pattern. This is not a personality or rank.
enum SignalAttentionState: String, Codable, Equatable {
    case drift
    case builder
    case guardian
}

struct NoiseRewindWeeklyInsight {
    let weekStart: Date
    let weekEnd: Date

    let signalChangePercent: Int
    let weeklySignalTitle: String
    let weeklySignalSubtitle: String

    let protectedSessionsCount: Int
    let totalProtectedDuration: TimeInterval
    let protectedMomentsText: String

    let deepestSessionDate: Date?
    let deepestSessionDuration: TimeInterval
    let deepestSessionModeName: String?

    let mostFocusedDayDate: Date?
    let mostFocusedDayDuration: TimeInterval

    let patternTitle: String
    let patternSubtitle: String
    let hourlySignalDurations: [TimeInterval]

    let primaryArchetype: SignalAttentionState
    let primaryArchetypeTitle: String
    let primaryArchetypeSubtitle: String

    let reflectionPrompt: String
}

struct NoiseRewindWeeklyInsightGenerator {

    private static let maxReasonableSessionDuration: TimeInterval = 16 * 60 * 60
    private static let protectedSessionThreshold: TimeInterval = 20 * 60

    static func generate(
        sessions: [Session],
        journals: [JournalEntry],
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) -> NoiseRewindWeeklyInsight? {

        let adjustedReferenceDate = rewindReferenceDateForDisplay(
            referenceDate: referenceDate,
            calendar: calendar
        )

        let weekInterval = currentWeekInterval(
            for: adjustedReferenceDate,
            calendar: calendar
        )

        let previousWeekInterval = previousWeekInterval(
            from: weekInterval,
            calendar: calendar
        )

        let thisWeekSessions = sessionsClippedToInterval(
            sessions: sessions,
            interval: weekInterval
        )

        guard !thisWeekSessions.isEmpty else { return nil }

        let previousWeekSessions = sessionsClippedToInterval(
            sessions: sessions,
            interval: previousWeekInterval
        )

        let currentScore = weeklySignalScore(
            sessions: thisWeekSessions,
            journals: journals,
            weekStart: weekInterval.start,
            weekEnd: weekInterval.end,
            calendar: calendar
        )

        let previousScore = weeklySignalScore(
            sessions: previousWeekSessions,
            journals: journals,
            weekStart: previousWeekInterval.start,
            weekEnd: previousWeekInterval.end,
            calendar: calendar
        )

        let signalChangePercent = calculateSignalChange(
            currentScore: currentScore,
            previousScore: previousScore
        )

        // Protected Attention slide:
        // Count all valid focus sessions that fall inside this week.
        let contributingSessions = thisWeekSessions.filter {
            $0.duration > 0
        }

        let deepestSession = thisWeekSessions.max(by: {
            $0.duration < $1.duration
        })

        let mostFocusedDay = calculateMostFocusedDay(
            sessions: thisWeekSessions,
            calendar: calendar
        )

        let pattern = generatePatternInsight(
            sessions: thisWeekSessions,
            calendar: calendar
        )

        let hourlySignalDurations = calculateHourlySignalDurations(
            sessions: thisWeekSessions,
            calendar: calendar
        )

        let archetype = calculatePrimaryArchetype(
            sessions: sessions,
            journals: journals,
            referenceDate: adjustedReferenceDate,
            calendar: calendar
        )

        return NoiseRewindWeeklyInsight(
            weekStart: weekInterval.start,
            weekEnd: weekInterval.end,

            signalChangePercent: signalChangePercent,
            weeklySignalTitle: weeklyTitle(for: signalChangePercent),
            weeklySignalSubtitle: weeklySubtitle(for: signalChangePercent),

            protectedSessionsCount: contributingSessions.count,
            totalProtectedDuration: contributingSessions.reduce(0) { $0 + $1.duration },
            protectedMomentsText: protectedMomentsText(count: contributingSessions.count),

            deepestSessionDate: deepestSession?.startDate,
            deepestSessionDuration: deepestSession?.duration ?? 0,
            deepestSessionModeName: deepestSession?.modeName,

            mostFocusedDayDate: mostFocusedDay.date,
            mostFocusedDayDuration: mostFocusedDay.duration,

            patternTitle: pattern.title,
            patternSubtitle: pattern.subtitle,
            hourlySignalDurations: hourlySignalDurations,

            primaryArchetype: archetype,
            primaryArchetypeTitle: archetypeTitle(for: archetype),
            primaryArchetypeSubtitle: archetypeSubtitle(for: archetype),

            reflectionPrompt: "What helped you protect your attention most this week?"
        )
    }

    static func shouldShowNoiseRewind(
        sessions: [Session],
        referenceDate: Date = Date(),
        calendar: Calendar = .current,
        lastSeenWeekStart: Date?,
        isSessionActive: Bool
    ) -> Bool {

        if isSessionActive { return false }

        var cal = calendar
        cal.firstWeekday = 1 // Sunday

        let weekday = cal.component(.weekday, from: referenceDate)
        let hour = cal.component(.hour, from: referenceDate)

        // Noise Rewind appears Sunday after 9AM.
        guard weekday == 1, hour >= 9 else {
            return false
        }

        let currentWeek = currentWeekInterval(for: referenceDate, calendar: cal)
        let previousWeek = previousWeekInterval(from: currentWeek, calendar: cal)

        if let lastSeen = lastSeenWeekStart,
           cal.isDate(lastSeen, inSameDayAs: previousWeek.start) {
            return false
        }

        let previousWeekSessions = sessionsClippedToInterval(
            sessions: sessions,
            interval: previousWeek
        )

        let contributingSessions = previousWeekSessions.filter {
            $0.duration > 0
        }

        let totalProtectedDuration = contributingSessions.reduce(0.0) {
            $0 + $1.duration
        }

        return contributingSessions.count >= 2 || totalProtectedDuration >= 60 * 60
    }

    // MARK: - Week Helpers

    private static func currentWeekInterval(
        for date: Date,
        calendar: Calendar
    ) -> DateInterval {
        var cal = calendar
        cal.firstWeekday = 1 // Sunday

        let startOfDay = cal.startOfDay(for: date)
        let weekday = cal.component(.weekday, from: startOfDay)

        let daysSinceSunday = weekday - 1

        let start = cal.date(
            byAdding: .day,
            value: -daysSinceSunday,
            to: startOfDay
        )!

        let end = cal.date(
            byAdding: .day,
            value: 7,
            to: start
        )!

        return DateInterval(start: start, end: end)
    }

    private static func previousWeekInterval(
        from current: DateInterval,
        calendar: Calendar
    ) -> DateInterval {
        let start = calendar.date(
            byAdding: .day,
            value: -7,
            to: current.start
        )!

        let end = current.start

        return DateInterval(start: start, end: end)
    }

    static func rewindReferenceDateForDisplay(
        referenceDate: Date = Date(),
        calendar: Calendar = .current
    ) -> Date {
        var cal = calendar
        cal.firstWeekday = 1 // Sunday

        let weekday = cal.component(.weekday, from: referenceDate)

        // On Sunday, show the previous completed week.
        if weekday == 1 {
            return cal.date(
                byAdding: .day,
                value: -1,
                to: referenceDate
            ) ?? referenceDate
        }

        return referenceDate
    }

    // MARK: - Session Clipping

    private static func sessionsClippedToInterval(
        sessions: [Session],
        interval: DateInterval
    ) -> [Session] {

        return sessions.compactMap { session in
            let rawDuration = max(0, session.duration)

            // Ignore broken/corrupted sessions.
            // 16h allows long real focus days but blocks obviously broken sessions.
            guard rawDuration > 0,
                  rawDuration <= maxReasonableSessionDuration
            else {
                return nil
            }

            let sessionStart = session.startDate
            let sessionEnd = session.startDate.addingTimeInterval(rawDuration)

            let overlapStart = max(sessionStart, interval.start)
            let overlapEnd = min(sessionEnd, interval.end)

            let overlapDuration = overlapEnd.timeIntervalSince(overlapStart)

            guard overlapDuration > 0 else {
                return nil
            }

            return Session(
                id: session.id,
                startDate: overlapStart,
                duration: overlapDuration,
                modeName: session.modeName,
                transcription: session.transcription,
                audioFileURL: session.audioFileURL
            )
        }
    }

    // MARK: - Most Focused Day

    private static func calculateMostFocusedDay(
        sessions: [Session],
        calendar: Calendar
    ) -> (date: Date?, duration: TimeInterval) {

        let validSessions = sessions.filter {
            $0.duration > 0
        }

        guard !validSessions.isEmpty else {
            return (nil, 0)
        }

        var totalsByDay: [Date: TimeInterval] = [:]

        for session in validSessions {
            let sessionStart = session.startDate
            let sessionEnd = session.startDate.addingTimeInterval(session.duration)

            var cursor = sessionStart

            while cursor < sessionEnd {
                let dayStart = calendar.startOfDay(for: cursor)
                let nextDayStart = calendar.date(
                    byAdding: .day,
                    value: 1,
                    to: dayStart
                )!

                let sliceEnd = min(sessionEnd, nextDayStart)
                let sliceDuration = sliceEnd.timeIntervalSince(cursor)

                if sliceDuration > 0 {
                    totalsByDay[dayStart, default: 0] += sliceDuration
                }

                cursor = sliceEnd
            }
        }

        guard let bestDay = totalsByDay.max(by: { $0.value < $1.value }) else {
            return (nil, 0)
        }

        return (bestDay.key, bestDay.value)
    }

    // MARK: - Hourly Signal Curve

    private static func calculateHourlySignalDurations(
        sessions: [Session],
        calendar: Calendar
    ) -> [TimeInterval] {

        var hourlyDurations = Array(repeating: TimeInterval(0), count: 24)

        let validSessions = sessions.filter {
            $0.duration > 0
        }

        for session in validSessions {
            let sessionStart = session.startDate
            let sessionEnd = session.startDate.addingTimeInterval(session.duration)

            var cursor = sessionStart

            while cursor < sessionEnd {
                let hour = calendar.component(.hour, from: cursor)

                let hourStart = calendar.date(
                    bySettingHour: hour,
                    minute: 0,
                    second: 0,
                    of: cursor
                ) ?? cursor

                let nextHour = calendar.date(
                    byAdding: .hour,
                    value: 1,
                    to: hourStart
                ) ?? sessionEnd

                let sliceEnd = min(sessionEnd, nextHour)
                let sliceDuration = max(0, sliceEnd.timeIntervalSince(cursor))

                if hour >= 0 && hour < 24 {
                    hourlyDurations[hour] += sliceDuration
                }

                cursor = sliceEnd
            }
        }

        return hourlyDurations
    }

    // MARK: - Weekly Signal

    private static func weeklySignalScore(
        sessions: [Session],
        journals: [JournalEntry],
        weekStart: Date,
        weekEnd: Date,
        calendar: Calendar
    ) -> Double {

        guard !sessions.isEmpty else { return 0 }

        let totalFocus = sessions.reduce(0.0) { $0 + $1.duration }
        let longestSession = sessions.map(\.duration).max() ?? 0

        let activeDays = Set(
            sessions.map { calendar.startOfDay(for: $0.startDate) }
        ).count

        let protectedSessions = sessions.filter {
            $0.duration >= protectedSessionThreshold
        }.count

        let shortSessions = sessions.filter {
            $0.duration >= 5 * 60 &&
            $0.duration < 15 * 60
        }.count

        let hasReflection = journals.contains {
            $0.createdAt >= weekStart &&
            $0.createdAt < weekEnd &&
            $0.hasContent
        }

        let depthScore = min(longestSession / (120 * 60), 1.0)
        let rhythmScore = min(Double(activeDays) / 5.0, 1.0)
        let volumeScore = min(totalFocus / (10 * 60 * 60), 1.0)
        let protectedScore = min(Double(protectedSessions) / 4.0, 1.0)
        let reflectionBoost = hasReflection ? 0.05 : 0.0
        let fragmentationPenalty = shortSessions >= 5 ? 0.10 : 0.0

        let score =
            depthScore * 0.35 +
            rhythmScore * 0.30 +
            volumeScore * 0.15 +
            protectedScore * 0.15 +
            reflectionBoost -
            fragmentationPenalty

        return max(0, min(score, 1.0))
    }

    private static func calculateSignalChange(
        currentScore: Double,
        previousScore: Double
    ) -> Int {
        if previousScore <= 0 {
            return Int((currentScore * 100).rounded())
        }

        let change = ((currentScore - previousScore) / previousScore) * 100
        return Int(change.rounded())
    }

    private static func weeklyTitle(for change: Int) -> String {
        if change > 5 {
            return "Your signal strengthened."
        } else if change < -5 {
            return "Your signal weakened."
        } else {
            return "Your signal stayed steady."
        }
    }

    private static func weeklySubtitle(for change: Int) -> String {
        if change > 5 {
            return "Your rhythm became more stable this week."
        } else if change < -5 {
            return "Fragmented sessions weakened your attention rhythm."
        } else {
            return "Your attention stayed consistent this week."
        }
    }

    private static func protectedMomentsText(count: Int) -> String {
        switch count {
        case 0:
            return "No sessions contributed to your protected attention this week."
        case 1:
            return "1 session contributed to your protected attention this week."
        default:
            return "\(count) sessions contributed to your protected attention this week."
        }
    }

    // MARK: - Pattern Insight

    static func generatePatternInsight(
        sessions: [Session],
        calendar: Calendar
    ) -> (title: String, subtitle: String) {

        let hourlyDurations = calculateHourlySignalDurations(
            sessions: sessions,
            calendar: calendar
        )

        let totalSignal = hourlyDurations.reduce(0, +)

        guard totalSignal > 0 else {
            return (
                "A pattern is starting to form.",
                "Complete a few more focus sessions to reveal your strongest signal window."
            )
        }

        var bestStartHour = 0
        var bestDuration: TimeInterval = 0

        for hour in stride(from: 0, to: 24, by: 2) {
            let duration = hourlyDurations[hour] + hourlyDurations[hour + 1]

            if duration > bestDuration {
                bestDuration = duration
                bestStartHour = hour
            }
        }

        let endHour = (bestStartHour + 2) % 24
        let rangeText = "\(formatHour(bestStartHour))–\(formatHour(endHour))"

        return (
            "Your signal peaked around \(rangeText).",
            "This window carried your strongest signal this week. Protect it next week."
        )
    }

    static func formatHour(_ hour: Int) -> String {
        let normalizedHour = ((hour % 24) + 24) % 24
        return String(format: "%02d:00", normalizedHour)
    }

    // MARK: - Primary Archetype

    private static func calculatePrimaryArchetype(
        sessions: [Session],
        journals: [JournalEntry],
        referenceDate: Date,
        calendar: Calendar
    ) -> SignalAttentionState {

        let start = calendar.date(
            byAdding: .day,
            value: -90,
            to: referenceDate
        )!

        let recentRawSessions = sessions.filter {
            $0.startDate >= start &&
            $0.startDate <= referenceDate
        }

        let recentSessions = recentRawSessions.filter {
            $0.duration > 0 &&
            $0.duration <= maxReasonableSessionDuration
        }

        let recentJournals = journals.filter {
            $0.createdAt >= start &&
            $0.createdAt <= referenceDate
        }

        guard !recentSessions.isEmpty else {
            return .drift
        }

        let totalFocus = recentSessions.reduce(0.0) {
            $0 + $1.duration
        }

        let activeDays = Set(
            recentSessions.map { calendar.startOfDay(for: $0.startDate) }
        ).count

        let protectedSessions = recentSessions.filter {
            $0.duration >= protectedSessionThreshold
        }.count

        let deepSessions = recentSessions.filter {
            $0.duration >= 60 * 60
        }.count

        let shortSessions = recentSessions.filter {
            $0.duration >= 5 * 60 &&
            $0.duration < 15 * 60
        }.count

        let hasReflection = recentJournals.contains {
            $0.hasContent
        }

        let depth = min(Double(deepSessions) / 12.0, 1.0)
        let rhythm = min(Double(activeDays) / 30.0, 1.0)
        let protection = min(Double(protectedSessions) / 30.0, 1.0)
        let volume = min(totalFocus / (60 * 60 * 60), 1.0)

        let reflection = hasReflection ? 0.08 : 0.0
        let fragmentation = shortSessions >= 20 ? 0.15 : 0.0

        let score =
            depth * 0.30 +
            rhythm * 0.30 +
            protection * 0.25 +
            volume * 0.15 +
            reflection -
            fragmentation

        if shortSessions >= 25 && protectedSessions < 8 {
            return .drift
        } else if score < 0.33 {
            return .drift
        } else if score < 0.66 {
            return .builder
        } else {
            return .guardian
        }
    }

    private static func archetypeTitle(for state: SignalAttentionState) -> String {
        switch state {
        case .drift:
            return "The Wanderer"
        case .builder:
            return "The Builder"
        case .guardian:
            return "The Hermit"
        }
    }

    private static func archetypeSubtitle(for state: SignalAttentionState) -> String {
        switch state {
        case .drift:
            return "The Wanderer feels pulled in many directions. Distractions, scrolling, and constant stimulation make it hard to stay focused on what truly matters. Their attention feels scattered, and they often feel mentally overwhelmed or disconnected."

        case .builder:
            return "The Creator is starting to take back control of their attention and energy. They’re building routines, creating more intentionally, and turning ideas into action. They still struggle with noise sometimes, but they’re building momentum and direction."

        case .guardian:
            return "The Hermit protects their focus, energy, and peace intentionally. They’ve built strong boundaries with distractions and are more selective about what they allow into their mind and environment. They value clarity, depth, and time away from unnecessary noise."
        }
    }
}
