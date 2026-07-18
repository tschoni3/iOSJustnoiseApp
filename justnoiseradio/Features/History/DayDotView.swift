import SwiftUI

struct DayDotView: View {
    let level: DotLevel
    let isToday: Bool
    let size: CGFloat
    let blinkToday: Bool
    var isSelected: Bool = false

    @State private var blink = false

    private let selectionOutlineColor = Color.orange.opacity(0.92)

    private var baseColor: Color {
        switch level {
        case .empty:  return Color(red: 235 / 255, green: 235 / 255, blue: 235 / 255)
        case .light:  return Color(red: 192 / 255, green: 192 / 255, blue: 192 / 255)
        case .medium: return Color(red: 119 / 255, green: 119 / 255, blue: 119 / 255)
        case .dense:  return Color(red: 25 / 255, green: 25 / 255, blue: 25 / 255)
        }
    }

    private var fill: Color {
        guard blinkToday, isToday else { return baseColor }
        return Color.orange.opacity(blink ? 1.0 : 0.25)
    }

    var body: some View {
        Circle()
            .fill(fill)
            .frame(width: size, height: size)
            .overlay {
                Circle()
                    .stroke(
                        isSelected ? selectionOutlineColor : .clear,
                        lineWidth: isSelected ? max(0.5, size * 0.05) : 0
                    )
            }
            .scaleEffect(isSelected ? 1.08 : 1.0)
            .onAppear {
                guard blinkToday, isToday else { return }
                withAnimation(
                    .easeInOut(duration: 0.9)
                        .repeatForever(autoreverses: true)
                ) {
                    blink.toggle()
                }
            }
    }
}
