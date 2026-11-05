package spa.agent.fixing

import spa.core.text.Color

/**
 * SEARCH: a `search your library for ...` clause, up to the next period or line end, that names a
 * `land card` and does not say `into your graveyard`. All five colours.
 */
object SearchRule : FixingRule {
    override val kind = FixingKind.SEARCH

    private val CLAUSE = Regex("""\bsearch your library for\b[^.\n]*""", RegexOption.IGNORE_CASE)

    override fun colours(faceText: String): Set<Color> =
        if (CLAUSE.findAll(faceText).any { fixes(it.value) }) ALL_COLOURS else emptySet()

    private fun fixes(clause: String): Boolean =
        clause.contains("land card", ignoreCase = true) &&
            !clause.contains("into your graveyard", ignoreCase = true)
}
