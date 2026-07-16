// SignalNoiseScorer.swift
// Lightweight logic for Signal-to-Noise scoring in JustNoise

import Foundation

/// The current dominant signal pattern produced by the scoring system.
/// Not a personality, rank, or permanent identity.
enum SignalAttentionState: String, Codable, Equatable {
    case drift
    case builder
    case guardian
}

struct SignalNoiseScoreResult: Equatable {
    let depthScore: Double
    let rhythmScore: Double
    let fragmentationPenalty: Double
    let reflectionBoost: Double
    let decayPenalty: Double
    let finalScore: Double
    let state: SignalAttentionState

    // For debugging or UI display
    let depthLabel: String
    let rhythmLabel: String
    let fragmentationLabel: String
    let reflectionBoosted: Bool
}

struct SignalNoiseScorer {

    // MARK: - Configuration

    static var minValidSessionSeconds: TimeInterval = 5 * 60
    static var shortSessionMaxSeconds: TimeInterval = 15 * 60

    static var depthThresholds: [(TimeInterval, SignalAttentionState, String)] = [
        (25 * 60, .builder, "Builder Patterns"),
        (60 * 60, .guardian, "Guardian Patterns")
    ]

    static var fragmentationSessionCount: Int = 5
    static var reflectionBoostAmount: Double = 0.05
    static var fragmentationPenaltyAmount: Double = 0.08

    // MARK: - Core Scoring API

    static func computeSignalNoise(
        sessions: [Session],
        reflections: [JournalEntry],
        targetDay: Date,
        calendar: Calendar = .current
    ) -> SignalNoiseScoreResult {

        let dayStart = calendar.startOfDay(for: targetDay)
        let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart)!

        // 1. Today's valid sessions
        let validSessions = sessions.filter {
            let end = $0.startDate.addingTimeInterval($0.duration)
            return end > dayStart &&
                   $0.startDate < dayEnd &&
                   $0.duration >= minValidSessionSeconds
        }

        let shortSessions = sessions.filter {
            let end = $0.startDate.addingTimeInterval($0.duration)
            return end > dayStart &&
                   $0.startDate < dayEnd &&
                   $0.duration >= minValidSessionSeconds &&
                   $0.duration < shortSessionMaxSeconds
        }

        // 2. Depth Score: longest valid session today
        let longestSession = validSessions.max(by: { $0.duration < $1.duration })
        let longestDuration = longestSession?.duration ?? 0

        var depthScore = 0.0
        var depthLabel = "Drift Patterns"

        for (threshold, _, label) in depthThresholds {
            if longestDuration >= threshold {
                depthScore = Double(threshold) / Double(120 * 60)
                depthLabel = label
            }
        }

        if longestDuration < depthThresholds[0].0 {
            depthScore = 0.0
            depthLabel = "Drift Patterns"
        }

        // 3. Rhythm Score: weighted active days in last 90 days
        let lookbackDays = 90

        var weightedFocusScore = 0.0
        var maxPossibleWeight = 0.0

        for offset in 0..<lookbackDays {
            let day = calendar.date(byAdding: .day, value: -offset, to: dayStart)!
            let start = calendar.startOfDay(for: day)
            let end = calendar.date(byAdding: .day, value: 1, to: start)!

            let weight: Double
            switch offset {
            case 0..<7:
                weight = 1.0
            case 7..<30:
                weight = 0.6
            default:
                weight = 0.25
            }

            maxPossibleWeight += weight

            let hasFocusSession = sessions.contains {
                let sessionEnd = $0.startDate.addingTimeInterval($0.duration)
                return sessionEnd > start &&
                       $0.startDate < end &&
                       $0.duration >= minValidSessionSeconds
            }

            if hasFocusSession {
                weightedFocusScore += weight
            }
        }

        let rhythmRatio = maxPossibleWeight > 0 ? weightedFocusScore / maxPossibleWeight : 0

        var rhythmScore = 0.0
        var rhythmLabel = "Low Rhythm"

        if rhythmRatio >= 0.35 {
            rhythmScore = 1.0
            rhythmLabel = "Strong Rhythm"
        } else if rhythmRatio >= 0.18 {
            rhythmScore = 0.6
            rhythmLabel = "Medium Rhythm"
        } else if rhythmRatio > 0 {
            rhythmScore = 0.3
            rhythmLabel = "Low Rhythm"
        }

        // 4. Decay: signal fades without recent meaningful sessions
        let lastMeaningfulSession = sessions
            .filter { $0.duration >= minValidSessionSeconds }
            .max(by: { $0.startDate < $1.startDate })

        let daysSinceLastSession: Int = {
            guard let last = lastMeaningfulSession else { return 90 }
            return calendar.dateComponents(
                [.day],
                from: calendar.startOfDay(for: last.startDate),
                to: dayStart
            ).day ?? 90
        }()

        let decayPenalty = min(Double(max(daysSinceLastSession - 3, 0)) * 0.015, 0.35)

        // 5. Fragmentation: too many short sessions today
        let fragmented = shortSessions.count >= fragmentationSessionCount
        let fragmentationPenalty = fragmented ? fragmentationPenaltyAmount : 0.0
        let fragmentationLabel = fragmented ? "Drift Patterns" : "Stable Signal"

        // 6. Reflection booster: optional, never required
        let hadReflection = reflections.contains { entry in
            entry.createdAt >= dayStart &&
            entry.createdAt < dayEnd &&
            entry.hasContent
        }

        let reflectionBoost = hadReflection ? reflectionBoostAmount : 0.0
        let reflectionBoosted = hadReflection

        // 7. Final Score
        var finalScore =
            depthScore * 0.50 +
            rhythmScore * 0.30 +
            reflectionBoost * 0.15 -
            fragmentationPenalty -
            decayPenalty

        finalScore = max(0.0, min(finalScore, 1.2))

        // 8. Current Signal Pattern Mapping
        let state: SignalAttentionState

        if fragmented || finalScore < 0.33 {
            state = .drift
        } else if finalScore < 0.66 {
            state = .builder
        } else {
            state = .guardian
        }

        return SignalNoiseScoreResult(
            depthScore: depthScore,
            rhythmScore: rhythmScore,
            fragmentationPenalty: fragmentationPenalty,
            reflectionBoost: reflectionBoost,
            decayPenalty: decayPenalty,
            finalScore: finalScore,
            state: state,
            depthLabel: depthLabel,
            rhythmLabel: rhythmLabel,
            fragmentationLabel: fragmentationLabel,
            reflectionBoosted: reflectionBoosted
        )
    }
}
