package spa.agent.review.agents

import spa.agent.review.AnalysisAgent
import spa.agent.review.NumberTable
import spa.agent.review.NumberTables
import spa.agent.review.ReviewInput
import spa.core.text.RulesTagger
import spa.core.text.Tag

/** How much removal and interaction the pool has, of which kinds, against the set baseline. */
object InteractionAgent : AnalysisAgent {
    private val PATTERNS =
        listOf(
            "/removalTotal",
            "/interactionTotal",
            "/pairs/*/removal",
            "/pairs/*/interaction",
            "/byColor/*/interaction",
        )
    private val TAGS = listOf(Tag.REMOVAL, Tag.COUNTER, Tag.BOUNCE, Tag.TRICK)

    override val id: String = "interaction"
    override val title: String = "Interaction"

    override fun numbers(input: ReviewInput): NumberTable =
        NumberTables.fromMetrics(input, PATTERNS)

    /** For each interaction tag, the nonland cards that carry it with their copies. */
    override fun facts(input: ReviewInput): String {
        val cards =
            input.pool.entries
                .filter { it.count > 0 && !it.printing.card.isLand }
                .groupBy { it.printing.card.oracleId }
                .values
                .map { same ->
                    val card = same.first().printing.card
                    val tags =
                        card.faces.flatMapTo(mutableSetOf()) {
                            RulesTagger.tag(it.typeLine, it.oracleText)
                        }
                    Triple(card.name, tags, same.sumOf { it.count })
                }
        val lines =
            TAGS.map { tag ->
                val tagged = cards.filter { tag in it.second }.sortedBy { it.first }
                val text =
                    if (tagged.isEmpty()) {
                        "none"
                    } else {
                        tagged.joinToString(", ") { (name, _, copies) -> "$name x$copies" }
                    }
                "${tag.name} (${tagged.sumOf { it.third }} copies): $text"
            }
        return (listOf("Cards by tag:") + lines).joinToString("\n")
    }

    override fun unavailable(input: ReviewInput): String? = null
}
