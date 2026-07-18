import SwiftUI

struct DayDotGridView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    let viewMode: HistoryViewMode

    private let spacing: CGFloat = 6
    private let padding: CGFloat = 16
    private let pastDaysContext = 14
    private let ninetyCols = 4

    @State private var didAutoScroll = false
    @State private var selectedDaySummary: SelectedDaySummary?

    var body: some View {
        GeometryReader { geo in
            let availableWidth = geo.size.width - padding * 2
            let availableHeight = geo.size.height - padding * 2
            let config = gridConfig(width: availableWidth, height: availableHeight)

            ZStack {
                Group {
                    if viewMode == .ninetyDays {
                        ninetyDaysView(config: config)
                    } else {
                        yearView(config: config)
                    }
                }

                if let selectedDaySummary {
                    selectedDayOverlay(for: selectedDaySummary)
                }
            }
        }
    }

    // MARK: - 90 DAYS VIEW

    private func ninetyDaysView(config: GridConfig) -> some View {
        let rows = chunk(config.days, size: ninetyCols)

        return ScrollViewReader { proxy in
            ScrollView(.vertical) {
                VStack(alignment: .center, spacing: spacing) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        let rowID = rowAnchorID(for: row)

                        HStack(spacing: spacing) {
                            ForEach(row) { day in
                                dayCell(day: day, dotSize: config.dotSize)
                            }

                            // fill last row
                            if row.count < ninetyCols {
                                ForEach(0..<(ninetyCols - row.count), id: \.self) { _ in
                                    Color.clear
                                        .frame(width: config.dotSize, height: config.dotSize)
                                }
                            }
                        }
                        .id(rowID) // row anchor
                    }
                }
                .padding(padding)
            }
            .scrollIndicators(.hidden)
            .onAppear {
                didAutoScroll = false
                Task { @MainActor in
                    await scrollToTodayContext(proxy: proxy, days: config.days)
                }
            }
            .onChange(of: viewMode) { oldValue, newValue in
                selectedDaySummary = nil
                if newValue == .ninetyDays {
                    didAutoScroll = false
                    Task { @MainActor in
                        await scrollToTodayContext(proxy: proxy, days: config.days)
                    }
                }
            }
            .onChange(of: config.days.count) { _, _ in
                selectedDaySummary = nil
                didAutoScroll = false
                Task { @MainActor in
                    await scrollToTodayContext(proxy: proxy, days: config.days)
                }
            }
            .onChange(of: config.dotSize) { _, _ in
                selectedDaySummary = nil
                didAutoScroll = false
                Task { @MainActor in
                    await scrollToTodayContext(proxy: proxy, days: config.days)
                }
            }
        }
    }

    // MARK: - YEAR VIEW

    private func yearView(config: GridConfig) -> some View {
        VStack(spacing: 8) {
            LazyVGrid(
                columns: Array(
                    repeating: GridItem(.fixed(config.dotSize), spacing: spacing),
                    count: config.columns
                ),
                spacing: spacing
            ) {
                ForEach(config.days) { day in
                    dayCell(day: day, dotSize: config.dotSize)
                }
            }
            .padding(padding)

            Text(yearFooterText)
                .font(.caption)
                .foregroundColor(.gray)
                .padding(.bottom, 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }

    // MARK: - DOT CELL

    @ViewBuilder
    private func dayCell(day: DaySummary, dotSize: CGFloat) -> some View {
        let cal = Calendar.current
        let isToday = cal.isDateInToday(day.date)

        Button {
            withAnimation(.spring(response: 0.24, dampingFraction: 0.92)) {
                selectedDaySummary = summary(for: day)
            }
        } label: {
            DayDotView(
                level: day.dotLevel,
                isToday: isToday,
                size: dotSize,
                blinkToday: true,
                isSelected: selectedDaySummary?.id == day.id
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func selectedDayOverlay(for summary: SelectedDaySummary) -> some View {
        ZStack {
            Color.black.opacity(0.16)
                .ignoresSafeArea()
                .onTapGesture {
                    dismissSelectedDay()
                }

            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top, spacing: 12) {
                    Text(formattedDate(summary.date))
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)

                    Spacer(minLength: 0)

                    Button {
                        dismissSelectedDay()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white.opacity(0.82))
                            .frame(width: 28, height: 28)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("\(formattedProtectedDuration(summary.protectedDuration)) Protected")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)

                    Text(reflectionLabel(summary.reflectionCount))
                        .font(.system(size: 17, weight: .medium))
                        .foregroundColor(.white.opacity(0.72))
                }
            }
            .padding(22)
            .frame(maxWidth: 290, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(Color(red: 0.08, green: 0.08, blue: 0.08))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.22), radius: 24, x: 0, y: 14)
            .padding(.horizontal, 24)
        }
        .transition(.opacity.combined(with: .scale(scale: 0.96)))
    }

    private func dismissSelectedDay() {
        withAnimation(.easeOut(duration: 0.18)) {
            selectedDaySummary = nil
        }
    }


    // MARK: - SCROLL LOGIC (DEVICE-SAFE)

    @MainActor
    private func scrollToTodayContext(proxy: ScrollViewProxy, days: [DaySummary]) async {
        guard viewMode == .ninetyDays else { return }
        guard didAutoScroll == false else { return }
        didAutoScroll = true

        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())

        guard let todayIndex = days.firstIndex(where: { cal.isDate($0.date, inSameDayAs: today) }) else {
            didAutoScroll = false
            return
        }

        let targetIndex = max(todayIndex - pastDaysContext, 0)

        // scroll to ROW anchor (more reliable on device)
        let targetRowStartIndex = (targetIndex / ninetyCols) * ninetyCols
        let targetDate = cal.startOfDay(for: days[targetRowStartIndex].date)

        // layout waits + retries (real device needs this)
        for _ in 0..<3 { await Task.yield() }

        for _ in 0..<8 {
            withAnimation(.none) {
                proxy.scrollTo(targetDate, anchor: .top)
            }
            try? await Task.sleep(nanoseconds: 40_000_000) // 40ms
        }

        withAnimation(.easeInOut(duration: 0.25)) {
            proxy.scrollTo(targetDate, anchor: .top)
        }
    }

    private func rowAnchorID(for row: [DaySummary]) -> Date {
        let cal = Calendar.current
        guard let first = row.first else { return cal.startOfDay(for: Date()) }
        return cal.startOfDay(for: first.date)
    }

    private func summary(for day: DaySummary) -> SelectedDaySummary {
        SelectedDaySummary(
            date: day.date,
            protectedDuration: day.totalFocus,
            reflectionCount: reflectionCount(for: day.date)
        )
    }

    private func reflectionCount(for date: Date) -> Int {
        let dayStart = Calendar.current.startOfDay(for: date)
        let dayEnd = Calendar.current.date(byAdding: .day, value: 1, to: dayStart) ?? dayStart

        let standaloneCount = nfcViewModel.journalHistory
            .filter { $0.hasContent }
            .filter { $0.createdAt >= dayStart && $0.createdAt < dayEnd }
            .count

        let linkedSessionIDs = Set(nfcViewModel.journalHistory.compactMap(\.linkedSessionId))
        let legacyAttachedCount = nfcViewModel.sessionHistory
            .filter { sessionOverlapsDay($0, dayStart: dayStart, dayEnd: dayEnd) }
            .filter { hasLegacyReflectionContent($0) && !linkedSessionIDs.contains($0.id) }
            .count

        return standaloneCount + legacyAttachedCount
    }

    private func sessionOverlapsDay(_ session: Session, dayStart: Date, dayEnd: Date) -> Bool {
        let sessionEnd = session.startDate.addingTimeInterval(max(0, session.duration))
        return sessionEnd > dayStart && session.startDate < dayEnd
    }

    private func hasLegacyReflectionContent(_ session: Session) -> Bool {
        guard let transcription = session.transcription else { return false }

        return !transcription.notetitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !transcription.overview.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !transcription.transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func formattedDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "dd.MM.yyyy"
        return formatter.string(from: date)
    }

    private func formattedProtectedDuration(_ duration: TimeInterval) -> String {
        let roundedMinutes = Int(duration.rounded(.down)) / 60
        let hours = roundedMinutes / 60
        let minutes = roundedMinutes % 60

        if roundedMinutes <= 0 {
            return duration > 0 ? "<1m" : "0m"
        }

        if hours > 0, minutes > 0 {
            return "\(hours)h \(minutes)m"
        }

        if hours > 0 {
            return "\(hours)h"
        }

        return "\(minutes)m"
    }

    private func reflectionLabel(_ count: Int) -> String {
        "\(count) \(count == 1 ? "reflection" : "reflections")"
    }

    // MARK: - YEAR FOOTER

    private var yearFooterText: String {
        let cal = Calendar.current
        let now = Date()

        let year = cal.component(.year, from: now)
        let startOfYear = cal.date(from: DateComponents(year: year, month: 1, day: 1))!
        let startOfToday = cal.startOfDay(for: now)

        let totalDays = cal.range(of: .day, in: .year, for: now)!.count
        let dayIndex = (cal.dateComponents([.day], from: startOfYear, to: startOfToday).day ?? 0) + 1
        let clamped = min(max(dayIndex, 1), totalDays)

        let daysLeft = max(0, totalDays - clamped)
        let pct = Int(round((Double(clamped) / Double(totalDays)) * 100.0))

        return "\(daysLeft)d left • \(pct)%"
    }

    // MARK: - CONFIG

    private func gridConfig(width: CGFloat, height: CGFloat) -> GridConfig {
        switch viewMode {
        case .ninetyDays:
            let days = nfcViewModel.generate90DaySummaries()
            let dotSize = floor((width - spacing * CGFloat(ninetyCols - 1)) / CGFloat(ninetyCols))
            return GridConfig(days: days, columns: ninetyCols, dotSize: dotSize)

        case .year:
            let days = nfcViewModel.generateYearSummaries()
            let best = bestYearLayout(n: days.count, width: width, height: height)
            return GridConfig(days: days, columns: best.columns, dotSize: best.dot)
        }
    }

    private func bestYearLayout(n: Int, width: CGFloat, height: CGFloat) -> (columns: Int, dot: CGFloat) {
        var bestColumns = 10
        var bestDot: CGFloat = 1

        for columns in 8...120 {
            let rows = Int(ceil(Double(n) / Double(columns)))
            guard rows > 0 else { continue }

            let dotByWidth = (width - spacing * CGFloat(columns - 1)) / CGFloat(columns)
            let dotByHeight = (height - spacing * CGFloat(rows - 1)) / CGFloat(rows)

            let dot = floor(min(dotByWidth, dotByHeight))
            guard dot > bestDot else { continue }

            bestDot = dot
            bestColumns = columns
        }

        return (bestColumns, bestDot)
    }

    // MARK: - HELPERS

    private func chunk<T>(_ array: [T], size: Int) -> [[T]] {
        guard size > 0 else { return [] }
        return stride(from: 0, to: array.count, by: size).map {
            Array(array[$0..<min($0 + size, array.count)])
        }
    }
}

// MARK: - CONFIG MODEL

struct GridConfig {
    let days: [DaySummary]
    let columns: Int
    let dotSize: CGFloat
}

private struct SelectedDaySummary: Identifiable, Equatable {
    let date: Date
    let protectedDuration: TimeInterval
    let reflectionCount: Int

    var id: Date { Calendar.current.startOfDay(for: date) }
}
