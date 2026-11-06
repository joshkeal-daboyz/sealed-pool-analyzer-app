package spa.agent.otags.cares

import spa.agent.facts.CardTypeRule

/**
 * What each code fact means, in a line, for the vocabulary of a cares packet (SPA-055). Written
 * from the fact rules in `spa.agent.facts`; a fact of an unknown family gets a plain line.
 */
object CodeFactDescriptions {
    private val named =
        mapOf(
            "legendary" to "The front face is a legendary card.",
            "cheap" to "Not a land, with a mana value of 2 or less.",
            "enters-trigger" to "The front face has its own trigger for when it enters.",
            "dies-trigger" to "The front face has its own trigger for when it dies.",
            "makes-token" to "Some face creates one or more tokens.",
        )

    /** The description of the code fact [fact]. */
    fun of(fact: String): String =
        when {
            fact in CardTypeRule.TYPES -> "The front face is a $fact card."
            fact in named -> named.getValue(fact)
            fact.startsWith("type:") ->
                "The front face is a creature with the creature type ${fact.removePrefix("type:")}."
            fact.startsWith("kw:") -> "The card has the keyword ${fact.removePrefix("kw:")}."
            else -> "A card fact."
        }
}
