package spa.agent.review.agents

import spa.agent.review.AnalysisAgent
import spa.agent.review.NumberEntry
import spa.agent.review.NumberTable
import spa.agent.review.ReviewInput
import spa.agent.review.SynergyView
import spa.agent.tags.Axis
import spa.agent.tags.TagCard

/** Which of the set's synergy axes the pool has enablers and payoffs for, and the best partners. */
object SynergyAgent : AnalysisAgent {
    private const val STRONGEST = 10
    private const val ENABLER = "enabler"
    private const val PAYOFF = "payoff"

    /** A tagged card of the pool and the copies the pool holds of it. */
    private class Held(val card: TagCard, val copies: Int)

    /** One pair line: its [score], the two names in order, and whether it is a card with itself. */
    private class PairLine(
        val score: Int,
        val first: String,
        val second: String,
        val itself: Boolean,
    )

    override val id: String = "synergy"
    override val title: String = "Synergy"

    /** The pool's tagged cards in pool order, copies of one tag card summed; others left out. */
    private fun held(input: ReviewInput, view: SynergyView): List<Held> {
        val byId = view.tags.cards.associateBy { it.id }
        val copies = LinkedHashMap<String, Int>()
        for (entry in input.pool.entries.filter { it.count > 0 }) {
            val tag = view.idOf(entry.printing)
            if (tag in byId) copies.merge(tag, entry.count, Int::plus)
        }
        return copies.map { (tag, count) -> Held(byId.getValue(tag), count) }
    }

    private fun copiesWith(held: List<Held>, axis: Axis, role: String): Int =
        held
            .filter { h -> h.card.roles.any { it.axis == axis.id && it.role == role } }
            .sumOf { it.copies }

    private fun axesInPlay(held: List<Held>, view: SynergyView): List<Axis> =
        view.tags.axes.filter { axis -> held.any { h -> h.card.roles.any { it.axis == axis.id } } }

    override fun numbers(input: ReviewInput): NumberTable {
        val view = input.synergy ?: return NumberTable(emptyList())
        val held = held(input, view)
        return NumberTable(
            axesInPlay(held, view).flatMap { axis ->
                listOf(
                    NumberEntry(
                        "/axes/${axis.id}/enablers",
                        copiesWith(held, axis, ENABLER).toDouble(),
                        null,
                    ),
                    NumberEntry(
                        "/axes/${axis.id}/payoffs",
                        copiesWith(held, axis, PAYOFF).toDouble(),
                        null,
                    ),
                )
            }
        )
    }

    override fun facts(input: ReviewInput): String {
        val view = input.synergy ?: return ""
        val held = held(input, view)
        val axisLines =
            axesInPlay(held, view).map { axis -> "${axis.id} (${axis.name}): ${axis.definition}" }
        val pairs = mutableListOf<PairLine>()
        for ((i, a) in held.withIndex()) {
            if (a.copies >= 2) {
                pairs +=
                    PairLine(view.table.score(a.card.id, a.card.id), a.card.name, a.card.name, true)
            }
            for (b in held.drop(i + 1)) {
                val (first, second) = listOf(a.card.name, b.card.name).sorted()
                pairs += PairLine(view.table.score(a.card.id, b.card.id), first, second, false)
            }
        }
        val strongest =
            pairs
                .filter { it.score >= 1 }
                .sortedWith(compareBy({ -it.score }, { it.first }, { it.second }))
                .take(STRONGEST)
                .map { "${it.score} ${it.first} with ${if (it.itself) "itself" else it.second}" }
        return (listOf("Axes in play:") +
                axisLines +
                "Strongest pairs in the pool:" +
                strongest.ifEmpty { listOf("none") })
            .joinToString("\n")
    }

    override fun unavailable(input: ReviewInput): String? =
        if (input.synergy == null) "no synergy tags for this set" else null
}
