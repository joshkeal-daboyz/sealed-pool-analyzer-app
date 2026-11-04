package spa.core.text

/** Reads which colors of mana a permanent can produce from its type line and rules text. */
object ManaProduction {
    private val basicLandTypes =
        mapOf(
            "plains" to Color.W,
            "island" to Color.U,
            "swamp" to Color.B,
            "mountain" to Color.R,
            "forest" to Color.G,
        )
    private val word = Regex("[a-z]+")
    private val addClause = Regex("\\badd ([^.\\n]*)")
    private val anyColor = Regex("\\bany (?:one )?color\\b|\\bany combination of colors\\b")
    private val symbol = Regex("\\{([^{}]*)\\}")
    private val treasure = Regex("\\bcreates? [^.\\n]*?\\btreasure tokens?\\b")

    /**
     * The colors of mana a permanent with this Scryfall `type_line` and `oracle_text` can produce,
     * in enum order. Both strings are matched case-insensitively and text inside parentheses
     * (reminder text) is ignored.
     *
     * Colors come from basic land types in the type line (a Forest makes G), from the mana symbols
     * after "Add" (`Add {R} or {G}` makes R and G, including "whenever a land enters, add ..."),
     * and are all five for "Add one mana of any color", "any combination of colors", or text that
     * creates one or more Treasure tokens. `{C}` adds no color. Returns an empty set for a card
     * that makes no colored mana.
     */
    fun colors(typeLine: String, oracleText: String): Set<Color> {
        val type = normalizeRulesText(typeLine)
        val text = normalizeRulesText(oracleText)
        val found = mutableSetOf<Color>()
        word.findAll(type).mapNotNullTo(found) { basicLandTypes[it.value] }
        val clauses = addClause.findAll(text).map { it.groupValues[1] }.toList()
        clauses.forEach { clause ->
            symbol.findAll(clause).forEach { match ->
                match.groupValues[1].mapNotNullTo(found) { Color.fromSymbol(it) }
            }
        }
        val makesAnyColor =
            treasure.containsMatchIn(text) || clauses.any { anyColor.containsMatchIn(it) }
        return if (makesAnyColor) Color.entries.toSet()
        else Color.entries.filter { it in found }.toSet()
    }
}
