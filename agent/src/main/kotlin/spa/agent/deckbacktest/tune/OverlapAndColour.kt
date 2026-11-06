package spa.agent.deckbacktest.tune

import spa.agent.deckbacktest.BuildOutcome
import spa.agent.deckbacktest.ColourAgreement
import spa.agent.deckbacktest.MainDeckOverlap

/**
 * A set overlap share plus its colour agreement (G-005 criterion 7, the two main numbers): the mean
 * main-deck overlap over the mean number of nonland cards the players registered, both over the
 * builds that have a built deck (0 when none has), plus the rate of builds whose top deck has the
 * player main colours, over all builds.
 */
object OverlapAndColour : TuneObjective {
    override val key = "overlap+colour"

    override fun forSet(set: String, outcomes: List<BuildOutcome>): Double {
        require(outcomes.isNotEmpty()) { "set '$set' has no used builds" }
        val built = outcomes.filter { it.built != null }
        val registered = built.map { it.player.spells.values.sum() }.average()
        val overlap = built.mapNotNull(MainDeckOverlap::value).average()
        val share = if (built.isEmpty() || registered == 0.0) 0.0 else overlap / registered
        return share + outcomes.mapNotNull(ColourAgreement::value).average()
    }
}
