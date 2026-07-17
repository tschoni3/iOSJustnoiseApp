//
//  JournalEntry.swift
//  justnoise
//
//  Created by TJ on 06.03.26.
//

import Foundation

struct JournalEntry: Identifiable, Codable, Hashable {
    let id: UUID
    let createdAt: Date
    let modeName: String?
    let transcription: TranscriptionResponse
    let audioFileURL: URL?
    let linkedSessionId: UUID?

    init(
        id: UUID = UUID(),
        createdAt: Date = Date(),
        modeName: String? = nil,
        transcription: TranscriptionResponse,
        audioFileURL: URL? = nil,
        linkedSessionId: UUID? = nil
    ) {
        self.id = id
        self.createdAt = createdAt
        self.modeName = modeName
        self.transcription = transcription
        self.audioFileURL = audioFileURL
        self.linkedSessionId = linkedSessionId
    }

    var previewText: String {
        let title = transcription.notetitle.trimmingCharacters(in: .whitespacesAndNewlines)
        let overview = transcription.overview.trimmingCharacters(in: .whitespacesAndNewlines)
        let transcript = transcription.transcript.trimmingCharacters(in: .whitespacesAndNewlines)

        if !title.isEmpty { return title }
        if !overview.isEmpty { return overview }
        if !transcript.isEmpty { return transcript }
        return "Voice Reflection"
    }

    var hasContent: Bool {
        !transcription.notetitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
        !transcription.overview.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
        !transcription.transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
