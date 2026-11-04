package spa.app.pair

import spa.app.importing.ImportOutcome
import spa.app.results.DisplayedValue
import spa.core.baseline.Baseline
import spa.core.metrics.ColorPair
import spa.core.metrics.SplashCandidate
import spa.core.text.Color

/** One land row: the basic lands of [color] the recommendation gives. */
data class LandUi(val color: Color, val lands: DisplayedValue)

/** One curve row: the bucket's [manaValue], its creatures and its other cards. */
data class CurveRowUi(val manaValue: Int, val creatures: DisplayedValue, val other: DisplayedValue)

/** Everything Pair detail shows for one pair. The screen renders only from this. */
data class PairDetailUi(
    val pair: ColorPair,
    val unresolvedLines: Int,
    val lands: List<LandUi>,
    val counts: List<DisplayedValue>,
    val curve: List<CurveRowUi>,
    val splashCount: DisplayedValue,
    val splashCandidates: List<SplashCandidate>,
) {
    val values: List<DisplayedValue>
        get() = TODO()

    companion object {
        fun from(success: ImportOutcome.Success, pair: ColorPair, baseline: Baseline): PairDetailUi =
            TODO()
    }
}
