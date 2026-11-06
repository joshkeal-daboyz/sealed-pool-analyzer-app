package spa.agent.review

import spa.core.baseline.Stat

/** How much a finding matters to the player. */
enum class Severity(val label: String) {
    INFO("info"),
    WARN("warn"),
    KEY("key"),
}

/** A number a finding quotes: the table's [key] and [value], with the set's [typical] spread. */
data class AnswerNumber(val key: String, val value: Double, val typical: Stat?)

/**
 * One kept finding: its [claim], the colour [pair] it is about (null for none), its [severity], the
 * full names of the pool [cards] it cites, and the [numbers] it quotes.
 */
data class Finding(
    val claim: String,
    val pair: String?,
    val severity: Severity,
    val cards: List<String>,
    val numbers: List<AnswerNumber>,
)

/**
 * A validated answer of agent [agent]: its [confidence] (0 to 1), [summary] and kept [findings].
 */
data class AgentAnswer(
    val agent: String,
    val confidence: Double,
    val summary: String,
    val findings: List<Finding>,
)

/** Why a finding was thrown away, in the order the reasons are tried. */
enum class DropReason {
    NO_CARD,
    UNKNOWN_CARD,
    UNKNOWN_KEY,
    WRONG_VALUE,
    OVER_LIMIT,
}

/** A finding that was thrown away: the [reason] and its [claim]. */
data class DroppedFinding(val reason: DropReason, val claim: String)

/** What [AnswerChecker.check] made of an answer: the kept [answer] and what it [dropped]. */
data class CheckedAnswer(val answer: AgentAnswer, val dropped: List<DroppedFinding>)

/** An answer that does not fit the schema; the one-line message names the field. */
class SchemaError(message: String) : RuntimeException(message)
