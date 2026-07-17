import SwiftUI

struct SignalTimelineSheet: View {
    @EnvironmentObject private var signalStore: SignalStore
    @Environment(\.dismiss) private var dismiss

    private let backgroundColor = Color(red: 14/255, green: 14/255, blue: 13/255)

    private var comments: [SignalComment] {
        signalStore.orderedSignalComments
    }

    private var shouldShowEmptyState: Bool {
        comments.isEmpty
            && signalStore.hasPendingSignalAnalysis == false
            && signalStore.signalReviewNotice == nil
    }

    var body: some View {
        ZStack {
            backgroundColor.ignoresSafeArea()

            VStack(spacing: 0) {
                header

                if shouldShowEmptyState {
                    emptyState
                } else {
                    contentScroll
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 24)
            .padding(.bottom, 18)
        }
        .preferredColorScheme(.dark)
        .onAppear {
            signalStore.signalTimelineDidAppear()
        }
        .onChange(of: comments.map(\.id)) { _, _ in
            signalStore.signalTimelineDidAppear()
        }
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 16) {
            Text("Comments")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.white)

            Spacer(minLength: 0)

            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white.opacity(0.82))
                    .frame(width: 34, height: 34)
                    .background(Color.white.opacity(0.08))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close insights")
        }
        .padding(.bottom, 18)
    }

    private var contentScroll: some View {
        ScrollView(.vertical, showsIndicators: false) {
            LazyVStack(alignment: .leading, spacing: 12) {
                if signalStore.hasPendingSignalAnalysis {
                    reviewingStatus
                } else if let notice = signalStore.signalReviewNotice {
                    reviewNoticeStatus(notice)
                }

                ForEach(comments) { comment in
                    SignalCommentCard(
                        comment: comment,
                        sourceReference: signalStore.captureReferenceText(for: comment),
                        sourceDate: signalStore.captureReferenceDate(for: comment)
                    )
                }
            }
            .padding(.bottom, 18)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Spacer(minLength: 24)

            Image(systemName: "sparkles")
                .font(.system(size: 28, weight: .semibold))
                .foregroundColor(.white.opacity(0.66))
                .frame(width: 64, height: 64)

            Text("Nothing has needed saying yet.")
                .font(.system(size: 21, weight: .bold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)

            Text("Keep capturing.")
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(.white.opacity(0.56))

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 16)
        .padding(.bottom, 28)
    }

    private var reviewingStatus: some View {
        HStack(spacing: 12) {
            ProgressView()
                .tint(.white.opacity(0.82))

            Text("Listening to the latest capture...")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white.opacity(0.76))

            Spacer(minLength: 0)
        }
        .padding(.vertical, 10)
    }

    private func reviewNoticeStatus(_ notice: SignalReviewNotice) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: noticeIcon(for: notice))
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white.opacity(0.66))
                .frame(width: 20)

            Text(noticeStatusLine(for: notice))
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.white.opacity(0.66))
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .padding(.vertical, 10)
    }

    private func noticeIcon(for notice: SignalReviewNotice) -> String {
        switch notice {
        case .paused:
            return "pause.circle.fill"
        case .retrying:
            return "clock.arrow.circlepath"
        }
    }

    private func noticeStatusLine(for notice: SignalReviewNotice) -> String {
        switch notice {
        case .paused(let until):
            return "Insights are paused until \(until.formatted(.dateTime.hour().minute()))."
        case .retrying(let until):
            return "The latest capture will be retried after \(until.formatted(.dateTime.hour().minute()))."
        }
    }
}

private struct SignalCommentCard: View {
    let comment: SignalComment
    let sourceReference: String?
    let sourceDate: Date?

    var body: some View {
        HStack(alignment: .top, spacing: 13) {
            Image(systemName: iconName)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(accentColor)
                .frame(width: 36, height: 36)
                .background(accentColor.opacity(0.14))
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(title)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(accentColor)

                    Spacer(minLength: 0)

                    if let sourceDate {
                        Text(Self.timestampFormatter.string(from: sourceDate))
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.white.opacity(0.38))
                    }
                }

                Text(comment.text)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white.opacity(0.9))
                    .lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)

                if let sourceReference {
                    Label(sourceReference, systemImage: "waveform")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.white.opacity(0.42))
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Color.white.opacity(0.05))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    private var normalizedHat: String {
        comment.hat?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased() ?? ""
    }

    private var title: String {
        switch normalizedHat {
        case "mirror": return "Mirror"
        case "pattern": return "Pattern"
        case "question": return "Question"
        case "action": return "Action"
        case "reframe": return "Reframe"
        case "anchor": return "Anchor"
        default: return "Insight"
        }
    }

    private var iconName: String {
        switch normalizedHat {
        case "mirror": return "rectangle.on.rectangle"
        case "pattern": return "repeat"
        case "question": return "questionmark"
        case "action": return "arrow.right"
        case "reframe": return "arrow.triangle.2.circlepath"
        case "anchor": return "bookmark.fill"
        default: return "sparkle"
        }
    }

    private var accentColor: Color {
        switch normalizedHat {
        case "mirror": return Color(red: 0.42, green: 0.76, blue: 0.96)
        case "pattern": return Color(red: 0.49, green: 0.82, blue: 0.67)
        case "question": return Color(red: 0.98, green: 0.69, blue: 0.38)
        case "action": return Color(red: 0.82, green: 0.92, blue: 0.30)
        case "reframe": return Color(red: 0.84, green: 0.58, blue: 0.78)
        case "anchor": return Color(red: 0.94, green: 0.49, blue: 0.43)
        default: return Color.white.opacity(0.78)
        }
    }

    private static let timestampFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, h:mm a"
        return formatter
    }()
}
