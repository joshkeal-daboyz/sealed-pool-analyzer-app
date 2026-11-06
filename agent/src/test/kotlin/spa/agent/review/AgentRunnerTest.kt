package spa.agent.review

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import spa.agent.model.BudgetExceededException
import spa.agent.model.ModelClient
import spa.agent.model.ModelRequest
import spa.agent.model.ModelResponse
import spa.agent.model.ModelUsage
import spa.agent.review.ReviewTestAnswers.answer
import spa.agent.review.ReviewTestAnswers.finding

class AgentRunnerTest {
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
    private val letters = listOf("a", "b", "c", "d", "e", "f")
    private val agents = letters.map { FakeAgent("fake-$it") }

    private fun config(
        ids: List<String> = agents.map { it.id },
        enabled: Set<String> = ids.toSet(),
        parallel: Int = 6,
        retries: Int = 1,
        timeout: Int = 120,
    ) =
        ReviewConfig(
            version = 1,
            model = "claude-test",
            maxInputTokens = 300_000,
            maxOutputTokens = 60_000,
            callTimeoutSeconds = timeout,
            parallel = parallel,
            retries = retries,
            maxFindings = 8,
            agents = ids.map { AgentSetting(it, it in enabled, 3000, "v1") },
            explainer = ExplainerSetting(true, 6000, "v1"),
        )

    private fun idOf(request: ModelRequest) = request.label.substringBefore("-retry")

    private fun validFor(id: String, marker: String = "marker-$id") =
        answer(finding("Claim $marker."), agentId = id, summary = "Summary $marker.")

    private fun runner(
        client: ModelClient,
        config: ReviewConfig = config(),
        list: List<AnalysisAgent> = agents,
    ) = AgentRunner(client, config, list)

    @Test
    fun `the first agent is answered before any other request is sent, then the rest run together`() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        val gate = CountDownLatch(3)
        val client = ModelClient { request ->
            val id = idOf(request)
            events.add("start:$id")
            if (id != "fake-a") {
                peak.accumulateAndGet(inFlight.incrementAndGet()) { a, b -> maxOf(a, b) }
                gate.countDown()
                assertTrue(gate.await(10, TimeUnit.SECONDS))
                Thread.sleep(100)
                inFlight.decrementAndGet()
            }
            events.add("end:$id")
            ModelResponse("m", validFor(id), "end_turn", ModelUsage(1, 1))
        }

        val outcomes = runner(client, config(parallel = 4)).run(input)

        assertEquals(agents, outcomes.map { (it as AgentOutcome.Answered).agent })
        assertEquals(listOf("start:fake-a", "end:fake-a"), events.take(2))
        assertTrue(peak.get() >= 3, "peak ${peak.get()}")
        assertTrue(peak.get() <= 4, "peak ${peak.get()}")
    }

    @Test
    fun `an invalid answer then a valid one is answered with the retry request as specified`() {
        val client = RecordingClient { request ->
            if (request.label == "fake-a") "not json at all" else validFor("fake-a")
        }
        val outcomes = runner(client, list = agents.take(1)).run(input)

        val outcome = assertIs<AgentOutcome.Answered>(outcomes.single())
        assertEquals(1, outcome.answer.findings.size)
        val (first, second) = client.requests
        assertEquals(listOf("fake-a", "fake-a-retry1"), client.requests.map { it.label })
        val error =
            assertFailsWith<SchemaError> {
                AnswerChecker.check(
                    "not json at all",
                    agents[0],
                    emptySet(),
                    NumberTable(emptyList()),
                    8,
                )
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
    fun `two invalid answers are missing with the error and retries bound the requests`() {
        val client = RecordingClient { "still not json" }
        val outcome = runner(client, list = agents.take(1)).run(input).single()

        val missing = assertIs<AgentOutcome.Missing>(outcome)
        assertTrue(missing.reason.startsWith("invalid answer: "), missing.reason)
        assertEquals(2, client.requests.size)

        val none = RecordingClient { "still not json" }
        runner(none, config(retries = 0), agents.take(1)).run(input)
        assertEquals(1, none.requests.size)

        val twice = RecordingClient { "still not json" }
        runner(twice, config(retries = 2), agents.take(1)).run(input)
        assertEquals(
            listOf("fake-a", "fake-a-retry1", "fake-a-retry2"),
            twice.requests.map { it.label },
        )
        assertTrue(twice.requests[2].userMessage.startsWith(twice.requests[0].userMessage))
        assertEquals(1, Regex("rejected").findAll(twice.requests[2].userMessage).count())
    }

    @Test
    fun `a spent budget is missing with its message and is not retried`() {
        val client = RecordingClient {
            throw BudgetExceededException("budget: input cap would be passed (9 of 8)")
        }
        val outcome = runner(client, list = agents.take(1)).run(input).single()

        assertEquals(
            AgentOutcome.Missing(agents[0], "budget: input cap would be passed (9 of 8)"),
            outcome,
        )
        assertEquals(1, client.requests.size)
    }

    @Test
    fun `a call that outlasts the timeout is missing`() {
        val client = ModelClient {
            Thread.sleep(30_000)
            error("unreachable")
        }
        val outcome = runner(client, config(timeout = 1), agents.take(1)).run(input).single()

        assertEquals(AgentOutcome.Missing(agents[0], "timed out after 1s"), outcome)
    }

    @Test
    fun `any other exception is missing with its class and first line`() {
        val boom = RecordingClient { throw IllegalStateException("boom\nsecond line") }
        assertEquals(
            AgentOutcome.Missing(agents[0], "error: IllegalStateException: boom"),
            runner(boom, list = agents.take(1)).run(input).single(),
        )
        assertEquals(1, boom.requests.size)

        val long = RecordingClient { throw IllegalArgumentException("x".repeat(300)) }
        assertEquals(
            AgentOutcome.Missing(agents[0], "error: IllegalArgumentException: " + "x".repeat(200)),
            runner(long, list = agents.take(1)).run(input).single(),
        )
    }

    @Test
    fun `one failing agent does not stop the others`() {
        val client = RecordingClient { request ->
            if (idOf(request) == "fake-c") error("down") else validFor(idOf(request))
        }
        val outcomes = runner(client).run(input)

        assertEquals(6, outcomes.size)
        assertEquals(
            listOf("fake-c"),
            outcomes.filterIsInstance<AgentOutcome.Missing>().map { it.agent.id },
        )
    }

    @Test
    fun `a switched off agent sends nothing and an unavailable one is missing with its reason`() {
        val list =
            listOf(
                FakeAgent("fake-a"),
                FakeAgent("fake-b"),
                FakeAgent("fake-c", reason = "no baseline for this set"),
                FakeAgent("fake-d"),
            )
        val config = config(ids = list.map { it.id }, enabled = setOf("fake-a", "fake-c", "fake-d"))
        val client = RecordingClient { validFor(idOf(it)) }

        val outcomes = runner(client, config, list).run(input)

        assertEquals(listOf("fake-a", "fake-c", "fake-d"), outcomes.map { it.agent.id })
        assertEquals(AgentOutcome.Missing(list[2], "no baseline for this set"), outcomes[1])
        assertEquals(setOf("fake-a", "fake-d"), client.requests.map { it.label }.toSet())
        assertEquals(2, client.requests.size)
    }

    @Test
    fun `an agent that is not in the config is not run`() {
        val client = RecordingClient { validFor(idOf(it)) }
        val outcomes = runner(client, config(ids = listOf("fake-a")), agents.take(2)).run(input)

        assertEquals(listOf("fake-a"), outcomes.map { it.agent.id })
        assertEquals(1, client.requests.size)
    }

    @Test
    fun `when the first agent is unavailable the next one is still sent first and alone`() {
        val list =
            listOf(FakeAgent("fake-a", reason = "off"), FakeAgent("fake-b"), FakeAgent("fake-c"))
        val client = RecordingClient { validFor(idOf(it)) }
        val outcomes = runner(client, config(ids = list.map { it.id }), list).run(input)

        assertEquals(3, outcomes.size)
        assertEquals("fake-b", client.requests.first().label)
        assertEquals(2, client.requests.size)
    }

    @Test
    fun `plan lists each enabled agent's request or reason and sends nothing`() {
        val list =
            listOf(
                FakeAgent("fake-a"),
                FakeAgent("fake-b"),
                FakeAgent("fake-c", reason = "no baseline for this set"),
            )
        val config = config(ids = list.map { it.id }, enabled = setOf("fake-a", "fake-c"))
        val client = RecordingClient { validFor(idOf(it)) }

        val plan = runner(client, config, list).plan(input)

        assertEquals(
            listOf(
                PlannedCall.Send(list[0], AgentRequests.of(list[0], input, config)),
                PlannedCall.Skipped(list[2], "no baseline for this set"),
            ),
            plan,
        )
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `dropped findings are reported with the answer`() {
        val text =
            answer(finding("Good."), finding("Bad.", cards = "[\"Ghost\"]"), agentId = "fake-a")
        val client = RecordingClient { text }
        val outcome =
            assertIs<AgentOutcome.Answered>(
                runner(client, list = agents.take(1)).run(input).single()
            )

        assertEquals(listOf("Good."), outcome.answer.findings.map { it.claim })
        assertEquals(listOf(DroppedFinding(DropReason.UNKNOWN_CARD, "Bad.")), outcome.dropped)
    }

    @Test
    fun `no request holds another agent's answer`() {
        val client = RecordingClient { validFor(idOf(it)) }
        runner(client).run(input)

        val markers = agents.map { "marker-${it.id}" }
        for (request in client.requests) {
            val text =
                listOf(request.system, request.userMessage, request.cachedContext.orEmpty())
                    .joinToString("\n")
            assertTrue(markers.none { text.contains(it) }, "${request.label} saw an answer")
        }
        assertEquals(6, client.requests.size)
    }

    @Test
    fun `a test only agent added to the list gets a request and an answer with no other change`() {
        val table = NumberTable(listOf(NumberEntry("/removal/count", 2.0, null)))
        val extra = FakeAgent("fake-f", table, "Extra fact.")
        val client = RecordingClient { validFor(idOf(it)) }

        val outcomes =
            runner(client, config(ids = listOf("fake-f")), AnalysisAgents.ALL + extra).run(input)

        assertIs<AgentOutcome.Answered>(outcomes.single())
        val request = client.requests.single()
        assertEquals("fake-f", request.label)
        assertTrue(request.userMessage.contains("/removal/count = 2"))
        assertTrue(request.userMessage.contains("Extra fact."))
    }
}
