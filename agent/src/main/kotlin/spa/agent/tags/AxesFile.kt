package spa.agent.tags

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** What reading an axes file gives: the [axes] when there are no [problems], else null. */
data class AxesResult(val axes: List<Axis>?, val problems: List<String>)

/**
 * Reads and checks an axes file, the tagger's `axes.response.json`: a JSON array of `{ id, name,
 * definition }`, [MIN_AXES] to [MAX_AXES] entries, each id lowercase letters, digits and hyphens,
 * the ids distinct, and each name and definition a non-empty string. Every problem is one line
 * starting with the file's name: first each entry's, then each repeated id, then the count.
 */
object AxesFile {
    const val MIN_AXES = 3
    const val MAX_AXES = 15

    private val fields = setOf("id", "name", "definition")
    private val idPattern = Regex("[a-z0-9-]+")

    /** The axes in [text], read from the file named [fileName]. */
    fun parse(text: String, fileName: String): AxesResult {
        val element =
            TagJson.parse(text).getOrElse {
                return AxesResult(null, listOf("$fileName: not JSON: ${it.message}"))
            }
        val problems =
            if (element is JsonArray) arrayProblems(element, fileName)
            else listOf("$fileName: not a JSON array of {id, name, definition}")
        return if (problems.isEmpty()) {
            AxesResult((element as JsonArray).map { axis(it as JsonObject) }, emptyList())
        } else {
            AxesResult(null, problems)
        }
    }

    /** Each entry's problem, then each repeated id, then a count out of range. */
    private fun arrayProblems(array: JsonArray, fileName: String): List<String> {
        val entries = array.mapIndexedNotNull { i, entry ->
            entryProblem(entry)?.let { "$fileName: entry ${i + 1}: $it" }
        }
        val repeated =
            array
                .mapNotNull { TagJson.string((it as? JsonObject)?.get("id")) }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .map { (id, n) -> "$fileName: id '$id' is given $n times" }
        val count =
            if (array.size in MIN_AXES..MAX_AXES) emptyList()
            else listOf("$fileName: ${array.size} axes; give $MIN_AXES to $MAX_AXES")
        return entries + repeated + count
    }

    private fun axis(entry: JsonObject) =
        Axis(
            TagJson.string(entry["id"])!!,
            TagJson.string(entry["name"])!!,
            TagJson.string(entry["definition"])!!,
        )

    /** Why [element] is not a well-formed axis, or null when it is one. */
    private fun entryProblem(element: JsonElement): String? {
        val entry = element as? JsonObject ?: return "not an object"
        val id = TagJson.string(entry["id"])
        val extra = entry.keys.firstOrNull { it !in fields }
        return when {
            id == null -> "id must be a string"
            !idPattern.matches(id) -> "id '$id' is not lowercase letters, digits and hyphens"
            TagJson.string(entry["name"]).isNullOrBlank() -> "name must be a non-empty string"
            TagJson.string(entry["definition"]).isNullOrBlank() ->
                "definition must be a non-empty string"
            extra != null -> "unexpected field '$extra'"
            else -> null
        }
    }
}
