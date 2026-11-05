package spa.agent.deckpick

import spa.agent.poolgrade.PoolGrader
import spa.core.cards.Printing

/**
 * The deck-pick test's result on one set: the [used] builds, the [skipped] ones by reason, and the
 * mean overlap of each ranking.
 */
data class DeckPickReport(
    val used: Int,
    val skipped: Map<SkipReason, Int>,
    val random: Double,
    val setGrade: Double,
    val adjusted: Double,
) {
    /** The adjusted ranking's mean overlap minus the set grade's. */
    val difference: Double
        get() = adjusted - setGrade
}

/**
 * The deck-pick test (G-004 deliverable 4): for each build, how many of its deck copies a ranking
 * of its candidates puts in the top N. No model or network call.
 */
object DeckPick {
    /**
     * The score an ungraded candidate ranks by, below every grade (0 to 5). The v2 grade files
     * grade every nonland card of `msh`, `hob` and `fra`, so it is unused on them.
     */
    const val UNGRADED = -1.0

    /** A build's overlap when its candidates are ranked by [setGrade] (null: ungraded). */
    fun setGradeOverlap(build: Build, setGrade: (Printing) -> Double?): Double =
        Overlap.of(build) { setGrade(it.printing) ?: UNGRADED }

    /**
     * A build's overlap when its candidates are ranked by [grader]'s unrounded adjusted grade for
     * the build's pair, on the build's [Build.pool], with set grades from [setGrade].
     */
    fun adjustedOverlap(
        build: Build,
        grader: PoolGrader,
        setGrade: (Printing) -> Double?,
    ): Double {
        val adjusted =
            grader.grade(build.pool(), build.pair, setGrade).cards.associate {
                it.printing.card.oracleId to it.adjusted
            }
        return Overlap.of(build) { adjusted[it.printing.card.oracleId] ?: UNGRADED }
    }

    /** The three rankings' mean overlaps over [selection]'s builds. */
    fun measure(
        selection: BuildSelection,
        setGrade: (Printing) -> Double?,
        grader: PoolGrader,
    ): DeckPickReport {
        val builds = selection.builds
        require(builds.isNotEmpty()) { "no build is left to measure" }
        return DeckPickReport(
            used = builds.size,
            skipped = selection.skipped,
            random = builds.map(Overlap::random).average(),
            setGrade = builds.map { setGradeOverlap(it, setGrade) }.average(),
            adjusted = builds.map { adjustedOverlap(it, grader, setGrade) }.average(),
        )
    }
}
