// SignalScreen.swift
// Implements the 'Signal' tape UI as described

import SwiftUI

struct TapeRecording: Identifiable {
    let id = UUID()
    let start: TimeInterval // Offset from start
    let duration: TimeInterval
}

class TapeViewModel: ObservableObject {
    @Published var recordings: [TapeRecording] = []
    @Published var isRecording: Bool = false
    @Published var isPlaying: Bool = false
    @Published var tapeDuration: TimeInterval = 0

    private var currentRecordingStartTime: Date?

    func startRecording() {
        guard !isRecording else { return }
        currentRecordingStartTime = Date()
        isRecording = true
    }

    func stopRecording() {
        guard isRecording, let start = currentRecordingStartTime else { return }
        let duration = Date().timeIntervalSince(start)
        let offset = tapeDuration
        let rec = TapeRecording(start: offset, duration: duration)
        recordings.append(rec)
        tapeDuration += duration
        isRecording = false
        currentRecordingStartTime = nil
    }

    func play() {
        guard !isPlaying else { return }
        isPlaying = true
    }

    func pause() {
        guard isPlaying else { return }
        isPlaying = false
    }
}

struct SignalScreen: View {
    @StateObject private var viewModel = TapeViewModel()

    var formattedDuration: String {
        let total = Int(viewModel.tapeDuration)
        let hours = total / 3600
        let mins = (total % 3600) / 60
        return String(format: "%dh %dm", hours, mins)
    }
    
    var body: some View {
        VStack(spacing: 28) {
            // Header
            VStack(spacing: 2) {
                Text("Captured")
                    .font(.system(size: 20, weight: .bold))
                Text(formattedDuration)
                    .font(.system(size: 36, weight: .heavy))
                    .foregroundColor(.primary)
            }
            .padding(.top, 48)
    
            // Waveform Tape
            TapeWaveView(recordings: viewModel.recordings, totalDuration: viewModel.tapeDuration)
                .frame(height: 60)
                .padding(.horizontal, 24)
    
            // Controls
            HStack(spacing: 30) {
                Button(action: { viewModel.isRecording ? viewModel.stopRecording() : viewModel.startRecording() }) {
                    Image(systemName: viewModel.isRecording ? "stop.circle.fill" : "mic.circle.fill")
                        .resizable()
                        .frame(width: 60, height: 60)
                        .foregroundColor(viewModel.isRecording ? .red : .blue)
                }
                Button(action: { viewModel.isPlaying ? viewModel.pause() : viewModel.play() }) {
                    Image(systemName: viewModel.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .resizable()
                        .frame(width: 60, height: 60)
                        .foregroundColor(.green)
                }
            }
            .padding(.top, 28)
            Spacer()
        }
        .navigationTitle("Signal Tape")
    }
}

struct TapeWaveView: View {
    let recordings: [TapeRecording]
    let totalDuration: TimeInterval

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                // Draw a simple continuous wave across the tape
                Path { path in
                    let width = geo.size.width
                    let height = geo.size.height
                    let baseline = height / 2
                    let waveCount = max(1, Int(width / 30))
                    path.move(to: CGPoint(x: 0, y: baseline))
                    for i in 0..<waveCount {
                        let x = CGFloat(i) * (width / CGFloat(waveCount))
                        let y = baseline + sin(Double(i) * 1.3) * 8
                        path.addLine(to: CGPoint(x: x, y: y))
                    }
                    path.addLine(to: CGPoint(x: width, y: baseline))
                }
                .stroke(Color.primary.opacity(0.6), lineWidth: 3)

                // Draw markers for each recording
                ForEach(recordings) { rec in
                    let x = totalDuration > 0 ? CGFloat(rec.start / totalDuration) * geo.size.width : 0
                    Rectangle()
                        .fill(Color.red.opacity(0.85))
                        .frame(width: 4, height: geo.size.height)
                        .cornerRadius(2)
                        .offset(x: x)
                }
            }
        }
    }
}

#Preview {
    SignalScreen()
}
