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

protocol CaptureAudioRecording: AnyObject {
    var isMeteringEnabled: Bool { get set }
    func prepareToRecord() -> Bool
    func record() -> Bool
    func stop()
    func updateMeters()
    func averagePower(forChannel channelNumber: Int) -> Float
    func peakPower(forChannel channelNumber: Int) -> Float
}

extension AVAudioRecorder: CaptureAudioRecording {}

typealias CaptureAudioRecorderFactory = (
    _ url: URL,
    _ settings: [String: Any]
) throws -> any CaptureAudioRecording

class AudioRecorder: ObservableObject {
    private var audioRecorder: (any CaptureAudioRecording)?
    private var recordingURL: URL?
    private let audioFileStore: CaptureAudioFileStore
    private let permissionDenied: () -> Bool
    private let activateAudioSession: () throws -> Void
    private let deactivateAudioSession: () -> Void
    private let recorderFactory: CaptureAudioRecorderFactory
    
    init(
        audioFileStore: CaptureAudioFileStore = CaptureAudioFileStore(),
        permissionDenied: (() -> Bool)? = nil,
        activateAudioSession: (() throws -> Void)? = nil,
        deactivateAudioSession: (() -> Void)? = nil,
        recorderFactory: @escaping CaptureAudioRecorderFactory = { url, settings in
            try AVAudioRecorder(url: url, settings: settings)
        }
    ) {
        let recordingSession = AVAudioSession.sharedInstance()
        self.audioFileStore = audioFileStore
        self.permissionDenied = permissionDenied ?? {
            if #available(iOS 17.0, *) {
                return AVAudioApplication.shared.recordPermission == .denied
            }
            return recordingSession.recordPermission == .denied
        }
        self.activateAudioSession = activateAudioSession ?? {
            try recordingSession.setCategory(
                .playAndRecord,
                mode: .default,
                options: [.defaultToSpeaker, .allowBluetoothHFP, .allowBluetoothA2DP]
            )
            try recordingSession.setActive(true)
        }
        self.deactivateAudioSession = deactivateAudioSession ?? {
            try? recordingSession.setActive(false, options: .notifyOthersOnDeactivation)
        }
        self.recorderFactory = recorderFactory
    }
    
    func startRecording() throws {
        cancelRecording()

        guard permissionDenied() == false else {
            throw AudioRecorderError.microphonePermissionDenied
        }

        var candidateURL: URL?
        do {
            try activateAudioSession()

            let settings: [String: Any] = [
                AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
                AVSampleRateKey: 44_100,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue,
                AVEncoderBitRateKey: 128_000
            ]

            let destinationURL = try audioFileStore.makeDraftURL()
            candidateURL = destinationURL

            let recorder = try recorderFactory(destinationURL, settings)
            recorder.isMeteringEnabled = true
            _ = recorder.prepareToRecord()

            guard recorder.record() else {
                throw AudioRecorderError.failedToStart
            }

            recordingURL = destinationURL
            audioRecorder = recorder
        } catch {
            audioRecorder?.stop()
            audioRecorder = nil
            recordingURL = nil
            if let candidateURL {
                audioFileStore.discardDraft(at: candidateURL)
            }
            deactivateAudioSession()
            throw error
        }
    }
    
    func stopRecording(completion: @escaping (Result<URL, Error>) -> Void) {
        guard audioRecorder != nil else {
            completion(.failure(AudioRecorderError.noActiveRecording))
            return
        }

        audioRecorder?.stop()
        audioRecorder = nil
        deactivateAudioSession()

        let completedURL = recordingURL
        recordingURL = nil

        if let completedURL {
            completion(.success(completedURL))
        } else {
            completion(.failure(AudioRecorderError.missingRecordingURL))
        }
    }

    func cancelRecording() {
        guard audioRecorder != nil || recordingURL != nil else { return }

        audioRecorder?.stop()
        audioRecorder = nil

        let cancelledURL = recordingURL
        recordingURL = nil
        deactivateAudioSession()

        if let cancelledURL {
            audioFileStore.discardDraft(at: cancelledURL)
        }
    }

    func discardRecording(at url: URL) {
        audioFileStore.discardDraft(at: url)
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
