package com.justnoise.gate0.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PortableFixtureContractTest {
    private val fixture: JSONObject by lazy {
        val stream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("portable-fixtures.v1.json"),
        ) { "product/behavior/portable-fixtures.v1.json is not on the JVM test classpath" }
        stream.bufferedReader().use { JSONObject(it.readText()) }
    }

    @Test
    fun `consumes every target selection fixture through production rule`() {
        val cases = fixture.getJSONArray("targetSelectionValidity")
        assertEquals(5, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            assertEquals(
                case.getString("id"),
                case.getBoolean("expectedValid"),
                TargetSelectionRules.isValid(
                    applicationCount = case.getInt("applicationCount"),
                    categoryCount = case.getInt("categoryCount"),
                    websiteCount = case.getInt("websiteCount"),
                ),
            )
        }
    }

    @Test
    fun `consumes every accidental stop boundary through production rule`() {
        val guard = fixture.getJSONObject("accidentalStopGuard")
        assertEquals(
            AccidentalStopGuard.MINIMUM_ELAPSED_MILLISECONDS,
            guard.getLong("minimumElapsedMilliseconds"),
        )
        val cases = guard.getJSONArray("cases")
        assertEquals(4, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            assertEquals(
                case.getString("id"),
                case.getBoolean("expectedStopAllowed"),
                AccidentalStopGuard.isStopAllowed(case.getLong("elapsedMilliseconds")),
            )
        }
    }

    @Test
    fun `consumes every Zap read fixture through production rule`() {
        val cases = fixture.getJSONArray("zapReadHandling")
        assertEquals(5, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val decision = ZapReadRules.decide(
                payloadClassification = PayloadClassification.entries.single {
                    it.contractValue == case.getString("payloadClassification")
                },
                acceptedReadAlreadyHandled = case.getBoolean("acceptedReadAlreadyHandled"),
            )
            assertEquals(
                case.getString("id"),
                case.getString("expectedOutcome"),
                decision.outcome.contractValue,
            )
            assertEquals(
                case.getString("id"),
                case.getBoolean("expectedMutationEligible"),
                decision.mutationEligible,
            )
        }
    }

    @Test
    fun `consumes every direct Zap entry mode fixture through production rule`() {
        val cases = fixture.getJSONArray("zapEntryModeResolution")
        assertEquals(4, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val action = ZapEntryModeRules.resolve(
                sessionActive = case.getBoolean("sessionActive"),
                recentModeExists = case.getBoolean("recentModeExists"),
                recentModeSelectionValid = case.getBoolean("recentModeSelectionValid"),
            )
            assertEquals(
                case.getString("id"),
                case.getString("expectedAction"),
                action.contractValue,
            )
        }
    }

    @Test
    fun `consumes every session lifecycle fixture through production reducer`() {
        assertEquals("1.5.0", fixture.getString("fixtureSetVersion"))
        val cases = fixture.getJSONArray("sessionLifecycle")
        assertEquals(7, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val decision = SessionReducer.reduce(
                priorState = SessionState.entries.single {
                    it.contractValue == case.getString("priorState")
                },
                readOutcome = ZapReadOutcome.entries.single {
                    it.contractValue == case.getString("readOutcome")
                },
                selectionValid = case.getBoolean("selectionValid"),
                capability = RestrictionCapability.entries.single {
                    it.contractValue == case.getString("capability")
                },
                elapsedMilliseconds = case.getLong("elapsedMilliseconds"),
            )
            assertEquals(
                case.getString("id"),
                case.getString("expectedTransition"),
                decision.transition.contractValue,
            )
            assertEquals(
                case.getString("id"),
                case.getString("expectedState"),
                decision.state.contractValue,
            )
            assertEquals(
                case.getString("id"),
                case.getString("expectedRestrictionAction"),
                decision.restrictionAction.contractValue,
            )
        }
    }

    @Test
    fun `consumes every schedule eligibility fixture through production rule`() {
        val cases = fixture.getJSONArray("scheduleEligibility")
        assertEquals(5, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val weekdayValues = case.getJSONArray("repeatWeekdays")
            val weekdays = (0 until weekdayValues.length()).map { weekdayIndex ->
                PortableWeekday.entries.single {
                    it.contractValue == weekdayValues.getString(weekdayIndex)
                }
            }
            val decision = PortableScheduleRules.evaluate(
                isEnabled = case.getBoolean("isEnabled"),
                modeResolvable = case.getBoolean("modeResolvable"),
                targetSelectionValid = case.getBoolean("targetSelectionValid"),
                kind = if (weekdays.isEmpty()) {
                    PortableScheduleKind.ONE_TIME
                } else {
                    PortableScheduleKind.REPEATING
                },
            )

            assertEquals(case.getString("id"), case.getString("expectedKind"), decision.kind.contractValue)
            assertEquals(case.getString("id"), case.getBoolean("expectedEligible"), decision.isEligible)
            assertEquals(case.getString("id"), case.getString("expectedReason"), decision.reason.contractValue)
        }
    }

    @Test
    fun `consumes every shield substitution fixture through production renderer`() {
        val cases = fixture.getJSONArray("shieldMessageRendering")
        assertEquals(3, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val fallback = when (case.getString("fallbackCopyKey")) {
                "shield.application.fallbackName" ->
                    ShieldMessageRenderer.APPLICATION_FALLBACK_NAME

                "shield.website.fallbackName" -> ShieldMessageRenderer.WEBSITE_FALLBACK_NAME
                else -> error("Unknown fixture fallback key")
            }
            assertEquals(
                case.getString("id"),
                case.getString("expectedMessage"),
                ShieldMessageRenderer.render(case.getString("displayName"), fallback),
            )
        }
    }
}
