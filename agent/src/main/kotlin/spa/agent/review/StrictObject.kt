package spa.agent.review

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A JSON object read strictly: an unknown key, a missing key or a wrong type calls [fail] with a
 * one-line message that names the key by its [path] (`findings[0].claim`). Shared by the answer
 * checker and the review config, which differ only in the error they raise.
 */
@Suppress("TooManyFunctions") // one reader per JSON type
internal class StrictObject(
    private val obj: JsonObject,
    private val path: String,
    allowed: Set<String>,
    private val fail: (String) -> Nothing,
) {
    init {
        obj.keys.firstOrNull { it !in allowed }?.let { fail("unknown key '${at(it)}'") }
    }

    private fun at(name: String) = if (path.isEmpty()) name else "$path.$name"

    private fun required(name: String, what: String): JsonElement =
        obj[name] ?: fail("missing key '${at(name)}', which must be $what")

    private fun primitive(name: String, what: String): JsonPrimitive =
        required(name, what) as? JsonPrimitive ?: fail("'${at(name)}' must be $what")

    fun string(name: String): String {
        val value = primitive(name, "a string")
        return if (value.isString) value.content else fail("'${at(name)}' must be a string")
    }

    /** A string, or null when the key holds JSON null. */
    fun nullableString(name: String): String? {
        val value = primitive(name, "a string or null")
        return when {
            value.isString -> value.content
            value.content == "null" -> null
            else -> fail("'${at(name)}' must be a string or null")
        }
    }

    fun number(name: String): Double =
        primitive(name, "a number").takeUnless { it.isString }?.doubleOrNull
            ?: fail("'${at(name)}' must be a number")

    fun whole(name: String): Long =
        primitive(name, "a whole number").takeUnless { it.isString }?.longOrNull
            ?: fail("'${at(name)}' must be a whole number")

    fun boolean(name: String): Boolean =
        primitive(name, "true or false").takeUnless { it.isString }?.booleanOrNull
            ?: fail("'${at(name)}' must be true or false")

    private fun array(name: String): JsonArray =
        required(name, "a list") as? JsonArray ?: fail("'${at(name)}' must be a list")

    /** The strings of the list [name]. */
    fun strings(name: String): List<String> =
        array(name).mapIndexed { index, item ->
            val primitive = item as? JsonPrimitive
            if (primitive != null && primitive.isString) {
                primitive.content
            } else {
                fail("'${at(name)}[$index]' must be a string")
            }
        }

    /** The objects of the list [name], each with the keys [allowed]. */
    fun objects(name: String, allowed: Set<String>): List<StrictObject> =
        array(name).mapIndexed { index, item ->
            val child = item as? JsonObject ?: fail("'${at(name)}[$index]' must be an object")
            StrictObject(child, "${at(name)}[$index]", allowed, fail)
        }

    /** The object [name], with the keys [allowed]. */
    fun child(name: String, allowed: Set<String>): StrictObject {
        val child = required(name, "an object") as? JsonObject
        return StrictObject(
            child ?: fail("'${at(name)}' must be an object"),
            at(name),
            allowed,
            fail,
        )
    }
}
