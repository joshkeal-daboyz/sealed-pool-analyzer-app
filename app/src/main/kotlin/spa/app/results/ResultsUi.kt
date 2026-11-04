package spa.app.results

import spa.app.importing.ImportOutcome
import spa.core.baseline.Baseline
import spa.core.baseline.Stat
import spa.core.intake.KitWarning
import spa.core.intake.UnresolvedLine
import spa.core.intake.UnresolvedReason
import spa.core.metrics.ColorBucket
import spa.core.metrics.ColorPair
import spa.core.metrics.FixingSource
import spa.core.metrics.RarityCounts

/** Where a value sits against its typical range. */
enum class Outside {
    /** Below the 10th percentile. */
    BELOW,

    /** Above the 90th percentile. */
    ABOVE,
}

/**
 * One number Results shows: its JSON [pointer] (a key of the baseline's `stats`), its [value], and
 * the baseline's [typical] stat at that pointer, or null when the baseline has none.
 */
data class DisplayedValue(val pointer: String, val value: Int, val typical: Stat?) {
    /** [Outside.BELOW] under the typical p10, [Outside.ABOVE] over its p90, else null. */
    val outside: Outside?
        get() =
            when {
                typical == null -> null
                value < typical.p10 -> Outside.BELOW
                value > typical.p90 -> Outside.ABOVE
                else -> null
            }
}

/** One colour pair's row: its cards and creatures. */
data class PairUi(val pair: ColorPair, val cards: DisplayedValue, val creatures: DisplayedValue)

/** One colour bucket's row: shown collapsed, then the rest when expanded. */
data class ColorUi(
    val bucket: ColorBucket,
    val cards: DisplayedValue,
    val playables: DisplayedValue,
    val creatures: DisplayedValue,
    val removal: DisplayedValue,
    val interaction: DisplayedValue,
    /** Common, uncommon, rare, mythic. */
    val cardsByRarity: List<DisplayedValue>,
    /** Common, uncommon, rare, mythic. */
    val playablesByRarity: List<DisplayedValue>,
) {
    /** Every value in this row, in display order. */
    val values: List<DisplayedValue>
        get() =
            listOf(cards, playables, creatures, removal, interaction) +
                cardsByRarity +
                playablesByRarity
}

/** Everything Results shows below the pool header. The screen renders only from this. */
data class ResultsUi(
    val unresolved: List<UnresolvedLine>,
    val kitWarnings: List<KitWarning>,
    /** At least one unresolved line is from another set (and the pool has entries). */
    val someFromAnotherSet: Boolean,
    /** How many kits the baseline sampled. */
    val kits: Int,
    /** Pool at a glance, in display order. */
    val glance: List<DisplayedValue>,
    /** The ten pairs, sorted by cards, ties in [ColorPair] order. */
    val pairs: List<PairUi>,
    /** The seven buckets, in [ColorBucket] order. */
    val colors: List<ColorUi>,
    val fixingSources: List<FixingSource>,
) {
    /** Unresolved lines and kit warnings together. */
    val problemCount: Int
        get() = unresolved.size + kitWarnings.size

    /** Every displayed value, in display order. */
    val values: List<DisplayedValue>
        get() =
            glance + pairs.flatMap { listOf(it.cards, it.creatures) } + colors.flatMap { it.values }

    companion object {
        /** Pool at a glance's pointers, in display order. */
        const val TOTAL_CARDS = "/totalCards"
        const val NONLAND_CARDS = "/nonlandCards"
        const val BASIC_LANDS = "/basicLands"
        const val NONBASIC_LANDS = "/nonbasicLands"
        const val REMOVAL = "/removalTotal"
        const val INTERACTION = "/interactionTotal"
        const val FIXING_COUNT = "/fixingSources/count"

        /** The rarities a colour row breaks down, as pointer segments, in display order. */
        val RARITIES = listOf("common", "uncommon", "rare", "mythic")

        /** The display model for [success] against [baseline]. */
        fun from(success: ImportOutcome.Success, baseline: Baseline): ResultsUi {
            val metrics = success.metrics
            val result = success.result
            fun shown(pointer: String, value: Int) =
                DisplayedValue(pointer, value, baseline.stats[pointer])

            val glance =
                listOf(
                    shown(TOTAL_CARDS, metrics.totalCards),
                    shown(NONLAND_CARDS, metrics.nonlandCards),
                    shown(BASIC_LANDS, metrics.basicLands),
                    shown(NONBASIC_LANDS, metrics.nonbasicLands),
                    shown(REMOVAL, metrics.removalTotal),
                    shown(INTERACTION, metrics.interactionTotal),
                    shown(FIXING_COUNT, metrics.fixingSources.size),
                )
            val pairs =
                ColorPair.entries
                    .map { pair ->
                        val numbers = metrics.pairs.getValue(pair)
                        PairUi(
                            pair = pair,
                            cards = shown("/pairs/$pair/cards", numbers.cards),
                            creatures = shown("/pairs/$pair/creatures", numbers.creatures),
                        )
                    }
                    // A stable sort keeps ties in ColorPair's order, WU first.
                    .sortedByDescending { it.cards.value }
            val colors =
                ColorBucket.entries.map { bucket ->
                    val numbers = metrics.byColor.getValue(bucket)
                    val at = "/byColor/$bucket"
                    fun byRarity(kind: String, counts: RarityCounts) = RARITIES.map { rarity ->
                        shown("$at/$kind/$rarity", counts.of(rarity))
                    }
                    ColorUi(
                        bucket = bucket,
                        cards = shown("$at/cards/total", numbers.cards.total),
                        playables = shown("$at/playables/total", numbers.playables.total),
                        creatures = shown("$at/creatures", numbers.creatures),
                        removal = shown("$at/removal", numbers.removal),
                        interaction = shown("$at/interaction", numbers.interaction),
                        cardsByRarity = byRarity("cards", numbers.cards),
                        playablesByRarity = byRarity("playables", numbers.playables),
                    )
                }
            return ResultsUi(
                unresolved = result.unresolved,
                kitWarnings = result.warnings,
                someFromAnotherSet =
                    result.pool.entries.isNotEmpty() &&
                        result.unresolved.any { it.reason == UnresolvedReason.WRONG_SET },
                kits = baseline.kits,
                glance = glance,
                pairs = pairs,
                colors = colors,
                fixingSources = metrics.fixingSources,
            )
        }

        private fun RarityCounts.of(rarity: String): Int =
            when (rarity) {
                "common" -> common
                "uncommon" -> uncommon
                "rare" -> rare
                "mythic" -> mythic
                else -> error("no rarity $rarity")
            }
    }
}
