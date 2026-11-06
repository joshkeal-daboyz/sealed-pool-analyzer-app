package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import spa.agent.model.ModelRequest
import spa.core.baseline.Stat

class ExplainerRequestTest {
    private val input =
        ReviewInput(
            "xyz",
            ReviewTestCards.metricsPool(),
            ReviewTestCards.metrics(),
            null,
            ReviewTestCards.brief,
            { null },
            emptyList(),
        )
    private val config =
        ReviewConfig(
            version = 1,
            model = "claude-test",
            maxInputTokens = 1000,
            maxOutputTokens = 500,
            callTimeoutSeconds = 120,
            parallel = 6,
            retries = 1,
            maxFindings = 8,
            agents = listOf(AgentSetting("fake-a", true, 2500, "v1")),
            explainer = ExplainerSetting(true, 6000, "v1"),
        )
    private val decksText = "Deck 1: White-Blue\nDeck 2: White-Red"

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/spa/agent/review/$name")) { "no $name" }
            .readBytes()
            .toString(Charsets.UTF_8)
            .removeSuffix("\n")

    private val answered =
        AgentOutcome.Answered(
            FakeAgent("fake-a"),
            AgentAnswer(
                "fake-a",
                0.8,
                "Fine pool.",
                listOf(
                    Finding(
                        "Two drops are plentiful.",
                        "WU",
                        Severity.KEY,
                        listOf("Bear", "Envoy"),
                        listOf(
                            AnswerNumber(
                                "/curve/twoDrops",
                                5.0,
                                Stat(median = 4.0, p10 = 2.0, p90 = 6.0, mean = 4.0),
                            ),
                            AnswerNumber("/removal/count", 2.5, null),
                        ),
                    ),
                    Finding("Red is thin.", null, Severity.WARN, listOf("Brute"), emptyList()),
                ),
            ),
            emptyList(),
        )
    private val empty =
        AgentOutcome.Answered(
            FakeAgent("fake-b"),
            AgentAnswer("fake-b", 0.333, "Nothing to say.", emptyList()),
            emptyList(),
        )
    private val missing = AgentOutcome.Missing(FakeAgent("fake-c"), "timed out after 1s")

    private fun request(vararg outcomes: AgentOutcome): ModelRequest =
        Explainer.request(input, decksText, outcomes.toList(), config)

    @Test
    fun `the request has every field as specified`() {
        val request = request(answered, empty, missing)

        assertEquals("claude-test", request.model)
        assertEquals(6000, request.maxTokens)
        assertEquals("explainer", request.label)
        assertEquals(PoolBundle.text(input), request.cachedContext)
        assertEquals(
            resource("explainer-v1.md") + "\n\n" + resource("explainer-schema-v1.md"),
            request.system,
        )
        assertEquals(
            "## Decks\n" +
                "Deck 1: White-Blue\nDeck 2: White-Red\n\n" +
                "## Findings\n\n" +
                "### Title of fake-a (fake-a, confidence 0.80)\n" +
                "Fine pool.\n" +
                "- [key] WU: Two drops are plentiful. | cards: Bear; Envoy | " +
                "numbers: /curve/twoDrops=5 (typical median 4), /removal/count=2.5\n" +
                "- [warn] -: Red is thin. | cards: Brute\n\n" +
                "### Title of fake-b (fake-b, confidence 0.33)\n" +
                "Nothing to say.\n" +
                "- no findings kept\n\n" +
                "Answer with the JSON object only.",
            request.userMessage,
        )
    }

    @Test
    fun `a missing agent is not mentioned`() {
        val message = request(answered, missing).userMessage

        assertTrue(!message.contains("fake-c"), message)
        assertTrue(!message.contains("timed out"), message)
    }

    @Test
    fun `the cached context is the one an analyst request carries`() {
        val analyst = AgentRequests.of(FakeAgent("fake-a"), input, config)

        assertEquals(analyst.cachedContext, request(answered).cachedContext)
    }
}
