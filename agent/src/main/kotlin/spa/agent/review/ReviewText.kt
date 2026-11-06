package spa.agent.review

import java.util.Locale

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
                review.sections.filterIsInstance<AgentOutcome.Answered>().map(::section) +
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
                        listOf(
                            "Dropped notes: ${outcome.dropped.size} " +
                                "(${counted(outcome.dropped.map { it.reason }, ::noteWords)})"
                        )
                    }
                (listOf("Verdict", outcome.result.verdict) + notes + dropped).joinToString("\n")
            }
        }

    private fun section(outcome: AgentOutcome.Answered): String {
        val answer = outcome.answer
        val findings = answer.findings.map(::line).ifEmpty { listOf("No findings kept.") }
        val dropped =
            if (outcome.dropped.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    "Dropped findings: ${outcome.dropped.size} " +
                        "(${counted(outcome.dropped.map { it.reason }, ::findingWords)})"
                )
            }
        return (listOf(
                "${outcome.agent.title}  (confidence ${decimals(answer.confidence)})",
                answer.summary,
            ) + findings + dropped)
            .joinToString("\n")
    }

    private fun line(finding: Finding): String {
        val pair = finding.pair?.let { "$it: " }.orEmpty()
        val numbers =
            if (finding.numbers.isEmpty()) {
                ""
            } else {
                " (numbers: ${finding.numbers.joinToString("; ", transform = ::number)})"
            }
        return "- [${finding.severity.label}] $pair${finding.claim} " +
            "(cards: ${finding.cards.joinToString(", ")})$numbers"
    }

    private fun number(number: AnswerNumber): String {
        val typical =
            number.typical?.let {
                " (typical ${NumberTable.format(it.median)}, range " +
                    "${NumberTable.format(it.p10)} to ${NumberTable.format(it.p90)})"
            }
        return "${number.key} = ${NumberTable.format(number.value)}${typical.orEmpty()}"
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

    /** `<words> <count>` per reason, in the reasons' enum order, joined by `, `. */
    private fun <R : Enum<R>> counted(reasons: List<R>, words: (R) -> String): String =
        reasons
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key.ordinal }
            .joinToString(", ") { "${words(it.key)} ${it.value}" }

    private fun decimals(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    /** The words the review uses for a finding's drop [reason]. */
    internal fun findingWords(reason: DropReason): String =
        when (reason) {
            DropReason.NO_CARD -> "no card"
            DropReason.UNKNOWN_CARD -> "unknown card"
            DropReason.UNKNOWN_KEY -> "unknown number key"
            DropReason.WRONG_VALUE -> "wrong number value"
            DropReason.OVER_LIMIT -> "over the limit"
        }

    /** The words the review uses for an Explainer item's drop [reason]. */
    internal fun noteWords(reason: ExplainerDropReason): String =
        when (reason) {
            ExplainerDropReason.UNKNOWN_RANK -> "unknown rank"
            ExplainerDropReason.DUPLICATE_RANK -> "duplicate rank"
            ExplainerDropReason.TOO_FEW_AGENTS -> "too few agents"
            ExplainerDropReason.UNKNOWN_AGENT -> "unknown agent"
            ExplainerDropReason.NO_CARD -> "no card"
            ExplainerDropReason.UNKNOWN_CARD -> "unknown card"
        }
}
