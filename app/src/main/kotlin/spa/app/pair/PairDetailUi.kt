package spa.app.pair

import spa.app.importing.ImportOutcome
import spa.app.results.DisplayedValue
import spa.core.baseline.Baseline
import spa.core.metrics.ColorPair
import spa.core.metrics.Curve
import spa.core.metrics.SplashCandidate
import spa.core.text.Color

/** One land row: the basic lands of [color] the recommendation gives. */
data class LandUi(val color: Color, val lands: DisplayedValue)

/**
 * One curve row: the bucket's [manaValue], its [creatures], and its [other] (noncreature) cards.
 */
data class CurveRowUi(
    val manaValue: Int,
    val creatures: DisplayedValue,
    val other: DisplayedValue,
) {
    /** The last bucket, which holds its mana value and everything above it. */
    val isTop: Boolean
        get() = manaValue == Curve.TOP
}

/** Everything Pair detail shows for one pair. The screen renders only from this. */
data class PairDetailUi(
    val pair: ColorPair,
    /** How many lines of the pool were not read. */
    val unresolvedLines: Int,
    /** The pair's first colour, then its second. */
    val lands: List<LandUi>,
    /** Cards, creatures, noncreatures, removal, interaction, fixing sources. */
    val counts: List<DisplayedValue>,
    /** The eight buckets, mana value 0 to [Curve.TOP]. */
    val curve: List<CurveRowUi>,
    val splashCount: DisplayedValue,
    /** By name then colour, as the engine gives them. */
    val splashCandidates: List<SplashCandidate>,
) {
    /** Every displayed value, in display order. */
    val values: List<DisplayedValue>
        get() =
            lands.map { it.lands } +
                counts +
                curve.flatMap { listOf(it.creatures, it.other) } +
                splashCount

    companion object {
        /** The counts under Cards, as pointer segments, in display order. */
        const val CARDS = "cards"
        const val CREATURES = "creatures"
        const val NONCREATURES = "noncreatures"
        const val REMOVAL = "removal"
        const val INTERACTION = "interaction"
        const val FIXING_SOURCES = "fixingSources"

        /** The display model for [pair] of [success] against [baseline]. */
        fun from(
            success: ImportOutcome.Success,
            pair: ColorPair,
            baseline: Baseline,
        ): PairDetailUi {
            val numbers = success.metrics.pairs.getValue(pair)
            val at = "/pairs/$pair"
            fun shown(pointer: String, value: Int) =
                DisplayedValue(pointer, value, baseline.stats[pointer])

            return PairDetailUi(
                pair = pair,
                unresolvedLines = success.result.unresolved.size,
                lands =
                    listOf(pair.first, pair.second).map { color ->
                        // A pair with no pips has no recommendation: 0 of each.
                        val lands = numbers.landRecommendation[color] ?: 0
                        LandUi(color, shown("$at/landRecommendation/$color", lands))
                    },
                counts =
                    listOf(
                        shown("$at/$CARDS", numbers.cards),
                        shown("$at/$CREATURES", numbers.creatures),
                        shown("$at/$NONCREATURES", numbers.noncreatures),
                        shown("$at/$REMOVAL", numbers.removal),
                        shown("$at/$INTERACTION", numbers.interaction),
                        shown("$at/$FIXING_SOURCES", numbers.fixingSources),
                    ),
                curve =
                    numbers.curve.buckets.mapIndexed { i, bucket ->
                        val bucketAt = "$at/curve/buckets/$i"
                        CurveRowUi(
                            manaValue = bucket.manaValue,
                            creatures = shown("$bucketAt/creatures", bucket.creatures),
                            other = shown("$bucketAt/noncreatures", bucket.noncreatures),
                        )
                    },
                splashCount = shown("$at/splashCandidates/count", numbers.splashCandidates.size),
                splashCandidates = numbers.splashCandidates,
            )
        }
    }
}
