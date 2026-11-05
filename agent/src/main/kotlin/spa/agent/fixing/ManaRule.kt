package spa.agent.fixing

import spa.core.text.Color

/**
 * MANA: an `Add ...` clause, up to the next period or line end, that makes any colour (`any color`,
 * `any one color`, `any combination of colors`, `chosen color`: all five) or names two or more
 * different coloured symbols (those colours). `{C}` and single-colour clauses do not count. Quoted
 * abilities, such as a token's, count too.
 */
object ManaRule : FixingRule {
    override val kind = FixingKind.MANA

    private val CLAUSE = Regex("""\bAdd\b[^.\n]*""")
    private val SYMBOL = Regex("""\{([WUBRG])\}""")
    private val ANY_COLOUR =
        listOf("any color", "any one color", "any combination of colors", "chosen color")

    override fun colours(faceText: String): Set<Color> =
        CLAUSE.findAll(faceText).flatMap { clause(it.value) }.toSet()

    private fun clause(text: String): Set<Color> {
        if (ANY_COLOUR.any { it in text }) return ALL_COLOURS
        val named =
            SYMBOL.findAll(text).mapNotNull { Color.fromSymbol(it.groupValues[1][0]) }.toSet()
        return if (named.size >= 2) named else emptySet()
    }
}
