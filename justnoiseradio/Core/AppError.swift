//
//  AppError.swift
//

import Foundation
import OSLog

enum AppError: LocalizedError, Identifiable {
    var id: String { errorDescription ?? UUID().uuidString }
    
    case audioRecordingFailed(description: String)
    case audioUploadFailed(description: String)
    case transcriptionDecodingFailed(description: String)
    case fileSavingFailed(description: String)
    case nfcSessionFailed(description: String)
    case invalidModeSelection
    case networkUnavailable
    case unknown(description: String)
    case unauthorizedNFCTag
    case nfcNotAvailable
    case activationSessionTimeout
    case blockingSessionTimeout
    case alreadyActivated
    case nfcSessionTimeout
    case invalidNFCTag

    var errorDescription: String? {
        switch self {
        case .audioRecordingFailed(let description):
            logError(description)
            return "Recording couldn’t start. Please try again."
        case .audioUploadFailed(let description):
            logError(description)
            return "Audio upload failed. Check your connection and retry."
        case .transcriptionDecodingFailed(let description):
            logError(description)
            return "We couldn’t process your voice note. Please try again."
        case .fileSavingFailed(let description):
            logError(description)
            return "File couldn’t be saved. Please try again."
        case .nfcSessionFailed(let description):
            logError(description)
            return "Connection to Zap failed. Try holding it closer."
        case .invalidNFCTag:
            logError("Invalid or malformed NFC Tag scanned.")
            return "This isn’t your Zap. Please use your linked device."
        case .invalidModeSelection:
            logError("Invalid mode selection.")
            return "Select a mode before starting your session."
        case .networkUnavailable:
            logError("Network is unavailable.")
            return "No internet connection. Please reconnect and try again."
        case .unknown(let description):
            logError(description)
            return "Something went wrong. Please try again."
        case .unauthorizedNFCTag:
            logError("Unauthorized NFC Tag scanned.")
            return "This Zap isn’t linked to your account."
        case .nfcNotAvailable:
            logError("NFC is not available on this device.")
            return "Your phone doesn’t support Zap scanning."
        case .activationSessionTimeout:
            logError("Activation session timed out.")
            return "Setup took too long. Please restart activation."
        case .blockingSessionTimeout:
            logError("Blocking/unblocking session timed out.")
            return "The action took too long. Please try again."
        case .alreadyActivated:
            logError("JustNoise is already activated.")
            return "Your Zap is already connected and ready to use."
        case .nfcSessionTimeout:
            logError("NFC session timed out.")
            return "Scan timed out. Please try again."
        }
    }
    
    private func logError(_ message: String) {
        let logger = Logger(subsystem: "com.stilltschoni.justnoise", category: "AppError")
        logger.error("\(message)")
    }
}
