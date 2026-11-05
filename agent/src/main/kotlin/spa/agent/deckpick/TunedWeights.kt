package spa.agent.deckpick

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import spa.agent.poolgrade.Weights

/**
 * A weights file with an optional `comment` string beside [Weights]' four numbers, as
 * `fixtures/weights/tuned.json` is written.
 */
object TunedWeights {
    /** The comment key. */
    const val COMMENT = "comment"

    private val PRETTY = Json { prettyPrint = true }

    /** The weights in [text]: [Weights.parse] after an optional string [COMMENT] is removed. */
    fun parse(text: String): Weights {
        val element =
            try {
                Json.parseToJsonElement(text)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("not JSON: ${e.message}", e)
            }
        require(element is JsonObject) { "expected a JSON object" }
        val comment = element[COMMENT]
        require(comment == null || (comment is JsonPrimitive && comment.isString)) {
            "'$COMMENT' must be a string"
        }
        return Weights.parse(JsonObject(element - COMMENT).toString())
    }

    /** [weights] and [comment] as a JSON object, keys in the order synergy, curve, colour, cap. */
    fun encode(weights: Weights, comment: String): String =
        PRETTY.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("synergy", weights.synergy)
                put("curve", weights.curve)
                put("colour", weights.colour)
                put("cap", weights.cap)
                put(COMMENT, comment)
            },
        ) + "\n"
}
