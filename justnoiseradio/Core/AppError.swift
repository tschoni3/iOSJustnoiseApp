//
//  AppError.swift
//

import Foundation
import OSLog

enum AppError: LocalizedError, Identifiable {
    var id: String { errorDescription ?? UUID().uuidString }
    
    case nfcSessionFailed(description: String)
    case invalidModeSelection
    case unknown(description: String)
    case unauthorizedNFCTag
    case nfcNotAvailable
    case alreadyActivated
    case invalidNFCTag

    var errorDescription: String? {
        switch self {
        case .nfcSessionFailed(let description):
            logError(description)
            return "Connection to Zap failed. Try holding it closer."
        case .invalidNFCTag:
            logError("Invalid or malformed NFC Tag scanned.")
            return "This isn’t your Zap. Please use your linked device."
        case .invalidModeSelection:
            logError("Invalid mode selection.")
            return "Select a mode before starting your session."
        case .unknown(let description):
            logError(description)
            return "Something went wrong. Please try again."
        case .unauthorizedNFCTag:
            logError("Unauthorized NFC Tag scanned.")
            return "This Zap isn’t linked to your account."
        case .nfcNotAvailable:
            logError("NFC is not available on this device.")
            return "Your phone doesn’t support Zap scanning."
        case .alreadyActivated:
            logError("JustNoise is already activated.")
            return "Your Zap is already connected and ready to use."
        }
    }
    
    private func logError(_ message: String) {
        let logger = Logger(subsystem: "com.stilltschoni.justnoise", category: "AppError")
        logger.error("\(message)")
    }
}
