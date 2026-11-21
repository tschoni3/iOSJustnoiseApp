import Foundation
import XCTest
@testable import justnoiseradio

final class RegisteredZapTests: XCTestCase {
    // Emitted by the factory CLI using a disposable issuer. No deployed tag or issuer is used.
    private let uid = Data([4, 16, 32, 48, 64, 80, 96])
    private let otherUID = Data([4, 16, 32, 48, 64, 80, 97])
    private let text = "JNZ1.4IOUMPu/Cn4gmwGoLeqJskYXNWnJKLCKF4BdWQZLuVmFyRxUn783LWQkGCre42SrmtQlCBC1RIjkppUOMYGJBqb4VKiDzwgOddayJ8SPzIlgJeCJEsUWalW70KdV+Y3T0xd50DaSIGVhZ8VPxIkBCi72puVlBWE="
    private let trust = RegisteredZap(
        verificationKey: Data(base64Encoded: "g2LYdJ6JDZ34kf5lknB2KRTWC5qe9I9ePZBspl4SjME=")!,
        contentKey: Data(base64Encoded: "x5pfrprIBU95PZqeV4gCDrwio5NDCbLjlgv5PvDizoA=")!
    )

    private func record(_ text: String) -> ZapNDEFRecordInput {
        ZapNDEFRecordInput(typeNameFormat: .wellKnown, type: Data([0x54]),
                           payload: Data([2]) + Data("en".utf8) + Data(text.utf8))
    }

    private func accepts(_ values: [String], _ physicalUID: Data, purpose: ScanningPurpose = .activation,
                         activated: Bool = false) -> Bool {
        ZapScanAuthorization.accepts(records: values.map(record), uid: physicalUID, issuer: trust,
                                     purpose: purpose, isActivated: activated,
                                     legacyLabels: ["legacy-test"])
    }

    func testFactoryRecordRequiresItsLiveUIDOnEachDevice() {
        XCTAssertTrue(accepts([text], uid))
        XCTAssertTrue(accepts([text], uid, purpose: .sessionToggle, activated: true))
        XCTAssertFalse(accepts([text], otherUID))
        XCTAssertFalse(accepts([text], otherUID, purpose: .sessionToggle, activated: true))
        XCTAssertFalse(accepts([text], Data()))
        XCTAssertFalse(accepts([text, text], uid))
        XCTAssertFalse(accepts([text], uid, purpose: .sessionToggle))
    }

    func testStationEncodingAndEarlierUTF16Writes() throws {
        XCTAssertEqual(ZapTextPayload.encode(text), record(text).payload)
        let header = Data([0x82, 0x65, 0x6e])
        let littleEndian = Data([0xff, 0xfe]) + (try XCTUnwrap(text.data(using: .utf16LittleEndian)))
        let bigEndian = try XCTUnwrap(text.data(using: .utf16BigEndian))
        func input(_ body: Data) -> ZapNDEFRecordInput {
            ZapNDEFRecordInput(typeNameFormat: .wellKnown, type: Data([0x54]), payload: header + body)
        }
        func allowed(_ records: [ZapNDEFRecordInput], _ physicalUID: Data,
                     purpose: ScanningPurpose = .activation) -> Bool {
            ZapScanAuthorization.accepts(records: records, uid: physicalUID, issuer: trust, purpose: purpose,
                                         isActivated: purpose == .sessionToggle, legacyLabels: ["legacy-test"])
        }
        for encoded in [littleEndian, Data([0xfe, 0xff]) + bigEndian, bigEndian] {
            let signed = input(encoded)
            XCTAssertTrue(allowed([signed], uid))
            XCTAssertTrue(allowed([signed], uid, purpose: .sessionToggle))
            XCTAssertFalse(allowed([signed], otherUID))
            XCTAssertFalse(allowed([record("legacy-test"), signed], uid))
        }
        XCTAssertFalse(allowed([input(littleEndian + Data([0]))], uid))
        let legacyUTF16 = try XCTUnwrap("legacy-test".data(using: .utf16BigEndian))
        XCTAssertFalse(allowed([input(legacyUTF16)], uid))
    }

    func testDamagedOrForeignRegistrationFailsClosed() throws {
        let bytes = try XCTUnwrap(Data(base64Encoded: String(text.dropFirst(5))))
        for index in [0, 64, bytes.count - 1] {
            var altered = bytes
            altered[index] ^= 1
            XCTAssertFalse(accepts(["JNZ1." + altered.base64EncodedString()], uid))
        }
        XCTAssertFalse(accepts(["JNZ1.?"], uid))
        let production = try XCTUnwrap(RegisteredZap.bundled)
        XCTAssertFalse(production.accepts(text: text, physicalUID: uid))
        let wrongContentKey = RegisteredZap(verificationKey: trust.verificationKey, contentKey: Data(repeating: 0, count: 32))
        XCTAssertFalse(wrongContentKey.accepts(text: text, physicalUID: uid))
    }

    func testPreviouslySoldTagsCanActivateFreshInstallationsAndToggleSessions() {
        XCTAssertTrue(accepts(["legacy-test"], uid))
        XCTAssertTrue(accepts(["legacy-test"], uid, purpose: .sessionToggle, activated: true))
        XCTAssertFalse(accepts(["unknown"], uid))
        XCTAssertFalse(accepts(["legacy-test", text], otherUID))
        XCTAssertFalse(accepts(["JNZ1.?", "legacy-test"], uid))
    }
}
