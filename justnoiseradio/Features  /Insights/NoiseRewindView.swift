import SwiftUI
import UIKit
import Combine

// MARK: - Design Tokens

private enum UIX {
    static let corner: CGFloat = 22
    static let cardPad: CGFloat = 18
    static let slidePadH: CGFloat = 22
    static let slidePadTop: CGFloat = 26
    static let bigNumber: CGFloat = 54
    static let heroLine: CGFloat = 34

    static let accent = LinearGradient(
        gradient: Gradient(colors: [
            Color(red: 0.98, green: 0.47, blue: 0.42),
            Color(red: 0.83, green: 0.23, blue: 0.20)
        ]),
        startPoint: .topLeading, endPoint: .bottomTrailing
    )

    static let lavender = LinearGradient(
        colors: [Color(red: 0.92, green: 0.93, blue: 1.0), Color.white],
        startPoint: .top, endPoint: .bottom
    )

    static let beige = LinearGradient(
        colors: [Color(red: 0.97, green: 0.96, blue: 0.93), Color.white],
        startPoint: .top, endPoint: .bottom
    )
}

// MARK: - PUBLIC API (VM + Root)

struct NoiseRewindSummary {
    let monthName: String
    let hoursFocused: Int
    let sessions: Int
    let bestStreakDays: Int
    let dominantMood: String
    let topWord: String
    let topics: [String]
    let percentile: Int?
}

final class NoiseRewindVM: ObservableObject {
    @Published var summary: NoiseRewindSummary
    @Published var isPresentingShare = false
    @Published var generatedQuote: String? = nil

    init(summary: NoiseRewindSummary) { self.summary = summary }

    var identityTier: String {
        switch summary.hoursFocused {
        case 0..<5: return "Starter"
        case 5..<15: return "Noise Cutter"
        case 15..<40: return "Clarity Builder"
        default: return "Focus Master"
        }
    }
    var identityLine: String { "This month, you leveled up: \(identityTier)" }
    var badgeLine: String? {
        guard let p = summary.percentile else { return nil }
        let top = 100 - p
        return "Top \(top)% Noise Cutter"
    }

    func generatePersonalQuote() {
        // TODO: replace with your AI call
        generatedQuote = "Noise off. Clarity on. \(summary.monthName) was yours."
        Haptics.light()
    }
}

struct NoiseRewindView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject var vm: NoiseRewindVM
    @State private var page = 0
    @State private var isPaused = false
    @State private var progress = Array(repeating: 0.0, count: 5)
    private let totalSlides = 5
    private let tapExclusionBottom: CGFloat = 160
    private let storyDuration: TimeInterval = 15
    @State private var timer = Timer.publish(every: 0.02, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            // Soft gradient background
            Rectangle()
                .fill(LinearGradient(colors: [Color(.systemBackground), Color(.secondarySystemBackground)], startPoint: .top, endPoint: .bottom))
                .ignoresSafeArea()

            VStack(spacing: 0) {
                StoryProgressRow(progress: progress)
                    .padding(.horizontal, 12)
                    .padding(.top, 8)

                // Slides
                ZStack {
                    TabView(selection: $page) {
                        IntroSlide(identityLine: vm.identityLine, badge: vm.badgeLine, monthName: vm.summary.monthName)
                            .tag(0)

                        HoursSessionsSlide(hours: vm.summary.hoursFocused, sessions: vm.summary.sessions)
                            .tag(1)

                        StreakSlide(days: vm.summary.bestStreakDays)
                            .tag(2)

                        MoodSlide(mood: vm.summary.dominantMood, topWord: vm.summary.topWord, topics: vm.summary.topics)
                            .tag(3)

                        FinalSlide(monthName: vm.summary.monthName,
                                   onShare: { Haptics.medium(); vm.isPresentingShare = true })
                            .tag(4)
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    .animation(.spring(response: 0.45, dampingFraction: 0.85), value: page)
                    .padding(.top, 6)

                    VStack(spacing: 0) {
                        HStack {
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture { goBack() }
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture { goForward() }
                        }
                        Spacer().frame(height: tapExclusionBottom)
                    }
                    .simultaneousGesture(
                        LongPressGesture(minimumDuration: 0.2)
                            .onChanged { _ in isPaused = true }
                            .onEnded { _ in isPaused = false }
                    )
                }
            }
        }
        .overlay(alignment: .topTrailing) {
            CloseButton { dismiss() }
                .padding(.top, 8)
                .padding(.trailing, UIX.slidePadH)
        }
        .onReceive(timer) { _ in
            guard !isPaused else { return }
            if progress[page] < 1 {
                progress[page] = min(1, progress[page] + 0.02 / storyDuration)
            } else if page < totalSlides - 1 {
                page += 1
            }
        }
        .onChange(of: page) { _, newValue in
            for i in 0..<totalSlides {
                if i < newValue { progress[i] = 1 }
                else if i > newValue { progress[i] = 0 }
            }
        }
        .sheet(isPresented: $vm.isPresentingShare) { ShareSheet(items: [shareText]) }
        .alert("Your Quote", isPresented: Binding(
            get: { vm.generatedQuote != nil },
            set: { if !$0 { vm.generatedQuote = nil } }
        )) {
            Button("Copy") { UIPasteboard.general.string = vm.generatedQuote; Haptics.light() }
            Button("OK", role: .cancel) {}
        } message: { Text(vm.generatedQuote ?? "") }
    }

    private var shareText: String {
        let s = vm.summary
        return """
        JustNoise • \(s.monthName) Noise Rewind
        ⏱ \(s.hoursFocused) hours • \(s.sessions) sessions
        🔥 \(s.bestStreakDays)-day streak
        Mood: \(s.dominantMood), Mantra: \(s.topWord).
        """
    }

    private func goForward() {
        if page < totalSlides - 1 {
            progress[page] = 1
            page += 1
        } else {
            progress[page] = 1
        }
        isPaused = false
    }

    private func goBack() {
        if page > 0 {
            progress[page - 1] = 0
            page -= 1
        } else {
            progress[0] = 0
        }
        isPaused = false
    }
}

// MARK: - SLIDES (Optimized)

private struct IntroSlide: View {
    let identityLine: String
    let badge: String?
    let monthName: String

    var body: some View {
        SlideContainer(gradient: .white) {
            VStack(spacing: 16) {
                Spacer()
                Text("Here’s your Noise Rewind")
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(.secondary)
                Text("\(monthName). You turned noise into clarity.")
                    .font(.system(size: UIX.heroLine + 6, weight: .bold, design: .rounded))
                    .multilineTextAlignment(.center)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer()
            }
            .frame(maxWidth: .infinity)
            .multilineTextAlignment(.center)
        }
    }
}

private struct HoursSessionsSlide: View {
    let hours: Int
    let sessions: Int

    var body: some View {
        SlideContainer(gradient: .white) {
            VStack(alignment: .leading, spacing: 18) {
                // Big numbers with label
                HStack(alignment: .firstTextBaseline, spacing: 16) {
                    BigStat(value: "\(hours)", label: "hours")
                    Divider().frame(height: 44).background(.secondary.opacity(0.25))
                    BigStat(value: "\(sessions)", label: "sessions")
                }

                Card {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("You focused for \(hours) hours this month.")
                            .font(.title3.weight(.semibold))
                        Text("That’s \(sessions) sessions of pure clarity.")
                            .foregroundStyle(.secondary)
                        DotGridView(filled: min(sessions, 20), total: 20)
                            .padding(.top, 4)
                    }
                }

                Spacer()
            }
        }
    }
}

private struct StreakSlide: View {
    let days: Int

    var body: some View {
        SlideContainer(gradient: UIX.beige) {
            VStack(alignment: .leading, spacing: 18) {
                Text("\(days)-day focus streak")
                    .font(.system(size: UIX.heroLine, weight: .bold, design: .rounded))
                    .fixedSize(horizontal: false, vertical: true)

                Card {
                    VStack(spacing: 14) {
                        WeekRow(highlightIndex: 2)
                        Text("That’s not luck — that’s discipline.")
                            .font(.callout.weight(.semibold))
                    }
                }

                Spacer()
            }
        }
    }
}

private struct MoodSlide: View {
    let mood: String
    let topWord: String
    let topics: [String]

    var body: some View {
        SlideContainer(gradient: UIX.lavender) {
            VStack(alignment: .leading, spacing: 18) {
                Text("Most days, you felt \(mood.lowercased()).")
                    .font(.system(size: UIX.heroLine, weight: .bold, design: .rounded))

                Card {
                    VStack(alignment: .leading, spacing: 12) {
                        BubbleCloud(labels: topics)
                        Group {
                            Text("Your most used word in journaling was ")
                            + Text(topWord).underline().bold()
                            + Text(".")
                        }
                        .font(.title3.weight(.semibold))
                    }
                }

                Spacer()
            }
        }
    }
}

private struct FinalSlide: View {
    let monthName: String
    var onShare: () -> Void

    var body: some View {
        SlideContainer(gradient: .white) {
            VStack(alignment: .leading, spacing: 18) {
                Text("\(monthName) was yours.")
                    .font(.system(size: UIX.heroLine, weight: .bold, design: .rounded))
                Text("Noise off. Clarity on.")
                    .font(.title3.weight(.semibold))

                Spacer()

                HStack(spacing: 14) {
                    PrimaryButton(title: "Share", filled: true, action: onShare)
                }
            }
        }
    }
}

// MARK: - Reusable UI

private struct SlideContainer<Content: View>: View {
    var gradient: AnyView
    @ViewBuilder var content: Content

    init(gradient: LinearGradient, @ViewBuilder content: () -> Content) {
        self.gradient = AnyView(gradient)
        self.content = content()
    }
    init(gradient: Color, @ViewBuilder content: () -> Content) {
        self.gradient = AnyView(Rectangle().fill(gradient))
        self.content = content()
    }

    var body: some View {
        ZStack {
            gradient.ignoresSafeArea()
            content
                .padding(.horizontal, UIX.slidePadH)
                .padding(.top, UIX.slidePadTop)
        }
    }
}

private struct Card<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading) {
            content
        }
        .padding(UIX.cardPad)
        .background(
            RoundedRectangle(cornerRadius: UIX.corner, style: .continuous)
                .fill(.ultraThinMaterial)
                .shadow(color: .black.opacity(0.06), radius: 14, x: 0, y: 8)
        )
    }
}

private struct Pill: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.caption2.weight(.bold))
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(UIX.accent)
            .foregroundColor(.white)
            .clipShape(Capsule())
            .shadow(color: .black.opacity(0.12), radius: 10, x: 0, y: 6)
    }
}

private struct Badge: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Color(.secondarySystemBackground))
            .clipShape(Capsule())
    }
}

private struct CloseButton: View {
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(.primary)
                .padding(10)
                .background(.ultraThinMaterial, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Close")
    }
}

private struct StoryProgressRow: View {
    let progress: [Double]
    var body: some View {
        HStack(spacing: 6) {
            ForEach(progress.indices, id: \.self) { i in
                StoryProgressBar(progress: progress[i])
            }
        }
    }
}

private struct StoryProgressBar: View {
    let progress: Double
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.primary.opacity(0.15))
                Capsule().fill(Color.primary)
                    .frame(width: geo.size.width * CGFloat(max(0, min(1, progress))))
            }
        }
        .frame(height: 3)
        .frame(maxWidth: .infinity)
    }
}

private struct BigStat: View {
    let value: String
    let label: String
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(value)
                .font(.system(size: UIX.bigNumber, weight: .bold, design: .rounded))
                .tracking(-1)
            Text(label.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
        }
    }
}

private struct PrimaryButton: View {
    let title: String
    let filled: Bool
    var action: () -> Void

    var body: some View {
        Button(action: { action() }) {
            Text(title)
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(filled ? AnyView(UIX.accent) : AnyView(Color(.secondarySystemBackground)))
                .foregroundColor(filled ? .white : .primary)
                .clipShape(Capsule())
                .shadow(color: filled ? .black.opacity(0.15) : .clear, radius: 10, x: 0, y: 8)
        }
        .buttonStyle(.plain)
    }
}

private struct DotGridView: View {
    let filled: Int
    let total: Int

    var body: some View {
        let cols = Array(repeating: GridItem(.flexible(), spacing: 10), count: 5)
        LazyVGrid(columns: cols, spacing: 10) {
            ForEach(0..<total, id: \.self) { i in
                Circle()
                    .fill((i < filled ? Color.primary : Color.secondary).opacity(i < filled ? 1 : 0.25))
                    .frame(width: 20, height: 20)
                    .overlay(
                        Circle().stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                    )
            }
        }
        .padding(.top, 4)
    }
}

private struct WeekRow: View {
    let highlightIndex: Int // 1..7
    private let labels = ["M","T","W","T","F","S","S"]

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 12) {
                ForEach(1...7, id: \.self) { i in
                    Circle()
                        .fill(i == highlightIndex ? UIX.accent : LinearGradient(colors: [Color.gray.opacity(0.25), Color.gray.opacity(0.18)], startPoint: .top, endPoint: .bottom))
                        .frame(width: 28, height: 28)
                        .overlay(
                            Circle().stroke(Color.primary.opacity(0.06), lineWidth: 0.5)
                        )
                }
            }
            HStack(spacing: 18) {
                ForEach(labels, id: \.self) { s in
                    Text(s)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.secondary)
                        .frame(width: 18)
                }
            }
        }
    }
}

private struct BubbleCloud: View {
    let labels: [String]

    var body: some View {
        FlexibleWrap(data: labels) { label in
            Text(label)
                .font(.callout.weight(.semibold))
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(.thinMaterial)
                .overlay(
                    Capsule().stroke(Color.primary.opacity(0.08), lineWidth: 1)
                )
                .clipShape(Capsule())
        }
        .frame(minHeight: 80, maxHeight: 150)
    }
}

private struct FlexibleWrap<Data: RandomAccessCollection, Content: View>: View where Data.Element: Hashable, Content: View {
    let data: Data
    let spacing: CGFloat
    let content: (Data.Element) -> Content

    init(data: Data, spacing: CGFloat = 10, @ViewBuilder content: @escaping (Data.Element) -> Content) {
        self.data = data
        self.spacing = spacing
        self.content = content
    }

    var body: some View {
        GeometryReader { proxy in
            generate(in: proxy.size)
        }
    }

    private func generate(in size: CGSize) -> some View {
        var x: CGFloat = 0
        var y: CGFloat = 0

        return ZStack(alignment: .topLeading) {
            ForEach(Array(data), id: \.self) { item in
                content(item)
                    .alignmentGuide(.leading) { _ in
                        let itemW = estimateWidth(for: item)
                        if x + itemW > size.width {
                            x = 0
                            y -= (36 + spacing)
                        }
                        let result = x
                        x += itemW + spacing
                        return result
                    }
                    .alignmentGuide(.top) { _ in y }
            }
        }
    }

    private func estimateWidth(for item: Data.Element) -> CGFloat {
        let text = String(describing: item) as NSString
        let w = text.size(withAttributes: [.font: UIFont.preferredFont(forTextStyle: .callout)]).width
        return w + 28 // padding
    }
}

// MARK: - Haptics

private enum Haptics {
    static func light()  { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    static func medium() { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
}

// MARK: - Share Sheet

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

// MARK: - Preview

struct NoiseRewindView_Previews: PreviewProvider {
    static var previews: some View {
        let sample = NoiseRewindSummary(
            monthName: "September",
            hoursFocused: 27,
            sessions: 43,
            bestStreakDays: 21,
            dominantMood: "Motivated",
            topWord: "Clarity",
            topics: ["Deep Work","Gym","Reading","Family","Build"],
            percentile: 85
        )
        Group {
            NoiseRewindView(vm: NoiseRewindVM(summary: sample))
                .preferredColorScheme(.light)
            NoiseRewindView(vm: NoiseRewindVM(summary: sample))
                .preferredColorScheme(.dark)
        }
    }
}

