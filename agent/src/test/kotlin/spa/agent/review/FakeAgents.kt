package spa.agent.review

import java.util.concurrent.CopyOnWriteArrayList
import spa.agent.model.ModelClient
import spa.agent.model.ModelRequest
import spa.agent.model.ModelResponse
import spa.agent.model.ModelUsage
import spa.core.baseline.Stat

/** A test-only agent; its prompt file is `fake-<letter>-v1.md` under the test resources. */
internal class FakeAgent(
    override val id: String,
    private val table: NumberTable = NumberTable(emptyList()),
    private val facts: String = "",
    private val reason: String? = null,
) : AnalysisAgent {
    override val title: String = "Title of $id"

    override fun numbers(input: ReviewInput): NumberTable = table

    override fun facts(input: ReviewInput): String = facts

    override fun unavailable(input: ReviewInput): String? = reason
}

/** A model client that answers with [respond] and keeps every request it was sent, in order. */
internal class RecordingClient(private val respond: (ModelRequest) -> String) : ModelClient {
    val requests = CopyOnWriteArrayList<ModelRequest>()

    override fun send(request: ModelRequest): ModelResponse {
        requests.add(request)
        return ModelResponse("test-model", respond(request), "end_turn", ModelUsage(1, 1))
    }
}

internal object ReviewTestAnswers {
    val table =
        NumberTable(
            listOf(
                NumberEntry(
                    "/curve/twoDrops",
                    5.0,
                    Stat(median = 4.0, p10 = 2.0, p90 = 6.0, mean = 4.0),
                ),
                NumberEntry("/removal/count", 2.0, null),
            )
        )

    /** One finding as JSON; [numbers] and [cards] are already JSON text. */
    fun finding(
        claim: String = "A claim.",
        cards: String = "[\"Bear\"]",
        numbers: String = "[]",
        pair: String = "null",
        severity: String = "info",
    ): String =
        """{"claim": "$claim", "pair": $pair, "severity": "$severity", "cards": $cards, "numbers": $numbers}"""

    /** An answer object as JSON text. */
    fun answer(
        vararg findings: String,
        agentId: String = "fake-a",
        confidence: String = "0.8",
        summary: String = "A summary.",
    ): String =
        """{"agent": "$agentId", "confidence": $confidence, "summary": "$summary", """ +
            """"findings": [${findings.joinToString(", ")}]}"""
}
