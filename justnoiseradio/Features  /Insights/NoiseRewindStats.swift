//
//  NoiseRewindStats.swift
//  justnoise
//
import Foundation

struct NoiseRewindStats {
    let monthName: String
    let hoursFocused: Int
    let sessions: Int
    let bestStreakDays: Int
    let dominantMood: String
    let topWord: String
    let topics: [String]
    let percentile: Int? // optional, server-driven later
}

extension NFCViewModel {

    func rewindStats(for month: Date = Date()) -> NoiseRewindStats? {
        let cal = Calendar.current
        let comps = cal.dateComponents([.year, .month], from: month)
        guard
            let start = cal.date(from: comps),
            let end   = cal.date(byAdding: .month, value: 1, to: start)
        else { return nil }

        let monthSessions = sessionHistory.filter { $0.startDate >= start && $0.startDate < end }
        guard !monthSessions.isEmpty else { return nil }

        // Hours & sessions
        let totalSeconds = monthSessions.reduce(0.0) { $0 + $1.duration }
        let hours = Int((totalSeconds / 3600.0).rounded())

        // Best streak
        let uniqueDays = Set(monthSessions.map { cal.startOfDay(for: $0.startDate) }).sorted()
        var best = 1, run = 1
        for i in 1..<uniqueDays.count {
            if cal.isDate(uniqueDays[i], inSameDayAs: cal.date(byAdding: .day, value: 1, to: uniqueDays[i-1])!) {
                run += 1; best = max(best, run)
            } else { run = 1 }
        }

        // Mood + keywords
        let moods = monthSessions.compactMap { $0.transcription?.sentiment }
        let dominantMood = mostCommon(in: moods) ?? "Focused"

        let allWords = monthSessions
            .compactMap { $0.transcription?.overview }
            .joined(separator: " ")
            .lowercased()
            .split{ !"abcdefghijklmnopqrstuvwxyz".contains($0) }
            .map(String.init)
            .filter { $0.count > 3 }

        let topWord = mostCommon(in: allWords) ?? "Clarity"

        let topics = monthSessions
            .compactMap { $0.modeName }
            .reduce(into: [:]) { $0[$1, default: 0] += 1 }
            .sorted { $0.value > $1.value }
            .prefix(5)
            .map { $0.key }

        let monthName = DateFormatter().then {
            $0.dateFormat = "LLLL"
        }.string(from: start)

        return NoiseRewindStats(
            monthName: monthName.capitalized,
            hoursFocused: hours,
            sessions: monthSessions.count,
            bestStreakDays: best,
            dominantMood: dominantMood.capitalized,
            topWord: topWord.capitalized,
            topics: topics.isEmpty ? ["Work","Gym","Family"] : topics,
            percentile: nil
        )
    }
}

private func mostCommon<T: Hashable>(in arr: [T]) -> T? {
    guard !arr.isEmpty else { return nil }
    return arr.reduce(into: [:]) { $0[$1, default: 0] += 1 }
        .max(by: { $0.value < $1.value })?.key
}

private extension DateFormatter {
    func then(_ block: (DateFormatter) -> Void) -> DateFormatter { block(self); return self }
}
