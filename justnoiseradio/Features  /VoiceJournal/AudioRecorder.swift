// AudioRecorder.swift

import Foundation
import AVFoundation

class AudioRecorder: NSObject, ObservableObject {
    private var audioRecorder: AVAudioRecorder?
    private var recordingSession: AVAudioSession!
    private var recordingURL: URL?
    
    override init() {
        super.init()
        recordingSession = AVAudioSession.sharedInstance()
    }
    
    func startRecording() {
        do {
            // Configure the audio session for recording
            try recordingSession.setCategory(.playAndRecord, mode: .default)
            try recordingSession.setActive(true)
            
            // Define the recording settings
            let settings = [
                AVFormatIDKey: Int(kAudioFormatLinearPCM),
                AVSampleRateKey: 44100,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
            ]
            
            // Define the file URL
            let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let filename = UUID().uuidString + ".wav"
            recordingURL = documentsDirectory.appendingPathComponent(filename)
            
            // Initialize the recorder
            audioRecorder = try AVAudioRecorder(url: recordingURL!, settings: settings)
            audioRecorder?.delegate = self
            audioRecorder?.record()
        } catch {
            print("Failed to start recording: \(error.localizedDescription)")
        }
    }
    
    func stopRecording(completion: @escaping (Result<URL, Error>) -> Void) {
        audioRecorder?.stop()
        if let url = recordingURL {
            completion(.success(url))
        } else {
            completion(.failure(NSError(domain: "AudioRecorder", code: -1, userInfo: [NSLocalizedDescriptionKey: "Recording URL is nil."])))
        }
    }
}

extension AudioRecorder: AVAudioRecorderDelegate {
    // Implement delegate methods if needed
}
