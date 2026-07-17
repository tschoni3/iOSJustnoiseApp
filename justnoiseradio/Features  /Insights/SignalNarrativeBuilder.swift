import Foundation

enum SignalNarrativeBuilder {
    static func build(
        recentCaptures: [SignalCaptureContextPayload],
        threads: [SignalMemoryThreadPayload],
        existingSignals: [SignalInsightContextPayload]
    ) -> String? {
        var parts: [String] = []

        let recurringLabels = threads
            .filter { $0.occurrenceCount >= 2 }
            .prefix(2)
            .map(\.label)

        if recurringLabels.isEmpty == false {
            parts.append("Recurring topics: \(recurringLabels.joined(separator: ", ")).")
        }

        if let strongestThread = threads
            .compactMap({ thread -> (String, Double)? in
                guard let intensity = thread.intensity else { return nil }
                return (thread.label, intensity)
            })
            .max(by: { $0.1 < $1.1 })?.0 {
            parts.append("Strongest thread: \(strongestThread).")
        }

        if let latestCapture = recentCaptures.last {
            let recentFocus = latestCapture.themes.first ?? shortLabel(from: latestCapture.summary)
            parts.append("Recent focus: \(recentFocus).")
        }

        if let latestSignal = existingSignals.first {
            parts.append("Latest signal type: \(latestSignal.type).")
        }

        let summary = parts.joined(separator: " ")
        return summary.isEmpty ? nil : summary
    }

    private static func shortLabel(from summary: String) -> String {
        let trimmed = summary
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.isEmpty == false }
            .joined(separator: " ")

        guard trimmed.count > 44 else { return trimmed }
        return String(trimmed.prefix(44)).trimmingCharacters(in: .whitespacesAndNewlines) + "..."
    }
}
