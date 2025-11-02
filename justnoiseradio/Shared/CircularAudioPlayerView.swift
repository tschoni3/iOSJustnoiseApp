// CircularAudioPlayerView.swift

import SwiftUI
import AVFoundation

struct CircularAudioPlayerView: View {
    @ObservedObject var audioManager: AudioPlayerManager
    var audioURL: URL?
    
    // Define the highlight color #D7FA00
    let highlightColor = Color(red: 215/255, green: 250/255, blue: 0/255)
    
    var body: some View {
        ZStack {
            // Black Background Circle
            Circle()
                .fill(Color.black) // Solid black fill
                .frame(width: 140, height: 140)
            
            // Background Circle Stroke
            Circle()
                .stroke(lineWidth: 4)
                .opacity(1)
                .foregroundColor(Color.white) // Light gray stroke
                .frame(width: 140, height: 140)

            // Progress Circle with Single Highlight Color
            Circle()
                .trim(from: 0.0, to: CGFloat(min(audioManager.progress, 1.0)))
                .stroke(
                    highlightColor,
                    style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round)
                )
                .rotationEffect(Angle(degrees: -90))
                .animation(.linear(duration: 0.1), value: audioManager.progress)
                .frame(width: 140, height: 140)
            
            // Play/Stop Button
            Button(action: {
                if let url = audioURL {
                    audioManager.togglePlayback(url: url)
                }
            }) {
                Image(systemName: audioManager.isPlaying ? "pause.fill" : "play.fill")
                    .resizable()
                    .frame(width: 28, height: 28)
                    .foregroundColor(.white) // White icon
            }
            .accessibilityLabel(audioManager.isPlaying ? "Stop Playback" : "Play Voice Journal")
            .accessibilityAddTraits(.isButton)
            .disabled(audioURL == nil) // Disable button if no audio URL
        }
    }
}

struct CircularAudioPlayerView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            // Preview with Mock Audio Player and No Audio URL
            CircularAudioPlayerView(audioManager: MockAudioPlayerManager(), audioURL: nil)
                .previewDisplayName("No Audio")
            
            // Preview with Mock Audio Player and Sample Audio URL
            CircularAudioPlayerView(audioManager: MockAudioPlayerManager(), audioURL: Bundle.main.url(forResource: "sample", withExtension: "wav"))
                .previewDisplayName("With Audio")
        }
        .preferredColorScheme(.dark) // Ensure previews use dark mode for consistency
    }
}

// MockAudioPlayerManager.swift

import Foundation
import Combine

class MockAudioPlayerManager: AudioPlayerManager {
    override init() {
        super.init()
        self.isPlaying = false
        self.progress = 0.0 // Start from 0 for a more realistic preview
    }
    
    override func togglePlayback(url: URL) {
        // Mock toggle without actual playback
        isPlaying.toggle()
        
        // Simulate progress change for preview
        if isPlaying {
            Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { timer in
                DispatchQueue.main.async {
                    self.progress += 0.01
                    if self.progress >= 1.0 {
                        self.progress = 1.0
                        self.isPlaying = false
                        timer.invalidate()
                    }
                }
            }
        } else {
            // Reset progress if stopped
            self.progress = 0.0
        }
    }
}
