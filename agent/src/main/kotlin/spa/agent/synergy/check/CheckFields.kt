package spa.agent.synergy.check

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads the fields of one check object; each missing or mistyped field is an
 * [IllegalArgumentException] naming the field.
 */
internal class CheckFields(private val check: JsonObject) {
    private fun field(name: String): JsonElement =
        requireNotNull(check[name]) { "missing field '$name'" }

    private fun primitive(name: String, element: JsonElement = field(name)): JsonPrimitive? =
        element as? JsonPrimitive

    /** The string field [name]. */
    fun string(name: String): String {
        val value = primitive(name)?.takeIf { it.isString }?.content
        return requireNotNull(value) { "'$name' must be a string" }
    }

    /** The whole-number field [name]. */
    fun int(name: String): Int {
        val value = primitive(name)?.takeUnless { it.isString }?.intOrNull
        return requireNotNull(value) { "'$name' must be a whole number" }
    }

    /** The finite number field [name]. */
    fun number(name: String): Double {
        val value = primitive(name)?.takeUnless { it.isString }?.doubleOrNull
        require(value != null && value.isFinite()) { "'$name' must be a number" }
        return value
    }

    /**
     * The card ids of the non-empty array [name], each a string or an object with a string `id`.
     */
    fun cardIds(name: String): List<String> {
        val array = field(name) as? JsonArray
        require(array != null && array.isNotEmpty()) { "'$name' must be a non-empty array" }
        return array.map { item ->
            val id =
                when (item) {
                    is JsonObject -> (item["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    is JsonPrimitive -> item.takeIf { it.isString }?.content
                    else -> null
                }
            requireNotNull(id) { "each of '$name' must be a card id or an object with one" }
        }
    }
}
