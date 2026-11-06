package spa.agent.review

import spa.agent.model.UsageTotals

/**
 * The top of a review: the [set] code and its [setName], the pool's [totalCards], [distinct] cards
 * and [unresolved] lines, and the [kitWarnings] as one line of text each.
 */
data class ReviewHeader(
    val set: String,
    val setName: String,
    val totalCards: Int,
    val distinct: Int,
    val unresolved: Int,
    val kitWarnings: List<String>,
)

/** Something the review lacks: the [id] of the agent (or `explainer`) and why, in [reason]. */
data class MissingEntry(val id: String, val reason: String)

/**
 * The whole pool review (G-006): the [header], the builder's top [decks], the Explainer's
 * [verdict], one outcome per analyst in [sections] (agent order), the [usage] of the run and the
 * [model] it used. The text and the JSON depend on nothing else.
 */
data class Review(
    val header: ReviewHeader,
    val decks: List<DeckSummary>,
    val verdict: ExplainerOutcome,
    val sections: List<AgentOutcome>,
    val usage: UsageTotals,
    val model: String,
) {
    /** The Explainer's kept disagreements; none when it has no explanation. */
    val disagreements: List<Disagreement>
        get() = (verdict as? ExplainerOutcome.Explained)?.result?.disagreements.orEmpty()

    /** Every missing analyst in agent order, then the Explainer if it is missing. */
    val missing: List<MissingEntry>
        get() =
            sections.filterIsInstance<AgentOutcome.Missing>().map {
                MissingEntry(it.agent.id, it.reason)
            } +
                listOfNotNull(
                    (verdict as? ExplainerOutcome.Missing)?.let {
                        MissingEntry(EXPLAINER_ID, it.reason)
                    }
                )

    companion object {
        /** The id the Explainer goes by in the review. */
        const val EXPLAINER_ID = "explainer"
    }
}
