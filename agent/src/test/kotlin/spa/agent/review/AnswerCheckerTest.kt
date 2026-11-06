package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import spa.agent.review.ReviewTestAnswers.answer
import spa.agent.review.ReviewTestAnswers.finding
import spa.agent.review.ReviewTestAnswers.table
import spa.core.baseline.Stat

class AnswerCheckerTest {
    private val agent = FakeAgent("fake-a")
    private val names = setOf("Bear", "Smite", "Envoy", "Sun // Moon")

    private fun check(text: String, maxFindings: Int = 8) =
        AnswerChecker.check(text, agent, names, table, maxFindings)

    private fun schemaMessage(text: String): String =
        assertFailsWith<SchemaError> { check(text) }.message.orEmpty()

    private fun assertNames(field: String, text: String) {
        val message = schemaMessage(text)
        assertTrue(message.contains(field), "'$message' should name '$field'")
    }

    @Test
    fun `a valid answer is accepted with its fields`() {
        val text =
            answer(
                finding(
                    "Two drops are plentiful.",
                    "[\"Bear\", \"Envoy\"]",
                    pair = "\"WU\"",
                    severity = "key",
                ),
                confidence = "0.75",
                summary = "Fine pool.",
            )
        val checked = check(text)

        val expected =
            Finding(
                "Two drops are plentiful.",
                "WU",
                Severity.KEY,
                listOf("Bear", "Envoy"),
                emptyList(),
            )
        assertEquals(AgentAnswer("fake-a", 0.75, "Fine pool.", listOf(expected)), checked.answer)
        assertEquals(emptyList(), checked.dropped)
    }

    @Test
    fun `an answer in a code fence or a sentence is accepted`() {
        val body = answer(finding())
        assertEquals(1, check("```json\n$body\n```").answer.findings.size)
        assertEquals(1, check("Here is my answer: $body Hope that helps.").answer.findings.size)
    }

    @Test
    fun `an answer that is not JSON is a schema error`() {
        assertNames("JSON", "I think the pool is fine.")
        assertNames("JSON", "{\"agent\": ")
        assertNames("JSON", "[1, 2]")
    }

    @Test
    fun `an unknown key at the top and inside a finding is a schema error naming it`() {
        assertNames("extra", answer(finding()).removeSuffix("}") + ", \"extra\": 1}")
        assertNames("bonus", answer(finding().removeSuffix("}") + ", \"bonus\": 1}"))
        val number =
            answer(finding(numbers = "[{\"key\": \"/removal/count\", \"value\": 2, \"note\": 1}]"))
        assertNames("note", number)
    }

    @Test
    fun `a missing field is a schema error naming it`() {
        val fields =
            mapOf(
                "agent" to "\"agent\": \"fake-a\", ",
                "confidence" to "\"confidence\": 0.8, ",
                "summary" to "\"summary\": \"A summary.\", ",
            )
        for ((field, part) in fields) {
            assertNames(field, answer(finding()).replace(part, ""))
        }
        val noFindings = """{"agent": "fake-a", "confidence": 0.5, "summary": "s"}"""
        assertNames("findings", noFindings)
        assertNames("claim", answer(finding().replace("\"claim\": \"A claim.\", ", "")))
        assertNames("key", answer(finding(numbers = "[{\"value\": 2}]")))
    }

    @Test
    fun `a wrong type is a schema error naming the field`() {
        assertNames("confidence", answer(finding(), confidence = "\"high\""))
        assertNames("summary", answer(finding()).replace("\"A summary.\"", "3"))
        assertNames("cards", answer(finding(cards = "\"Bear\"")))
        assertNames("cards", answer(finding(cards = "[3]")))
        assertNames("pair", answer(finding(pair = "5")))
        assertNames("numbers", answer(finding(numbers = "{}")))
        assertNames(
            "value",
            answer(finding(numbers = "[{\"key\": \"/removal/count\", \"value\": \"2\"}]")),
        )
    }

    @Test
    fun `a bad severity, confidence or agent id is a schema error naming the field`() {
        assertNames("severity", answer(finding(severity = "huge")))
        assertNames("confidence", answer(finding(), confidence = "1.5"))
        assertNames("confidence", answer(finding(), confidence = "-0.1"))
        assertNames("agent", answer(finding(), agentId = "other"))
    }

    @Test
    fun `findings that is not a list is a schema error`() {
        val text = """{"agent": "fake-a", "confidence": 0.5, "summary": "s", "findings": {}}"""
        assertNames("findings", text)
    }

    @Test
    fun `a finding with no cards is dropped`() {
        val checked = check(answer(finding("No cards here.", cards = "[]")))
        assertEquals(emptyList(), checked.answer.findings)
        assertEquals(listOf(DroppedFinding(DropReason.NO_CARD, "No cards here.")), checked.dropped)
    }

    @Test
    fun `a card not in the pool drops the finding even beside a pool card`() {
        val checked =
            check(
                answer(
                    finding("Ghost.", cards = "[\"Ghost\"]"),
                    finding("Mixed.", cards = "[\"Bear\", \"Ghost\"]"),
                    finding("Case.", cards = "[\"bear\"]"),
                )
            )
        assertEquals(emptyList(), checked.answer.findings)
        assertEquals(listOf("Ghost.", "Mixed.", "Case."), checked.dropped.map { it.claim })
        assertTrue(checked.dropped.all { it.reason == DropReason.UNKNOWN_CARD })
    }

    @Test
    fun `a front face name of a multi face pool card is kept as the full name`() {
        val checked = check(answer(finding(cards = "[\"Sun\", \"Bear\", \"Sun // Moon\"]")))
        assertEquals(
            listOf("Sun // Moon", "Bear", "Sun // Moon"),
            checked.answer.findings.single().cards,
        )
        val back = check(answer(finding(cards = "[\"Moon\"]")))
        assertEquals(DropReason.UNKNOWN_CARD, back.dropped.single().reason)
    }

    @Test
    fun `a key outside the table or a wrong value drops the finding`() {
        val unknown = check(answer(finding(numbers = "[{\"key\": \"/nope\", \"value\": 1}]")))
        assertEquals(DropReason.UNKNOWN_KEY, unknown.dropped.single().reason)
        val wrong =
            check(answer(finding(numbers = "[{\"key\": \"/removal/count\", \"value\": 3}]")))
        assertEquals(DropReason.WRONG_VALUE, wrong.dropped.single().reason)
        assertEquals(emptyList(), wrong.answer.findings)
    }

    @Test
    fun `the first reason that applies is the one given`() {
        val badNumber = "[{\"key\": \"/nope\", \"value\": 1}]"
        val noCard = check(answer(finding(cards = "[]", numbers = badNumber)))
        assertEquals(DropReason.NO_CARD, noCard.dropped.single().reason)
        val unknownCard = check(answer(finding(cards = "[\"Ghost\"]", numbers = badNumber)))
        assertEquals(DropReason.UNKNOWN_CARD, unknownCard.dropped.single().reason)
    }

    @Test
    fun `a finding with no numbers is kept and kept numbers carry the table values and typical`() {
        val checked =
            check(
                answer(
                    finding("Plain."),
                    finding(
                        "Numbers.",
                        numbers =
                            "[{\"key\": \"/curve/twoDrops\", \"value\": 5.00001}, " +
                                "{\"key\": \"/removal/count\", \"value\": 2}]",
                    ),
                )
            )
        assertEquals(2, checked.answer.findings.size)
        assertEquals(emptyList(), checked.answer.findings[0].numbers)
        val typical = Stat(median = 4.0, p10 = 2.0, p90 = 6.0, mean = 4.0)
        assertEquals(
            listOf(
                AnswerNumber("/curve/twoDrops", 5.0, typical),
                AnswerNumber("/removal/count", 2.0, null),
            ),
            checked.answer.findings[1].numbers,
        )
    }

    @Test
    fun `findings past the limit are dropped over limit and dropped ones do not count`() {
        val ten = (0 until 10).map { finding("c$it") }
        val checked = check(answer(*ten.toTypedArray()), maxFindings = 8)
        assertEquals((0 until 8).map { "c$it" }, checked.answer.findings.map { it.claim })
        assertEquals(listOf("c8", "c9"), checked.dropped.map { it.claim })
        assertTrue(checked.dropped.all { it.reason == DropReason.OVER_LIMIT })

        val good = (0 until 9).map { finding("g$it") }
        val mixed =
            check(answer(finding("bad", cards = "[]"), *good.toTypedArray()), maxFindings = 8)
        assertEquals(8, mixed.answer.findings.size)
        assertEquals(
            listOf(
                DroppedFinding(DropReason.NO_CARD, "bad"),
                DroppedFinding(DropReason.OVER_LIMIT, "g8"),
            ),
            mixed.dropped,
        )
    }
}
