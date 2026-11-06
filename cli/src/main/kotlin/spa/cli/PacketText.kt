package spa.cli

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import spa.agent.grade.GradeLookup
import spa.agent.synergy.SynergyTable
import spa.core.baseline.Baseline
import spa.core.cards.Card
import spa.core.cards.Pool
import spa.core.cards.PoolEntry

/** The text of the packet `spa packet` writes (SPA-071): seven sections, line feeds only. */
internal object PacketText {
    const val NO_DECK = "No deck could be built from this pool."
    const val NO_FIXING = "No fixing cards."
    const val NO_TAGS = "No synergy tags for this set."
    private const val NO_SYNERGY = "None."
    private const val MAX_SYNERGY = 20
    private const val DECIMALS = 4
    private const val DASH = "-"
    private const val FACES = " // "

    private const val SOURCES =
        "Card data from Scryfall (scryfall.com). Card text and images are property of " +
            "Wizards of the Coast."

    private val OPENING =
        """
        |You are helping a player build a Magic: The Gathering limited deck from their sealed pool. Everything below was worked out by code from the player's own pool: the cards with a grade for each (0 to 5, relative to this set), the pool's numbers beside what a typical pool from this set holds, the pool's fixing, the strongest card-to-card synergies, and the three best decks the deck builder found, with its reasons.
        |
        |Please:
        |1. Say which of the three decks to register, and why, in a short paragraph.
        |2. Go through the pool by colour: where it is deep, where it is thin, and how the removal, the curve and the mana compare with the typical pool.
        |3. Name cards left out of the best deck that deserve a second look, and any card in it that you would cut, with the reason.
        |4. Say what you are unsure of, and anything in this packet that looks wrong.
        |
        |Use only this packet. Quote card names exactly as written here, and take numbers from the packet rather than computing your own.
        """
            .trimMargin()

    /** The whole packet: the opening text, then each section's lines under its heading. */
    fun packet(
        pool: List<String>,
        numbers: List<String>,
        fixing: List<String>,
        synergy: List<String>,
        decks: List<String>,
    ): String =
        listOf(
                "# Sealed pool review packet" to listOf(OPENING),
                "## Pool" to pool,
                "## Numbers" to numbers,
                "## Fixing" to fixing,
                "## Synergy" to synergy,
                "## Decks" to decks,
                "## Sources" to listOf(SOURCES),
            )
            .joinToString("\n\n") { (heading, lines) ->
                heading + "\n\n" + lines.joinToString("\n")
            } + "\n"

    /** The set line, then one line per distinct card of [pool], sorted by name. */
    fun pool(set: String, setName: String, pool: Pool, lookup: GradeLookup): List<String> {
        val cards = pool.entries.groupBy { it.printing.card.oracleId }.values
        val header = "Set: $set ($setName), ${pool.totalCount} cards, ${cards.size} distinct"
        val lines =
            cards
                .map { entries -> entries.first().printing.card to entries }
                .sortedBy { it.first.name }
                .map { (card, entries) -> cardLine(set, card, entries, lookup) }
        return listOf(header) + lines
    }

    private fun cardLine(set: String, card: Card, entries: List<PoolEntry>, lookup: GradeLookup) =
        listOf(
                "${entries.sumOf { it.count }}x ${card.name}",
                card.faces.joinToString(FACES) { it.manaCost.ifEmpty { DASH } },
                card.faces.joinToString(FACES) { it.typeLine },
                card.rarity.scryfallName,
                grade(set, entries, lookup),
                card.faces.joinToString(FACES) { it.oracleText.replace("\n", " / ") },
            )
            .joinToString(" | ")
            .trimEnd()

    private fun grade(set: String, entries: List<PoolEntry>, lookup: GradeLookup): String {
        val score = entries.firstNotNullOfOrNull { lookup.grade(it.printing, set)?.score }
        return if (score == null) "grade none"
        else String.format(Locale.ROOT, "grade %.1f (%s)", score, GradesCommand.label(score))
    }

    /**
     * One line per [metrics] entry but the curve's, by pointer, with the [baseline]'s spread when
     * it has one for the pointer.
     */
    fun numbers(metrics: Map<String, Double>, baseline: Baseline?): List<String> =
        metrics.entries
            .filter { "/curve/" !in it.key }
            .sortedBy { it.key }
            .map { (pointer, value) ->
                val typical =
                    baseline?.stats?.get(pointer)?.let {
                        " (typical: median ${number(it.median)}, " +
                            "p10 ${number(it.p10)}, p90 ${number(it.p90)})"
                    }
                "$pointer = ${number(value)}${typical.orEmpty()}"
            }

    /** [value] without a decimal point when whole, else to at most 4 decimals, no trailing zero. */
    fun number(value: Double): String =
        BigDecimal(value).setScale(DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().let {
            if (it.signum() == 0) "0" else it.toPlainString()
        }

    /**
     * Up to 20 `<score> <name> with <name>` lines over [pool]'s distinct cards, score 1 or more,
     * highest first, ties by the two names; a card held twice or more pairs with itself. [ids] maps
     * a card's oracle id to its id in [table].
     */
    fun synergy(pool: Pool, ids: Map<String, String>, table: SynergyTable): List<String> {
        val counts = pool.cardCounts()
        val cards =
            pool.entries
                .map { it.printing.card }
                .distinctBy { it.oracleId }
                .filter { it.oracleId in ids }
                .sortedBy { it.name }
        val pairs =
            cards.indices.flatMap { i ->
                (i until cards.size).mapNotNull { j ->
                    val self = i == j
                    if (self && counts.getValue(cards[i].oracleId) < 2) return@mapNotNull null
                    val score =
                        table.score(
                            ids.getValue(cards[i].oracleId),
                            ids.getValue(cards[j].oracleId),
                        )
                    if (score < 1) null else Triple(score, cards[i].name, cards[j].name)
                }
            }
        val lines =
            pairs
                .sortedWith(
                    compareByDescending<Triple<Int, String, String>> { it.first }
                        .thenBy { it.second }
                        .thenBy { it.third }
                )
                .take(MAX_SYNERGY)
                .map { (score, a, b) -> "$score $a with ${if (a == b) "itself" else b}" }
        return lines.ifEmpty { listOf(NO_SYNERGY) }
    }
}
