// AudioPlayerManager.swift

import Foundation
import AVFoundation
import Combine

class AudioPlayerManager: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published var isPlaying: Bool = false
    @Published var progress: Double = 0.0 // Progress from 0.0 to 1.0
    var audioPlayer: AVAudioPlayer?
    private var timer: Timer?
    
    func togglePlayback(url: URL) {
        if isPlaying {
            stopPlayback()
        } else {
            startPlayback(url: url)
        }
    }
    
    private func startPlayback(url: URL) {
        do {
            // Configure audio session and force output to speaker
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try audioSession.overrideOutputAudioPort(.speaker) // Force audio to play on speaker
            try audioSession.setActive(true)

            // Initialize and play audio
            audioPlayer = try AVAudioPlayer(contentsOf: url)
            audioPlayer?.delegate = self
            audioPlayer?.play()
            isPlaying = true
            startTimer()
        } catch {
            print("Failed to play audio: \(error.localizedDescription)")
        }
    }
    
    private func stopPlayback() {
        audioPlayer?.stop()
        isPlaying = false
        progress = 0.0
        timer?.invalidate()
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            guard let self = self, let player = self.audioPlayer else { return }
            self.progress = player.currentTime / player.duration
            if player.currentTime >= player.duration {
                self.stopPlayback()
            }
        }
    }
    
    // AVAudioPlayerDelegate Method
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async {
            self.isPlaying = false
            self.progress = 0.0
            self.timer?.invalidate()
        }
    }
}
