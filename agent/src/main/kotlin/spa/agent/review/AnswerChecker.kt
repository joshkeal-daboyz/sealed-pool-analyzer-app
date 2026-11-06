package spa.agent.review

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Validates one agent's answer text: the schema (strict), then each finding against the pool and
 * the agent's number table. A schema fault is a [SchemaError]; a bad finding is dropped and
 * counted.
 */
object AnswerChecker {
    private val ANSWER_KEYS = setOf("agent", "confidence", "summary", "findings")
    private val FINDING_KEYS = setOf("claim", "pair", "severity", "cards", "numbers")
    private val NUMBER_KEYS = setOf("key", "value")

    /** A finding as the model wrote it, before it is checked. */
    private class RawFinding(
        val claim: String,
        val pair: String?,
        val severity: Severity,
        val cards: List<String>,
        val numbers: List<Pair<String, Double>>,
    )

    /**
     * Checks [text] as the answer of [agent]. The part from the first `{` to the last `}` is read,
     * so a code fence or a sentence around the object is tolerated. [poolNames] are the pool cards'
     * full names. A kept finding holds the table's values for its numbers, not the model's digits.
     * At most [maxFindings] findings are kept; later ones are dropped as over the limit.
     */
    fun check(
        text: String,
        agent: AnalysisAgent,
        poolNames: Collection<String>,
        table: NumberTable,
        maxFindings: Int,
    ): CheckedAnswer {
        val root = parseObject(text)
        val answer = StrictObject(root, "", ANSWER_KEYS, ::schemaFault)
        val agentId = answer.string("agent")
        if (agentId != agent.id) schemaFault("'agent' must be '${agent.id}', not '$agentId'")
        val confidence = answer.number("confidence")
        if (confidence !in 0.0..1.0) schemaFault("'confidence' must be from 0 to 1")
        val summary = answer.string("summary")
        val raw = answer.objects("findings", FINDING_KEYS).map(::rawFinding)

        val names = PoolNames(poolNames)
        val kept = mutableListOf<Finding>()
        val dropped = mutableListOf<DroppedFinding>()
        for (finding in raw) {
            val reason = dropReason(finding, names, table)
            when {
                reason != null -> dropped.add(DroppedFinding(reason, finding.claim))
                kept.size >= maxFindings ->
                    dropped.add(DroppedFinding(DropReason.OVER_LIMIT, finding.claim))
                else -> kept.add(keep(finding, names, table))
            }
        }
        return CheckedAnswer(AgentAnswer(agentId, confidence, summary, kept), dropped)
    }

    private fun schemaFault(message: String): Nothing = throw SchemaError(message)

    private fun parseObject(text: String): JsonObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < start) schemaFault("the answer is not JSON: no object found")
        val parsed =
            try {
                Json.parseToJsonElement(text.substring(start, end + 1))
            } catch (error: SerializationException) {
                schemaFault(
                    "the answer is not valid JSON: ${error.message.orEmpty().lines().first()}"
                )
            }
        return parsed as? JsonObject ?: schemaFault("the answer is not a JSON object")
    }

    private fun rawFinding(finding: StrictObject): RawFinding {
        val severityName = finding.string("severity")
        val severity =
            Severity.entries.firstOrNull { it.label == severityName }
                ?: schemaFault("'severity' must be info, warn or key, not '$severityName'")
        return RawFinding(
            claim = finding.string("claim"),
            pair = finding.nullableString("pair"),
            severity = severity,
            cards = finding.strings("cards"),
            numbers =
                finding.objects("numbers", NUMBER_KEYS).map {
                    it.string("key") to it.number("value")
                },
        )
    }

    private fun dropReason(finding: RawFinding, names: PoolNames, table: NumberTable): DropReason? =
        when {
            finding.cards.isEmpty() -> DropReason.NO_CARD
            finding.cards.any { names.resolve(it) == null } -> DropReason.UNKNOWN_CARD
            else ->
                finding.numbers.firstNotNullOfOrNull { (key, value) ->
                    when (table.check(key, value)) {
                        NumberCheck.Ok -> null
                        NumberCheck.UnknownKey -> DropReason.UNKNOWN_KEY
                        is NumberCheck.WrongValue -> DropReason.WRONG_VALUE
                    }
                }
        }

    private fun keep(finding: RawFinding, names: PoolNames, table: NumberTable): Finding =
        Finding(
            claim = finding.claim,
            pair = finding.pair,
            severity = finding.severity,
            cards = finding.cards.map { checkNotNull(names.resolve(it)) },
            numbers =
                finding.numbers.map { (key, _) ->
                    val entry = table.entries.first { it.key == key }
                    AnswerNumber(entry.key, entry.value, entry.typical)
                },
        )
}
