package spa.core.cards

import spa.core.text.Color

/**
 * The color for a one-letter Scryfall color symbol such as `"W"`, or null for anything else.
 * Scryfall writes colors in upper case, so `"w"` is null.
 */
internal fun Color.Companion.fromSymbol(symbol: String): Color? =
    Color.entries.firstOrNull { it.name == symbol }

/** The colors among [symbols], ignoring anything that is not a color, in WUBRG order. */
internal fun Color.Companion.setOf(symbols: Iterable<String>): Set<Color> {
    val found = symbols.mapNotNull { fromSymbol(it) }.toSet()
    return Color.entries.filter { it in found }.toSet()
}
