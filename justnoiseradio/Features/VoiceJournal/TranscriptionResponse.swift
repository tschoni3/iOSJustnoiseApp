// TranscriptionResponse.swift

import Foundation

struct TranscriptionResponse: Codable, Hashable, Equatable {
    var id = UUID()
    let notetitle: String
    let overview: String
    let actionsteps: String
    let challenges: String
    let transcript: String
    let sentiment: String
    let aifeedback: String

    enum CodingKeys: String, CodingKey {
        case notetitle
        case overview
        case actionsteps
        case challenges
        case transcript
        case sentiment
        case aifeedback
     
    }
}
