package spa.core.text

/**
 * A parsed mana cost.
 *
 * @property generic the generic mana in the cost: the sum of `{N}` symbols plus one per `{C}`. No
 *   other symbol adds to it.
 * @property pips colored weight per color. A plain or Phyrexian symbol is 1.0, a hybrid symbol is
 *   0.5 toward each of its colors. Only colors with weight above zero are keys.
 * @property manaValue the mana value, with `{X}` counted as zero.
 * @property hasX whether the cost contains `{X}`, `{Y}`, or `{Z}`.
 */
data class ManaCost(
    val generic: Int = 0,
    val pips: Map<Color, Double> = emptyMap(),
    val manaValue: Int = 0,
    val hasX: Boolean = false,
) {
    /** The sum of [pips]: how many colored symbols the cost has, with hybrid halves. */
    val coloredPipCount: Double
        get() = pips.values.sum()

    /** The colors with weight above zero in [pips], in enum order. */
    val colors: Set<Color>
        get() = Color.entries.filter { (pips[it] ?: 0.0) > 0.0 }.toSet()

    companion object {
        private const val HYBRID_WEIGHT = 0.5
        private val symbolPattern = Regex("\\{([^{}]*)\\}")
        private val allowedBetweenSymbols = Regex("[\\s/]*")
        private val variables = setOf("X", "Y", "Z")

        /**
         * Parses a Scryfall `mana_cost` string such as `{2}{R}{R}` or `{1}{G/W}{W/P}`. Symbols are
         * case-insensitive. A split card's ` // ` separator is allowed and both halves are summed.
         * A null, empty, or blank cost parses to all zeros.
         *
         * Weights: `{N}` adds N to generic and mana value; `{C}` adds 1 to both; `{W}` adds 1.0 to
         * W and 1 to mana value; hybrid `{G/W}` adds 0.5 to each color and 1 to mana value;
         * Phyrexian `{W/P}` adds 1.0 to W and 1 to mana value; `{2/W}` adds 0.5 to W and 2 to mana
         * value; `{C/W}` adds 0.5 to W and 1 to mana value; `{S}` adds 1 to mana value only; `{X}`,
         * `{Y}`, and `{Z}` add nothing and set [hasX].
         *
         * @throws IllegalArgumentException for a symbol it does not know, such as `{HW}`, or for
         *   text outside braces other than whitespace and `/`.
         */
        fun parse(cost: String?): ManaCost {
            if (cost.isNullOrBlank()) return ManaCost()
            require(allowedBetweenSymbols.matches(symbolPattern.replace(cost, ""))) {
                "Mana cost has text outside its symbols: '$cost'"
            }
            val builder = Builder()
            symbolPattern.findAll(cost).forEach { builder.add(it.groupValues[1].uppercase()) }
            return builder.build()
        }
    }

    private class Builder {
        private var generic = 0
        private var manaValue = 0
        private var hasX = false
        private val pips = mutableMapOf<Color, Double>()

        fun add(symbol: String) {
            val number = symbol.toIntOrNull()
            when {
                number != null -> addGeneric(number)
                symbol == "C" -> addGeneric(1)
                symbol == "S" -> manaValue += 1
                symbol in variables -> hasX = true
                '/' in symbol -> addSplitSymbol(symbol)
                else -> {
                    addPip(colorOf(symbol, symbol), 1.0)
                    manaValue += 1
                }
            }
        }

        private fun addGeneric(amount: Int) {
            generic += amount
            manaValue += amount
        }

        private fun addSplitSymbol(symbol: String) {
            val parts = symbol.split('/')
            val colored = parts.filter { it != "P" && it != "C" && it.toIntOrNull() == null }
            val phyrexian = "P" in parts
            require(colored.size in 1..2) { "Unknown mana symbol {$symbol}" }
            val weight = if (phyrexian && colored.size == 1) 1.0 else HYBRID_WEIGHT
            colored.forEach { addPip(colorOf(it, symbol), weight) }
            manaValue += parts.firstNotNullOfOrNull { it.toIntOrNull() } ?: 1
        }

        private fun addPip(color: Color, weight: Double) {
            pips[color] = (pips[color] ?: 0.0) + weight
        }

        private fun colorOf(letter: String, symbol: String): Color {
            val color = letter.singleOrNull()?.let(Color::fromSymbol)
            require(color != null) { "Unknown mana symbol {$symbol}" }
            return color
        }

        fun build(): ManaCost =
            ManaCost(
                generic = generic,
                pips = Color.entries.filter { it in pips }.associateWith { pips.getValue(it) },
                manaValue = manaValue,
                hasX = hasX,
            )
    }
}
