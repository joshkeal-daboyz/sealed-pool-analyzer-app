package spa.agent.deckbacktest

/** The report lines of the deck backtest: one per group, each with the count and every measure. */
object BacktestReport {
    /** The group of all builds. */
    const val OVERALL = "overall"

    /** The builds of [outcomes] overall, then by deck shape, in that order. */
    fun groups(outcomes: List<BuildOutcome>): Map<String, List<BuildOutcome>> =
        linkedMapOf(OVERALL to outcomes) +
            DeckShape.entries.associate { shape ->
                shape.label to outcomes.filter { it.shape == shape }
            }

    /**
     * One line per group of [outcomes]: `<name> <group> n <count>` and the cell of each of
     * [measures], in order.
     */
    fun lines(
        name: String,
        outcomes: List<BuildOutcome>,
        measures: List<Measure> = Measures.ALL,
    ): List<String> =
        groups(outcomes).map { (group, builds) ->
            (listOf("$name $group", "n ${builds.size}") + measures.map { it.cell(builds) })
                .joinToString(" ")
        }
}
