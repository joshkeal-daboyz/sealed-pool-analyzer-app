package spa.agent.deckbacktest.tune

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import spa.agent.build.DeckWeights
import spa.agent.build.Fields

/** One knob of the grid: the [path] of a number in a deck weights file and the [values] to try. */
class Knob(val path: String, val values: List<Double>)

/**
 * The grid the tuner searches (SPA-060, `fixtures/weights/deck-grid.json`): a JSON object of knobs,
 * each a path into the deck weights JSON and the list of numbers to try there, in file order.
 */
class DeckGrid(val knobs: List<Knob>) {
    companion object {
        private val SECTIONS = listOf("weights", "params", "lands", "splash", "configs")

        /**
         * The grid in [text], checked against the [start] weights: the start must parse as deck
         * weights, and every value, put at its path in the start, must still parse. Anything else
         * is an [IllegalArgumentException] of one line naming the knob or key at fault.
         */
        fun parse(text: String, start: String): DeckGrid {
            try {
                DeckWeights.parse(start)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("the start weights do not parse: ${line(e)}", e)
            }
            val root =
                try {
                    Fields.obj(Fields.parseJson(text), "the grid")
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException(line(e), e)
                }
            return DeckGrid(root.map { (path, element) -> knob(path, element, start) })
        }

        private fun knob(path: String, element: JsonElement, start: String): Knob {
            val section = path.substringBefore('.')
            require(section in SECTIONS) {
                "unknown top-level key '$section' in grid knob '$path': " +
                    "expected ${SECTIONS.joinToString(", ")}"
            }
            val values = element as? JsonArray
            require(!values.isNullOrEmpty()) { "grid knob '$path' needs a list of numbers" }
            val numbers = values.map { value ->
                val primitive = value as? JsonPrimitive
                val number = primitive?.takeUnless { it.isString }?.doubleOrNull
                require(number != null && number.isFinite()) {
                    "grid knob '$path' has a value that is not a number: $value"
                }
                number
            }
            numbers.forEach { check(path, it, start) }
            return Knob(path, numbers.distinct())
        }

        private fun check(path: String, value: Double, start: String) {
            try {
                DeckWeights.parse(WeightsEdit.apply(start, mapOf(path to value)))
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "grid knob '$path' value ${WeightsEdit.text(value)}: ${line(e)}",
                    e,
                )
            }
        }

        private fun line(e: Exception): String = e.message.orEmpty().lineSequence().first()
    }
}
