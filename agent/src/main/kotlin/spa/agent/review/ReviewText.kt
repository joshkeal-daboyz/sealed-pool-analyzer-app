package spa.agent.review

/**
 * The plain text of a [Review] (G-006 Decided 3), blocks in a fixed order with one blank line
 * between them. It depends on the review alone, so the same review gives the same bytes.
 */
object ReviewText {
    /** The line every review ends with (G-006 Constraints). */
    const val ATTRIBUTION =
        "Card data from Scryfall (scryfall.com). Card text and images are property of " +
            "Wizards of the Coast."

    /** The text of [review], ending with a newline. */
    fun render(review: Review): String {
        val blocks =
            listOf(header(review.header), decks(review.decks), verdict(review.verdict)) +
                review.sections
                    .filterIsInstance<AgentOutcome.Answered>()
                    .map(SectionText::section) +
                listOf(
                    disagreements(review.disagreements),
                    missing(review.missing),
                    usage(review),
                    ATTRIBUTION,
                )
        return blocks.joinToString("\n\n") + "\n"
    }

    private fun header(header: ReviewHeader): String =
        (listOf(
                "Review: ${header.set} (${header.setName})",
                "Pool: ${header.totalCards} cards, ${header.distinct} distinct, " +
                    "${header.unresolved} unresolved lines",
            ) + header.kitWarnings.map { "Kit warning: $it" })
            .joinToString("\n")

    private fun decks(decks: List<DeckSummary>): String =
        if (decks.isEmpty()) {
            "Decks\nNo deck could be built from this pool."
        } else {
            (listOf("Decks") + decks.map { it.text() }).joinToString("\n")
        }

    private fun verdict(outcome: ExplainerOutcome): String =
        when (outcome) {
            is ExplainerOutcome.Missing -> "Verdict\nNot available (${outcome.reason})."
            is ExplainerOutcome.Explained -> {
                val notes =
                    outcome.result.decks
                        .sortedBy { it.rank }
                        .map {
                            "Rank ${it.rank}: ${it.note} (cards: ${it.cards.joinToString(", ")})"
                        }
                val dropped =
                    if (outcome.dropped.isEmpty()) {
                        emptyList()
                    } else {
                        val reasons =
                            SectionText.counted(
                                outcome.dropped.map { it.reason },
                                SectionText::noteWords,
                            )
                        listOf("Dropped notes: ${outcome.dropped.size} ($reasons)")
                    }
                (listOf("Verdict", outcome.result.verdict) + notes + dropped).joinToString("\n")
            }
        }

    private fun disagreements(disagreements: List<Disagreement>): String =
        (listOf("Disagreements") +
                if (disagreements.isEmpty()) {
                    listOf("None.")
                } else {
                    disagreements.map {
                        "- ${it.topic} [${it.agents.joinToString(", ")}]: ${it.note} " +
                            "(cards: ${it.cards.joinToString(", ")})"
                    }
                })
            .joinToString("\n")

    private fun missing(missing: List<MissingEntry>): String =
        if (missing.isEmpty()) {
            "Missing: none"
        } else {
            "Missing: " + missing.joinToString("; ") { "${it.id} (${it.reason})" }
        }

    private fun usage(review: Review): String {
        val usage = review.usage
        return "Usage: ${usage.calls} calls, input ${usage.input}, output ${usage.output}, " +
            "cache read ${usage.cacheRead}, cache write ${usage.cacheWrite} (model ${review.model})"
    }
}
