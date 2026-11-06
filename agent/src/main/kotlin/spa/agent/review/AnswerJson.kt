package spa.agent.review

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The JSON of the Explainer's verdict and of each analyst's section of the review. */
internal object AnswerJson {
    /** The verdict: `status` `explained` or `missing`, with its fields. */
    fun verdict(outcome: ExplainerOutcome): JsonObject = buildJsonObject {
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
                                put("cards", ReviewJson.strings(it.cards))
                            }
                        )
                    }
                }
                putJsonArray("dropped") {
                    outcome.dropped.forEach {
                        add(
                            buildJsonObject {
                                put("kind", it.kind.name.lowercase())
                                put("reason", SectionText.noteWords(it.reason))
                                put("text", it.text)
                            }
                        )
                    }
                }
            }
        }
    }

    /** One analyst: `status` `answered` or `missing`, with its fields. */
    fun agent(outcome: AgentOutcome): JsonObject = buildJsonObject {
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
                                put("reason", SectionText.findingWords(it.reason))
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
        put("cards", ReviewJson.strings(finding.cards))
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
}
