package spa.core.math

import kotlin.math.floor
import spa.core.text.Color

/**
 * Land counts for a limited deck: how to split basics by color and how many sources a color needs.
 */
object LandMath {
    /** The usual land count for a 40-card limited deck. */
    const val DEFAULT_TOTAL_LANDS = 17

    /** The fewest lands of each color in a two-color split. */
    const val DEFAULT_MIN_PER_COLOR = 6

    /** The usual limited deck size. */
    const val DEFAULT_DECK_SIZE = 40

    private const val TWO_COLORS = 2

    /**
     * Splits [totalLands] lands among the colors in [pips] in proportion to their weight, such as a
     * [spa.core.text.ManaCost.pips] summed over a deck.
     *
     * Only colors with weight above zero get lands, and the result always sums to [totalLands].
     * Rounding is largest remainder: each color gets the floor of its share, and the lands left
     * over go to the largest fractional parts, ties broken by [Color] enum order. When exactly two
     * colors are present, each gets at least [minPerColor] (capped at half of [totalLands]), taking
     * the difference from the larger share.
     *
     * Weights must be finite and >= 0, and [totalLands] and [minPerColor] >= 0. Returns an empty
     * map when no color has weight above zero.
     */
    fun recommendLands(
        pips: Map<Color, Double>,
        totalLands: Int = DEFAULT_TOTAL_LANDS,
        minPerColor: Int = DEFAULT_MIN_PER_COLOR,
    ): Map<Color, Int> {
        require(totalLands >= 0) { "totalLands must be >= 0, got $totalLands" }
        require(minPerColor >= 0) { "minPerColor must be >= 0, got $minPerColor" }
        require(pips.values.all { it.isFinite() && it >= 0.0 }) {
            "pips must be finite and >= 0, got $pips"
        }
        val colors = Color.entries.filter { (pips[it] ?: 0.0) > 0.0 }
        if (colors.isEmpty()) return emptyMap()
        val split = proportionalSplit(colors.associateWith { pips.getValue(it) }, totalLands)
        if (colors.size == TWO_COLORS) applyMinimum(split, minOf(minPerColor, totalLands / 2))
        return colors.associateWith { split.getValue(it) }
    }

    /**
     * The smallest number of sources in a [deckSize]-card deck for which the chance of seeing at
     * least one in [cardsSeen] cards, [Hypergeometric.atLeast], is at least [targetProbability].
     *
     * [targetProbability] must be in 0..1 and [cardsSeen] in 0..[deckSize].
     *
     * @throws IllegalArgumentException when no number of sources reaches the target, which happens
     *   only when [cardsSeen] is 0 and the target is above 0.
     */
    fun sourcesFor(
        targetProbability: Double,
        cardsSeen: Int,
        deckSize: Int = DEFAULT_DECK_SIZE,
    ): Int {
        require(targetProbability in 0.0..1.0) {
            "targetProbability must be in 0..1, got $targetProbability"
        }
        require(deckSize >= 0) { "deckSize must be >= 0, got $deckSize" }
        require(cardsSeen in 0..deckSize) { "cardsSeen must be in 0..$deckSize, got $cardsSeen" }
        val sources =
            (0..deckSize).firstOrNull {
                Hypergeometric.atLeast(deckSize, it, cardsSeen, 1) >= targetProbability
            }
        requireNotNull(sources) { "No source count reaches $targetProbability in $cardsSeen cards" }
        return sources
    }

    private fun proportionalSplit(
        weights: Map<Color, Double>,
        totalLands: Int,
    ): MutableMap<Color, Int> {
        val sum = weights.values.sum()
        val shares = weights.mapValues { (_, weight) -> totalLands * weight / sum }
        val split = shares.mapValues { (_, share) -> floor(share).toInt() }.toMutableMap()
        val leftOver = totalLands - split.values.sum()
        // sortedByDescending is stable, so equal fractions keep enum order.
        val byFraction = shares.keys.sortedByDescending { shares.getValue(it) - split.getValue(it) }
        repeat(leftOver) { i ->
            val color = byFraction[i % byFraction.size]
            split[color] = split.getValue(color) + 1
        }
        return split
    }

    private fun applyMinimum(split: MutableMap<Color, Int>, minimum: Int) {
        val (smaller, larger) = split.keys.sortedBy { split.getValue(it) }
        val shortfall = minimum - split.getValue(smaller)
        if (shortfall > 0) {
            split[smaller] = split.getValue(smaller) + shortfall
            split[larger] = split.getValue(larger) - shortfall
        }
    }
}
