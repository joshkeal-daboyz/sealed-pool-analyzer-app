package spa.core.cards

/**
 * One of Magic's five colors, in WUBRG order.
 *
 * SPA-004 owns the canonical `Color` in `spa.core.text`; this copy has the same shape so the card
 * index can be built before that ticket merges. The Architect reconciles the two.
 */
enum class Color {
    W,
    U,
    B,
    R,
    G;

    companion object {
        /** The color for a one-letter Scryfall symbol such as `"W"`, or null for anything else. */
        fun fromSymbol(symbol: String): Color? = entries.firstOrNull { it.name == symbol }

        /** The colors among [symbols], ignoring anything that is not a color, in WUBRG order. */
        fun setOf(symbols: Iterable<String>): Set<Color> {
            val found = symbols.mapNotNull(::fromSymbol).toSet()
            return entries.filter { it in found }.toSet()
        }
    }
}
