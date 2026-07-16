//
//  NoiseRewindWeeklyView.swift
//  justnoise
//

import SwiftUI

struct NoiseRewindWeeklyView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var nfcViewModel: NFCViewModel

    let insight: NoiseRewindWeeklyInsight

    @State private var page: Int = 0
    @State private var reflectionText: String = ""

    @State private var slideProgress: Double = 0
    @State private var autoPlayStarted = false

    private let totalPages = 7
    private let slideDuration: Double = 12.0
    private let timerInterval: Double = 0.05

    // Intro = page 0
    // Autoplay slides = pages 1...5
    // Reflection/final = page 6
    private let firstAutoPlayPage = 1
    private let lastAutoPlayPage = 5

    private let timer = Timer.publish(
        every: 0.05,
        on: .main,
        in: .common
    ).autoconnect()

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 0) {
                if page >= firstAutoPlayPage && page <= lastAutoPlayPage {
                    topProgress
                }

                TabView(selection: $page) {
                    introScreen.tag(0)
                    weeklySignalScreen.tag(1)
                    protectedMomentsScreen.tag(2)
                    deepestSessionScreen.tag(3)
                    patternInsightScreen.tag(4)
                    archetypeScreen.tag(5)
                    reflectionScreen.tag(6)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .overlay {
                    if page >= firstAutoPlayPage && page <= lastAutoPlayPage {
                        HStack(spacing: 0) {
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    goBack()
                                }

                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    goNext()
                                }
                        }
                    }
                }
            }
        }
        .preferredColorScheme(.light)
        .onReceive(timer) { _ in
            guard autoPlayStarted else { return }
            guard page >= firstAutoPlayPage else { return }
            guard page <= lastAutoPlayPage else { return }

            slideProgress = min(
                slideProgress + (timerInterval / slideDuration),
                1.0
            )

            if slideProgress >= 1.0 {
                goNext()
            }
        }
        .onChange(of: page) { _, _ in
            slideProgress = 0
        }
        .overlay(alignment: .topTrailing) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.black.opacity(0.65))
                    .padding(12)
                    .background(Color.black.opacity(0.06))
                    .clipShape(Circle())
            }
            .padding(.top, 14)
            .padding(.trailing, 18)
        }
    }
}

// MARK: - Screens

private extension NoiseRewindWeeklyView {

    var introScreen: some View {
        RewindSlide {
            Spacer()

            Text("Your Week in Signal")
                .font(.system(size: 42, weight: .bold))
                .multilineTextAlignment(.center)
                .foregroundColor(.black)

            Text("A reflection of your attention patterns.")
                .font(.system(size: 17, weight: .medium))
                .multilineTextAlignment(.center)
                .foregroundColor(.black.opacity(0.55))
                .padding(.top, 6)

            Spacer()

            primaryButton(title: "Begin") {
                slideProgress = 0
                autoPlayStarted = true
                goNext()
            }
        }
    }

    var weeklySignalScreen: some View {
        RewindSlide {
            VStack(spacing: 12) {
                Text("WEEKLY SIGNAL")
                    .font(.system(size: 13, weight: .semibold))
                    .tracking(1.4)
                    .foregroundColor(.black.opacity(0.45))

                Text(insight.weeklySignalTitle)
                    .font(.system(size: 34, weight: .bold))
                    .multilineTextAlignment(.center)
                    .foregroundColor(.black)
            }

            Spacer()

            SignalRingView(percent: insight.signalChangePercent)
                .frame(width: 210, height: 210)

            Spacer()
        }
    }

    var protectedMomentsScreen: some View {
        RewindSlide {
            VStack(alignment: .leading, spacing: 0) {

                Text("This Week")
                    .font(.system(size: 28, weight: .regular))
                    .foregroundColor(.black.opacity(0.45))

                Text("\(formattedDuration(insight.totalProtectedDuration)) of protected attention")
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(.black)
                    .minimumScaleFactor(0.45)
                    .lineLimit(2)
                    .padding(.top, 24)

                Spacer()

                DotGridView(
                    filled: min(insight.protectedSessionsCount, 20),
                    total: 20
                )
                .frame(width: 220)
                .frame(maxWidth: .infinity, alignment: .center)

                Spacer()

                Text(insight.protectedMomentsText)
                    .font(.system(size: 17, weight: .medium))
                    .multilineTextAlignment(.center)
                    .foregroundColor(.black.opacity(0.58))
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 22)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    var deepestSessionScreen: some View {
        RewindSlide {
            VStack(alignment: .leading, spacing: 0) {

                Text("Most focused day")
                    .font(.system(size: 28, weight: .regular))
                    .foregroundColor(.black.opacity(0.45))

                Text(mostFocusedDayText)
                    .font(.system(size: 62, weight: .bold))
                    .foregroundColor(.black)
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                    .padding(.top, 24)

                Spacer()

                VStack(alignment: .leading, spacing: 2) {
                    Text(formattedDuration(insight.mostFocusedDayDuration))
                        .font(.system(size: 72, weight: .bold))
                        .foregroundColor(.black)
                        .minimumScaleFactor(0.55)
                        .lineLimit(1)

                    Text("of protected focus")
                        .font(.system(size: 36, weight: .regular))
                        .foregroundColor(.black.opacity(0.45))
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)
                }

                Spacer()

                weekDotsRow
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
    var weekDotsRow: some View {
        let days = ["M", "T", "W", "T", "F", "S", "S"]
        let activeIndex = deepestSessionWeekdayIndex

        return HStack {
            ForEach(0..<7, id: \.self) { index in
                VStack(spacing: 10) {
                    Circle()
                        .fill(index == activeIndex ? Color.black : Color.black.opacity(0.12))
                        .frame(width: 34, height: 34)

                    Text(days[index])
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(.black.opacity(0.65))
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
    var deepestSessionWeekdayIndex: Int {
        guard let date = insight.mostFocusedDayDate else { return -1 }

        let weekday = Calendar.current.component(.weekday, from: date)

        switch weekday {
        case 2: return 0
        case 3: return 1
        case 4: return 2
        case 5: return 3
        case 6: return 4
        case 7: return 5
        case 1: return 6
        default: return -1
        }
    }

    var patternInsightScreen: some View {
        RewindSlide {
            VStack(spacing: 0) {
                Text("PATTERN INSIGHT")
                    .font(.system(size: 13, weight: .semibold))
                    .tracking(1.4)
                    .foregroundColor(.black.opacity(0.45))

                Text(insight.patternTitle)
                    .font(.system(size: 34, weight: .bold))
                    .multilineTextAlignment(.center)
                    .foregroundColor(.black)
                    .padding(.top, 14)

                Spacer()

                SignalBarChartView(values: insight.hourlySignalDurations)
                    .frame(height: UIScreen.main.bounds.height * 0.50)
                    .padding(.top, 18)

                Spacer()

                Text(insight.patternSubtitle)
                    .font(.system(size: 17, weight: .medium))
                    .multilineTextAlignment(.center)
                    .foregroundColor(.black.opacity(0.55))
                    .padding(.horizontal, 18)
            }
        }
    }

    var archetypeScreen: some View {
        RewindSlide {
            Spacer()

            Text("CURRENT SIGNAL PATTERN")
                .font(.system(size: 13, weight: .semibold))
                .tracking(1.4)
                .foregroundColor(.black.opacity(0.45))

            Text(insight.primaryArchetypeTitle.uppercased())
                .font(.system(size: 42, weight: .bold))
                .multilineTextAlignment(.center)
                .foregroundColor(.black)
                .padding(.top, 10)

            Text(insight.primaryArchetypeSubtitle)
                .font(.system(size: 18, weight: .medium))
                .multilineTextAlignment(.center)
                .foregroundColor(.black.opacity(0.56))
                .padding(.horizontal, 20)
                .padding(.top, 12)

            Spacer()
        }
    }

    var reflectionScreen: some View {
        RewindSlide {
            Spacer()

            Text(insight.reflectionPrompt)
                .font(.system(size: 34, weight: .bold))
                .multilineTextAlignment(.center)
                .foregroundColor(.black)
                .padding(.horizontal, 4)

            TextField("Write your reflection...", text: $reflectionText, axis: .vertical)
                .font(.system(size: 16, weight: .medium))
                .padding(18)
                .frame(minHeight: 120, alignment: .topLeading)
                .background(
                    RoundedRectangle(cornerRadius: 22)
                        .fill(Color.black.opacity(0.045))
                )
                .padding(.top, 24)

            Spacer()

            VStack(spacing: 12) {
                primaryButton(title: "Done") {
                    nfcViewModel.saveWeeklyReflectionToHistory(
                        text: reflectionText,
                        prompt: insight.reflectionPrompt,
                        weekStart: insight.weekStart,
                        weekEnd: insight.weekEnd
                    )

                    dismiss()
                }

                Button("Skip") {
                    dismiss()
                }
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.black.opacity(0.45))

                Text("See you next week.")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.black.opacity(0.38))
                    .padding(.top, 8)
            }
        }
    }
}

// MARK: - Components

private extension NoiseRewindWeeklyView {

    var topProgress: some View {
        HStack(spacing: 6) {
            ForEach(firstAutoPlayPage...lastAutoPlayPage, id: \.self) { index in
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Color.black.opacity(0.14))

                        Capsule()
                            .fill(Color.black)
                            .frame(
                                width: progressWidth(
                                    for: index,
                                    totalWidth: geo.size.width
                                )
                            )
                    }
                }
                .frame(height: 3)
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 12)
    }

    func progressWidth(for index: Int, totalWidth: CGFloat) -> CGFloat {
        if index < page {
            return totalWidth
        }

        if index == page {
            return totalWidth * CGFloat(slideProgress)
        }

        return 0
    }

    func primaryButton(title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 22))
        }
    }

    func goNext() {
        withAnimation(.spring(response: 0.4, dampingFraction: 0.86)) {
            if page < totalPages - 1 {
                page += 1
                slideProgress = 0
            }
        }
    }

    func goBack() {
        withAnimation(.spring(response: 0.4, dampingFraction: 0.86)) {
            if page > firstAutoPlayPage {
                page -= 1
                slideProgress = 0
            } else if page == firstAutoPlayPage {
                page = 0
                autoPlayStarted = false
                slideProgress = 0
            }
        }
    }

    var mostFocusedDayText: String {
        guard let date = insight.mostFocusedDayDate else { return "No focus yet" }

        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE"
        return formatter.string(from: date)
    }

    var protectedAttentionTitle: String {
        let totalMinutes = Int(insight.totalProtectedDuration / 60)
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60

        if hours > 0 {
            return "\(hours) hours of protected attention"
        } else if minutes > 0 {
            return "\(minutes) minutes of protected attention"
        } else {
            return "This Week protected attention"
        }
    }

    func formattedDuration(_ duration: TimeInterval) -> String {
        let totalMinutes = Int(duration / 60)
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60

        if hours > 0 {
            return "\(hours)h \(minutes)m"
        } else {
            return "\(minutes)m"
        }
    }
}

private struct RewindSlide<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            content
        }
        .padding(.horizontal, 28)
        .padding(.top, 70)
        .padding(.bottom, 34)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.white)
    }
}

private struct SignalRingView: View {
    let percent: Int

    private var clampedProgress: Double {
        let value = abs(Double(percent)) / 100
        return min(max(value, 0.08), 1.0)
    }

    private var displayText: String {
        if percent > 0 {
            return "+\(percent)%"
        } else if percent < 0 {
            return "\(percent)%"
        } else {
            return "0%"
        }
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.black.opacity(0.08), lineWidth: 18)

            Circle()
                .trim(from: 0, to: clampedProgress)
                .stroke(
                    Color.black,
                    style: StrokeStyle(lineWidth: 18, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))

            Text(displayText)
                .font(.system(size: 42, weight: .bold))
                .foregroundColor(.black)
        }
    }
}

private struct DotGridView: View {
    let filled: Int
    let total: Int

    private let columns = Array(repeating: GridItem(.fixed(18), spacing: 12), count: 5)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(0..<total, id: \.self) { index in
                Circle()
                    .fill(index < filled ? Color.black : Color.black.opacity(0.12))
                    .frame(width: 18, height: 18)
            }
        }
    }
}

private struct SignalBarChartView: View {
    let values: [TimeInterval]

    private struct BarItem: Identifiable {
        let id = UUID()
        let value: TimeInterval
    }

    private var groupedBars: [BarItem] {
        let safeValues = values.count == 24
            ? values
            : Array(repeating: 0, count: 24)

        let windows: [Range<Int>] = [
            0..<2,
            2..<4,
            4..<6,
            6..<8,
            8..<10,
            10..<12,
            12..<14,
            14..<16,
            16..<18,
            18..<20,
            20..<22,
            22..<24
        ]

        return windows.map { window in
            let total = window.reduce(0.0) { partial, hour in
                partial + safeValues[hour]
            }

            return BarItem(value: total)
        }
    }

    private var maxValue: TimeInterval {
        groupedBars.map(\.value).max() ?? 0
    }

    private var peakIndex: Int? {
        guard maxValue > 0 else { return nil }
        return groupedBars.firstIndex(where: { $0.value == maxValue })
    }

    var body: some View {
        VStack(spacing: 16) {
            HStack(alignment: .bottom, spacing: 7) {
                ForEach(Array(groupedBars.enumerated()), id: \.offset) { index, item in
                    VStack(spacing: 0) {
                        Spacer()

                        Capsule()
                            .fill(index == peakIndex ? Color.black : Color.black.opacity(0.13))
                            .frame(
                                width: index == peakIndex ? 26 : 22,
                                height: barHeight(for: item.value)
                            )
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(maxHeight: .infinity)

            HStack {
                Text("00:00")
                Spacer()
                Text("06:00")
                Spacer()
                Text("12:00")
                Spacer()
                Text("18:00")
                Spacer()
                Text("24:00")
            }
            .font(.system(size: 11, weight: .medium))
            .foregroundColor(.black.opacity(0.35))
            .padding(.horizontal, 4)
        }
    }

    private func barHeight(for value: TimeInterval) -> CGFloat {
        guard maxValue > 0 else { return 60 }

        let normalized = value / maxValue
        let minHeight: CGFloat = 60
        let maxHeight: CGFloat = 320

        return minHeight + CGFloat(normalized) * (maxHeight - minHeight)
    }
}
