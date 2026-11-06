package spa.agent.review.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import spa.agent.review.AgentRequests
import spa.agent.review.AgentSetting
import spa.agent.review.AnalysisAgents
import spa.agent.review.ExplainerSetting
import spa.agent.review.ReviewConfig
import spa.agent.review.ReviewInput
import spa.agent.review.ReviewTestCards

class AnalysisAgentsTest {
    private val questions =
        mapOf(
            "colour-depth" to
                "Which colors are deep enough to play? Say, per color, how many playables the " +
                    "pool has and how good they are, and warn where a color is thin, each " +
                    "compared with the set baseline.",
            "curve" to
                "Does each color pair have a playable curve and enough creatures? Say, per " +
                    "pair, how the curve is shaped, how many creatures and two-drops it has, " +
                    "how heavy the top end is, and flag the gaps.",
            "mana" to
                "How many lands, and is a splash safe? Say what land count and color source " +
                    "counts the pool supports, which fixing it has, and which splash cards are " +
                    "castable on curve.",
        )

    private fun prompt(id: String): String =
        checkNotNull(javaClass.getResourceAsStream("/spa/agent/review/$id-v1.md")) { "no $id" }
            .readBytes()
            .toString(Charsets.UTF_8)

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
            agents = questions.keys.map { AgentSetting(it, true, 3000, "v1") },
            explainer = ExplainerSetting(true, 6000, "v1"),
        )

    @Test
    fun `the agents are listed in running order`() {
        assertEquals(listOf("colour-depth", "curve", "mana"), AnalysisAgents.ALL.map { it.id })
    }

    @Test
    fun `each prompt file starts with its question, has at most 30 lines and no digit`() {
        questions.forEach { (id, question) ->
            val text = prompt(id)
            assertTrue(text.startsWith("$question\n"), "$id should start with its question")
            assertTrue(text.trimEnd('\n').lines().size <= 30, "$id has more than 30 lines")
            assertFalse(text.any { it.isDigit() }, "$id contains a digit")
        }
    }

    @Test
    fun `each agent request carries its prompt in the system text`() {
        AnalysisAgents.ALL.forEach { agent ->
            val request = AgentRequests.of(agent, input, config)

            assertTrue(request.system.contains(questions.getValue(agent.id)), agent.id)
            assertTrue(request.system.contains(prompt(agent.id).removeSuffix("\n")), agent.id)
            assertEquals(agent.id, request.label)
            assertEquals(3000L, request.maxTokens)
        }
    }
}
