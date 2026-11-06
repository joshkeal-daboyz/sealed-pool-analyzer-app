package spa.agent.deckbacktest.tune

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import spa.agent.build.Fields

/**
 * Reads and sets one number of a deck weights file by its path, such as `weights.curve` or
 * `params.creatures.minCreatures` (SPA-060). The file is JSON text in, JSON text out; nothing here
 * knows what a weight means.
 */
object WeightsEdit {
    private val pretty = Json { prettyPrint = true }

    /** The number at [path] in [text]; null when the file has none there. */
    fun read(text: String, path: String): Double? {
        var node: JsonElement? = Fields.parseJson(text)
        for (key in path.split(PATH_SEPARATOR)) node = (node as? JsonObject)?.get(key)
        return (node as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    }

    /**
     * [text] with each number of [choice] put at its path, objects created on the way when the file
     * lacks them, as pretty printed JSON. The result does not depend on the order of [choice]: the
     * paths are applied in sorted order. A path through a value that is not an object is an
     * [IllegalArgumentException] naming it.
     */
    fun apply(text: String, choice: Map<String, Double>): String {
        var root = Fields.obj(Fields.parseJson(text), "the file")
        for ((path, value) in choice.toSortedMap()) {
            root = put(root, path.split(PATH_SEPARATOR), number(value), path)
        }
        return pretty.encodeToString(JsonElement.serializer(), root) + "\n"
    }

    /** [text] without its `configs` section, as text: what the decks of one build depend on. */
    fun withoutConfigs(text: String): String =
        JsonObject(Fields.obj(Fields.parseJson(text), "the file") - CONFIGS).toString()

    /** [value] as the shortest text: no decimal point when it is whole. */
    fun text(value: Double): String =
        if (isWhole(value)) String.format(Locale.ROOT, "%d", value.toLong()) else value.toString()

    private fun number(value: Double): JsonPrimitive =
        if (isWhole(value)) JsonPrimitive(value.toLong()) else JsonPrimitive(value)

    private fun isWhole(value: Double): Boolean =
        value == Math.floor(value) && Math.abs(value) < WHOLE_LIMIT

    private fun put(
        node: JsonObject,
        keys: List<String>,
        value: JsonElement,
        path: String,
    ): JsonObject {
        val key = keys.first()
        val child = node[key]
        val next =
            if (keys.size == 1) {
                value
            } else {
                require(child == null || child is JsonObject) {
                    "the path '$path' goes through '$key', which is not an object"
                }
                put((child as? JsonObject) ?: JsonObject(emptyMap()), keys.drop(1), value, path)
            }
        return JsonObject(LinkedHashMap(node).also { it[key] = next })
    }

    private const val PATH_SEPARATOR = '.'
    private const val CONFIGS = "configs"
    private const val WHOLE_LIMIT = 1e15
}
