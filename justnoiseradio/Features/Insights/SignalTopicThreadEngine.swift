import Foundation

enum SignalTopicThreadEngine {
    private static let threadLimit = 12
    private static let evidenceQuoteLimit = 3

    static func build(
        from captures: [SignalCaptureContextPayload],
        limit: Int = threadLimit
    ) -> [SignalMemoryThreadPayload] {
        typealias ThreadAccumulator = (
            label: String,
            topicKey: String,
            category: String?,
            themes: [String],
            occurrenceCount: Int,
            intensitySum: Double,
            intensityCount: Int,
            firstSeen: String?,
            lastSeen: String?,
            captureIds: [String],
            evidenceQuotes: [SignalMemoryEvidenceQuotePayload]
        )

        var buckets: [String: ThreadAccumulator] = [:]

        for capture in captures {
            let topicSeed = capture.themes.first ?? capture.summary
            let topicKey = normalizeKey(topicSeed, limit: 80)

            guard topicKey.isEmpty == false else { continue }

            let label = capture.themes.first?.capitalized ?? shortLabel(from: capture.summary)
            let category = capture.themes.first.map { normalizeKey($0, limit: 64) }

            var bucket = buckets[topicKey] ?? (
                label: label,
                topicKey: topicKey,
                category: category,
                themes: [],
                occurrenceCount: 0,
                intensitySum: 0,
                intensityCount: 0,
                firstSeen: nil,
                lastSeen: nil,
                captureIds: [],
                evidenceQuotes: []
            )

            bucket.occurrenceCount += 1

            for theme in capture.themes where bucket.themes.contains(theme) == false {
                bucket.themes.append(theme)
            }

            if let signalStrength = capture.signalStrength {
                bucket.intensitySum += signalStrength
                bucket.intensityCount += 1
            }

            bucket.firstSeen = minDateString(bucket.firstSeen, capture.created_at)
            bucket.lastSeen = maxDateString(bucket.lastSeen, capture.created_at)

            if bucket.captureIds.contains(capture.capture_id) == false {
                bucket.captureIds.append(capture.capture_id)
            }

            if let quote = capture.strongQuote ?? capture.highlight,
               bucket.evidenceQuotes.count < evidenceQuoteLimit {
                bucket.evidenceQuotes.append(
                    SignalMemoryEvidenceQuotePayload(
                        captureId: capture.capture_id,
                        quote: quote
                    )
                )
            }

            buckets[topicKey] = bucket
        }

        return buckets.values
            .sorted { lhs, rhs in
                if lhs.occurrenceCount != rhs.occurrenceCount {
                    return lhs.occurrenceCount > rhs.occurrenceCount
                }

                return lhs.topicKey < rhs.topicKey
            }
            .prefix(limit)
            .map { bucket in
                let averageIntensity: Double?
                if bucket.intensityCount > 0 {
                    averageIntensity = bucket.intensitySum / Double(bucket.intensityCount)
                } else {
                    averageIntensity = nil
                }

                return SignalMemoryThreadPayload(
                    id: bucket.topicKey,
                    label: bucket.label,
                    topicKey: bucket.topicKey,
                    category: bucket.category,
                    themes: bucket.themes,
                    occurrenceCount: bucket.occurrenceCount,
                    intensity: averageIntensity,
                    intensityTrend: nil,
                    firstSeen: bucket.firstSeen,
                    lastSeen: bucket.lastSeen,
                    captureIds: bucket.captureIds,
                    evidenceQuotes: bucket.evidenceQuotes
                )
            }
    }

    private static func cleaned(_ text: String) -> String {
        text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.isEmpty == false }
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func normalizeKey(_ text: String, limit: Int) -> String {
        let normalized = cleaned(text)
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { $0.isEmpty == false }
            .joined(separator: " ")

        guard normalized.isEmpty == false else { return "" }

        if normalized.count <= limit {
            return normalized
        }

        return String(normalized.prefix(limit)).trimmingCharacters(in: .whitespaces)
    }

    private static func shortLabel(from summary: String) -> String {
        let trimmed = cleaned(summary)
        guard trimmed.count > 44 else { return trimmed }
        return String(trimmed.prefix(44)).trimmingCharacters(in: .whitespacesAndNewlines) + "..."
    }

    private static func minDateString(_ lhs: String?, _ rhs: String?) -> String? {
        switch (lhs, rhs) {
        case let (left?, right?):
            return min(left, right)
        case let (left?, nil):
            return left
        case let (nil, right?):
            return right
        case (nil, nil):
            return nil
        }
    }

    private static func maxDateString(_ lhs: String?, _ rhs: String?) -> String? {
        switch (lhs, rhs) {
        case let (left?, right?):
            return max(left, right)
        case let (left?, nil):
            return left
        case let (nil, right?):
            return right
        case (nil, nil):
            return nil
        }
    }
}
