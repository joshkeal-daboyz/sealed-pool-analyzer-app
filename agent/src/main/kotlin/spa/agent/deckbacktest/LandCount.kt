package spa.agent.deckbacktest

import kotlin.math.abs

/** The built deck land count is within one of the player registered land count. */
object LandCount : RateMeasure("lands") {
    private const val TOLERANCE = 1

    override fun hit(outcome: BuildOutcome): Boolean? =
        outcome.built?.let { abs(it.lands - outcome.player.lands) <= TOLERANCE }
}
