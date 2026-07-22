import Foundation
import XCTest
@testable import justnoiseradio

final class TargetSelectionRuleTests: XCTestCase {
    func testPortableTargetSelectionValidityCases() throws {
        let fixtures = try PortableBehaviorFixtureLoader.load()

        for testCase in fixtures.targetSelectionValidity {
            XCTAssertEqual(
                TargetSelectionRule.isValid(
                    applicationCount: testCase.applicationCount,
                    categoryCount: testCase.categoryCount,
                    websiteCount: testCase.websiteCount
                ),
                testCase.expectedValid,
                testCase.id
            )
        }
    }
}

final class ZapReadPolicyTests: XCTestCase {
    func testIdentifierFreeReadOutcomes() throws {
        let fixtures = try PortableBehaviorFixtureLoader.load()

        for testCase in fixtures.zapReadHandling {
            XCTAssertEqual(
                ZapReadPolicy.decide(
                    classification: testCase.payloadClassification,
                    acceptedReadAlreadyHandled: testCase.acceptedReadAlreadyHandled
                ),
                ZapReadDecision(
                    outcome: testCase.expectedOutcome,
                    mutationEligible: testCase.expectedMutationEligible
                ),
                testCase.id
            )
        }
    }

    func testTextPayloadClassificationUsesAuthorizationWithoutExposingShippedIdentifiers() {
        let allowedForTest = "allowed-for-test"
        let authorizedRecord = textRecord(allowedForTest)
        let unauthorizedRecord = textRecord("not-allowed-for-test")

        XCTAssertEqual(
            ZapReadPolicy.classify(
                record: authorizedRecord,
                authorizedPayloads: [allowedForTest]
            ),
            .authorized
        )
        XCTAssertEqual(
            ZapReadPolicy.classify(
                record: unauthorizedRecord,
                authorizedPayloads: [allowedForTest]
            ),
            .unauthorized
        )
        XCTAssertEqual(
            ZapReadPolicy.classify(
                record: ZapNDEFRecordInput(
                    typeNameFormat: .wellKnown,
                    type: Data([0x54]),
                    payload: Data([0x02, 0x65])
                ),
                authorizedPayloads: [allowedForTest]
            ),
            .malformed
        )
    }

    func testUnsupportedTypeNameFormatAndRecordTypeAreMalformed() {
        let allowedForTest = "allowed-for-test"
        let validPayload = textRecord(allowedForTest).payload

        XCTAssertEqual(
            ZapReadPolicy.classify(
                record: ZapNDEFRecordInput(
                    typeNameFormat: .unsupported,
                    type: Data([0x54]),
                    payload: validPayload
                ),
                authorizedPayloads: [allowedForTest]
            ),
            .malformed
        )
        XCTAssertEqual(
            ZapReadPolicy.classify(
                record: ZapNDEFRecordInput(
                    typeNameFormat: .wellKnown,
                    type: Data([0x55]),
                    payload: validPayload
                ),
                authorizedPayloads: [allowedForTest]
            ),
            .malformed
        )
    }

    func testUTF16TextPayloadIsMalformedWhenOnlyUTF8IsSupported() {
        let allowedForTest = "allowed-for-test"
        let utf16StatusByte: UInt8 = 0x80 | 0x02
        let payload = Data([utf16StatusByte]) + Data("en".utf8) + Data([0x00, 0x41])

        XCTAssertEqual(
            ZapReadPolicy.classify(
                record: ZapNDEFRecordInput(
                    typeNameFormat: .wellKnown,
                    type: Data([0x54]),
                    payload: payload
                ),
                authorizedPayloads: [allowedForTest]
            ),
            .malformed
        )
    }

    func testReservedStatusBitAndMissingLanguageCodeAreMalformed() {
        let allowedForTest = "allowed-for-test"
        let records = [
            ZapNDEFRecordInput(
                typeNameFormat: .wellKnown,
                type: Data([0x54]),
                payload: Data([0x42]) + Data("en".utf8) + Data(allowedForTest.utf8)
            ),
            ZapNDEFRecordInput(
                typeNameFormat: .wellKnown,
                type: Data([0x54]),
                payload: Data([0x00]) + Data(allowedForTest.utf8)
            ),
        ]

        for record in records {
            XCTAssertEqual(
                ZapReadPolicy.classify(
                    record: record,
                    authorizedPayloads: [allowedForTest]
                ),
                .malformed
            )
        }
    }

    func testMultipleAuthorizedRecordsProduceOnlyOneMutationEligibleDecision() {
        let allowedForTest = "allowed-for-test"
        let duplicateRecords = [textRecord(allowedForTest), textRecord(allowedForTest)]

        XCTAssertEqual(
            ZapReadPolicy.firstRelevantDecision(
                records: duplicateRecords,
                authorizedPayloads: [allowedForTest],
                acceptedReadAlreadyHandled: false
            ),
            ZapReadDecision(outcome: .accepted, mutationEligible: true)
        )
        XCTAssertEqual(
            ZapReadPolicy.firstRelevantDecision(
                records: duplicateRecords,
                authorizedPayloads: [allowedForTest],
                acceptedReadAlreadyHandled: true
            ),
            ZapReadDecision(outcome: .duplicateIgnored, mutationEligible: false)
        )
    }

    private func textRecord(_ text: String) -> ZapNDEFRecordInput {
        ZapNDEFRecordInput(
            typeNameFormat: .wellKnown,
            type: Data([0x54]),
            payload: Data([0x02]) + Data("en".utf8) + Data(text.utf8)
        )
    }

}

final class AccidentalStopGuardTests: XCTestCase {
    func testPortableThreeSecondBoundaryCases() throws {
        let fixture = try PortableBehaviorFixtureLoader.load().accidentalStopGuard
        XCTAssertEqual(
            AccidentalStopGuard.minimumElapsedSeconds,
            TimeInterval(fixture.minimumElapsedMilliseconds) / 1_000
        )

        for testCase in fixture.cases {
            XCTAssertEqual(
                AccidentalStopGuard.allowsStop(
                    elapsedSeconds: TimeInterval(testCase.elapsedMilliseconds) / 1_000
                ),
                testCase.expectedStopAllowed,
                testCase.id
            )
        }
    }
}

private enum PortableBehaviorFixtureLoader {
    static func load() throws -> PortableBehaviorFixtureDocument {
        let repositoryRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let fixtureURL = repositoryRoot
            .appendingPathComponent("product", isDirectory: true)
            .appendingPathComponent("behavior", isDirectory: true)
            .appendingPathComponent("portable-fixtures.v1.json", isDirectory: false)
        let document = try JSONDecoder().decode(
            PortableBehaviorFixtureDocument.self,
            from: Data(contentsOf: fixtureURL)
        )
        guard document.schemaVersion == 1 else {
            throw FixtureLoadingError.unsupportedSchemaVersion(document.schemaVersion)
        }
        return document
    }
}

private struct PortableBehaviorFixtureDocument: Decodable {
    let schemaVersion: Int
    let targetSelectionValidity: [TargetSelectionFixture]
    let accidentalStopGuard: AccidentalStopGuardFixture
    let zapReadHandling: [ZapReadHandlingFixture]
}

private struct TargetSelectionFixture: Decodable {
    let id: String
    let applicationCount: Int
    let categoryCount: Int
    let websiteCount: Int
    let expectedValid: Bool
}

private struct AccidentalStopGuardFixture: Decodable {
    let minimumElapsedMilliseconds: Int
    let cases: [AccidentalStopGuardCase]
}

private struct AccidentalStopGuardCase: Decodable {
    let id: String
    let elapsedMilliseconds: Int
    let expectedStopAllowed: Bool
}

private struct ZapReadHandlingFixture: Decodable {
    let id: String
    let payloadClassification: ZapPayloadClassification
    let acceptedReadAlreadyHandled: Bool
    let expectedOutcome: ZapReadOutcome
    let expectedMutationEligible: Bool
}

private enum FixtureLoadingError: Error {
    case unsupportedSchemaVersion(Int)
}
