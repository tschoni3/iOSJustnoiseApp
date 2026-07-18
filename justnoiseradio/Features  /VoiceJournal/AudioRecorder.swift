// AudioRecorder.swift

import Foundation
import AVFoundation

enum AudioRecorderError: LocalizedError {
    case microphonePermissionDenied
    case failedToStart
    case noActiveRecording
    case missingRecordingURL

    var errorDescription: String? {
        switch self {
        case .microphonePermissionDenied:
            return "Microphone access is disabled."
        case .failedToStart:
            return "Recording could not start."
        case .noActiveRecording:
            return "There is no active recording to stop."
        case .missingRecordingURL:
            return "Recording file could not be found."
        }
    }
}

class AudioRecorder: ObservableObject {
    private var audioRecorder: AVAudioRecorder?
    private var recordingSession: AVAudioSession!
    private var recordingURL: URL?
    
    init() {
        recordingSession = AVAudioSession.sharedInstance()
    }
    
    func startRecording() throws {
        if #available(iOS 17.0, *) {
            guard AVAudioApplication.shared.recordPermission != .denied else {
                throw AudioRecorderError.microphonePermissionDenied
            }
        } else {
            guard recordingSession.recordPermission != .denied else {
                throw AudioRecorderError.microphonePermissionDenied
            }
        }

        try recordingSession.setCategory(
            .playAndRecord,
            mode: .default,
            options: [.defaultToSpeaker, .allowBluetoothHFP, .allowBluetoothA2DP]
        )
        try recordingSession.setActive(true)

        let settings = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44_100,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue,
            AVEncoderBitRateKey: 128_000
        ]

        let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let filename = UUID().uuidString + ".m4a"
        let destinationURL = documentsDirectory.appendingPathComponent(filename)

        let recorder = try AVAudioRecorder(url: destinationURL, settings: settings)
        recorder.isMeteringEnabled = true
        recorder.prepareToRecord()

        guard recorder.record() else {
            throw AudioRecorderError.failedToStart
        }

        recordingURL = destinationURL
        audioRecorder = recorder
    }
    
    func stopRecording(completion: @escaping (Result<URL, Error>) -> Void) {
        guard audioRecorder != nil else {
            completion(.failure(AudioRecorderError.noActiveRecording))
            return
        }

        audioRecorder?.stop()
        audioRecorder = nil
        try? recordingSession.setActive(false, options: .notifyOthersOnDeactivation)

        if let url = recordingURL {
            completion(.success(url))
        } else {
            completion(.failure(AudioRecorderError.missingRecordingURL))
        }
    }

    func currentNormalizedPowerLevel() -> CGFloat {
        guard let audioRecorder else { return 0 }

        audioRecorder.updateMeters()

        let averagePower = audioRecorder.averagePower(forChannel: 0)
        let peakPower = audioRecorder.peakPower(forChannel: 0)
        let combinedPower = max(averagePower, peakPower - 7)
        let floor: Float = -52

        guard combinedPower > floor else { return 0 }

        let normalized = (combinedPower - floor) / abs(floor)
        return CGFloat(min(max(pow(normalized, 1.05), 0), 1))
    }
}
