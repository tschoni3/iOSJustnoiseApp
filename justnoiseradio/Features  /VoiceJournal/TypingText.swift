// TypingText.swift

import SwiftUI

struct TypingText: View {
    let text: String
    let typingSpeed: Double // Characters per second
    @State private var displayedText = ""
    @State private var timer: Timer?

    var body: some View {
        Text(displayedText)
            .onAppear {
                startTyping()
            }
            .onDisappear {
                timer?.invalidate()
            }
    }

    private func startTyping() {
        displayedText = ""
        timer?.invalidate()
        var currentIndex = 0
        let totalCharacters = text.count

        timer = Timer.scheduledTimer(withTimeInterval: 1.0 / typingSpeed, repeats: true) { timer in
            if currentIndex < totalCharacters {
                let index = text.index(text.startIndex, offsetBy: currentIndex)
                displayedText.append(text[index])
                currentIndex += 1
            } else {
                timer.invalidate()
            }
        }
    }
}
