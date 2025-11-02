//
//  SessionDetailView.swift
//  JustNoise
//

import SwiftUI
import AVFoundation
import UIKit

struct SessionDetailView: View {
    let session: Session
    @Environment(\.dismiss) private var dismiss
    @StateObject private var audioManager = AudioPlayerManager()

    // 👇 Add this
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @State private var showDeleteConfirmation = false

    private enum ActiveModal: Identifiable { case action, challenge, transcript
        var id: Int { hashValue }
    }
    @State private var activeModal: ActiveModal?

    var body: some View {
        Group {
            if session.transcription == nil && session.audioFileURL == nil {
                VStack {
                    Text("No journal available for this session")
                        .foregroundColor(.gray)
                        .padding()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(red: 14/255, green: 14/255, blue: 13/255))
            } else {
                VStack(spacing: 0) {
                    ScrollView {
                        VStack(spacing: 20) {
                            if let audioURL = session.audioFileURL {
                                CircularAudioPlayerView(audioManager: audioManager, audioURL: audioURL)
                                    .padding()
                                    .frame(maxWidth: .infinity)
                                    .padding(.top, 80)
                                    .shadow(color: Color.white.opacity(0.1), radius: 40, x: 0, y: 0)
                            }
                            if let transcription = session.transcription {
                                VStack(spacing: 16) {
                                    Text(transcription.notetitle)
                                        .font(.custom("Technology-Bold", size: 48))
                                        .foregroundColor(.white)
                                        .multilineTextAlignment(.center)

                                    HStack(spacing: 40) {
                                        VStack(spacing: 4) {
                                            Text("Duration").font(.caption).foregroundColor(.gray)
                                            Text("\(session.formattedDuration)").font(.caption).foregroundColor(.white)
                                        }
                                        VStack(spacing: 4) {
                                            Text("Sentiment").font(.caption).foregroundColor(.gray)
                                            Text(transcription.sentiment).font(.caption).foregroundColor(.white)
                                        }
                                    }

                                    Text(transcription.overview)
                                        .foregroundColor(.white)
                                        .multilineTextAlignment(.leading)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .font(.title).bold().lineSpacing(5)
                                }
                                .frame(maxWidth: .infinity)
                            }
                            Spacer()
                        }
                        .padding()
                    }
                    toolbarSection
                }
                .background(Color(red: 14/255, green: 14/255, blue: 13/255))
                .navigationBarTitle(session.modeName ?? "Session Detail", displayMode: .inline)

                // 👇 Add a trash in the top bar
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(role: .destructive) {
                            showDeleteConfirmation = true
                        } label: {
                            Image(systemName: "trash")
                        }
                        .accessibilityLabel("Delete Session")
                    }
                }
                // 👇 Confirm + perform deletion
                .alert("Delete this session?", isPresented: $showDeleteConfirmation) {
                    Button("Delete", role: .destructive) {
                        nfcViewModel.deleteSession(session)
                        dismiss()
                    }
                    Button("Cancel", role: .cancel) { }
                } message: {
                    Text("This action cannot be undone.")
                }

                // ✅ One stable sheet here is fine now that parent is unified
                .sheet(item: $activeModal) { modal in
                    switch modal {
                    case .action:
                        if let t = session.transcription {
                            CardStackModalView(
                                title: "Action Steps",
                                items: SessionDetailView.parseItems(from: t.actionsteps),
                                sessionID: session.id,
                                kind: .action
                            )
                            .preferredColorScheme(.dark)
                            .presentationDetents([.medium, .large])
                            .interactiveDismissDisabled(false)
                        }
                    case .challenge:
                        if let t = session.transcription {
                            CardStackModalView(
                                title: "Challenges",
                                items: SessionDetailView.parseItems(from: t.challenges),
                                sessionID: session.id,
                                kind: .challenge
                            )
                            .preferredColorScheme(.dark)
                            .presentationDetents([.medium, .large])
                            .interactiveDismissDisabled(false)
                        }
                    case .transcript:
                        if let t = session.transcription {
                            TranscriptModalView(title: "Transcript", content: t.transcript)
                                .preferredColorScheme(.dark)
                                .presentationDetents([.large])
                                .interactiveDismissDisabled(false)
                        }
                    }
                }
                // helps prevent accidental hierarchy churn on fast taps
                .transaction { t in t.disablesAnimations = false }
            }
        }
    }

    private var toolbarSection: some View {
        let hasTranscription = (session.transcription != nil)
        return HStack {
            Button { activeModal = .action } label: {
                VStack (spacing: 8) {
                    Image(systemName: "list.bullet").resizable().frame(width: 16, height: 16).foregroundColor(.white)
                    Text("Action Steps").font(.caption2).foregroundColor(.gray)
                }
            }
            .disabled(!hasTranscription).opacity(hasTranscription ? 1 : 0.4)
            Spacer()
            Button { activeModal = .challenge } label: {
                VStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle").resizable().frame(width: 16, height: 16).foregroundColor(.white)
                    Text("Challenges").font(.caption2).foregroundColor(.gray)
                }
            }
            .disabled(!hasTranscription).opacity(hasTranscription ? 1 : 0.4)
            Spacer()
            Button { activeModal = .transcript } label: {
                VStack (spacing: 8){
                    Image(systemName: "doc.text").resizable().frame(width: 16, height: 16).foregroundColor(.white)
                    Text("Transcript").font(.caption2).foregroundColor(.gray)
                }
            }
            .disabled(!hasTranscription).opacity(hasTranscription ? 1 : 0.4)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 18)
        .background(Color.black)
    }
}

// MARK: - Transcript modal
private struct TranscriptModalView: View {
    let title: String
    let content: String
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                Text(content)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
            }
            .background(Color(red: 14/255, green: 14/255, blue: 13/255))
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }.foregroundColor(.white)
                }
            }
        }
    }
}

// MARK: - Parsing helper
extension SessionDetailView {
    static func parseItems(from raw: String) -> [String] {
        raw
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .map { line in
                if let range = line.range(of: "^(([-•])|([0-9]+[\\.\\)])\\s*)", options: [.regularExpression]) {
                    return String(line[range.upperBound...]).trimmingCharacters(in: .whitespaces)
                }
                return line
            }
    }
}

// MARK: - Swipe components
private enum SwipeKind { case action, challenge }
private enum SwipeDirection { case left, right }

private struct SwipeCard: View {
    let text: String
    let dragX: CGFloat
    let tint: Color
    let tag: String
    let onDecisionPreview: (SwipeDirection) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(tag.uppercased())
                .font(.caption2.weight(.semibold))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(tint.opacity(0.22))
                .foregroundColor(tint.opacity(0.95))
                .cornerRadius(6)
                .padding(.top, 2)

            Text(text)
                .font(.title)
                .fontWeight(.semibold)
                .lineSpacing(4)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(20)
        .frame(maxWidth: UIScreen.main.bounds.width * 0.8)
        .frame(maxHeight: UIScreen.main.bounds.height * 0.8)
        .background(.ultraThinMaterial)
        .cornerRadius(20)
        .overlay(
            RoundedRectangle(cornerRadius: 20).fill(tint.opacity(0.14))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.35), radius: 18, x: 0, y: 10)
        .overlay(alignment: .topLeading) {
            if dragX < -40 { labelCue(text: "NO", color: .red) }
        }
        .overlay(alignment: .topTrailing) {
            if dragX > 40 { labelCue(text: "YES", color: .green) }
        }
        .onAppear { onDecisionPreview(.right) } // layout hint; no-op
    }

    @ViewBuilder private func labelCue(text: String, color: Color) -> some View {
        Text(text)
            .font(.headline.weight(.bold))
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(color.opacity(0.15))
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(color.opacity(0.8), lineWidth: 2)
            )
            .cornerRadius(6)
            .foregroundColor(color.opacity(0.95))
            .padding(14)
    }
}

private struct CardStackView: View {
    let items: [String]
    let tint: Color
    let tag: String
    let onSwipe: (Int, SwipeDirection) -> Void

    @State private var topIndex: Int = 0
    @State private var offset: CGSize = .zero

    private let threshold: CGFloat = 120

    var body: some View {
        ZStack {
            let displayRange = (topIndex..<min(topIndex + 3, items.count)).reversed()
            ForEach(Array(displayRange), id: \.self) { idx in
                let position = idx - topIndex
                card(at: idx)
                    .scaleEffect(position == 0 ? 1.0 : (position == 1 ? 0.98 : 0.96))
                    .offset(y: position == 0 ? 0 : (position == 1 ? 16 : 32))
                    .allowsHitTesting(position == 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .padding(.top, 10)
        .padding(.bottom, 16)
        .animation(.spring(response: 0.35, dampingFraction: 0.86, blendDuration: 0.2), value: topIndex)
        .animation(.spring(response: 0.25, dampingFraction: 0.85), value: offset)
    }

    @ViewBuilder
    private func card(at index: Int) -> some View {
        let isTop = index == topIndex
        SwipeCard(text: items[index], dragX: isTop ? offset.width : 0, tint: tint, tag: tag) { _ in }
            .offset(isTop ? offset : .zero)
            .rotationEffect(.degrees(isTop ? Double(offset.width / 12) : 0))
            .gesture(
                DragGesture()
                    .onChanged { value in
                        offset = value.translation
                    }
                    .onEnded { value in
                        let dx = value.translation.width
                        let direction: SwipeDirection? =
                            dx > threshold ? .right : (dx < -threshold ? .left : nil)

                        if let dir = direction {
                            let generator = UINotificationFeedbackGenerator(); generator.notificationOccurred(.success)
                            withAnimation {
                                offset = CGSize(width: (dir == .right ? 1 : -1) * 1000, height: value.translation.height)
                            }
                            let swiped = topIndex
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                                offset = .zero
                                topIndex = min(topIndex + 1, items.count)
                                onSwipe(swiped, dir)
                            }
                        } else {
                            withAnimation { offset = .zero }
                        }
                    }
            )
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
    }
}

private struct CardStackModalView: View {
    let title: String
    let items: [String]
    let sessionID: UUID
    let kind: SwipeKind
    @Environment(\.dismiss) private var dismiss

    @State private var accepted: [Int] = []
    @State private var rejected: [Int] = []

    private var tint: Color {
        switch kind {
        case .action: return Color.green
        case .challenge: return Color.orange
        }
    }

    private var tagLabel: String {
        switch kind {
        case .action: return "Action"
        case .challenge: return "Challenge"
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    gradient: Gradient(colors: [Color.black, Color.black.opacity(0.85)]),
                    startPoint: .top,
                    endPoint: .bottom
                ).ignoresSafeArea()

                VStack(spacing: 12) {
                    // Top progress
                    progress
                        .padding(.top, 6)
                        .padding(.horizontal, 20)

                    let reviewed = accepted.count + rejected.count
                    if items.isEmpty {
                        emptyState
                    } else if reviewed < items.count {
                        Spacer(minLength: 0)
                        CardStackView(items: items, tint: tint, tag: tagLabel) { index, direction in
                            if direction == .right { accepted.append(index) } else { rejected.append(index) }
                            logSwipe(index: index, direction: direction)
                        }
                        Spacer(minLength: 0)
                        actionRow
                            .padding(.horizontal, 24)
                            .padding(.bottom, 6)
                    } else {
                        completionView
                            .padding(.horizontal, 20)
                            .padding(.bottom, 16)
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") { dismiss() }.foregroundColor(.white)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if !accepted.isEmpty {
                        Button("Save (\(accepted.count))") { finalizeSelection() }
                            .foregroundColor(.white)
                            .accessibilityLabel("Save accepted items to your plan")
                    }
                }
            }
        }
    }

    private var actionRow: some View {
        HStack(spacing: 16) {
            Button {
                manualSwipe(.left)
            } label: {
                HStack { Image(systemName: "xmark"); Text("Skip") }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(12)
            }

            Button {
                manualSwipe(.right)
            } label: {
                HStack { Image(systemName: "checkmark"); Text("Add") }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(tint.opacity(0.28))
                    .cornerRadius(12)
            }
        }
        .foregroundColor(.white)
    }

    private var completionView: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 52))
                .foregroundColor(tint)
                .opacity(0.9)
                .padding(.top, 8)

            Text("You're set")
                .font(.title2).fontWeight(.semibold)
                .foregroundColor(.white)
                .opacity(0.95)

            let acceptedItems = accepted.map { items[$0] }
            if !acceptedItems.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Accepted")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(.white.opacity(0.7))
                    ForEach(acceptedItems.prefix(5), id: \.self) { it in
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: "checkmark.circle")
                                .foregroundColor(.white.opacity(0.8))
                            Text(it)
                                .foregroundColor(.white)
                                .font(.callout)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    if acceptedItems.count > 5 {
                        Text("+\(acceptedItems.count - 5) more…")
                            .font(.footnote)
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(Color.white.opacity(0.06))
                .cornerRadius(12)
            } else {
                Text("You skipped all suggestions. Want to try again?")
                    .foregroundColor(.white.opacity(0.8))
                    .font(.callout)
                    .padding(.top, 4)
            }

            HStack(spacing: 12) {
                Button {
                    restartReview()
                } label: {
                    HStack { Image(systemName: "arrow.counterclockwise"); Text("Restart") }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.white.opacity(0.08))
                        .cornerRadius(12)
                }

                Button {
                    finalizeSelection()
                } label: {
                    HStack { Image(systemName: "checkmark"); Text("Add \(accepted.count)") }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(tint.opacity(0.28))
                        .cornerRadius(12)
                }
                .disabled(accepted.isEmpty)
                .opacity(accepted.isEmpty ? 0.5 : 1)
            }
            .foregroundColor(.white)
        }
    }

    private func manualSwipe(_ dir: SwipeDirection) {
        let current = accepted.count + rejected.count
        guard current < items.count else { return }
        if dir == .right { accepted.append(current) } else { rejected.append(current) }
        logSwipe(index: current, direction: dir)
    }

    private func restartReview() {
        accepted.removeAll()
        rejected.removeAll()
    }

    private var progress: some View {
        let total = max(items.count, 1)
        let progress = Double(accepted.count + rejected.count) / Double(total)
        return ZStack(alignment: .leading) {
            Capsule().fill(Color.white.opacity(0.12)).frame(height: 6)
            Capsule().fill(tint.opacity(0.7)).frame(width: CGFloat(progress) * UIScreen.main.bounds.width * 0.9, height: 6)
        }
        .accessibilityLabel("Progress")
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Text("Nothing to review")
                .foregroundColor(.white)
                .font(.headline)
            Text("No items available for this section.")
                .foregroundColor(.white.opacity(0.7))
                .font(.subheadline)
        }
        .padding(40)
    }

    private func finalizeSelection() {
        // TODO: Hook into your planner/task system
        let key = preferenceKey()
        var stored = UserDefaults.standard.dictionary(forKey: key) as? [String: Bool] ?? [:]
        for i in accepted { stored[items[i]] = true }
        for i in rejected { stored[items[i]] = false }
        UserDefaults.standard.set(stored, forKey: key)
        dismiss()
    }

    private func preferenceKey() -> String {
        let k = (kind == .action) ? "actions" : "challenges"
        return "prefs_\(k)_\(sessionID.uuidString)"
    }

    private func logSwipe(index: Int, direction: SwipeDirection) {
        let k = (kind == .action) ? "action" : "challenge"
        let payload: [String: Any] = [
            "sessionID": sessionID.uuidString,
            "type": k,
            "item": items[index],
            "direction": (direction == .right ? "right" : "left"),
            "ts": Date().timeIntervalSince1970
        ]
        #if DEBUG
        print("[Swipe] \(payload)")
        #endif
    }
}

// MARK: - Preview
struct SessionDetailView_Previews: PreviewProvider {
    static var previews: some View {
        let mockTranscription = TranscriptionResponse(
            notetitle: "Daily Reflection",
            overview: "Today was productive with several key achievements.",
            actionsteps: "1. Continue with current tasks.\n2. Schedule meetings for next week.",
            challenges: "1. Time management.\n2. Balancing work and personal life.",
            transcript: "Transcript of the voice journal...",
            sentiment: "Positive",
            aifeedback: "Great job maintaining focus and achieving your goals today!"
        )

        let mockSession = Session(
            id: UUID(),
            startDate: Date(),
            duration: 3600,
            transcription: mockTranscription,
            audioFileURL: Bundle.main.url(forResource: "sample", withExtension: "wav")
        )

        return NavigationStack {
            SessionDetailView(session: mockSession)
                .preferredColorScheme(.dark)
        }
    }
}
