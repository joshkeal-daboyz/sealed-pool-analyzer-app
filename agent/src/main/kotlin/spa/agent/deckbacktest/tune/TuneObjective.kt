package spa.agent.deckbacktest.tune

import spa.agent.deckbacktest.BuildOutcome

/**
 * What the tuner maximises (SPA-060), each objective its own object in its own file. Adding an
 * objective adds a file and one entry to [TuneObjectives.ALL].
 */
interface TuneObjective {
    /** The name of the objective. */
    val key: String

    /**
     * The value for the set [set] from the builder [outcomes] of its builds. An
     * [IllegalArgumentException] naming the set when it has no used builds.
     */
    fun forSet(set: String, outcomes: List<BuildOutcome>): Double

    /** The mean of [forSet] over the [sets], by set code; higher is better. */
    fun value(sets: Map<String, List<BuildOutcome>>): Double {
        require(sets.isNotEmpty()) { "no set to tune on" }
        return sets.map { (set, outcomes) -> forSet(set, outcomes) }.average()
    }
}
