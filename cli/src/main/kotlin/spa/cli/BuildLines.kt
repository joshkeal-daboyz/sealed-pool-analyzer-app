package spa.cli

import java.util.Locale
import kotlin.math.abs
import spa.agent.build.BuildSetup
import spa.agent.build.Candidate
import spa.agent.build.DeckCard
import spa.agent.build.ScoredDeck
import spa.core.text.Color

/**
 * The lines `spa build` prints for a [ScoredDeck] (SPA-044): a heading with the score, one line per
 * part (the part's own value and reason, so a new part prints itself), the spells by mana value,
 * the lands, the sideboard and the off-colour count. It knows no part by name.
 */
internal object BuildLines {
    private const val NAMED_PARTNERS = 2
    private const val CASTABLE_NOTE = 0.9
    private const val PERCENT = 100
    private const val ROUNDS_TO_ZERO = 0.0005
    private val BASICS =
        mapOf(
            Color.W to "Plains",
            Color.U to "Island",
            Color.B to "Swamp",
            Color.R to "Mountain",
            Color.G to "Forest",
        )

    fun lines(setup: BuildSetup, scored: ScoredDeck): List<String> {
        val deck = scored.deck
        val out = mutableListOf("Deck ${setup.config}: score ${number(scored.total)}")
        scored.parts.forEach { part ->
            out += "  ${part.key} ${signed(part.value)}" + (part.reason?.let { ": $it" } ?: "")
        }
        out += "Spells (${deck.spells.size})"
        deck.spells
            .distinct()
            .groupBy { it.bucket }
            .toSortedMap()
            .forEach { (bucket, cards) ->
                val label = if (bucket == DeckCard.TOP_BUCKET) "$bucket or more" else "$bucket"
                out +=
                    "Mana value $label (${cards.sumOf { card -> deck.spells.count { it === card } }})"
                cards.forEach { card ->
                    val copies = deck.spells.count { it === card }
                    out += "  $copies ${card.name}  grade ${grade(card)}  ${reason(scored, card)}"
                }
            }
        out += lands(scored)
        out += sideboard(setup, scored)
        return out
    }

    private fun reason(scored: ScoredDeck, card: DeckCard): String {
        val deck = scored.deck
        val partners =
            deck.spells
                .distinct()
                .filter { it !== card }
                .map { it to deck.setup.pair(card, it) }
                .filter { it.second > 0 }
                .sortedWith(
                    compareByDescending<Pair<DeckCard, Int>> { it.second }.thenBy { it.first.id }
                )
                .take(NAMED_PARTNERS)
        val pairs =
            if (partners.isEmpty()) "no synergy partners"
            else "pairs with " + partners.joinToString(", ") { "${it.first.name} (${it.second})" }
        val castable = deck.castable(card)
        return if (castable < CASTABLE_NOTE) {
            "$pairs; castable ${String.format(Locale.ROOT, "%.0f", castable * PERCENT)}%"
        } else {
            pairs
        }
    }

    private fun lands(scored: ScoredDeck): List<String> {
        val deck = scored.deck
        val named =
            deck.nonbasics.groupingBy { it.name }.eachCount().map { "${it.value} ${it.key}" }
        val basics =
            deck.basics
                .filterValues { it > 0 }
                .map { (colour, n) -> "$n ${BASICS.getValue(colour)}" }
        return listOf("Lands (${deck.landCount}): " + (named + basics).joinToString(", "))
    }

    private fun sideboard(setup: BuildSetup, scored: ScoredDeck): List<String> {
        val deck = scored.deck
        val used = (deck.spells + deck.nonbasics).groupingBy { it }.eachCount()
        val colours = setup.config.colours
        val heavy = setup.rest.filter { c -> c.card.card.colors.all { it in colours } }
        val off = setup.rest.filter { c -> c.card.card.colors.any { it !in colours } }
        val left =
            (setup.spells + heavy + setup.lands + setup.unplayedLands)
                .map { it to it.quantity - (used[it.card] ?: 0) }
                .filter { it.second > 0 }
        val byColour =
            Color.entries
                .filter { it !in colours }
                .map { colour ->
                    colour to
                        off.filter { colour in it.card.card.colors }.sumOf(Candidate::quantity)
                }
                .filter { it.second > 0 }
        return listOf("Sideboard (${left.sumOf { it.second }})") +
            left.map { (c, n) -> "  $n ${c.card.name}  grade ${grade(c.card)}" } +
            ("Off-colour: " +
                if (byColour.isEmpty()) "none"
                else byColour.joinToString(", ") { "${it.first.symbol} ${it.second}" })
    }

    private fun grade(card: DeckCard) = String.format(Locale.ROOT, "%.1f", card.grade)

    private fun number(value: Double) = String.format(Locale.ROOT, "%.3f", clean(value))

    private fun signed(value: Double) = String.format(Locale.ROOT, "%+.3f", clean(value))

    private fun clean(value: Double) = if (abs(value) < ROUNDS_TO_ZERO) 0.0 else value
}
