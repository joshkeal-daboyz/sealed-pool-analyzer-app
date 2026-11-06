package spa.agent.review

import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import spa.agent.model.ModelClient
import spa.agent.model.ModelRequest
import spa.agent.model.ModelResponse
import spa.agent.model.ModelUsage
import spa.agent.model.UsageTotals
import spa.agent.review.ReviewTestAnswers.answer
import spa.agent.review.ReviewTestAnswers.finding

/** SPA-069 criteria 6 to 8: a review run through a fake client. */
class ReviewRunTest {
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
    private val agents = listOf(FakeAgent("fake-a"), FakeAgent("fake-b"), FakeAgent("fake-c"))
    private val config =
        ReviewConfig(
            version = 1,
            model = "claude-test",
            maxInputTokens = 300_000,
            maxOutputTokens = 60_000,
            callTimeoutSeconds = 120,
            parallel = 6,
            retries = 1,
            maxFindings = 8,
            agents = agents.map { AgentSetting(it.id, true, 3000, "v1") },
            explainer = ExplainerSetting(true, 6000, "v1"),
        )
    private val decks = listOf(ReviewTestData.deck1, ReviewTestData.deck2)

    private val explainerAnswer =
        """{"confidence": 0.7, "verdict": "Register deck one.", """ +
            """"decks": [{"rank": 1, "note": "Solid.", "cards": ["Bear"]}], """ +
            """"disagreements": [{"topic": "Blue", "agents": ["fake-a", "fake-b"], """ +
            """"cards": ["Envoy"], "note": "Split."}]}"""

    /** A client that logs `start:<label>` and `end:<label>` and counts the calls it answered. */
    private class LoggingClient(private val respond: (ModelRequest) -> String) : ModelClient {
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val requests: MutableList<ModelRequest> = Collections.synchronizedList(mutableListOf())
        val calls = AtomicLong()

        override fun send(request: ModelRequest): ModelResponse {
            events.add("start:${request.label}")
            requests.add(request)
            val text = respond(request)
            calls.incrementAndGet()
            events.add("end:${request.label}")
            return ModelResponse("test-model", text, "end_turn", ModelUsage(1, 1))
        }
    }

    private fun run(client: LoggingClient, decks: List<DeckSummary> = this.decks): Review =
        ReviewRun(client, { UsageTotals(client.calls.get(), 10, 20, 30, 40) }, config, agents)
            .run(input, decks, ReviewTestData.header)

    private fun answerFor(request: ModelRequest): String {
        val id = request.label.substringBefore("-retry")
        return if (id == "explainer") {
            explainerAnswer
        } else {
            answer(finding("Claim of $id."), agentId = id, summary = "Summary of $id.")
        }
    }

    @Test
    fun `the Explainer is sent after every analyst has answered, with the decks and findings`() {
        val client = LoggingClient(::answerFor)

        val review = run(client)

        assertEquals(agents.map { it.id }, review.sections.map { it.agent.id })
        assertTrue(review.sections.all { it is AgentOutcome.Answered })
        val explainerStart = client.events.indexOf("start:explainer")
        assertEquals(client.events.size - 2, explainerStart)
        assertEquals(
            listOf("end:fake-a", "end:fake-b", "end:fake-c"),
            client.events.take(explainerStart).filter { it.startsWith("end:") }.sorted(),
        )
        val message = client.requests.single { it.label == "explainer" }.userMessage
        assertTrue(message.startsWith("## Decks\n${decks.joinToString("\n") { it.text() }}\n\n"))
        agents.forEach { assertTrue(message.contains("Claim of ${it.id}.")) }
        assertIs<ExplainerOutcome.Explained>(review.verdict)
        assertEquals(UsageTotals(4, 10, 20, 30, 40), review.usage)
        assertEquals("claude-test", review.model)
        assertEquals(ReviewTestData.header, review.header)
        assertEquals(decks, review.decks)
        assertEquals(emptyList(), review.missing)
        assertEquals(1, review.disagreements.size)
    }

    @Test
    fun `with every analyst failing no Explainer is called and the review still prints`() {
        val client = LoggingClient { "not an answer" }

        val review = run(client)

        assertTrue(client.requests.none { it.label == "explainer" })
        assertEquals(
            agents.map { it.id } + "explainer",
            review.missing.map { it.id },
        )
        assertTrue(review.missing.dropLast(1).all { it.reason.startsWith("invalid answer") })
        assertEquals(
            MissingEntry("explainer", "no findings to explain"),
            review.missing.last(),
        )
        val lines = ReviewText.render(review).lines()
        assertEquals("Review: xyz (Test Set)", lines.first())
        assertTrue(lines.contains("Rank 1: WU (same cards as WB)  score 12.50"))
        assertTrue(lines.contains("Not available (no findings to explain)."))
        assertTrue(lines.contains("None."))
        assertTrue(lines.any { it.startsWith("Missing: fake-a (invalid answer") })
        assertTrue(lines.any { it.startsWith("Usage: 6 calls, input 10, output 20") })
        assertEquals(ReviewTestData.ATTRIBUTION, lines[lines.size - 2])
    }

    @Test
    fun `with no deck the analysts run and the Explainer is missing without a call`() {
        val client = LoggingClient(::answerFor)

        val review = run(client, emptyList())

        assertEquals(3, client.requests.size)
        assertTrue(client.requests.none { it.label == "explainer" })
        assertTrue(review.sections.all { it is AgentOutcome.Answered })
        assertEquals(ExplainerOutcome.Missing("no deck could be built"), review.verdict)
        assertEquals(listOf(MissingEntry("explainer", "no deck could be built")), review.missing)
        val lines = ReviewText.render(review).lines()
        assertTrue(lines.contains("No deck could be built from this pool."))
        assertTrue(lines.contains("Not available (no deck could be built)."))
    }
}
