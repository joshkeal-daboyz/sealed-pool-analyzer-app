package spa.agent.build

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import spa.core.math.Hypergeometric
import spa.core.math.LandMath
import spa.core.text.Color

/**
 * A deck of [spells] (one entry per copy, in collector order) and [landCount] lands: the pool's
 * nonbasic lands that supply two or more of the deck's colours ([nonbasics]), then [basics] split
 * by the spells' coloured pips ([LandMath.recommendLands]). Everything the [DeckPart]s read is
 * here.
 */
class Deck
internal constructor(val setup: BuildSetup, val spells: List<DeckCard>, val landCount: Int) {
    val config: BuildConfig
        get() = setup.config

    /** The cards in the deck, spells and lands. */
    val size: Int = spells.size + landCount

    /** The mean mana value of the spells; 0 with none. */
    val meanManaValue: Double =
        if (spells.isEmpty()) 0.0 else spells.sumOf { it.card.manaValue } / spells.size

    /** The nonbasic lands played, one entry per copy. */
    val nonbasics: List<DeckCard> =
        setup.landCopies.take(
            maxOf(0, minOf(landCount - config.colours.size, setup.landCopies.size))
        )

    /** The basic lands played, by colour. */
    val basics: Map<Color, Int> = splitBasics()

    private val castableMemo = HashMap<DeckCard, Double>()

    /** The spells with fixing, and the nonbasic lands. */
    val fixingCards: List<DeckCard> = spells.filter { it.fixing != null } + nonbasics

    /** The number of spells in curve bucket [bucket] (6 is 6 or more). */
    fun bucketCount(bucket: Int): Int = spells.count { it.bucket == bucket }

    /** The sum of every pair of spell copies' synergy score, a second copy's diagonal included. */
    val pairSum: Int by lazy {
        var sum = 0
        for (i in spells.indices) {
            for (j in i + 1 until spells.size) sum += setup.pair(spells[i], spells[j])
        }
        sum
    }

    /**
     * The sources of [colour]: basics, nonbasic lands that supply it, and half a source (rounded
     * down in all) for each nonland fixing spell that supplies it.
     */
    fun sources(colour: Color): Int {
        val lands = (basics[colour] ?: 0) + nonbasics.count { supplies(it, colour) }
        val spellsFixing = spells.count { supplies(it, colour) }
        return minOf(DECK_SIZE, (2 * lands + spellsFixing) / 2)
    }

    /**
     * The chance [card] is castable on curve: for each colour of its cost, at least as many sources
     * as pips among the cards seen by turn mana-value (6 + mana value, at least 7 cards).
     */
    fun castable(card: DeckCard): Double =
        castableMemo.getOrPut(card) {
            val draws = minOf(DECK_SIZE, OPENING_CARDS + maxOf(card.manaValue, 1))
            card.pips.entries.fold(1.0) { chance, (colour, pips) ->
                chance * atLeast(sources(colour), draws, ceil(pips).toInt())
            }
        }

    private fun supplies(card: DeckCard, colour: Color) =
        card.fixing?.colours?.contains(colour) == true

    private fun splitBasics(): Map<Color, Int> {
        val count = landCount - nonbasics.size
        val pips = HashMap<Color, Double>()
        spells.forEach { s -> s.pips.forEach { (c, p) -> pips.merge(c, p, Double::plus) } }
        val weights = if (pips.isEmpty()) config.main.associateWith { 1.0 } else pips.toMap()
        return LandMath.recommendLands(weights, count)
    }

    private companion object {
        const val DECK_SIZE = LandMath.DEFAULT_DECK_SIZE
        const val OPENING_CARDS = 6
        const val KEY_SOURCES = 10_000
        const val KEY_DRAWS = 100
        val chances = ConcurrentHashMap<Int, Double>()

        fun atLeast(sources: Int, draws: Int, pips: Int): Double =
            chances.getOrPut(sources * KEY_SOURCES + draws * KEY_DRAWS + pips) {
                Hypergeometric.atLeast(DECK_SIZE, sources, draws, pips)
            }
    }
}
