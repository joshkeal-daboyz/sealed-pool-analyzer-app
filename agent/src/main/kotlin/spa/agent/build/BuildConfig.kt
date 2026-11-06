package spa.agent.build

import spa.core.text.Color

/**
 * The colours a deck is built in (SPA-044): [main] colours and up to two [splash] colours, written
 * `<main>[/<splash>]`, such as `WU`, `WU/R` or `WUB`.
 */
class BuildConfig(main: Set<Color>, splash: Set<Color> = emptySet()) {
    /** The main colours, in WUBRG order. */
    val main: Set<Color> = Color.entries.filter { it in main }.toSet()

    /** The splash colours, in WUBRG order. */
    val splash: Set<Color> = Color.entries.filter { it in splash }.toSet()

    /** Every colour of the deck, in WUBRG order. */
    val colours: Set<Color> = Color.entries.filter { it in main || it in splash }.toSet()

    init {
        require(main.isNotEmpty()) { "a deck needs a main colour" }
        require(splash.size <= MAX_SPLASH) { "at most $MAX_SPLASH splash colours" }
        require(main.none { it in splash }) { "a colour is either main or splash" }
    }

    override fun equals(other: Any?): Boolean =
        other is BuildConfig && other.main == main && other.splash == splash

    override fun hashCode(): Int = main.hashCode() * HASH + splash.hashCode()

    override fun toString(): String =
        main.joinToString("") { "${it.symbol}" } +
            if (splash.isEmpty()) "" else "/" + splash.joinToString("") { "${it.symbol}" }

    companion object {
        /** The most splash colours a configuration has. */
        const val MAX_SPLASH = 2
        private const val HASH = 31

        /**
         * The configuration written [text], in any case: main colours, then optionally `/` and one
         * or two splash colours. Anything else is an [IllegalArgumentException].
         */
        fun parse(text: String): BuildConfig {
            val fail = "unknown colours '$text': expected main[/splash], such as WU, WU/R or WUB"
            val sides = text.uppercase().split('/')
            require(sides.size <= 2 && sides.none { it.isEmpty() }) { fail }
            val colours = sides.map { side ->
                side
                    .map { requireNotNull(Color.fromSymbol(it)) { fail } }
                    .also { require(it.toSet().size == it.size) { fail } }
            }
            val splash = colours.getOrElse(1) { emptyList() }
            require(splash.size <= MAX_SPLASH && colours[0].none { it in splash }) { fail }
            return BuildConfig(colours[0].toSet(), splash.toSet())
        }
    }
}
