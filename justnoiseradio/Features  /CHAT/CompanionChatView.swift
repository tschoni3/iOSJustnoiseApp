import SwiftUI

struct CompanionChatView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var input: String = ""
    @FocusState private var inputFocused: Bool
    @AppStorage("userName") private var userName: String = ""

    private let bg = Color(red: 14/255, green: 14/255, blue: 13/255)
    private let accent = Color(red: 215/255, green: 250/255, blue: 0/255)

    // ✅ avoid recreating DateFormatter per row
    private static let timeFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateFormat = "HH:mm"
        return df
    }()

    var body: some View {
        NavigationStack {
            ZStack {
                bg.ignoresSafeArea()

                VStack(spacing: 0) {
                    messagesArea
                    composer
                }
            }
//            .navigationTitle("Companion")  // Removed as per instructions
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { topToolbar }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            // Delay slightly to avoid keyboard + layout stutter on present
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                inputFocused = true
            }
        }
    }
}

// MARK: - Toolbar
private extension CompanionChatView {
    @ToolbarContentBuilder
    var topToolbar: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            VStack(spacing: 2) {
                Text("Companion")
                    .font(.headline)
                    .foregroundColor(.white)
                // Show the chosen mode under the title (adjust property name if needed)
                Text(nfcViewModel.selectedMode?.name ?? "")
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.6))
            }
        }

        ToolbarItem(placement: .topBarLeading) {
            HStack(spacing: 8) {
                Circle()
                    .fill(accent)
                    .frame(width: 8, height: 8)

                Text("In Session")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.white.opacity(0.75))
            }
        }

        ToolbarItem(placement: .topBarTrailing) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 34, height: 34)
                    .background(Color.white.opacity(0.08))
                    .clipShape(Circle())
                    .overlay(
                        Circle().stroke(Color.white.opacity(0.08), lineWidth: 1)
                    )
            }
            .accessibilityLabel("Minimize Companion")
        }
    }
}

// MARK: - Messages
private extension CompanionChatView {
    var messagesArea: some View {
        ScrollViewReader { proxy in
            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 12) {
                    if nfcViewModel.currentSessionCompanionMessages.isEmpty {
                        emptyState
                    } else {
                        ForEach(nfcViewModel.currentSessionCompanionMessages) { msg in
                            messageRow(msg)
                                .id(msg.id)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.top, 14)
                .padding(.bottom, 10)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: nfcViewModel.currentSessionCompanionMessages.count) { _, _ in
                guard let last = nfcViewModel.currentSessionCompanionMessages.last else { return }

                // Reduce animation overhead when message list grows
                if nfcViewModel.currentSessionCompanionMessages.count <= 8 {
                    withAnimation(.easeOut(duration: 0.16)) {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                } else {
                    proxy.scrollTo(last.id, anchor: .bottom)
                }
            }
        }
    }

    var emptyState: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.06))
                        .frame(width: 34, height: 34)

                    Image(systemName: "sparkles")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(accent)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("Use Companion while you focus")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.white)

                    Text("Think, decide, capture, continue.")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.6))
                }
            }

            Text("Dump thoughts, ask questions, or capture ideas without leaving your session.")
                .font(.callout)
                .foregroundColor(.white.opacity(0.78))
                .lineSpacing(4)

            Divider().overlay(Color.white.opacity(0.08))

            Text("Try one of these")
                .font(.footnote.weight(.semibold))
                .foregroundColor(.white.opacity(0.88))

            VStack(alignment: .leading, spacing: 8) {
                examplePrompt("What’s the next smallest step?")
                examplePrompt("Help me decide what to do first.")
                examplePrompt("Turn this idea into 3 action steps.")
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.05))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    func examplePrompt(_ text: String) -> some View {
        Button {
            input = text
            inputFocused = true
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "sparkles")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.65))

                Text(text)
                    .font(.footnote)
                    .foregroundColor(.white.opacity(0.9))
                    .lineLimit(1)

                Spacer(minLength: 0)

                Image(systemName: "arrow.up.left")
                    .font(.caption2.weight(.bold))
                    .foregroundColor(.white.opacity(0.35))
            }
            .padding(.vertical, 9)
            .padding(.horizontal, 10)
            .background(Color.white.opacity(0.035))
            .cornerRadius(10)
        }
        .buttonStyle(.plain)
    }

    // ✅ FIXED: Companion left, User right — 100% deterministic layout
    func messageRow(_ msg: CompanionMessage) -> some View {
        let isUser = (msg.role == .user)

        return HStack(alignment: .bottom, spacing: 8) {
            if isUser {
                Spacer(minLength: 44)
                userBubble(msg)
            } else {
                companionBubble(msg)
                Spacer(minLength: 44)
            }
        }
        .frame(maxWidth: .infinity, alignment: isUser ? .trailing : .leading)
        .padding(.vertical, 2)
    }

    func companionBubble(_ msg: CompanionMessage) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Text("Companion")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(.white.opacity(0.65))

                Text(timeString(from: msg.createdAt))
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.40))
            }

            Text(msg.text)
                .font(.system(size: 15))
                .foregroundColor(.white)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 12)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.white.opacity(0.08))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .frame(maxWidth: 310, alignment: .leading)
    }

    func userBubble(_ msg: CompanionMessage) -> some View {
        VStack(alignment: .trailing, spacing: 6) {
            HStack(spacing: 6) {
                Text(timeString(from: msg.createdAt))
                    .font(.caption2)
                    .foregroundColor(.black.opacity(0.45))

                Text(userName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "You" : userName)
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(.black.opacity(0.75))
            }

            Text(msg.text)
                .font(.system(size: 15))
                .foregroundColor(.black)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 12)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(accent.opacity(0.96))
        )
        .frame(maxWidth: 310, alignment: .trailing)
    }

    func timeString(from date: Date) -> String {
        CompanionChatView.timeFormatter.string(from: date)
    }
}

// MARK: - Composer
private extension CompanionChatView {
    var composer: some View {
        VStack(spacing: 0) {
            Divider().overlay(Color.white.opacity(0.08))

            HStack(alignment: .bottom, spacing: 10) {
                TextField("Write a note or ask something…", text: $input, axis: .vertical)
                    .textFieldStyle(.plain)
                    .foregroundColor(.white)
                    .font(.system(size: 15))
                    .padding(.vertical, 12)
                    .padding(.horizontal, 12)
                    .background(Color.white.opacity(0.06))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.06), lineWidth: 1)
                    )
                    .focused($inputFocused)
                    .lineLimit(1...5)
                    .submitLabel(.send)
                    .onSubmit { send() }

                Button { send() } label: {
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.black)
                        .frame(width: 42, height: 42)
                        .background(accent)
                        .clipShape(Circle())
                        .shadow(color: accent.opacity(0.18), radius: 8, x: 0, y: 3)
                }
                .disabled(isSendDisabled)
                .opacity(isSendDisabled ? 0.45 : 1)
            }
            .padding(.horizontal, 12)
            .padding(.top, 10)
            .padding(.bottom, 10)
            .background(bg)
        }
    }

    var isSendDisabled: Bool {
        input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func send() {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        // Store user message immediately for snappy UI
        nfcViewModel.addCompanionUserMessage(trimmed)
        input = ""
        UIImpactFeedbackGenerator(style: .light).impactOccurred()

        nfcViewModel.requestCompanionReply(userMessage: trimmed) { result in
            switch result {
            case .success(let reply):
                nfcViewModel.addCompanionAssistantMessage(reply)
            case .failure(let error):
                nfcViewModel.addCompanionAssistantMessage(
                    "I hit a connection issue. Try again in a moment. (\(error.localizedDescription))"
                )
            }
        }
    }
}

