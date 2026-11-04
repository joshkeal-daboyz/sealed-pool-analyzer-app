package spa.agent.backtest

import java.util.Locale
import kotlin.math.abs
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
    /**
     * The report as printed: `<group>: rho <rho to 3 places or n/a>, n <n>` per group, the two
     * provenance lines, the count of cards missing from the model file, and one line per card.
     */
    fun lines(): List<String> {
        val count = missingFromModel.size
        return groups.map { "${it.group.label}: rho ${format(it.rho)}, n ${it.n}" } +
            "model: $modelProvenance" +
            "winrates: $winRateProvenance" +
            "missing from model: $count ${if (count == 1) "card" else "cards"}" +
            missingFromModel.map { "missing: ${it.name} (${it.setCode} ${it.collectorNumber})" }
    }

    private fun format(rho: Double?): String {
        if (rho == null) return "n/a"
        // A rho that rounds to zero prints 0.000, never -0.000.
        val shown = if (abs(rho) < HALF_THOUSANDTH) 0.0 else rho
        return String.format(Locale.ROOT, "%.3f", shown)
    }

    private companion object {
        const val HALF_THOUSANDTH = 0.0005
    }
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
    ): BacktestReport {
        val modelByCard = model.grades.associateBy(::key)
        val (matched, missing) = winRates.grades.partition { key(it) in modelByCard }
        val pairs = matched.map { entry -> Pair(entry, requireNotNull(modelByCard[key(entry)])) }
        val groups =
            BacktestGroup.entries.map { group ->
                val members =
                    if (group.rarity == null) pairs
                    else
                        pairs.filter {
                            rarity(it.first.setCode, it.first.collectorNumber) == group.rarity
                        }
                result(group, members)
            }
        return BacktestReport(
            groups,
            missing,
            model.header.provenance,
            winRates.header.provenance,
        )
    }

    private fun result(
        group: BacktestGroup,
        pairs: List<Pair<GradeEntry, GradeEntry>>,
    ): GroupResult {
        val rho =
            if (pairs.size < MIN_CARDS) null
            else Spearman.rho(pairs.map { it.first.score }, pairs.map { it.second.score })
        return GroupResult(group, rho, pairs.size)
    }

    private fun key(entry: GradeEntry): Pair<String, String> =
        entry.setCode.lowercase(Locale.ROOT) to entry.collectorNumber.lowercase(Locale.ROOT)
}
