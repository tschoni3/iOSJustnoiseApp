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
    
    var formattedDuration: String {
        let totalSeconds = Int(duration)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        // Format as "H:MM"
        return String(format: "%d:%02d", hours, minutes)
    }

    var formattedStartDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: startDate)
    }
}
