    // Session.swift

    import Foundation

struct Session: Identifiable, Hashable, Codable {
    let id: UUID
    let startDate: Date
    let duration: TimeInterval
    
    // Add the new property here
    var modeName: String?
    
    var transcription: TranscriptionResponse?
    var audioFileURL: URL?

    init(
        id: UUID = UUID(),
        startDate: Date,
        duration: TimeInterval,
        modeName: String? = nil,
        transcription: TranscriptionResponse? = nil,
        audioFileURL: URL? = nil
    ) {
        self.id = id
        self.startDate = startDate
        self.duration = duration
        self.modeName = modeName
        self.transcription = transcription
        self.audioFileURL = audioFileURL
    }
    
}
