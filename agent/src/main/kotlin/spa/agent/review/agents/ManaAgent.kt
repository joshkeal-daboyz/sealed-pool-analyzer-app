package spa.agent.review.agents

import spa.agent.review.AnalysisAgent
import spa.agent.review.NumberTable
import spa.agent.review.NumberTables
import spa.agent.review.ReviewInput
import spa.core.metrics.ColorPair

/** How many lands the pool supports, what fixes its mana, and which splashes are safe. */
object ManaAgent : AnalysisAgent {
    private val PATTERNS =
        listOf(
            "/basicLands",
            "/nonbasicLands",
            "/fixingSources/count",
            "/pairs/*/fixingSources",
            "/pairs/*/splashCandidates/count",
            "/pairs/*/landRecommendation/",
            "/pairs/*/castProbability/",
        )

    override val id: String = "mana"
    override val title: String = "Mana and fixing"

    override fun numbers(input: ReviewInput): NumberTable =
        NumberTables.fromMetrics(input, PATTERNS)

    /** The pool fixing sources, then the splash candidates of each pair that has any. */
    override fun facts(input: ReviewInput): String {
        val sources = input.metrics.fixingSources
        val fixing =
            if (sources.isEmpty()) {
                "none"
            } else {
                sources.joinToString(", ") { "${it.name} x${it.count}" }
            }
        val splashes =
            ColorPair.entries.mapNotNull { pair ->
                val candidates = input.metrics.pairs.getValue(pair).splashCandidates
                if (candidates.isEmpty()) {
                    null
                } else {
                    "Splash candidates ${pair.name}: " +
                        candidates.joinToString(", ") { "${it.name} (${it.color.name})" }
                }
            }
        return (listOf("Fixing sources: $fixing") + splashes).joinToString("\n")
    }

    override fun unavailable(input: ReviewInput): String? = null
}
