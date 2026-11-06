package spa.agent.review

import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import spa.agent.model.ModelClient
import spa.agent.model.ModelRequest

/**
 * The Explainer of the pool review: one call that reads every kept finding and the builder's top
 * decks and writes the verdict, a note per deck and the disagreements between agents. It restates
 * the builder's ranking; it never reorders it.
 */
object Explainer {
    private const val SEPARATOR = "\n\n"

    /**
     * The request for [input]: the config's model and the Explainer's output cap, the label
     * `explainer`, the pool bundle as cached context (as the analysts have it), the prompt and the
     * schema as system text, and as user message [decksText] and the findings of every answered
     * agent in [outcomes]. A missing agent is not mentioned.
     */
    fun request(
        input: ReviewInput,
        decksText: String,
        outcomes: List<AgentOutcome>,
        config: ReviewConfig,
    ): ModelRequest {
        val system =
            listOf(
                    AgentRequests.resource("explainer-${config.explainer.prompt}.md"),
                    AgentRequests.resource("explainer-schema-v${config.version}.md"),
                )
                .joinToString(SEPARATOR)
        val findings =
            outcomes.filterIsInstance<AgentOutcome.Answered>().joinToString("") {
                SEPARATOR + section(it)
            }
        return ModelRequest(
            model = config.model,
            maxTokens = config.explainer.maxTokens,
            system = system,
            userMessage =
                "## Decks\n$decksText${SEPARATOR}## Findings$findings" +
                    "${SEPARATOR}Answer with the JSON object only.",
            cachedContext = PoolBundle.text(input),
            label = "explainer",
        )
    }

    private fun section(outcome: AgentOutcome.Answered): String {
        val answer = outcome.answer
        val confidence = String.format(Locale.ROOT, "%.2f", answer.confidence)
        val lines = answer.findings.map(::line).ifEmpty { listOf("- no findings kept") }
        return (listOf(
                "### ${outcome.agent.title} (${outcome.agent.id}, confidence $confidence)",
                answer.summary,
            ) + lines)
            .joinToString("\n")
    }

    private fun line(finding: Finding): String {
        val numbers =
            finding.numbers.joinToString(", ") { number ->
                val typical =
                    number.typical?.let { " (typical median ${NumberTable.format(it.median)})" }
                "${number.key}=${NumberTable.format(number.value)}${typical.orEmpty()}"
            }
        val numbersPart = if (numbers.isEmpty()) "" else " | numbers: $numbers"
        return "- [${finding.severity.label}] ${finding.pair ?: "-"}: ${finding.claim} | " +
            "cards: ${finding.cards.joinToString("; ")}$numbersPart"
    }

    /**
     * Runs the Explainer on [outcomes] through [client]. Nothing is sent, and the result is
     * missing, when the config switches the Explainer off or no agent answered with a kept finding.
     * [deckRanks] are the ranks of the decks in [decksText]. Retries, timeout and errors follow the
     * analysts' rules.
     */
    @Suppress("LongParameterList") // the signature is fixed by SPA-068
    fun run(
        client: ModelClient,
        config: ReviewConfig,
        input: ReviewInput,
        decksText: String,
        outcomes: List<AgentOutcome>,
        deckRanks: List<Int>,
    ): ExplainerOutcome {
        val answered = outcomes.filterIsInstance<AgentOutcome.Answered>()
        return when {
            answered.none { it.answer.findings.isNotEmpty() } ->
                ExplainerOutcome.Missing("no findings to explain")
            !config.explainer.enabled -> ExplainerOutcome.Missing("switched off in the config")
            else -> {
                val names = input.pool.entries.map { it.printing.card.name }
                val ids = answered.map { it.agent.id }
                send(client, config, request(input, decksText, answered, config)) { text ->
                    ExplainerChecker.check(text, names, deckRanks, ids)
                }
            }
        }
    }

    /** Sends [request] under the call rules and hands each reply text to [check]. */
    private fun send(
        client: ModelClient,
        config: ReviewConfig,
        request: ModelRequest,
        check: (String) -> CheckedExplainer,
    ): ExplainerOutcome {
        val pool = Executors.newCachedThreadPool { task -> Thread(task).apply { isDaemon = true } }
        try {
            val calls = ModelCalls(client, config, pool, Semaphore(1))
            return calls.guarded({ ExplainerOutcome.Missing(it) }) {
                try {
                    val checked = calls.checked(request, check)
                    ExplainerOutcome.Explained(checked.result, checked.dropped)
                } catch (error: SchemaError) {
                    ExplainerOutcome.Missing("invalid answer: ${error.message}")
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
