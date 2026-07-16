import Foundation

enum JustNoiseBackendError: LocalizedError {
    case invalidConfiguration

    var errorDescription: String? {
        switch self {
        case .invalidConfiguration:
            return "The backend configuration is invalid."
        }
    }
}

enum JustNoiseBackend {
    private static let backendHost = "swift-5e8ce9b2e6d0.herokuapp.com"

    static let baseURL = URL(string: "https://\(backendHost)")!
    static let capturesURL = baseURL.appendingPathComponent("captures")

    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.waitsForConnectivity = true
        configuration.timeoutIntervalForRequest = 120
        configuration.timeoutIntervalForResource = 240
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: configuration)
    }()

    static func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        let validatedRequest = try validate(request)

#if DEBUG
        let metricsDelegate = TransportMetricsDelegate(requestURL: validatedRequest.url)
        return try await session.data(for: validatedRequest, delegate: metricsDelegate)
#else
        return try await session.data(for: validatedRequest)
#endif
    }

    private static func validate(_ request: URLRequest) throws -> URLRequest {
        guard let url = request.url else {
            throw JustNoiseBackendError.invalidConfiguration
        }

        guard url.scheme?.lowercased() == "https",
              url.host?.lowercased() == backendHost else {
            throw JustNoiseBackendError.invalidConfiguration
        }

        return request
    }

    fileprivate static func debugLogTransport(
        for url: URL?,
        metrics: URLSessionTaskTransactionMetrics
    ) {
        guard let url else { return }

        let protocolName = metrics.networkProtocolName ?? "unknown"
        let tlsVersion = metrics.negotiatedTLSProtocolVersion.map(String.init(describing:)) ?? "none"
        let secureHandshakeObserved = metrics.secureConnectionStartDate != nil
        print(
            "[Backend] host=\(url.host ?? "unknown") protocol=\(protocolName) tls=\(tlsVersion) secureHandshake=\(secureHandshakeObserved) proxy=\(metrics.isProxyConnection)"
        )
    }
}

private final class TransportMetricsDelegate: NSObject, URLSessionTaskDelegate {
    private let requestURL: URL?

    init(requestURL: URL?) {
        self.requestURL = requestURL
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didFinishCollecting metrics: URLSessionTaskMetrics
    ) {
        if let transaction = metrics.transactionMetrics.last ?? metrics.transactionMetrics.first {
            JustNoiseBackend.debugLogTransport(for: requestURL, metrics: transaction)
        }
    }
}
