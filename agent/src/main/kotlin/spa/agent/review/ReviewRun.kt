package spa.agent.review

import spa.agent.model.ModelClient
import spa.agent.model.UsageTotals

/**
 * Runs a pool review through [client]: every analyst in [agents] (as [config] says), then, only
 * after all of them have finished, the Explainer over the [decks][run] and the kept findings.
 * [usage] is read after the last call. With no deck the Explainer is missing and makes no call; the
 * analysts still run.
 */
class ReviewRun(
    private val client: ModelClient,
    private val usage: () -> UsageTotals,
    private val config: ReviewConfig,
    private val agents: List<AnalysisAgent>,
) {
    /** The review of [input] with the builder's [decks] under [header]. */
    fun run(input: ReviewInput, decks: List<DeckSummary>, header: ReviewHeader): Review {
        val sections = AgentRunner(client, config, agents).run(input)
        val verdict =
            if (decks.isEmpty()) {
                ExplainerOutcome.Missing("no deck could be built")
            } else {
                Explainer.run(
                    client,
                    config,
                    input,
                    decks.joinToString("\n") { it.text() },
                    sections,
                    decks.map { it.rank },
                )
            }
        return Review(header, decks, verdict, sections, usage(), config.model)
    }
}
