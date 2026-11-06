package spa.agent.deckbacktest

import spa.core.text.Color

/** The shape of a player's deck, by its main colours and splash (G-005 deliverable 6). */
enum class DeckShape(val label: String) {
    /** Two main colours and no splash. */
    TWO("two"),

    /** Two main colours and a splash. */
    SPLASH("splash"),

    /** Three or four main colours, with or without a splash. */
    MULTI("multi");

    companion object {
        private const val PAIR = 2
        private val MULTI_COLOURS = 3..4

        /** The shape of a deck with [main] and [splash] colours; null for one colour or five. */
        fun of(main: Set<Color>, splash: Set<Color>): DeckShape? =
            when {
                main.size == PAIR -> if (splash.isEmpty()) TWO else SPLASH
                main.size in MULTI_COLOURS -> MULTI
                else -> null
            }
    }
}
