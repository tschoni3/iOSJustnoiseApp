//
//  CoachMarks.swift
//

import SwiftUI

// MARK: - Model

struct CoachMark: Identifiable, Equatable {
    let id = UUID()
    let targetID: String
    let title: String
    let message: String
    let cornerRadius: CGFloat
    let padding: CGFloat
    let offset: CGSize

    init(
        targetID: String,
        title: String,
        message: String,
        cornerRadius: CGFloat = 14,
        padding: CGFloat = 8,
        offset: CGSize = .zero
    ) {
        self.targetID = targetID
        self.title = title
        self.message = message
        self.cornerRadius = cornerRadius
        self.padding = padding
        self.offset = offset
    }
}

// MARK: - PreferenceKey (GLOBAL frames)

struct CoachMarkFramesKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}

// MARK: - Tag targets (GLOBAL coords)

extension View {
    /// Tag any view you want to highlight. Measures in .global space.
    func coachMarkTarget(id: String) -> some View {
        background(
            GeometryReader { geo in
                Color.clear.preference(
                    key: CoachMarkFramesKey.self,
                    value: [id: geo.frame(in: .global)]
                )
            }
        )
    }

    /// Use this on toolbar items.
    func coachMarkToolbarTarget(id: String) -> some View {
        background(
            GeometryReader { geo in
                Color.clear.preference(
                    key: CoachMarkFramesKey.self,
                    value: [id: geo.frame(in: .global)]
                )
            }
        )
    }
}

// MARK: - Overlay

struct CoachMarksOverlay: View {
    @Binding var isPresented: Bool
    @Binding var stepIndex: Int
    let marks: [CoachMark]
    let frames: [String: CGRect]
    var onFinish: (() -> Void)? = nil

    var body: some View {
        GeometryReader { proxy in
            if isPresented, let active = nextVisibleMark() {
                overlayView(active: active, screenSize: proxy.size)
                    .transition(.opacity.combined(with: .scale))
            }
        }
    }

    // MARK: - Choose branch

    @ViewBuilder
    private func overlayView(active: CoachMark, screenSize: CGSize) -> some View {
        if let source = frames[active.targetID], !source.isNull {
            overlayCore(sourceRect: source, active: active, screenSize: screenSize)
        } else {
            Color.clear
        }
    }

    // MARK: - Core (Canvas draws hole + border with SAME path → perfect alignment)

    private func overlayCore(sourceRect: CGRect, active: CoachMark, screenSize: CGSize) -> some View {
        // Inflate + offset + align to pixels
        let inflated = sourceRect
            .insetBy(dx: -active.padding, dy: -active.padding)
            .offsetBy(dx: active.offset.width, dy: active.offset.height)
        let rect = pixelAligned(inflated)

        // Build the ONE rounded-rect path we’ll reuse
        let rr = RoundedRectangle(cornerRadius: active.cornerRadius, style: .continuous)
        let rrPath = rr.path(in: rect)

        // Step math (1-based for display)
        let currentStep = min(max(stepIndex + 1, 1), marks.count)

        return ZStack(alignment: .topLeading) {
            // Draw overlay with a single Canvas so fill & stroke share identical geometry.
            Canvas { context, size in
                // Dimmer with punched hole (even-odd fill)
                var bg = Path(CGRect(origin: .zero, size: size))
                bg.addPath(rrPath)
                context.fill(
                    bg,
                    with: .color(Color.black.opacity(0.6)),
                    style: FillStyle(eoFill: true)
                )

                // Border (comment out these 3 lines to remove)
                context.stroke(
                    rrPath,
                    with: .color(Color.white.opacity(0.95)),
                    lineWidth: 2
                )
            }
            .ignoresSafeArea()

            // Info card
            VStack(alignment: .leading, spacing: 10) {
                // Title + step badge
                HStack(alignment: .firstTextBaseline) {
                    Text(active.title)
                        .font(.headline)
                        .foregroundColor(.white)
                    Spacer(minLength: 12)
                    Text("\(currentStep)/\(max(marks.count, 1))")
                        .font(.caption).bold()
                        .foregroundColor(.black)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(
                            Capsule().fill(Color.white.opacity(0.9))
                        )
                        .accessibilityLabel("Step \(currentStep) of \(marks.count)")
                }

                Text(active.message)
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.85))

                HStack {
                    Button("Skip") { finish() }
                        .foregroundColor(.white.opacity(0.85))
                    Spacer()
                    Button(stepIndex == marks.count - 1 ? "Finish" : "Next") {
                        goToNext()
                    }
                    .font(.headline)
                    .foregroundColor(.black)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.white)
                    .cornerRadius(10)
                }
                .padding(.top, 6)
            }
            .padding(14)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.white.opacity(0.15), lineWidth: 1)
            )
            .frame(maxWidth: min(420, screenSize.width - 32))
            .position(infoPosition(for: rect, in: screenSize))
            .zIndex(3)
        }
        .compositingGroup()
    }

    // MARK: - Helpers (unchanged)

    private func nextVisibleMark() -> CoachMark? {
        for i in stepIndex..<marks.count {
            let m = marks[i]
            if frames[m.targetID] != nil { return m }
        }
        finish()
        return nil
    }

    private func goToNext() {
        var i = stepIndex + 1
        while i < marks.count {
            if frames[marks[i].targetID] != nil { stepIndex = i; return }
            i += 1
        }
        finish()
    }

    private func finish() {
        isPresented = false
        onFinish?()
    }

    private func infoPosition(for holeRect: CGRect, in size: CGSize) -> CGPoint {
        let spacing: CGFloat = 14
        let cardHeight: CGFloat = 140
        let belowY = holeRect.maxY + spacing + cardHeight / 2
        let aboveY = holeRect.minY - spacing - cardHeight / 2
        let centerX = min(max(holeRect.midX, 16 + 160), size.width - 16 - 160)
        let y: CGFloat = (belowY + cardHeight / 2 < size.height) ? belowY : max(cardHeight / 2 + 16, aboveY)
        return CGPoint(x: centerX, y: y)
    }

    private func pixelAligned(_ rect: CGRect) -> CGRect {
        let scale = UIScreen.main.scale
        func align(_ v: CGFloat, _ fn: (CGFloat) -> CGFloat) -> CGFloat { fn(v * scale) / scale }
        return CGRect(
            x: align(rect.origin.x, floor),
            y: align(rect.origin.y, floor),
            width: align(rect.size.width, round),
            height: align(rect.size.height, round)
        )
    }
}
