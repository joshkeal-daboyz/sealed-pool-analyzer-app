package spa.agent.review

import java.util.Locale
import kotlin.math.abs
import spa.agent.build.DeckPartScore
import spa.agent.build.RankedDeck
import spa.core.text.Color

/** [count] copies of the card called [name]. */
data class NameCount(val name: String, val count: Int)

/**
 * A ranked deck as the review shows it (G-006 Decided 3): its [rank], its [colours] (the
 * configuration as `spa build` prints it), the configurations it shares its cards with ([sameAs]),
 * its [score], its [spells] and [lands] by name and copies, and [why] it ranks where it does (empty
 * when no part gave a reason).
 */
data class DeckSummary(
    val rank: Int,
    val colours: String,
    val sameAs: List<String>,
    val score: Double,
    val spells: List<NameCount>,
    val landCount: Int,
    val lands: List<NameCount>,
    val why: String,
) {
    /** The deck's block in the review: a heading, the spells, the lands and, if any, the why. */
    fun text(): String {
        val same = if (sameAs.isEmpty()) "" else " (same cards as ${sameAs.joinToString(", ")})"
        val lines =
            listOf(
                "Rank $rank: $colours$same  score ${decimals(score)}",
                "  Spells (${spells.sumOf { it.count }}): ${names(spells)}",
                "  Lands ($landCount): ${names(lands)}",
            ) + if (why.isEmpty()) emptyList() else listOf("  Why: $why")
        return lines.joinToString("\n")
    }

    companion object {
        private const val WHY_PARTS = 2
        private val BASICS =
            mapOf(
                Color.W to "Plains",
                Color.U to "Island",
                Color.B to "Swamp",
                Color.R to "Mountain",
                Color.G to "Forest",
            )

        /** The summary of [ranked], which stands at [rank] in its ranking. */
        fun of(rank: Int, ranked: RankedDeck): DeckSummary {
            val deck = ranked.scored.deck
            val nonbasics = deck.nonbasics.groupingBy { it.name }.eachCount()
            val basics =
                Color.entries
                    .filter { (deck.basics[it] ?: 0) > 0 }
                    .map { NameCount(BASICS.getValue(it), deck.basics.getValue(it)) }
            return DeckSummary(
                rank = rank,
                colours = ranked.config.toString(),
                sameAs = ranked.sameAs.map { it.toString() },
                score = ranked.scored.total,
                spells = counts(deck.spells.groupingBy { it.name }.eachCount()),
                landCount = deck.landCount,
                lands = counts(nonbasics) + basics,
                why = why(ranked.scored.parts),
            )
        }

        private fun counts(copies: Map<String, Int>): List<NameCount> =
            copies.entries.sortedBy { it.key }.map { NameCount(it.key, it.value) }

        private fun why(parts: List<DeckPartScore>): String =
            parts
                .filter { it.reason != null }
                .sortedByDescending { abs(it.value) }
                .take(WHY_PARTS)
                .joinToString("; ") {
                    "${it.key} ${String.format(Locale.ROOT, "%+.2f", it.value)}: ${it.reason}"
                }

        private fun decimals(value: Double) = String.format(Locale.ROOT, "%.2f", value)

        private fun names(entries: List<NameCount>) =
            entries.joinToString(", ") { "${it.count}x ${it.name}" }
    }
}
