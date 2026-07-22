import Foundation
import XCTest
@testable import justnoiseradio

final class SignalBackendContractFixtureTests: XCTestCase {
    private let releasedResponseFields: Set<String> = [
        "contractVersion",
        "engineVersion",
        "operationId",
        "baseMemoryRevision",
        "nextMemoryRevision",
        "requiresExactBaseRevision",
        "capturePatch",
        "memoryPatch",
        "commentDecision",
        "safety",
        "comment",
        "serverStorage",
    ]

    func testReleasedCommentFixtureDecodesThroughProductionDTOs() throws {
        let response = try decodeResponseFixture(named: "comment.json")

        XCTAssertEqual(response.contractVersion, 1)
        XCTAssertEqual(response.engineVersion, "justnoise-backend-v1")
        XCTAssertEqual(response.baseMemoryRevision, 7)
        XCTAssertEqual(response.nextMemoryRevision, 8)
        XCTAssertTrue(response.requiresExactBaseRevision)
        XCTAssertEqual(response.capturePatch.captureId, "11111111-1111-4111-8111-111111111111")
        XCTAssertEqual(response.capturePatch.signalStrength, 0.91)
        XCTAssertEqual(response.memoryPatch.operations.map(\.type), [.createThread])
        XCTAssertEqual(response.memoryPatch.operations.first?.thread?.topicKey, "promotion conflict")
        XCTAssertEqual(response.commentDecision.type, "mirror")
        XCTAssertEqual(response.commentDecision.shouldShow, true)
        XCTAssertEqual(response.safety.isSafe, true)
        XCTAssertEqual(response.comment?.anchorCaptureId, "11111111-1111-4111-8111-111111111111")
        XCTAssertEqual(response.comment?.threadIds, ["thread-promotion-conflict"])
        XCTAssertEqual(response.serverStorage, "none")
    }

    func testReleasedExistingThreadFixtureDecodesOperationVariants() throws {
        let response = try decodeResponseFixture(named: "existing-thread.json")

        XCTAssertEqual(
            response.memoryPatch.operations.map(\.type),
            [.appendCaptureToThread, .updateThreadState]
        )
        XCTAssertEqual(response.memoryPatch.operations[0].threadId, "thread-promotion-conflict")
        XCTAssertEqual(
            response.memoryPatch.operations[1].threadPatch?.captureIds,
            [
                "22222222-2222-4222-8222-222222222222",
                "33333333-3333-4333-8333-333333333333",
                "11111111-1111-4111-8111-111111111111",
            ]
        )
        XCTAssertEqual(response.memoryPatch.operations[1].threadPatch?.intensityTrend, "rising")
        XCTAssertEqual(response.comment?.state, "visible")
    }

    func testReleasedSilenceFixtureDecodesWithoutInventingContent() throws {
        let response = try decodeResponseFixture(named: "silence.json")

        XCTAssertEqual(response.baseMemoryRevision, 8)
        XCTAssertEqual(response.nextMemoryRevision, 9)
        XCTAssertTrue(response.memoryPatch.operations.isEmpty)
        XCTAssertEqual(response.commentDecision.type, "silence")
        XCTAssertEqual(response.commentDecision.shouldShow, false)
        XCTAssertEqual(response.commentDecision.blockReason, "transcript_too_short")
        XCTAssertEqual(response.safety.isSafe, true)
        XCTAssertNil(response.comment)
    }

    func testReleasedSafetyHoldFixtureDecodesSafetyState() throws {
        let response = try decodeResponseFixture(named: "safety-hold.json")

        XCTAssertEqual(response.baseMemoryRevision, 9)
        XCTAssertEqual(response.nextMemoryRevision, 10)
        XCTAssertTrue(response.memoryPatch.operations.isEmpty)
        XCTAssertEqual(response.commentDecision.blockReason, "safety_hold")
        XCTAssertEqual(response.safety.isSafe, false)
        XCTAssertEqual(response.safety.blocked, true)
        XCTAssertEqual(response.safety.hold, true)
        XCTAssertEqual(response.safety.categories, ["immediate danger"])
        XCTAssertNil(response.comment)
    }

    func testReleasedSuccessFixturesHaveNoIgnoredTopLevelFields() throws {
        for fixtureName in ["comment.json", "existing-thread.json", "silence.json", "safety-hold.json"] {
            let data = try fixtureData(named: fixtureName)
            let object = try XCTUnwrap(
                JSONSerialization.jsonObject(with: data) as? [String: Any],
                "Expected an object in \(fixtureName)"
            )

            XCTAssertEqual(
                Set(object.keys),
                releasedResponseFields,
                "BackendCaptureResponse intentionally ignores no released top-level field in \(fixtureName)"
            )
        }
    }

    func testReleasedErrorFixtureCharacterizesIgnoredCodeField() throws {
        let data = try fixtureData(named: "error.json")
        let fixture = try JSONDecoder().decode(ReleasedErrorFixture.self, from: data)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(fixture.error, "Authentication is required.")
        XCTAssertEqual(fixture.code, "unauthenticated")
        XCTAssertEqual(Set(object.keys).subtracting(["error"]), ["code"])
        // The production V1 error path consumes the human-readable `error` string and HTTP status.
        // It has no error-envelope DTO, so the released `code` field is intentionally ignored today.
    }

    private func decodeResponseFixture(named name: String) throws -> BackendCaptureResponse {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(BackendCaptureResponse.self, from: fixtureData(named: name))
    }

    private func fixtureData(named name: String) throws -> Data {
        try Data(contentsOf: fixtureDirectory.appendingPathComponent(name))
    }

    private var fixtureDirectory: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("product/api/signal/v1/fixtures", isDirectory: true)
    }
}

private struct ReleasedErrorFixture: Decodable, Equatable {
    let error: String
    let code: String
}
