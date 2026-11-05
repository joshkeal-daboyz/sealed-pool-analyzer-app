package spa.agent.facts

/**
 * `makes-token`: any face says "create" or "creates", followed later in the same sentence by
 * "token" or "tokens", in any case. A sentence ends at a full stop or a line break.
 */
object MakesTokenRule : FactRule {
    private val sentenceEnd = Regex("[.\n]")
    private val creates = Regex("\\bcreates?\\b.*\\btokens?\\b", RegexOption.IGNORE_CASE)

    override fun facts(card: FactCard): Set<String> =
        if (card.faceTexts.any { text -> text.split(sentenceEnd).any(creates::containsMatchIn) }) {
            setOf("makes-token")
        } else {
            emptySet()
        }
}
