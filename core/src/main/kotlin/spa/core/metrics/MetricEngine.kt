package spa.core.metrics

import kotlin.math.floor
import spa.core.cards.Pool
import spa.core.cards.Rarity
import spa.core.math.Hypergeometric
import spa.core.math.LandMath
import spa.core.text.Color

/**
 * Computes a pool's [PoolMetrics]. Every number is a deterministic function of the pool: no model
 * calls, no randomness, and the order of the pool's entries does not matter. Counts are by copies,
 * and an entry with a count of zero or less contributes nothing.
 */
object MetricEngine {
    private const val DECK_SIZE = LandMath.DEFAULT_DECK_SIZE
    private const val CARDS_SEEN = 9
    private const val ROUNDING = 10_000.0
    private const val SPLASH_PIPS = 1.0

    /** The metrics for [pool]. Equal pools, in any entry order, give equal results. */
    fun compute(pool: Pool): PoolMetrics {
        val copies = pool.entries.filter { it.count > 0 }.map(::PoolCard)
        val nonland = copies.filterNot { it.card.isLand }
        val lands = copies.filter { it.card.isLand }
        val fixing = fixingSources(copies)
        return PoolMetrics(
            totalCards = copies.copies(),
            nonlandCards = nonland.copies(),
            basicLands = lands.filter { it.card.isBasicLand }.copies(),
            nonbasicLands = lands.filterNot { it.card.isBasicLand }.copies(),
            byColor = ColorBucket.entries.associateWith { colorMetrics(it, nonland) },
            fixingSources = fixing,
            removalTotal = nonland.filter { it.isRemoval }.copies(),
            interactionTotal = nonland.filter { it.isInteraction }.copies(),
            pairs = ColorPair.entries.associateWith { pairMetrics(it, nonland, fixing) },
        )
    }

    private fun colorMetrics(bucket: ColorBucket, nonland: List<PoolCard>): ColorMetrics {
        val cards = nonland.filter { bucket.holds(it.card.colors) }
        return ColorMetrics(
            cards = rarityCounts(cards),
            playables = rarityCounts(cards.filter { bucket.isPlayable(it.card.colors) }),
            creatures = cards.filter { it.card.isCreature }.copies(),
            removal = cards.filter { it.isRemoval }.copies(),
            interaction = cards.filter { it.isInteraction }.copies(),
        )
    }

    private fun rarityCounts(cards: List<PoolCard>): RarityCounts {
        val byRarity = cards.groupBy { it.rarity }.mapValues { it.value.copies() }
        return RarityCounts(
            common = byRarity[Rarity.COMMON] ?: 0,
            uncommon = byRarity[Rarity.UNCOMMON] ?: 0,
            rare = byRarity[Rarity.RARE] ?: 0,
            mythic = byRarity[Rarity.MYTHIC] ?: 0,
            total = cards.copies(),
        )
    }

    /** One [FixingSource] per card, printings and entries merged, sorted by name. */
    private fun fixingSources(copies: List<PoolCard>): List<FixingSource> =
        copies
            .filter { it.isFixingSource }
            .groupBy { it.card.oracleId }
            .values
            .map { same ->
                FixingSource(same.first().card.name, same.first().produces, same.copies())
            }
            .sortedBy { it.name }

    private fun pairMetrics(
        pair: ColorPair,
        nonland: List<PoolCard>,
        fixing: List<FixingSource>,
    ): ColorPairMetrics {
        val castable = nonland.filter { pair.colors.containsAll(it.card.colors) }
        val lands = LandMath.recommendLands(pipTotals(castable), LandMath.DEFAULT_TOTAL_LANDS)
        return ColorPairMetrics(
            cards = castable.copies(),
            creatures = castable.filter { it.card.isCreature }.copies(),
            noncreatures = castable.filterNot { it.card.isCreature }.copies(),
            curve = curve(castable),
            removal = castable.filter { it.isRemoval }.copies(),
            interaction = castable.filter { it.isInteraction }.copies(),
            fixingSources =
                fixing
                    .filter { source -> source.colors.any { it in pair.colors } }
                    .sumOf { it.count },
            splashCandidates =
                nonland
                    .mapNotNull { splashCandidate(pair, it) }
                    .distinct()
                    .sortedWith(compareBy({ it.name }, { it.color })),
            landRecommendation = lands,
            castProbability = pair.colors.associateWith { castProbability(it, lands, fixing) },
        )
    }

    /** The pips of [cards] summed per color, by copies. */
    private fun pipTotals(cards: List<PoolCard>): Map<Color, Double> {
        val totals = mutableMapOf<Color, Double>()
        cards.forEach { card ->
            card.pips.forEach { (color, weight) ->
                totals.merge(color, weight * card.count, Double::plus)
            }
        }
        return totals
    }

    private fun curve(cards: List<PoolCard>): Curve {
        val byManaValue = cards.groupBy { floor(it.card.manaValue).toInt().coerceIn(0, Curve.TOP) }
        return Curve(
            (0..Curve.TOP).map { manaValue ->
                val bucket = byManaValue[manaValue].orEmpty()
                CurveBucket(
                    manaValue = manaValue,
                    creatures = bucket.filter { it.card.isCreature }.copies(),
                    noncreatures = bucket.filterNot { it.card.isCreature }.copies(),
                )
            }
        )
    }

    /**
     * The chance of at least one source of [color] in the first [CARDS_SEEN] cards of a
     * [DECK_SIZE]-card deck, where the sources are the recommended [lands] of that color plus the
     * copies of [fixing] sources that produce it, rounded to 4 decimals.
     */
    private fun castProbability(
        color: Color,
        lands: Map<Color, Int>,
        fixing: List<FixingSource>,
    ): Double {
        val sources = (lands[color] ?: 0) + fixing.filter { color in it.colors }.sumOf { it.count }
        val chance = Hypergeometric.atLeast(DECK_SIZE, minOf(sources, DECK_SIZE), CARDS_SEEN, 1)
        return Math.round(chance * ROUNDING) / ROUNDING
    }

    /**
     * [card] as a splash candidate for [pair]: a nonland card with exactly one color outside the
     * pair, exactly one pip of it and no pip of any other off-pair color, that is removal or a rare
     * or mythic printing. Null for any other card.
     */
    private fun splashCandidate(pair: ColorPair, card: PoolCard): SplashCandidate? {
        val color = (card.card.colors - pair.colors).singleOrNull()
        val reason =
            when {
                card.isRemoval -> SplashReason.REMOVAL
                card.rarity == Rarity.RARE || card.rarity == Rarity.MYTHIC ->
                    SplashReason.RARE_OR_MYTHIC
                else -> null
            }
        if (color == null || reason == null) return null
        val offPairPips = card.pips.filterKeys { it !in pair.colors }
        return if (offPairPips == mapOf(color to SPLASH_PIPS)) {
            SplashCandidate(card.card.name, color, reason)
        } else {
            null
        }
    }
}
