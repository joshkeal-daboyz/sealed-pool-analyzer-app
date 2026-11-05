package spa.agent.fixing

import spa.core.text.Color

/** TREASURE: `create ... Treasure` in one sentence. All five colours. */
object TreasureRule : FixingRule {
    override val kind = FixingKind.TREASURE

    private val CLAUSE = Regex("""\bcreate\b[^.\n]*\bTreasure""", RegexOption.IGNORE_CASE)

    override fun colours(faceText: String): Set<Color> =
        if (CLAUSE.containsMatchIn(faceText)) ALL_COLOURS else emptySet()
}
