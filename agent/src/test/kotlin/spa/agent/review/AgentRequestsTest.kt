package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.model.ModelRequest

class AgentRequestsTest {
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

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/spa/agent/review/$name")) { "no $name" }
            .readBytes()
            .toString(Charsets.UTF_8)
            .removeSuffix("\n")

    private val expectedSystem =
        resource("preamble-v1.md") +
            "\n\n" +
            "Prompt for fake-a." +
            "\n\n" +
            resource("schema-v1.md")

    @Test
    fun `a request without facts has every field as specified`() {
        val table = NumberTable(listOf(NumberEntry("/a", 1.0, null), NumberEntry("/b", 2.5, null)))
        val request = AgentRequests.of(FakeAgent("fake-a", table), input, config)

        val expected =
            ModelRequest(
                model = "claude-test",
                maxTokens = 2500,
                system = expectedSystem,
                userMessage =
                    "## Number table\n/a = 1\n/b = 2.5\n\nAnswer with the JSON object only.",
                cachedContext = PoolBundle.text(input),
                label = "fake-a",
            )
        assertEquals(expected, request)
    }

    @Test
    fun `facts add a facts section after the table`() {
        val table = NumberTable(listOf(NumberEntry("/a", 1.0, null)))
        val request = AgentRequests.of(FakeAgent("fake-a", table, "Bear is a fine card."), input, config)

        assertEquals(
            "## Number table\n/a = 1\n\n## Facts\nBear is a fine card.\n\n" +
                "Answer with the JSON object only.",
            request.userMessage,
        )
    }

    @Test
    fun `the system text joins preamble, prompt and schema with blank lines`() {
        val system = AgentRequests.of(FakeAgent("fake-a"), input, config).system
        assertEquals(expectedSystem, system)
    }
}
