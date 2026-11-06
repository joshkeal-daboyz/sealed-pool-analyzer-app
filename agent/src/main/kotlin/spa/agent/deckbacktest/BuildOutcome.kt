package spa.agent.deckbacktest

import spa.core.text.Color

/**
 * What a strong player registered: the [main] and [splash] colours, the deck copies of each nonland
 * card by oracle id ([spells]), and the deck copies of lands, basics included ([lands]).
 */
data class Player(
    val main: Set<Color>,
    val splash: Set<Color>,
    val spells: Map<String, Int>,
    val lands: Int,
) {
    /** The player's deck shape; null for one main colour or five. */
    val shape: DeckShape?
        get() = DeckShape.of(main, splash)
}

/** The colours of one ranked deck: its [main] and [splash] colours. */
data class RankedColours(val main: Set<Color>, val splash: Set<Color> = emptySet())

/** A built deck as the measures see it: its nonland copies by oracle id and its land count. */
data class BuiltDeck(val spells: Map<String, Int>, val lands: Int)

/**
 * One build result, the input of every [Measure]: the [player], the [ranked] decks colours best
 * first, and the [built] deck the overlap and land count are measured on (null when it could not be
 * built).
 */
class BuildOutcome(val player: Player, val ranked: List<RankedColours>, val built: BuiltDeck?) {
    /** The player deck shape, which groups the outcome. */
    val shape: DeckShape =
        requireNotNull(player.shape) { "a build of ${player.main.size} main colours has no shape" }
}
