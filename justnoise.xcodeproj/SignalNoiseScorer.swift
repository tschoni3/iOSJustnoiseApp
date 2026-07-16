// SignalNoiseScorer.swift
// Lightweight logic for Signal-to-Noise scoring in JustNoise
import Foundation

/// The emotional/attention states produced by the scoring system.
enum SignalAttentionState: String, Codable, Equatable {
    case noiseHeavy
    case signalStarting
    case signalBuilding
    case signalStrong
    case deepClarity
    case fragmentedAttention
}

struct SignalNoiseScoreResult: Equatable {
    let depthScore: Double
    let rhythmScore: Double
    let fragmentationPenalty: Double
    let reflectionBoost: Double
    let finalScore: Double
    let state: SignalAttentionState
    // For debugging or UI display
    let depthLabel: String
    let rhythmLabel: String
    let fragmentationLabel: String
    let reflectionBoosted: Bool
}

struct SignalNoiseScorer {
    // MARK: - Configuration (tunable thresholds)
    static var minValidSessionSeconds: TimeInterval = 5 * 60 // 5 min
    static var shortSessionMaxSeconds: TimeInterval = 15 * 60 // 15 min
    static var depthThresholds: [(TimeInterval, SignalAttentionState, String)] = [
        (5*60, .signalStarting, "Signal Starting"),
        (25*60, .signalBuilding, "Signal Building"),
        (60*60, .signalStrong, "Signal Strong"),
        (120*60, .deepClarity, "Deep Clarity")
    ]
    static var rhythmThresholds: [(Int, String)] = [
        (1, "Low Rhythm"),
        (2, "Medium Rhythm"),
        (3, "Strong Rhythm")
    ]
    static var fragmentationSessionCount: Int = 5 // 5+ short sessions
    static var reflectionBoostAmount: Double = 0.05
    static var fragmentationPenaltyAmount: Double = 0.08

    // MARK: - Core Scoring API
    /// Computes daily signal-to-noise for a single target date
    static func computeSignalNoise(
        sessions: [Session],
        reflections: [JournalEntry],
        targetDay: Date,
        calendar: Calendar = .current
    ) -> SignalNoiseScoreResult {
        let dayStart = calendar.startOfDay(for: targetDay)
        let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart)!

        // 1. Filter for valid sessions
        let validSessions = sessions.filter {
            let end = $0.startDate.addingTimeInterval($0.duration)
            return end > dayStart && $0.startDate < dayEnd && $0.duration >= minValidSessionSeconds
        }
        let shortSessions = sessions.filter {
            let end = $0.startDate.addingTimeInterval($0.duration)
            return end > dayStart && $0.startDate < dayEnd &&
                   $0.duration >= minValidSessionSeconds && $0.duration < shortSessionMaxSeconds
        }

        // 2. Depth Score: Use the longest valid session
        let longestSession = validSessions.max(by: { $0.duration < $1.duration })
        let longestDuration = longestSession?.duration ?? 0
        var depthScore = 0.0
        var depthState: SignalAttentionState = .noiseHeavy
        var depthLabel = "Noise Heavy"
        for (threshold, state, label) in depthThresholds {
            if longestDuration >= threshold {
                depthScore = Double(threshold) / (120*60) // normalize by 2h
                depthState = state
                depthLabel = label
            }
        }
        if longestDuration < depthThresholds[0].0 {
            depthScore = 0.0
            depthState = .noiseHeavy
            depthLabel = "Noise Heavy"
        }

        // 3. Rhythm Score: count active days in past 7 days
        let days = (0..<7).map { offset in
            calendar.date(byAdding: .day, value: -offset, to: dayStart)!
        }
        let focusDays: Int = days.reduce(0) { sum, day in
            let ds = sessions.filter {
                let end = $0.startDate.addingTimeInterval($0.duration)
                return end > calendar.startOfDay(for: day) && $0.startDate < calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: day))! && $0.duration >= minValidSessionSeconds
            }
            return sum + (ds.isEmpty ? 0 : 1)
        }
        var rhythmScore = 0.0
        var rhythmLabel = rhythmThresholds[0].1
        if focusDays >= rhythmThresholds[2].0 { rhythmScore = 1.0; rhythmLabel = rhythmThresholds[2].1 }
        else if focusDays >= rhythmThresholds[1].0 { rhythmScore = 0.6; rhythmLabel = rhythmThresholds[1].1 }
        else if focusDays >= rhythmThresholds[0].0 { rhythmScore = 0.3; rhythmLabel = rhythmThresholds[0].1 }
        else { rhythmScore = 0.0 }

        // 4. Fragmentation
        let fragmented = shortSessions.count >= fragmentationSessionCount
        let fragmentationPenalty = fragmented ? fragmentationPenaltyAmount : 0.0
        let fragmentationLabel = fragmented ? "Fragmented Attention" : "No Fragmentation"

        // 5. Reflection booster
        let hadReflection = reflections.contains { je in
            je.createdAt >= dayStart && je.createdAt < dayEnd && je.hasContent
        }
        let reflectionBoost = hadReflection ? reflectionBoostAmount : 0.0
        let reflectionBoosted = hadReflection

        // 6. Final Score: weighted sum
        var finalScore = depthScore * 0.55 + rhythmScore * 0.35 + reflectionBoost * 0.15 - fragmentationPenalty
        finalScore = max(0.0, min(finalScore, 1.2)) // Clamp >1 for deep clarity

        // 7. State mapping
        let state: SignalAttentionState
        if fragmented {
            state = .fragmentedAttention
        } else if finalScore < 0.12 { state = .noiseHeavy }
        else if finalScore < 0.22 { state = .signalStarting }
        else if finalScore < 0.45 { state = .signalBuilding }
        else if finalScore < 0.7 { state = .signalStrong }
        else { state = .deepClarity }

        return SignalNoiseScoreResult(
            depthScore: depthScore,
            rhythmScore: rhythmScore,
            fragmentationPenalty: fragmentationPenalty,
            reflectionBoost: reflectionBoost,
            finalScore: finalScore,
            state: state,
            depthLabel: depthLabel,
            rhythmLabel: rhythmLabel,
            fragmentationLabel: fragmentationLabel,
            reflectionBoosted: reflectionBoosted
        )
    }
}

// MARK: - Expecting Session and JournalEntry types to be defined elsewhere in the app.
// This struct is plug-and-play and expects those types to be available.

