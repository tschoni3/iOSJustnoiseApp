import SwiftUI

struct CompanionChatView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @State private var draft: String = ""

    private let bg = Color(red: 14/255, green: 14/255, blue: 13/255)

    private var currentMessages: [CompanionMessage] {
        guard let sid = nfcViewModel.currentSessionId else { return [] }
        return nfcViewModel.messagesForSession(sid)
    }

    var body: some View {
        VStack(spacing: 0) {
            header

            Divider().overlay(Color.white.opacity(0.08))

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 10) {
                        if currentMessages.isEmpty {
                            emptyState
                        } else {
                            ForEach(currentMessages) { msg in
                                messageBubble(msg)
                                    .id(msg.id)
                            }
                        }
                    }
                    .padding(12)
                }
                .background(bg)
                .onChange(of: currentMessages.count) { _, _ in
                    if let last = currentMessages.last?.id {
                        withAnimation(.easeOut(duration: 0.2)) {
                            proxy.scrollTo(last, anchor: .bottom)
                        }
                    }
                }
            }

            composer
        }
        .background(bg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.35), radius: 12, x: 0, y: 6)
    }

    private var header: some View {
        HStack {
            HStack(spacing: 8) {
                Circle()
                    .fill(Color(red: 215/255, green: 250/255, blue: 0/255))
                    .frame(width: 8, height: 8)

                Text("Companion")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white.opacity(0.95))
            }

            Spacer()

            Button {
                withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                    nfcViewModel.isCompanionExpanded.toggle()
                }
            } label: {
                Image(systemName: nfcViewModel.isCompanionExpanded ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                    .foregroundColor(.white.opacity(0.85))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.white.opacity(0.03))
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField("Think out loud…", text: $draft, axis: .vertical)
                .textFieldStyle(.plain)
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color.white.opacity(0.06))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .lineLimit(1...4)

            Button {
                let text = draft
                draft = ""
                nfcViewModel.sendCompanionMessage(text)
            } label: {
                Image(systemName: "paperplane.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.black)
                    .frame(width: 40, height: 40)
                    .background(Color(red: 215/255, green: 250/255, blue: 0/255))
                    .clipShape(Circle())
            }
            .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .opacity(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.45 : 1)
        }
        .padding(10)
        .background(Color.black.opacity(0.35))
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Use this during your session")
                .font(.footnote.weight(.semibold))
                .foregroundColor(.white.opacity(0.9))

            Text("Ask a question, dump thoughts, or write a quick note without leaving focus mode.")
                .font(.caption)
                .foregroundColor(.white.opacity(0.65))
                .lineSpacing(3)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.white.opacity(0.04))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func messageBubble(_ msg: CompanionMessage) -> some View {
        let isUser = msg.role == .user

        return HStack {
            if isUser { Spacer(minLength: 40) }

            VStack(alignment: .leading, spacing: 6) {
                Text(isUser ? "You" : "Companion")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(.white.opacity(0.65))

                Text(msg.text)
                    .font(.callout)
                    .foregroundColor(.white)
                    .lineSpacing(3)
            }
            .padding(10)
            .background(isUser ? Color.white.opacity(0.10) : Color.white.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white.opacity(0.06), lineWidth: 1)
            )
            .frame(maxWidth: 280, alignment: .leading)

            if !isUser { Spacer(minLength: 40) }
        }
    }
}
