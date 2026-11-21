import Foundation

enum StationTagContent: Equatable {
    case blank, text, link, other

    init(format: UInt8, type: Data, id: Data, payload: Data) {
        guard id.isEmpty else { self = .other; return }
        switch format {
        case 0x00, 0x05: // Empty or Unknown TNF
            self = type.isEmpty && payload.isEmpty ? .blank : .other
        case 0x01 where type == Data([0x54]): // NFC Forum Text
            self = Self.isEmptyText(payload) ? .blank : .text
        case 0x01 where type == Data([0x55]): // NFC Forum URI
            self = payload.isEmpty || payload == Data([0]) ? .blank : .link
        case 0x03: // The type itself holds the absolute URI.
            self = .link
        default:
            self = .other
        }
    }

    private static func isEmptyText(_ payload: Data) -> Bool {
        guard let status = payload.first else { return true }
        guard status & 0x40 == 0 else { return false }
        let languageLength = Int(status & 0x3F)
        let afterStatus = payload.dropFirst()
        guard afterStatus.count >= languageLength else { return false }
        let language = afterStatus.prefix(languageLength)
        guard language.allSatisfy({ byte in
            (65...90).contains(byte) || (97...122).contains(byte) || (48...57).contains(byte) || byte == 45
        }) else { return false }
        let body = afterStatus.dropFirst(languageLength)
        if body.isEmpty { return true }
        // A UTF-16 byte order mark by itself represents zero characters.
        return status & 0x80 != 0 && (body.elementsEqual([0xFE, 0xFF]) || body.elementsEqual([0xFF, 0xFE]))
    }
}
