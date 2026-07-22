// SignalAnalysisClient.swift

import Foundation

protocol SignalAnalyzing: Sendable {
    func analyzeCaptureClip(
        _ clip: CaptureClip,
        selectedModeName: String?,
        context: SignalAnalysisContext
    ) async throws -> BackendCaptureResponse
}

struct LiveSignalAnalysisClient: SignalAnalyzing {
    func analyzeCaptureClip(
        _ clip: CaptureClip,
        selectedModeName: String?,
        context: SignalAnalysisContext
    ) async throws -> BackendCaptureResponse {
        try await SignalAnalysisService.analyzeCaptureClip(
            clip,
            selectedModeName: selectedModeName,
            context: context
        )
    }
}
