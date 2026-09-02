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

final class ZapEntryModeRulesTests: XCTestCase {
    func testPortableDirectEntryUsesOnlyTheRecentExplicitMode() throws {
        let fixtures = try PortableBehaviorFixtureLoader.load()

        for testCase in fixtures.zapEntryModeResolution {
            XCTAssertEqual(
                ZapEntryModeRules.resolve(
                    sessionActive: testCase.sessionActive,
                    recentModeExists: testCase.recentModeExists,
                    recentModeSelectionValid: testCase.recentModeSelectionValid
                ),
                testCase.expectedAction,
                testCase.id
            )
        }
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

final class PortableScheduleRulesTests: XCTestCase {
    func testPortableScheduleEligibilityCases() throws {
        let fixtures = try PortableBehaviorFixtureLoader.load()

        for testCase in fixtures.scheduleEligibility {
            XCTAssertEqual(
                PortableScheduleRules.evaluate(
                    isEnabled: testCase.isEnabled,
                    modeResolvable: testCase.modeResolvable,
                    targetSelectionValid: testCase.targetSelectionValid,
                    kind: testCase.repeatWeekdays.isEmpty ? .oneTime : .repeating
                ),
                PortableScheduleEligibilityDecision(
                    kind: testCase.expectedKind,
                    isEligible: testCase.expectedEligible,
                    reason: testCase.expectedReason
                ),
                testCase.id
            )
        }
    }

    func testPortableOneTimeResolutionCases() throws {
        let temporal = try PortableBehaviorFixtureLoader.load().scheduleTemporal
        XCTAssertEqual(
            PortableScheduleTimingRules.maximumEpochMilliseconds,
            temporal.maximumEpochMilliseconds
        )
        XCTAssertFalse(temporal.oneTimeResolutionCases.isEmpty)

        for testCase in temporal.oneTimeResolutionCases {
            XCTAssertEqual(
                PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds(
                    localDate: testCase.localDate,
                    hour: testCase.hour,
                    minute: testCase.minute,
                    zoneId: testCase.zoneId,
                    nowEpochMilliseconds: try epochMilliseconds(testCase.now)
                ),
                try testCase.expectedInstant.map(epochMilliseconds),
                testCase.id
            )
        }
    }

    func testPortableNextOccurrenceCases() throws {
        let cases = try PortableBehaviorFixtureLoader.load().scheduleTemporal.nextOccurrenceCases
        XCTAssertFalse(cases.isEmpty)

        for testCase in cases {
            let occurrence = PortableScheduleTimingRules.nextOccurrence(
                timing: PortableScheduleTiming(
                    oneTimeEpochMilliseconds: try testCase.timing.oneTimeInstant.map(epochMilliseconds),
                    repeatWeekdays: Set(testCase.timing.repeatWeekdays),
                    hour: testCase.timing.hour,
                    minute: testCase.timing.minute
                ),
                afterExclusiveEpochMilliseconds: try epochMilliseconds(testCase.after),
                zoneId: testCase.zoneId,
                lastResolvedLocalDate: testCase.lastResolvedLocalDate
            )
            XCTAssertEqual(
                occurrence?.epochMilliseconds,
                try testCase.expectedInstant.map(epochMilliseconds),
                testCase.id
            )
            XCTAssertEqual(occurrence?.key, testCase.expectedKey, testCase.id)
            let expectedLocalDate = testCase.expectedKey.flatMap { key in
                key.hasPrefix("repeating:") ? String(key.split(separator: ":")[1]) : nil
            }
            XCTAssertEqual(occurrence?.localDate, expectedLocalDate, testCase.id)
        }
    }

    func testPortableScheduleDeliveryCases() throws {
        let temporal = try PortableBehaviorFixtureLoader.load().scheduleTemporal
        XCTAssertEqual(PortableScheduleDeliveryRules.graceMilliseconds, temporal.graceMilliseconds)
        XCTAssertFalse(temporal.deliveryCases.isEmpty)

        for testCase in temporal.deliveryCases {
            XCTAssertEqual(
                PortableScheduleDeliveryRules.evaluate(
                    currentRevisionEnabled: testCase.currentRevisionEnabled,
                    alreadyHandled: testCase.alreadyHandled,
                    sessionRequested: testCase.sessionRequested,
                    scheduledAtEpochMilliseconds: testCase.scheduledAtEpochMilliseconds,
                    nowEpochMilliseconds: testCase.nowEpochMilliseconds
                ),
                testCase.expected,
                testCase.id
            )
        }
    }

    func testScheduleTimingShapesAndSupportedEpochBoundaries() {
        let maximum = PortableScheduleTimingRules.maximumEpochMilliseconds
        let valid = [
            PortableScheduleTiming(oneTimeEpochMilliseconds: 0),
            PortableScheduleTiming(oneTimeEpochMilliseconds: maximum),
            PortableScheduleTiming(repeatWeekdays: [.monday, .friday], hour: 0, minute: 59),
        ]
        let invalid = [
            PortableScheduleTiming(),
            PortableScheduleTiming(oneTimeEpochMilliseconds: -1),
            PortableScheduleTiming(oneTimeEpochMilliseconds: maximum + 1),
            PortableScheduleTiming(oneTimeEpochMilliseconds: Int64.max),
            PortableScheduleTiming(oneTimeEpochMilliseconds: 0, hour: 9),
            PortableScheduleTiming(oneTimeEpochMilliseconds: 0, repeatWeekdays: [.monday], hour: 9, minute: 0),
            PortableScheduleTiming(repeatWeekdays: [.monday], hour: 9),
            PortableScheduleTiming(repeatWeekdays: [.monday], hour: -1, minute: 0),
            PortableScheduleTiming(repeatWeekdays: [.monday], hour: 9, minute: 60),
        ]
        for timing in valid { XCTAssertTrue(PortableScheduleTimingRules.isValid(timing), "\(timing)") }
        for timing in invalid { XCTAssertFalse(PortableScheduleTimingRules.isValid(timing), "\(timing)") }

        XCTAssertEqual(
            PortableScheduleTimingRules.nextOccurrence(
                timing: PortableScheduleTiming(oneTimeEpochMilliseconds: maximum),
                afterExclusiveEpochMilliseconds: maximum - 1,
                zoneId: "UTC"
            ),
            PortableScheduleOccurrence(key: "oneTime:\(maximum)", epochMilliseconds: maximum)
        )
        let repeating = PortableScheduleTiming(repeatWeekdays: Set(PortableWeekday.allCases), hour: 9, minute: 0)
        for after in [-1, maximum, Int64.max] {
            XCTAssertNil(PortableScheduleTimingRules.nextOccurrence(
                timing: repeating, afterExclusiveEpochMilliseconds: after, zoneId: "UTC"
            ))
        }
        XCTAssertEqual(
            PortableScheduleDeliveryRules.evaluate(
                currentRevisionEnabled: true, alreadyHandled: false, sessionRequested: false,
                scheduledAtEpochMilliseconds: maximum, nowEpochMilliseconds: maximum
            ), .startCandidate
        )
        for invalidEpoch in [Int64.min, maximum + 1, Int64.max] {
            XCTAssertEqual(
                PortableScheduleDeliveryRules.evaluate(
                    currentRevisionEnabled: true, alreadyHandled: false, sessionRequested: false,
                    scheduledAtEpochMilliseconds: invalidEpoch, nowEpochMilliseconds: maximum
                ), .invalid
            )
            XCTAssertEqual(
                PortableScheduleDeliveryRules.evaluate(
                    currentRevisionEnabled: true, alreadyHandled: false, sessionRequested: false,
                    scheduledAtEpochMilliseconds: 0, nowEpochMilliseconds: invalidEpoch
                ), .invalid
            )
        }
    }

    func testScheduleRejectsInvalidHighWaterDatesAndNonNamedZones() throws {
        let repeating = PortableScheduleTiming(repeatWeekdays: [.thursday], hour: 9, minute: 0)
        let after = try epochMilliseconds("2026-09-02T00:00:00Z")
        for date in ["1500-02-29", "2026-02-30", "2026-9-03", "0000-01-01", "9999-12-31"] {
            XCTAssertNil(PortableScheduleTimingRules.nextOccurrence(
                timing: repeating, afterExclusiveEpochMilliseconds: after,
                zoneId: "UTC", lastResolvedLocalDate: date
            ), date)
        }
        XCTAssertEqual(
            PortableScheduleTimingRules.nextOccurrence(
                timing: repeating, afterExclusiveEpochMilliseconds: after,
                zoneId: "UTC", lastResolvedLocalDate: "1582-10-10"
            )?.key,
            "repeating:2026-09-03:09:00"
        )
        XCTAssertEqual(
            PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds(
                localDate: "2026-09-03", hour: 9, minute: 0,
                zoneId: "US/Pacific", nowEpochMilliseconds: after
            ),
            try epochMilliseconds("2026-09-03T16:00:00Z")
        )
        for zone in ["", "+02:00", "GMT+02:00", "Not/AZone"] {
            XCTAssertNil(PortableScheduleTimingRules.nextOccurrence(
                timing: repeating, afterExclusiveEpochMilliseconds: after, zoneId: zone
            ), zone)
            XCTAssertNil(PortableScheduleTimingRules.resolveOneTimeEpochMilliseconds(
                localDate: "2026-09-03", hour: 9, minute: 0,
                zoneId: zone, nowEpochMilliseconds: after
            ), zone)
        }
    }

    private func epochMilliseconds(_ instant: String) throws -> Int64 {
        let date = try XCTUnwrap(ISO8601DateFormatter().date(from: instant), instant)
        return Int64((date.timeIntervalSince1970 * 1_000).rounded())
    }
}

final class PortableHistoryRulesTests: XCTestCase {
    func testPortableHistoryDensityCases() throws {
        let history = try PortableBehaviorFixtureLoader.load().historyPresentation

        XCTAssertEqual(
            PortableHistoryRules.lightMinimumSeconds,
            history.densityThresholdSeconds.light
        )
        XCTAssertEqual(
            PortableHistoryRules.mediumMinimumSeconds,
            history.densityThresholdSeconds.medium
        )
        XCTAssertEqual(
            PortableHistoryRules.denseMinimumSeconds,
            history.densityThresholdSeconds.dense
        )

        for testCase in history.densityCases {
            XCTAssertEqual(
                PortableHistoryRules.density(totalSeconds: testCase.totalSeconds),
                testCase.expectedDensity,
                testCase.id
            )
        }
    }

    func testPortableHistoryDurationLabels() throws {
        let history = try PortableBehaviorFixtureLoader.load().historyPresentation

        for testCase in history.durationLabelCases {
            XCTAssertEqual(
                PortableHistoryRules.formatDuration(
                    totalSeconds: testCase.totalSeconds,
                    style: testCase.style
                ),
                testCase.expectedLabel,
                testCase.id
            )
        }
    }

    func testPortableHistorySummaryCases() throws {
        let history = try PortableBehaviorFixtureLoader.load().historyPresentation

        for testCase in history.summaryCases {
            let summary = PortableHistoryRules.summarize(
                durationsSeconds: testCase.durationSeconds
            )
            XCTAssertEqual(summary.zapCount, testCase.expectedZapCount, testCase.id)
            XCTAssertEqual(
                summary.totalSeconds,
                testCase.expectedTotalSeconds,
                accuracy: 0.000_001,
                testCase.id
            )
            XCTAssertEqual(
                summary.protectedAttentionLabel,
                testCase.expectedProtectedAttentionLabel,
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
    let zapEntryModeResolution: [ZapEntryModeResolutionFixture]
    let scheduleEligibility: [PortableScheduleEligibilityFixture]
    let scheduleTemporal: PortableScheduleTemporalFixture
    let historyPresentation: PortableHistoryPresentationFixture
}

private struct ZapEntryModeResolutionFixture: Decodable {
    let id: String
    let sessionActive: Bool
    let recentModeExists: Bool
    let recentModeSelectionValid: Bool
    let expectedAction: ZapEntryModeAction
}

private struct PortableHistoryPresentationFixture: Decodable {
    let densityThresholdSeconds: PortableHistoryDensityThresholdFixture
    let densityCases: [PortableHistoryDensityFixture]
    let durationLabelCases: [PortableHistoryDurationLabelFixture]
    let summaryCases: [PortableHistorySummaryFixture]
}

private struct PortableHistoryDensityThresholdFixture: Decodable {
    let light: TimeInterval
    let medium: TimeInterval
    let dense: TimeInterval
}

private struct PortableHistoryDensityFixture: Decodable {
    let id: String
    let totalSeconds: TimeInterval
    let expectedDensity: PortableHistoryDensity
}

private struct PortableHistoryDurationLabelFixture: Decodable {
    let id: String
    let totalSeconds: TimeInterval
    let style: PortableHistoryDurationStyle
    let expectedLabel: String
}

private struct PortableHistorySummaryFixture: Decodable {
    let id: String
    let durationSeconds: [TimeInterval]
    let expectedZapCount: Int
    let expectedTotalSeconds: TimeInterval
    let expectedProtectedAttentionLabel: String
}

private struct PortableScheduleEligibilityFixture: Decodable {
    let id: String
    let isEnabled: Bool
    let modeResolvable: Bool
    let targetSelectionValid: Bool
    let repeatWeekdays: [PortableWeekday]
    let expectedKind: PortableScheduleKind
    let expectedEligible: Bool
    let expectedReason: PortableScheduleEligibilityReason
}

private struct PortableScheduleTemporalFixture: Decodable {
    let graceMilliseconds: Int64
    let maximumEpochMilliseconds: Int64
    let oneTimeResolutionCases: [PortableOneTimeResolutionFixture]
    let nextOccurrenceCases: [PortableNextOccurrenceFixture]
    let deliveryCases: [PortableScheduleDeliveryFixture]
}

private struct PortableOneTimeResolutionFixture: Decodable {
    let id: String
    let localDate: String
    let hour: Int
    let minute: Int
    let zoneId: String
    let now: String
    let expectedInstant: String?
}

private struct PortableScheduleTimingFixture: Decodable {
    let oneTimeInstant: String?
    let repeatWeekdays: [PortableWeekday]
    let hour: Int?
    let minute: Int?
}

private struct PortableNextOccurrenceFixture: Decodable {
    let id: String
    let timing: PortableScheduleTimingFixture
    let after: String
    let zoneId: String
    let lastResolvedLocalDate: String?
    let expectedInstant: String?
    let expectedKey: String?
}

private struct PortableScheduleDeliveryFixture: Decodable {
    let id: String
    let currentRevisionEnabled: Bool
    let alreadyHandled: Bool
    let sessionRequested: Bool
    let scheduledAtEpochMilliseconds: Int64
    let nowEpochMilliseconds: Int64
    let expected: PortableScheduleDeliveryDecision
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
