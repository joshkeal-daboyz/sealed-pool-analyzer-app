package spa.agent.review

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Validates the Explainer's answer text: the schema (strict), then each deck note and each
 * disagreement against the pool, the offered ranks and the agents that answered. A schema fault is
 * a [SchemaError]; a bad item is dropped and counted.
 */
object ExplainerChecker {
    private val ANSWER_KEYS = setOf("confidence", "verdict", "decks", "disagreements")
    private val DECK_KEYS = setOf("rank", "note", "cards")
    private val DISAGREEMENT_KEYS = setOf("topic", "agents", "cards", "note")
    private const val MIN_AGENTS = 2

    /**
     * Checks [text]. The part from the first `{` to the last `}` is read, so a code fence or a
     * sentence around the object is tolerated. [poolNames] are the pool cards' full names,
     * [deckRanks] the ranks of the decks that were offered and [agentIds] the ids of the agents
     * that answered. A deck note repeating a rank already kept is dropped as a duplicate.
     */
    fun check(
        text: String,
        poolNames: Collection<String>,
        deckRanks: Collection<Int>,
        agentIds: Collection<String>,
    ): CheckedExplainer {
        val answer = StrictObject(parseObject(text), "", ANSWER_KEYS, ::schemaFault)
        val confidence = answer.number("confidence")
        if (confidence !in 0.0..1.0) schemaFault("'confidence' must be from 0 to 1")
        val verdict = answer.string("verdict")
        if (verdict.isBlank()) schemaFault("'verdict' must not be empty")
        val decks = answer.objects("decks", DECK_KEYS)
        val disagreements = answer.objects("disagreements", DISAGREEMENT_KEYS)

        val names = PoolNames(poolNames)
        val dropped = mutableListOf<DroppedItem>()
        val keptDecks = keepDecks(decks, names, deckRanks.map(Int::toLong).toSet(), dropped)
        val keptDisagreements = keepDisagreements(disagreements, names, agentIds.toSet(), dropped)
        return CheckedExplainer(
            ExplainerResult(confidence, verdict, keptDecks, keptDisagreements),
            dropped,
        )
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

    private fun keepDecks(
        items: List<StrictObject>,
        names: PoolNames,
        ranks: Set<Long>,
        dropped: MutableList<DroppedItem>,
    ): List<DeckNote> {
        val kept = mutableListOf<DeckNote>()
        for (item in items) {
            val rank = item.whole("rank")
            val note = item.string("note")
            val cards = item.strings("cards")
            val reason =
                when {
                    rank !in ranks -> ExplainerDropReason.UNKNOWN_RANK
                    kept.any { it.rank.toLong() == rank } -> ExplainerDropReason.DUPLICATE_RANK
                    else -> cardReason(cards, names)
                }
            if (reason == null) {
                kept.add(
                    DeckNote(rank.toInt(), note, cards.map { checkNotNull(names.resolve(it)) })
                )
            } else {
                dropped.add(DroppedItem(ItemKind.DECK, reason, note))
            }
        }
        return kept
    }

    private fun keepDisagreements(
        items: List<StrictObject>,
        names: PoolNames,
        agentIds: Set<String>,
        dropped: MutableList<DroppedItem>,
    ): List<Disagreement> {
        val kept = mutableListOf<Disagreement>()
        for (item in items) {
            val topic = item.string("topic")
            val agents = item.strings("agents")
            val cards = item.strings("cards")
            val note = item.string("note")
            val reason =
                when {
                    agents.toSet().size < MIN_AGENTS -> ExplainerDropReason.TOO_FEW_AGENTS
                    agents.any { it !in agentIds } -> ExplainerDropReason.UNKNOWN_AGENT
                    else -> cardReason(cards, names)
                }
            if (reason == null) {
                val full = cards.map { checkNotNull(names.resolve(it)) }
                kept.add(Disagreement(topic, agents, full, note))
            } else {
                dropped.add(DroppedItem(ItemKind.DISAGREEMENT, reason, topic))
            }
        }
        return kept
    }

    private fun cardReason(cards: List<String>, names: PoolNames): ExplainerDropReason? =
        when {
            cards.isEmpty() -> ExplainerDropReason.NO_CARD
            cards.any { names.resolve(it) == null } -> ExplainerDropReason.UNKNOWN_CARD
            else -> null
        }
}
