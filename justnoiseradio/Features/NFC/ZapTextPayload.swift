import Foundation

enum ZapTextPayload {
    static let type = Data([0x54]) // NFC Forum well-known Text record

    static func encode(_ text: String) -> Data {
        // UTF-8, language code "en". Keep the station's wire format deterministic.
        Data([0x02, 0x65, 0x6E]) + Data(text.utf8)
    }

    static func decode(_ payload: Data, allowsUTF16: Bool) -> String? {
        guard let status = payload.first, status & 0x40 == 0 else { return nil }
        let languageLength = Int(status & 0x3F)
        guard languageLength > 0, payload.count > 1 + languageLength else { return nil }
        let body = payload.dropFirst(1 + languageLength)
        if status & 0x80 == 0 { return String(data: body, encoding: .utf8) }
        guard allowsUTF16, body.count.isMultiple(of: 2) else { return nil }
        // External UTF-16 uses its BOM for byte order, defaulting to big-endian.
        return String(data: body, encoding: .utf16)
    }
}
