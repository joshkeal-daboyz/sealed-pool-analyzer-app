package spa.agent.facts

/**
 * `kw:<keyword>` for a keyword the front face grants itself (owner, 2026-11-05: Moopsy's reach):
 * "this creature", its full name or its short name, then "gains" or "has", then one or more of
 * [KEYWORDS]. Only the words before any "until", "as long as" or "if" in that sentence count, so
 * "This creature gains flying and haste until end of turn" gives `kw:flying` and `kw:haste`.
 */
object SelfGrantedKeywordRule : FactRule {
    override fun knows(fact: String): Boolean = KEYWORDS.any { fact == "kw:${FactRules.slug(it)}" }

    /** The keywords a card can grant itself, as printed. */
    val KEYWORDS =
        listOf(
            "flying",
            "reach",
            "deathtouch",
            "lifelink",
            "first strike",
            "double strike",
            "trample",
            "vigilance",
            "haste",
            "menace",
            "hexproof",
            "indestructible",
        )

    private val cut = Regex("\\b(?:until|as long as|if)\\b", RegexOption.IGNORE_CASE)
    private val keyword =
        Regex(KEYWORDS.joinToString("|", "\\b(?:", ")\\b"), RegexOption.IGNORE_CASE)

    override fun facts(card: FactCard): Set<String> {
        val grant =
            Regex(
                "\\b${card.subjects(listOf("this creature"))}\\s+(?:gains|has)\\s+([^.\n;]*)",
                RegexOption.IGNORE_CASE,
            )
        return grant
            .findAll(card.frontText)
            .flatMap { match ->
                val words = cut.split(match.groupValues[1], limit = 2).first()
                keyword.findAll(words).map { "kw:${FactRules.slug(it.value)}" }
            }
            .toSet()
    }
}
