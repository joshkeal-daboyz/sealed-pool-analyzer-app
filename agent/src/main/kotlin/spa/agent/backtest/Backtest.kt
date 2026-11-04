package spa.agent.backtest

import spa.agent.grade.GradeEntry
import spa.agent.grade.GradeFile
import spa.core.cards.Rarity

/** A group of cards the backtest reports a correlation for. */
enum class BacktestGroup(val label: String, val rarity: Rarity?) {
    ALL("all", null),
    COMMON("common", Rarity.COMMON),
    UNCOMMON("uncommon", Rarity.UNCOMMON),
    RARE("rare", Rarity.RARE),
    MYTHIC("mythic", Rarity.MYTHIC),
}

/** One group's Spearman [rho], null when undefined or under [Backtest.MIN_CARDS], over [n]. */
data class GroupResult(val group: BacktestGroup, val rho: Double?, val n: Int)

/**
 * The backtest of a model grade file against a win-rate grade file: one [GroupResult] per
 * [BacktestGroup] in order, the win-rate cards the model file does not grade, and both files'
 * provenance.
 */
data class BacktestReport(
    val groups: List<GroupResult>,
    val missingFromModel: List<GradeEntry>,
    val modelProvenance: String,
    val winRateProvenance: String,
) {
    /** The report as printed, one line each. */
    fun lines(): List<String> = TODO("SPA-032")
}

/** Scores a model grade file against a win-rate grade file by rank correlation. */
object Backtest {
    /** A group with fewer cards than this has no rho. */
    const val MIN_CARDS = 5

    /**
     * The backtest over the cards [winRates] grades that [model] grades too, matched by set code
     * and collector number. [rarity] gives a printing's rarity, or null when it is unknown; such a
     * card counts only in [BacktestGroup.ALL].
     */
    fun run(
        model: GradeFile,
        winRates: GradeFile,
        rarity: (setCode: String, collectorNumber: String) -> Rarity?,
    ): BacktestReport = TODO("SPA-032")
}
