package spa.core.cards

/** Reads Scryfall mana-cost strings such as `{2}{G/W}{G/W}`. */
internal object ManaCost {
    private val symbol = Regex("\\{([^}]*)\\}")
    private val variable = setOf("X", "Y", "Z")
    private const val HALF = 0.5

    /** The symbols of [cost] without braces, in order. */
    fun symbols(cost: String): List<String> =
        symbol.findAll(cost).map { it.groupValues[1] }.toList()

    /**
     * The mana value of [cost]: a number counts as itself, X, Y, and Z as zero, a half symbol as
     * one half, a hybrid such as `{2/W}` as its larger half, and every other symbol as one.
     */
    fun manaValue(cost: String): Double = symbols(cost).sumOf(::symbolValue)

    /** The colors whose symbols appear in [cost], hybrid and Phyrexian halves included. */
    fun colors(cost: String): Set<Color> = Color.setOf(symbols(cost).flatMap { it.split('/') })

    private fun symbolValue(symbol: String): Double {
        val parts = symbol.split('/')
        return when {
            parts.size > 1 -> parts.maxOf { it.toDoubleOrNull() ?: 1.0 }
            symbol in variable -> 0.0
            symbol == "½" || symbol.startsWith("H") -> HALF
            else -> symbol.toDoubleOrNull() ?: 1.0
        }
    }
}
