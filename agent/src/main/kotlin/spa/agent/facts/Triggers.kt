package spa.agent.facts

/** The text rule the trigger facts share. */
internal object Triggers {
    /**
     * True when [card]'s front face says `When` or `Whenever`, then one of [subjects] or the card's
     * full or short name, then [event] as a word (`enters`, `dies`).
     */
    fun has(card: FactCard, subjects: List<String>, event: String): Boolean =
        Regex("\\b(?:When|Whenever)\\s+${card.subjects(subjects)}\\s+$event\\b")
            .containsMatchIn(card.frontText)
}
