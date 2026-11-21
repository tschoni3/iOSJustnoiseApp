import CryptoKit
import Foundation

/// An issuer signature is the offline registration for one physical UID.
/// The AES key ships to readers; only the separate factory signing key grants access.
struct RegisteredZap: Decodable {
    let verificationKey: Data
    let contentKey: Data

    static let bundled = load("ZapTrust")
    #if ZAP_READER_TEST_TRUST
    static let previousOperatorTestIssuer = load("ZapWorkshopTestTrust")
    #endif

    private static func load(_ name: String) -> RegisteredZap? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(RegisteredZap.self, from: data)
    }

    func accepts(text: String, physicalUID: Data) -> Bool {
        let prefix = "JNZ1."
        let product = Data("justnoise.zap.activation.v1".utf8)
        let signatureSize = 64
        let sealedSize = 12 + product.count + 16  // AES-GCM nonce, ciphertext and tag
        guard physicalUID.count == 7, contentKey.count == 32,
              text.utf8.count <= 256, text.hasPrefix(prefix),
              let envelope = Data(base64Encoded: String(text.dropFirst(prefix.count))),
              envelope.count == signatureSize + sealedSize,
              let issuer = try? Curve25519.Signing.PublicKey(rawRepresentation: verificationKey)
        else { return false }

        let binding = Data("JustNoise/Zap/1".utf8) + physicalUID
        let sealed = envelope.dropFirst(signatureSize)
        guard issuer.isValidSignature(envelope.prefix(signatureSize), for: binding + sealed),
              let box = try? AES.GCM.SealedBox(combined: sealed),
              let value = try? AES.GCM.open(box, using: SymmetricKey(data: contentKey), authenticating: binding)
        else { return false }
        return value == product
    }
}
