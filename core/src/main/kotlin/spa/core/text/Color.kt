package spa.core.text

/**
 * The five colors of Magic, in WUBRG order. Enum order is the tie-break order wherever core has to
 * choose between colors.
 *
 * @property symbol the upper-case letter that stands for this color in a mana symbol, as in `{G}`.
 */
enum class Color(val symbol: Char) {
    W('W'),
    U('U'),
    B('B'),
    R('R'),
    G('G');

    companion object {
        /**
         * The color whose mana symbol letter is [symbol], ignoring case, so `'g'` and `'G'` are
         * both [G]. Returns null for any other character, including `'C'` (colorless) and digits.
         */
        fun fromSymbol(symbol: Char): Color? = entries.firstOrNull {
            it.symbol == symbol.uppercaseChar()
        }
    }
}
