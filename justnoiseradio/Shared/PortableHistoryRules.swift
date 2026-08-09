import Foundation

enum PortableHistoryDensity: String, Codable, CaseIterable {
    case empty
    case light
    case medium
    case dense
}

enum PortableHistoryDurationStyle: String, Codable, CaseIterable {
    case summary
    case dayDetail
}

struct PortableHistorySummary: Equatable {
    let zapCount: Int
    let totalSeconds: TimeInterval
    let protectedAttentionLabel: String
}

enum PortableHistoryRules {
    static let lightMinimumSeconds: TimeInterval = 15 * 60
    static let mediumMinimumSeconds: TimeInterval = 120 * 60
    static let denseMinimumSeconds: TimeInterval = 360 * 60

    static func density(totalSeconds: TimeInterval) -> PortableHistoryDensity {
        let seconds = normalizedDuration(totalSeconds)

        if seconds >= denseMinimumSeconds { return .dense }
        if seconds >= mediumMinimumSeconds { return .medium }
        if seconds >= lightMinimumSeconds { return .light }
        return .empty
    }

    static func formatDuration(
        totalSeconds: TimeInterval,
        style: PortableHistoryDurationStyle
    ) -> String {
        let seconds = normalizedDuration(totalSeconds)
        let totalMinutes = Int(seconds) / 60
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60

        if hours > 0 {
            return minutes > 0 ? "\(hours)h \(minutes)m" : "\(hours)h"
        }
        if style == .dayDetail, seconds > 0, totalMinutes == 0 {
            return "<1m"
        }
        return "\(minutes)m"
    }

    static func summarize<Durations: Sequence>(
        durationsSeconds: Durations
    ) -> PortableHistorySummary where Durations.Element == TimeInterval {
        var zapCount = 0
        var totalSeconds: TimeInterval = 0
        for duration in durationsSeconds {
            zapCount += 1
            totalSeconds += normalizedDuration(duration)
        }
        return PortableHistorySummary(
            zapCount: zapCount,
            totalSeconds: totalSeconds,
            protectedAttentionLabel: formatDuration(
                totalSeconds: totalSeconds,
                style: .summary
            )
        )
    }

    private static func normalizedDuration(_ duration: TimeInterval) -> TimeInterval {
        guard duration.isFinite, duration > 0 else { return 0 }
        return duration
    }
}
