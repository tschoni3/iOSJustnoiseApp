import Foundation
import Security

/// The Mac pairs this specific developer installation through Xcode's device service.
struct WorkshopPairing: Codable {
    let host: String
    let address: String?
    let port: Int
    let certificate: Data
    let token: String

    private static let item: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "JustNoise.PrivateZapWorkshop",
        kSecAttrAccount as String: "paired-mac"
    ]

    static func load() throws -> Self {
        let caches = try FileManager.default.url(for: .cachesDirectory, in: .userDomainMask,
                                                 appropriateFor: nil, create: false)
        let incoming = caches.appendingPathComponent("ZapWorkshopPairing.json")
        if FileManager.default.fileExists(atPath: incoming.path) {
            let data = try Data(contentsOf: incoming)
            let binding = try decode(data)
            let attributes: [String: Any] = [
                kSecValueData as String: data,
                kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            ]
            var status = SecItemUpdate(item as CFDictionary, attributes as CFDictionary)
            if status == errSecItemNotFound {
                status = SecItemAdd(item.merging(attributes) { _, new in new } as CFDictionary, nil)
            }
            guard status == errSecSuccess else { throw WorkshopError.pairing }
            try FileManager.default.removeItem(at: incoming)
            return binding
        }
        var result: CFTypeRef?
        let query = item.merging([kSecReturnData as String: true]) { _, new in new }
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { throw WorkshopError.pairing }
        return try decode(data)
    }

    private static func decode(_ data: Data) throws -> Self {
        let pairing = try JSONDecoder().decode(Self.self, from: data)
        guard pairing.host.hasSuffix(".local"), (1...65535).contains(pairing.port),
              pairing.token.utf8.count == 64,
              SecCertificateCreateWithData(nil, pairing.certificate as CFData) != nil
        else { throw WorkshopError.pairing }
        return pairing
    }
}

private final class WorkshopTLS: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    let pairing: WorkshopPairing
    init(_ pairing: WorkshopPairing) { self.pairing = pairing }

    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              (challenge.protectionSpace.host.caseInsensitiveCompare(pairing.host) == .orderedSame
               || challenge.protectionSpace.host == pairing.address),
              let trust = challenge.protectionSpace.serverTrust,
              let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
              let leaf = chain.first,
              SecCertificateCopyData(leaf) as Data == pairing.certificate
        else { completionHandler(.cancelAuthenticationChallenge, nil); return }
        completionHandler(.useCredential, URLCredential(trust: trust))
    }

    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

final class ZapWorkshopConnection {
    private var activeHost: String
    let pairing: WorkshopPairing
    private let session: URLSession

    init() throws {
        let binding = try WorkshopPairing.load()
        pairing = binding
        activeHost = binding.host.lowercased()
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 8
        config.timeoutIntervalForResource = 12
        config.httpShouldSetCookies = false
        config.urlCache = nil
        session = URLSession(configuration: config, delegate: WorkshopTLS(binding), delegateQueue: nil)
    }

    func close() { session.invalidateAndCancel() }

    func request(uid: Data? = nil) async throws -> String {
        if uid != nil { return try await send(uid: uid, host: activeHost) }
        // Prefer the known Wi-Fi address. Bonjour still works after the Mac gets a new address.
        for host in [pairing.address, pairing.host.lowercased()].compactMap({ $0 }) {
            do {
                let value = try await send(uid: nil, host: host)
                guard value == "ready" else { throw WorkshopError.station }
                activeHost = host
                return value
            } catch let error as WorkshopError { throw error }
            catch { if host == pairing.host.lowercased() { throw error } }
        }
        throw WorkshopError.station
    }

    private func send(uid: Data?, host: String) async throws -> String {
        var parts = URLComponents()
        parts.scheme = "https"
        parts.host = host
        parts.port = pairing.port
        parts.path = uid == nil ? "/ready" : "/issue"
        guard let url = parts.url else { throw WorkshopError.pairing }
        var request = URLRequest(url: url)
        request.setValue("Bearer " + pairing.token, forHTTPHeaderField: "Authorization")
        if let uid {
            request.httpMethod = "POST"
            request.setValue("text/plain", forHTTPHeaderField: "Content-Type")
            request.httpBody = Data(uid.map { String(format: "%02X", $0) }.joined().utf8)
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw WorkshopError.station }
        if http.statusCode == 403 { throw WorkshopError.pairing }
        guard http.statusCode == 200, data.count <= 256,
              let value = String(data: data, encoding: .utf8) else { throw WorkshopError.station }
        return value
    }
}
