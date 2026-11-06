package spa.agent.review

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The full structured [Review] as pretty-printed JSON (G-006 Decided 3), keys in a fixed order. It
 * depends on the review alone, so the same review gives the same bytes.
 */
object ReviewJson {
    private val printer = Json { prettyPrint = true }

    /** The JSON of [review], ending with a newline. */
    fun render(review: Review): String {
        val root = buildJsonObject {
            put("set", review.header.set)
            put("setName", review.header.setName)
            put("pool", pool(review.header))
            put("decks", JsonArray(review.decks.map(::deck)))
            put("verdict", verdict(review.verdict))
            put("agents", JsonArray(review.sections.map(::agent)))
            put("disagreements", JsonArray(review.disagreements.map(::disagreement)))
            put("missing", JsonArray(review.missing.map(::missing)))
            putJsonObject("usage") {
                put("calls", review.usage.calls)
                put("input", review.usage.input)
                put("output", review.usage.output)
                put("cacheRead", review.usage.cacheRead)
                put("cacheWrite", review.usage.cacheWrite)
            }
            put("model", review.model)
            put("attribution", ReviewText.ATTRIBUTION)
        }
        return printer.encodeToString(JsonElement.serializer(), root) + "\n"
    }

    private fun strings(values: List<String>): JsonArray =
        JsonArray(values.map { JsonPrimitive(it) })

    private fun pool(header: ReviewHeader): JsonObject = buildJsonObject {
        put("totalCards", header.totalCards)
        put("distinct", header.distinct)
        put("unresolved", header.unresolved)
        put("kitWarnings", strings(header.kitWarnings))
    }

    private fun counts(counts: List<NameCount>): JsonArray =
        JsonArray(
            counts.map {
                buildJsonObject {
                    put("name", it.name)
                    put("count", it.count)
                }
            }
        )

    private fun deck(deck: DeckSummary): JsonObject = buildJsonObject {
        put("rank", deck.rank)
        put("colours", deck.colours)
        put("sameAs", strings(deck.sameAs))
        put("score", deck.score)
        put("spells", counts(deck.spells))
        put("landCount", deck.landCount)
        put("lands", counts(deck.lands))
        put("why", deck.why)
    }

    private fun verdict(outcome: ExplainerOutcome): JsonObject = buildJsonObject {
        when (outcome) {
            is ExplainerOutcome.Missing -> {
                put("status", "missing")
                put("reason", outcome.reason)
            }
            is ExplainerOutcome.Explained -> {
                put("status", "explained")
                put("text", outcome.result.verdict)
                put("confidence", outcome.result.confidence)
                putJsonArray("deckNotes") {
                    outcome.result.decks.forEach {
                        add(
                            buildJsonObject {
                                put("rank", it.rank)
                                put("note", it.note)
                                put("cards", strings(it.cards))
                            }
                        )
                    }
                }
                putJsonArray("dropped") {
                    outcome.dropped.forEach {
                        add(
                            buildJsonObject {
                                put("kind", it.kind.name.lowercase())
                                put("reason", ReviewText.noteWords(it.reason))
                                put("text", it.text)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun agent(outcome: AgentOutcome): JsonObject = buildJsonObject {
        put("id", outcome.agent.id)
        put("title", outcome.agent.title)
        when (outcome) {
            is AgentOutcome.Missing -> {
                put("status", "missing")
                put("reason", outcome.reason)
            }
            is AgentOutcome.Answered -> {
                put("status", "answered")
                put("confidence", outcome.answer.confidence)
                put("summary", outcome.answer.summary)
                put("findings", JsonArray(outcome.answer.findings.map(::finding)))
                putJsonArray("dropped") {
                    outcome.dropped.forEach {
                        add(
                            buildJsonObject {
                                put("reason", ReviewText.findingWords(it.reason))
                                put("claim", it.claim)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun finding(finding: Finding): JsonObject = buildJsonObject {
        put("severity", finding.severity.label)
        put("pair", finding.pair)
        put("claim", finding.claim)
        put("cards", strings(finding.cards))
        put("numbers", JsonArray(finding.numbers.map(::number)))
    }

    private fun number(number: AnswerNumber): JsonObject = buildJsonObject {
        put("key", number.key)
        put("value", number.value)
        val typical = number.typical
        put(
            "typical",
            if (typical == null) {
                JsonNull
            } else {
                buildJsonObject {
                    put("median", typical.median)
                    put("p10", typical.p10)
                    put("p90", typical.p90)
                    put("mean", typical.mean)
                }
            },
        )
    }

    private fun disagreement(disagreement: Disagreement): JsonObject = buildJsonObject {
        put("topic", disagreement.topic)
        put("agents", strings(disagreement.agents))
        put("cards", strings(disagreement.cards))
        put("note", disagreement.note)
    }

    private fun missing(entry: MissingEntry): JsonObject = buildJsonObject {
        put("id", entry.id)
        put("reason", entry.reason)
    }
}
