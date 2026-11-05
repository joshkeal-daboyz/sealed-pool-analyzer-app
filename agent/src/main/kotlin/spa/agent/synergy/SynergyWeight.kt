package spa.agent.synergy

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The tag synergy part's weight, `fixtures/weights/tag-synergy.json`: the most the part moves a
 * card, up or down. Weight 0 switches the part off.
 */
data class SynergyWeight(val weight: Double) {
    companion object {
        /** `fixtures/weights/tag-synergy.json`. */
        val DEFAULT = SynergyWeight(0.5)

        private const val KEY = "weight"

        /**
         * The weight in [text], a JSON object with exactly the number `weight`, finite and not
         * negative. Anything else is an [IllegalArgumentException].
         */
        fun parse(text: String): SynergyWeight {
            val element =
                try {
                    Json.parseToJsonElement(text)
                } catch (e: SerializationException) {
                    throw IllegalArgumentException("not JSON: ${e.message}", e)
                }
            require(element is JsonObject) { "expected a JSON object" }
            require(element.keys == setOf(KEY)) {
                "expected exactly the key $KEY, got " + element.keys.sorted().joinToString(", ")
            }
            val primitive = element[KEY] as? JsonPrimitive
            val value = primitive?.takeUnless { it.isString }?.doubleOrNull
            require(value != null && value.isFinite() && value >= 0.0) {
                "'$KEY' must be a number, 0 or more"
            }
            return SynergyWeight(value)
        }
    }
}
