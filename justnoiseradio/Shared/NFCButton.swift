// NFCButton.swift

import SwiftUI

struct NFCButton: View {
    var action: () -> Void
    var isBlocked: Bool

    var body: some View {
        Button(action: action) {
            Text(isBlocked ? "Tap to End Session" : "Tap to Start Session")
                .font(.title2)
                .frame(maxWidth: .infinity)
                .padding()
                .background(buttonBackgroundColor)
                .foregroundColor(.white)
                .cornerRadius(10)
                .padding(.horizontal)
        }
    }

    private var buttonBackgroundColor: Color {
        isBlocked ? Color.red.opacity(1.0) : Color.green.opacity(1.0)
    }
}
