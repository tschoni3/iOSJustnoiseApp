import Foundation

enum SignalAnalysisError: LocalizedError, Equatable {
    case unauthenticated
    case quotaExceeded
    case serviceUnavailable(String)
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .unauthenticated:
            return "Signal analysis needs an active signed-in user."
        case .quotaExceeded:
            return "Signal analysis quota is exhausted."
        case .serviceUnavailable(let message):
            return message
        case .invalidResponse:
            return "Signal analysis response could not be decoded."
        }
    }
}

enum SignalAnalysisService {
    private static let endpoint = JustNoiseBackend.capturesURL

    static func analyzeCaptureClip(
        _ clip: CaptureClip,
        selectedModeName: String?,
        context: SignalAnalysisContext
    ) async throws -> BackendCaptureResponse {
        try await requestSignalAnalysis(
            clip: clip,
            selectedModeName: selectedModeName,
            context: context
        )
    }

    private static func requestSignalAnalysis(
        clip: CaptureClip,
        selectedModeName: String?,
        context: SignalAnalysisContext
    ) async throws -> BackendCaptureResponse {
        let currentAccessToken = await SupabaseManager.shared.currentAccessToken()
        guard let accessToken = currentAccessToken?.trimmingCharacters(in: .whitespacesAndNewlines),
              accessToken.isEmpty == false else {
            throw SignalAnalysisError.unauthenticated
        }

        let audioURL = clip.audioFileURL
        let audioData = try Data(contentsOf: audioURL)
        let boundary = UUID().uuidString

        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(
            "multipart/form-data; boundary=\(boundary)",
            forHTTPHeaderField: "Content-Type"
        )

        let selectedLanguage = UserDefaults.standard.string(forKey: "userLanguage") ?? "en"
        let modeName = selectedModeName ?? "Signal"
        let uploadFile = uploadFileMetadata(for: audioURL)

        var body = Data()
        body.append(fileFieldData(
            named: "audio",
            filename: uploadFile.filename,
            mimeType: uploadFile.mimeType,
            data: audioData,
            boundary: boundary
        ))
        body.append(formFieldData(named: "selected_mode", value: modeName, boundary: boundary))
        body.append(formFieldData(named: "language", value: selectedLanguage, boundary: boundary))
        body.append(formFieldData(named: "capture_id", value: clip.id.uuidString, boundary: boundary))
        body.append(formFieldData(named: "insight_mode", value: "automatic", boundary: boundary))
        body.append(formFieldData(
            named: "memory_revision",
            value: String(context.memory.memoryRevision),
            boundary: boundary
        ))
        body.append(formFieldData(
            named: "recent_captures",
            value: jsonString(from: context.recentCaptures),
            boundary: boundary
        ))
        body.append(formFieldData(
            named: "existing_signals",
            value: jsonString(from: context.existingSignals),
            boundary: boundary
        ))
        body.append(formFieldData(
            named: "existing_comments",
            value: jsonString(from: context.existingComments),
            boundary: boundary
        ))
        body.append(formFieldData(
            named: "threads",
            value: jsonString(from: context.memory.threads),
            boundary: boundary
        ))
        body.append(formFieldData(
            named: "all_known_threads",
            value: jsonString(from: context.memory.allKnownThreads),
            boundary: boundary
        ))
        body.append(formFieldData(
            named: "thread_links",
            value: jsonString(from: context.memory.threadLinks),
            boundary: boundary
        ))
        body.append(formFieldData(
            named: "memory",
            value: jsonString(from: context.memory),
            boundary: boundary
        ))
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)

        request.httpBody = body

#if DEBUG
        print("[Signal] POST \(endpoint.absoluteString)")
        print("[Signal] clip=\(clip.id.uuidString)")
        print("[Signal] mode=\(modeName)")
        print("[Signal] recentCaptures=\(context.recentCaptures.count)")
        print("[Signal] existingSignals=\(context.existingSignals.count)")
        print("[Signal] existingComments=\(context.existingComments.count)")
        print("[Signal] memoryThreads=\(context.memory.threads.count)")
        print("[Signal] memoryRevision=\(context.memory.memoryRevision)")
#endif

        let data: Data
        let response: URLResponse

        do {
            (data, response) = try await JustNoiseBackend.data(for: request)
        } catch let error as JustNoiseBackendError {
            throw SignalAnalysisError.serviceUnavailable(error.localizedDescription)
        }

        let statusCode = (response as? HTTPURLResponse)?.statusCode ?? -1

#if DEBUG
        print("[Signal] response status=\(statusCode)")
#endif

        if !(200...299).contains(statusCode) {
            throw analysisError(from: data, statusCode: statusCode)
        }

        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let errorMessage = json["error"] as? String {
            throw analysisError(from: errorMessage)
        } else if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let errorObject = json["error"] as? [String: Any],
                  let errorMessage = errorObject["message"] as? String {
            throw analysisError(from: errorMessage)
        }

        do {
            let backendResponse = try backendV1Decoder().decode(BackendCaptureResponse.self, from: data)
            guard backendResponse.contractVersion == 1,
                  backendResponse.engineVersion == "justnoise-backend-v1",
                  backendResponse.requiresExactBaseRevision else {
                throw SignalAnalysisError.invalidResponse
            }
            return backendResponse
        } catch {
#if DEBUG
            print("[Signal] Backend V1 decode failed: \(error)")
#endif
            throw SignalAnalysisError.invalidResponse
        }
    }

    private static func analysisError(from data: Data, statusCode: Int) -> SignalAnalysisError {
        if statusCode == 401 || statusCode == 403 {
            return .unauthenticated
        }

        if statusCode == 429 {
            return .quotaExceeded
        }

        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let message = json["error"] as? String {
                return analysisError(from: message)
            }

            if let errorObject = json["error"] as? [String: Any],
               let message = errorObject["message"] as? String {
                return analysisError(from: message)
            }
        }

        return .serviceUnavailable("Signal analysis failed with status \(statusCode).")
    }

    private static func analysisError(from message: String) -> SignalAnalysisError {
        let normalized = message.lowercased()

        if normalized.contains("insufficient_quota")
            || normalized.contains("exceeded your current quota")
            || normalized.contains("quota_unavailable")
            || normalized.contains("error code: 429") {
            return .quotaExceeded
        }

        return .serviceUnavailable(message)
    }

    private static func uploadFileMetadata(for audioURL: URL) -> (filename: String, mimeType: String) {
        let fileExtension = audioURL.pathExtension.lowercased()

        switch fileExtension {
        case "m4a":
            return ("signalcapture.m4a", "audio/mp4")
        case "mp3":
            return ("signalcapture.mp3", "audio/mpeg")
        case "wav":
            return ("signalcapture.wav", "audio/wav")
        default:
            let safeExtension = fileExtension.isEmpty ? "audio" : fileExtension
            return ("signalcapture.\(safeExtension)", "application/octet-stream")
        }
    }

    private static func fileFieldData(
        named name: String,
        filename: String,
        mimeType: String,
        data: Data,
        boundary: String
    ) -> Data {
        var field = Data()
        field.append("--\(boundary)\r\n".data(using: .utf8)!)
        field.append(
            "Content-Disposition: form-data; name=\"\(name)\"; filename=\"\(filename)\"\r\n"
                .data(using: .utf8)!
        )
        field.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        field.append(data)
        field.append("\r\n".data(using: .utf8)!)
        return field
    }

    private static func formFieldData(
        named name: String,
        value: String,
        boundary: String
    ) -> Data {
        var field = Data()
        field.append("--\(boundary)\r\n".data(using: .utf8)!)
        field.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
        field.append("\(value)\r\n".data(using: .utf8)!)
        return field
    }

    private static func jsonString<T: Encodable>(from value: T) -> String {
        let encoder = JSONEncoder()

        guard let data = try? encoder.encode(value),
              let string = String(data: data, encoding: .utf8) else {
            return "[]"
        }

        return string
    }

    private static func backendV1Decoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }
}
