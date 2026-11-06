package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import spa.agent.model.BudgetExceededException
import spa.agent.model.ModelClient

class ExplainerRunTest {
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

    private fun config(enabled: Boolean = true, retries: Int = 1, timeout: Int = 120) =
        ReviewConfig(
            version = 1,
            model = "claude-test",
            maxInputTokens = 1000,
            maxOutputTokens = 500,
            callTimeoutSeconds = timeout,
            parallel = 6,
            retries = retries,
            maxFindings = 8,
            agents = listOf(AgentSetting("fake-a", true, 2500, "v1")),
            explainer = ExplainerSetting(enabled, 6000, "v1"),
        )

    private val finding = Finding("A claim.", null, Severity.INFO, listOf("Bear"), emptyList())
    private val withFinding =
        AgentOutcome.Answered(
            FakeAgent("fake-a"),
            AgentAnswer("fake-a", 0.8, "S.", listOf(finding)),
            emptyList(),
        )
    private val withoutFinding =
        AgentOutcome.Answered(
            FakeAgent("fake-b"),
            AgentAnswer("fake-b", 0.8, "S.", emptyList()),
            emptyList(),
        )
    private val missing = AgentOutcome.Missing(FakeAgent("fake-c"), "off")
    private val all = listOf(withFinding, withoutFinding, missing)

    private val valid =
        """{"confidence": 0.7, "verdict": "Register deck one.", """ +
            """"decks": [{"rank": 1, "note": "Solid.", "cards": ["Bear"]}, """ +
            """{"rank": 9, "note": "Odd.", "cards": ["Bear"]}], """ +
            """"disagreements": [{"topic": "Blue", "agents": ["fake-a", "fake-b"], """ +
            """"cards": ["Envoy"], "note": "Split."}]}"""

    private fun run(
        client: ModelClient,
        config: ReviewConfig = config(),
        outcomes: List<AgentOutcome> = all,
    ) = Explainer.run(client, config, input, "Deck 1", outcomes, listOf(1, 2, 3))

    @Test
    fun `a valid answer is explained with its dropped items and one request is sent`() {
        val client = RecordingClient { valid }
        val outcome = assertIs<ExplainerOutcome.Explained>(run(client))

        assertEquals("Register deck one.", outcome.result.verdict)
        assertEquals(listOf(1), outcome.result.decks.map { it.rank })
        assertEquals(1, outcome.result.disagreements.size)
        assertEquals(
            listOf(DroppedItem(ItemKind.DECK, ExplainerDropReason.UNKNOWN_RANK, "Odd.")),
            outcome.dropped,
        )
        assertEquals(
            listOf(Explainer.request(input, "Deck 1", all, config())),
            client.requests.toList(),
        )
    }

    @Test
    fun `no answered agent with a kept finding sends nothing`() {
        val client = RecordingClient { valid }

        assertEquals(
            ExplainerOutcome.Missing("no findings to explain"),
            run(client, outcomes = listOf(missing, withoutFinding)),
        )
        assertEquals(
            ExplainerOutcome.Missing("no findings to explain"),
            run(client, outcomes = emptyList()),
        )
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `a switched off explainer sends nothing`() {
        val client = RecordingClient { valid }

        assertEquals(
            ExplainerOutcome.Missing("switched off in the config"),
            run(client, config(enabled = false)),
        )
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `an invalid answer then a valid one is explained with the retry request as specified`() {
        val client = RecordingClient { request ->
            if (request.label == "explainer") "not json at all" else valid
        }
        assertIs<ExplainerOutcome.Explained>(run(client))

        val (first, second) = client.requests
        assertEquals(listOf("explainer", "explainer-retry1"), client.requests.map { it.label })
        val error =
            assertFailsWith<SchemaError> {
                ExplainerChecker.check("not json at all", emptySet(), emptyList(), emptySet())
            }
        assertEquals(
            first.userMessage +
                "\n\nYour previous answer was rejected: ${error.message}. " +
                "Answer again with the JSON object only.",
            second.userMessage,
        )
        assertEquals(first.system, second.system)
        assertEquals(first.cachedContext, second.cachedContext)
    }

    @Test
    fun `invalid answers are missing with the error and retries bound the requests`() {
        val client = RecordingClient { "still not json" }
        val outcome = assertIs<ExplainerOutcome.Missing>(run(client))

        assertTrue(outcome.reason.startsWith("invalid answer: "), outcome.reason)
        assertEquals(2, client.requests.size)

        val none = RecordingClient { "still not json" }
        run(none, config(retries = 0))
        assertEquals(1, none.requests.size)

        val twice = RecordingClient { "still not json" }
        run(twice, config(retries = 2))
        assertEquals(
            listOf("explainer", "explainer-retry1", "explainer-retry2"),
            twice.requests.map { it.label },
        )
    }

    @Test
    fun `an empty verdict is retried like any schema error`() {
        val client = RecordingClient {
            """{"confidence": 0.7, "verdict": "", "decks": [], "disagreements": []}"""
        }
        val outcome = assertIs<ExplainerOutcome.Missing>(run(client))

        assertTrue(outcome.reason.startsWith("invalid answer: "), outcome.reason)
        assertTrue(outcome.reason.contains("verdict"), outcome.reason)
    }

    @Test
    fun `a spent budget is missing with its message and is not retried`() {
        val client = RecordingClient {
            throw BudgetExceededException("budget: input cap would be passed (9 of 8)")
        }

        assertEquals(
            ExplainerOutcome.Missing("budget: input cap would be passed (9 of 8)"),
            run(client),
        )
        assertEquals(1, client.requests.size)
    }

    @Test
    fun `a call that outlasts the timeout is missing`() {
        val client = ModelClient {
            Thread.sleep(30_000)
            error("unreachable")
        }

        assertEquals(
            ExplainerOutcome.Missing("timed out after 1s"),
            run(client, config(timeout = 1)),
        )
    }

    @Test
    fun `any other exception is missing with its class and first line`() {
        val boom = RecordingClient { throw IllegalStateException("boom\nsecond line") }
        assertEquals(ExplainerOutcome.Missing("error: IllegalStateException: boom"), run(boom))
        assertEquals(1, boom.requests.size)

        val long = RecordingClient { throw IllegalArgumentException("x".repeat(300)) }
        assertEquals(
            ExplainerOutcome.Missing("error: IllegalArgumentException: " + "x".repeat(200)),
            run(long),
        )
    }
}
