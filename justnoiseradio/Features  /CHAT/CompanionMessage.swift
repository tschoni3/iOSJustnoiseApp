// CompanionMessage.swift

import Foundation

enum CompanionRole: String, Codable {
    case user
    case assistant
}

struct CompanionMessage: Identifiable, Codable, Hashable {
    let id: UUID
    let role: CompanionRole
    let text: String
    let createdAt: Date

    // Optional session linkage
    let sessionId: String?

    init(
        id: UUID = UUID(),
        role: CompanionRole,
        text: String,
        createdAt: Date = Date(),
        sessionId: String? = nil
    ) {
        self.id = id
        self.role = role
        self.text = text
        self.createdAt = createdAt
        self.sessionId = sessionId
    }
}
