package spa.agent.review.agents

import spa.agent.review.AnalysisAgent
import spa.agent.review.NumberEntry
import spa.agent.review.NumberTable
import spa.agent.review.NumberTables
import spa.agent.review.ReviewInput
import spa.core.brief.Archetype
import spa.core.metrics.ColorPair

/** Which of the set's designed archetypes the pool supports: signposts and key commons present. */
object ArchetypeAgent : AnalysisAgent {
    private val PATTERNS = listOf("/pairs/*/cards", "/pairs/*/creatures")

    override val id: String = "archetypes"
    override val title: String = "Archetypes"

    /** The brief's archetypes by pair, in [ColorPair] order; one with no such pair is left out. */
    private fun byPair(input: ReviewInput): List<Pair<ColorPair, Archetype>> =
        input.brief.archetypes
            .mapNotNull { archetype ->
                val colors = archetype.colors.toSet()
                ColorPair.entries
                    .firstOrNull { pair -> pair.colors.map { it.symbol }.toSet() == colors }
                    ?.let { it to archetype }
            }
            .sortedBy { it.first }

    /** The names the pool holds: each card's full name and its front-face name. */
    private fun poolNames(input: ReviewInput): Set<String> =
        input.pool.entries
            .filter { it.count > 0 }
            .flatMap { listOf(it.printing.card.name, it.printing.card.faces.first().name) }
            .toSet()

    override fun numbers(input: ReviewInput): NumberTable {
        val names = poolNames(input)
        val own =
            byPair(input).flatMap { (pair, archetype) ->
                val signposts = archetype.signpostUncommons.distinct()
                val commons = archetype.keyCommons.distinct()
                val base = "/archetypes/$pair"
                listOf(
                    NumberEntry(
                        "$base/signpostsPresent",
                        signposts.count { it in names }.toDouble(),
                        null,
                    ),
                    NumberEntry(
                        "$base/signpostsTotal",
                        archetype.signpostUncommons.size.toDouble(),
                        null,
                    ),
                    NumberEntry(
                        "$base/keyCommonsPresent",
                        commons.count { it in names }.toDouble(),
                        null,
                    ),
                    NumberEntry(
                        "$base/keyCommonsTotal",
                        archetype.keyCommons.size.toDouble(),
                        null,
                    ),
                )
            }
        return NumberTable(own + NumberTables.fromMetrics(input, PATTERNS).entries)
    }

    override fun facts(input: ReviewInput): String {
        val names = poolNames(input)
        return byPair(input).joinToString("\n") { (pair, archetype) ->
            val (signposts, missingSignposts) =
                archetype.signpostUncommons.partition { it in names }
            val (commons, missingCommons) = archetype.keyCommons.partition { it in names }
            "$pair ${archetype.name}: signposts present: ${list(signposts)}; " +
                "missing: ${list(missingSignposts)}. Key commons present: ${list(commons)}; " +
                "missing: ${list(missingCommons)}."
        }
    }

    override fun unavailable(input: ReviewInput): String? = null

    private fun list(names: List<String>): String =
        if (names.isEmpty()) "none" else names.joinToString(", ")
}
