import Foundation

// Run together with StationTagContent.swift using swiftc.
func record(_ format: UInt8, _ type: String = "", _ payload: [UInt8] = [],
            id: [UInt8] = []) -> StationTagContent {
    StationTagContent(format: format, type: Data(type.utf8), id: Data(id), payload: Data(payload))
}

let en: [UInt8] = [2, 0x65, 0x6E]
let cases: [(String, StationTagContent, StationTagContent)] = [
    ("empty record", record(0), .blank),
    ("untyped placeholder", record(5), .blank),
    ("empty text", record(1, "T", en), .blank),
    ("empty UTF-16 text", record(1, "T", [0x82, 0x65, 0x6E, 0xFE, 0xFF]), .blank),
    ("empty URI", record(1, "U", [0]), .blank),
    ("regular text", record(1, "T", en + Array("existing".utf8)), .text),
    ("signed text", record(1, "T", en + Array("JNZ1.record".utf8)), .text),
    ("whitespace can be user data", record(1, "T", en + [0x20]), .text),
    ("unknown language field", record(1, "T", [2, 0, 0]), .text),
    ("truncated text header", record(1, "T", [3, 0x65, 0x6E]), .text),
    ("reserved status bit", record(1, "T", [0x40]), .text),
    ("URI with prefix", record(1, "U", [3]), .link),
    ("URI without prefix", record(1, "U", [0] + Array("urn:test".utf8)), .link),
    ("absolute URI", record(3, "urn:test"), .link),
    ("ID alone", record(5, id: [1]), .other),
    ("unknown data", record(5, "", [0]), .other),
    ("empty MIME payload retains type", record(2, "text/plain"), .other),
    ("external record retains type", record(4, "example.com:test"), .other)
]
for (name, actual, expected) in cases { precondition(actual == expected, name) }
print("Passed \(cases.count) station record checks.")
