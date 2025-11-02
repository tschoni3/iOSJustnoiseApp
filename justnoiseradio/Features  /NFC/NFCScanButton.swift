// NFCScanButton.swift

import SwiftUI

// 1. Define the Custom ButtonStyle
struct SolidButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(1.0) // Maintain full opacity
            .scaleEffect(1.0) // No scaling on press
            .animation(.none, value: configuration.isPressed) // Disable animation
    }
}

struct NFCScanButton: View {
    // Actions
    var action: () -> Void
    var isBlocked: Bool
    var longPressAction: (() -> Void)? = nil

    // Tunables
    var longPressDuration: Double = 3.0
    var debounceInterval: Double = 0.6
    var accessibilityLabelText: String? = nil
    var hapticsOnTap: Bool = true

    // States
    @GestureState private var isDetectingLongPress = false
    @State private var isBusy = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button(action: {
            guard !isBusy else { return }
            isBusy = true
            if hapticsOnTap {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
            action()
            DispatchQueue.main.asyncAfter(deadline: .now() + debounceInterval) {
                isBusy = false
            }
        }) {
            ZStack {
                Circle()
                    .fill(Color(red: 71/255, green: 71/255, blue: 71/255))
                    .frame(width: 220, height: 220)
                    .overlay(
                        Circle().stroke(Color(red: 60/255, green: 60/255, blue: 60/255),
                                        lineWidth: 3)
                    )
                    .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                    .scaleEffect((!reduceMotion && isDetectingLongPress) ? 1.1 : 1.0)
                    .animation(reduceMotion ? .none : .easeInOut(duration: 0.2), value: isDetectingLongPress)

                Image("Justnoise_logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 120, height: 120)
                    .foregroundColor(.white)
                    .accessibilityHidden(true)
            }
            .contentShape(Circle())
        }
        .buttonStyle(SolidButtonStyle())
        // Allow both tap + long press
        .simultaneousGesture(
            LongPressGesture(minimumDuration: longPressDuration)
                .updating($isDetectingLongPress) { current, state, _ in
                    state = current
                }
                .onEnded { _ in
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    longPressAction?()
                }
        )
        .disabled(isBusy)
        .accessibilityLabel(accessibilityLabelText ?? "Zap button")
        .accessibilityAddTraits(.isButton)
        .accessibilityHint(longPressAction != nil ? "Long press for alternate action." : "")
    }
}

#Preview("NFCScanButton") {
    ZStack {
        Color.black.ignoresSafeArea()
        NFCScanButton(
            action: { print("Tapped") },
            isBlocked: false,
            longPressAction: { print("Long pressed") },
            longPressDuration: 1.5,
            debounceInterval: 0.5,
            accessibilityLabelText: "Zap button"
        )
    }
    .preferredColorScheme(.dark)
}

