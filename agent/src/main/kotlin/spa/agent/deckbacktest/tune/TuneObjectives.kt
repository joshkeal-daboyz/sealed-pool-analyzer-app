package spa.agent.deckbacktest.tune

import spa.agent.deckbacktest.BuildOutcome

/** The objectives the tuner knows, and the one it uses. */
object TuneObjectives {
    val ALL: List<TuneObjective> = listOf(OverlapAndColour)

    /** The objective `spa deck-tune` maximises. */
    val DEFAULT: TuneObjective = ALL.first()

    /** The mean of [objective] over the [sets], by set code; higher is better. */
    fun value(sets: Map<String, List<BuildOutcome>>, objective: TuneObjective = DEFAULT): Double {
        require(sets.isNotEmpty()) { "no set to tune on" }
        return sets.map { (set, outcomes) -> objective.forSet(set, outcomes) }.average()
    }
}
