package spa.agent.facts

/** The text rule the trigger facts share. */
internal object Triggers {
    /**
     * True when [card]'s front face says `When` or `Whenever`, then one of [subjects] or the card's
     * names ([FactCard.subjects]), then [event] as a word (`enters`, `dies`). With [orAnother], "or
     * another …" may come between the subject and the event in the same sentence (SPA-056:
     * "Whenever Captain Janeway or another creature you control enters").
     */
    fun has(
        card: FactCard,
        subjects: List<String>,
        event: String,
        orAnother: Boolean = false,
    ): Boolean {
        val another = if (orAnother) "(?:\\s+or\\s+another\\b[^.\\n]*?)?" else ""
        return Regex("\\b(?:When|Whenever)\\s+${card.subjects(subjects)}$another\\s+$event\\b")
            .containsMatchIn(card.frontText)
    }
}
