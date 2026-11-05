package spa.agent.poolgrade

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The pool adjustment's weights: the most each rule can move a grade ([synergy], [curve],
 * [colour]), and the [cap] on their sum, in grade points.
 */
data class Weights(val synergy: Double, val curve: Double, val colour: Double, val cap: Double) {
    companion object {
        /** `fixtures/weights/default.json`. */
        val DEFAULT = Weights(synergy = 0.5, curve = 0.25, colour = 0.25, cap = 1.0)

        private val KEYS = listOf("synergy", "curve", "colour", "cap")

        /**
         * The weights in [text], a JSON object with exactly the numbers `synergy`, `curve`,
         * `colour` and `cap`, each finite and not negative. Anything else is an
         * [IllegalArgumentException].
         */
        fun parse(text: String): Weights {
            val element =
                try {
                    Json.parseToJsonElement(text)
                } catch (e: SerializationException) {
                    throw IllegalArgumentException("not JSON: ${e.message}", e)
                }
            require(element is JsonObject) { "expected a JSON object" }
            require(element.keys == KEYS.toSet()) {
                "expected exactly the keys ${KEYS.joinToString(", ")}, got " +
                    element.keys.sorted().joinToString(", ")
            }
            val values = KEYS.associateWith { key -> number(element, key) }
            return Weights(
                synergy = values.getValue("synergy"),
                curve = values.getValue("curve"),
                colour = values.getValue("colour"),
                cap = values.getValue("cap"),
            )
        }

        private fun number(element: JsonObject, key: String): Double {
            val primitive = element[key] as? JsonPrimitive
            val value = primitive?.takeUnless { it.isString }?.doubleOrNull
            require(value != null && value.isFinite() && value >= 0.0) {
                "'$key' must be a number, 0 or more"
            }
            return value
        }
    }
}
