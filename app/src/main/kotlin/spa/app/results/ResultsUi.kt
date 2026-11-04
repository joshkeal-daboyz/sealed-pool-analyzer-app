package spa.app.results

import spa.app.importing.ImportOutcome
import spa.core.baseline.Baseline
import spa.core.baseline.Stat
import spa.core.intake.KitWarning
import spa.core.intake.UnresolvedLine
import spa.core.metrics.ColorBucket
import spa.core.metrics.ColorPair
import spa.core.metrics.FixingSource

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
        get() = TODO()
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
        get() = TODO()
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
        get() = TODO()

    /** Every displayed value, in display order. */
    val values: List<DisplayedValue>
        get() = TODO()

    companion object {
        /** The display model for [success] against [baseline]. */
        fun from(success: ImportOutcome.Success, baseline: Baseline): ResultsUi = TODO()
    }
}
