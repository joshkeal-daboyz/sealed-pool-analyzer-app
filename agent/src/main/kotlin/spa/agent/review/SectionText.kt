package spa.agent.review

import java.util.Locale

/** The text of one analyst's section of the review, and the words for why items were dropped. */
internal object SectionText {
    /** The block of an answered analyst: heading, summary, findings and the dropped count. */
    fun section(outcome: AgentOutcome.Answered): String {
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

    /** `<words> <count>` per reason, in the reasons' enum order, joined by `, `. */
    fun <R : Enum<R>> counted(reasons: List<R>, words: (R) -> String): String =
        reasons
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key.ordinal }
            .joinToString(", ") { "${words(it.key)} ${it.value}" }

    private fun decimals(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    /** The words the review uses for a finding's drop [reason]. */
    fun findingWords(reason: DropReason): String =
        when (reason) {
            DropReason.NO_CARD -> "no card"
            DropReason.UNKNOWN_CARD -> "unknown card"
            DropReason.UNKNOWN_KEY -> "unknown number key"
            DropReason.WRONG_VALUE -> "wrong number value"
            DropReason.OVER_LIMIT -> "over the limit"
        }

    /** The words the review uses for an Explainer item's drop [reason]. */
    fun noteWords(reason: ExplainerDropReason): String =
        when (reason) {
            ExplainerDropReason.UNKNOWN_RANK -> "unknown rank"
            ExplainerDropReason.DUPLICATE_RANK -> "duplicate rank"
            ExplainerDropReason.TOO_FEW_AGENTS -> "too few agents"
            ExplainerDropReason.UNKNOWN_AGENT -> "unknown agent"
            ExplainerDropReason.NO_CARD -> "no card"
            ExplainerDropReason.UNKNOWN_CARD -> "unknown card"
        }
}
