package spa.agent.review.agents

import spa.agent.review.AnalysisAgent
import spa.agent.review.NumberTable
import spa.agent.review.NumberTables
import spa.agent.review.ReviewInput
import spa.core.cards.Card
import spa.core.metrics.ColorPair

/** Whether each colour pair has a playable curve: its shape, creatures, two-drops and top end. */
object CurveAgent : AnalysisAgent {
    private const val MAX_NAMES = 8
    private const val TWO_DROP = 2.0
    private const val TOP_END = 5.0
    private val PATTERNS =
        listOf("/pairs/*/cards", "/pairs/*/creatures", "/pairs/*/noncreatures", "/pairs/*/curve/")

    override val id: String = "curve"
    override val title: String = "Curve"

    override fun numbers(input: ReviewInput): NumberTable =
        NumberTables.fromMetrics(input, PATTERNS)

    /** Per pair, the castable creatures of mana value 2 or less and cards of mana value 5 or more. */
    override fun facts(input: ReviewInput): String {
        val cards =
            input.pool.entries
                .filter { it.count > 0 && !it.printing.card.isLand }
                .map { it.printing.card }
        return ColorPair.entries.joinToString("\n") { pair ->
            val castable = cards.filter { pair.colors.containsAll(it.colors) }
            val twoDrops = names(castable.filter { it.isCreature && it.manaValue <= TWO_DROP })
            val topEnd = names(castable.filter { it.manaValue >= TOP_END })
            "${pair.name} creatures at mana value 2 or less: $twoDrops; " +
                "cards at mana value 5 or more: $topEnd"
        }
    }

    override fun unavailable(input: ReviewInput): String? = null

    private fun names(cards: List<Card>): String {
        val sorted = cards.map { it.name }.distinct().sorted()
        if (sorted.isEmpty()) return "none"
        val shown = sorted.take(MAX_NAMES).joinToString(", ")
        val more = sorted.size - MAX_NAMES
        return if (more > 0) "$shown (+$more more)" else shown
    }
}
