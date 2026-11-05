package spa.agent.tags

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** What reading an axes file gives: the [axes] when there are no [problems], else null. */
data class AxesResult(val axes: List<Axis>?, val problems: List<String>)

/**
 * The two forms of an axes file. [PLAIN] (tags prompt v1): `{ id, name, definition }`, at most 15
 * axes. [SUPPLIED] (v2 on): each axis also carries `suppliedBy`, an array of `{ fact, strength }`
 * that may be empty, and there may be up to 20 axes.
 */
enum class AxesFormat(val maxAxes: Int, val supplied: Boolean) {
    PLAIN(AxesFile.MAX_AXES, false),
    SUPPLIED(AxesFile.MAX_SUPPLIED_AXES, true),
}

/**
 * Reads and checks an axes file, the tagger's `axes.response.json`: a JSON array of `{ id, name,
 * definition }` ([AxesFormat.PLAIN]) or of `{ id, name, definition, suppliedBy }`
 * ([AxesFormat.SUPPLIED]), [MIN_AXES] to the format's most entries, each id lowercase letters,
 * digits and hyphens, the ids distinct, and each name and definition a non-empty string. This
 * checks the shape of `suppliedBy` (objects with a string `fact` and a whole-number `strength`);
 * which facts and strengths are allowed is for the supply [TagCheck]s. Every problem is one line
 * starting with the file's name: first each entry's, then each repeated id, then the count.
 */
object AxesFile {
    const val MIN_AXES = 3

    /** The most axes of a [AxesFormat.PLAIN] file, tags prompt v1's cap. */
    const val MAX_AXES = 15

    /** The most axes of a [AxesFormat.SUPPLIED] file, from tags prompt v2. */
    const val MAX_SUPPLIED_AXES = 20

    private const val SUPPLIED_BY = "suppliedBy"
    private val supplyFields = setOf("fact", "strength")
    private val idPattern = Regex("[a-z0-9-]+")

    /** The axes in [text], read from the file named [fileName], as a [AxesFormat.PLAIN] file. */
    fun parse(text: String, fileName: String): AxesResult = parse(text, fileName, AxesFormat.PLAIN)

    /**
     * The axes in [text] in the format the file itself shows: [AxesFormat.SUPPLIED] when any entry
     * has `suppliedBy`, else [AxesFormat.PLAIN]. `spa tags-import` reads the packet's `axes.json`
     * this way, so a v1 packet imports exactly as before.
     */
    fun parseDetected(text: String, fileName: String): AxesResult {
        val array = TagJson.parse(text).getOrNull() as? JsonArray
        val supplied = array?.any { (it as? JsonObject)?.containsKey(SUPPLIED_BY) == true } == true
        return parse(text, fileName, if (supplied) AxesFormat.SUPPLIED else AxesFormat.PLAIN)
    }

    /** The axes in [text], read from the file named [fileName], in [format]. */
    fun parse(text: String, fileName: String, format: AxesFormat): AxesResult {
        val element =
            TagJson.parse(text).getOrElse {
                return AxesResult(null, listOf("$fileName: not JSON: ${it.message}"))
            }
        val problems =
            if (element is JsonArray) arrayProblems(element, fileName, format)
            else listOf("$fileName: not a JSON array of {id, name, definition}")
        return if (problems.isEmpty()) {
            AxesResult((element as JsonArray).map { axis(it as JsonObject, format) }, emptyList())
        } else {
            AxesResult(null, problems)
        }
    }

    /** Each entry's problem, then each repeated id, then a count out of range. */
    private fun arrayProblems(
        array: JsonArray,
        fileName: String,
        format: AxesFormat,
    ): List<String> {
        val entries = array.mapIndexedNotNull { i, entry ->
            entryProblem(entry, format)?.let { "$fileName: entry ${i + 1}: $it" }
        }
        val repeated =
            array
                .mapNotNull { TagJson.string((it as? JsonObject)?.get("id")) }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .map { (id, n) -> "$fileName: id '$id' is given $n times" }
        val max = format.maxAxes
        val count =
            if (array.size in MIN_AXES..max) emptyList()
            else listOf("$fileName: ${array.size} axes; give $MIN_AXES to $max")
        return entries + repeated + count
    }

    private fun axis(entry: JsonObject, format: AxesFormat) =
        Axis(
            TagJson.string(entry["id"])!!,
            TagJson.string(entry["name"])!!,
            TagJson.string(entry["definition"])!!,
            if (format.supplied) (entry[SUPPLIED_BY] as JsonArray).map(::supply) else null,
        )

    private fun supply(element: JsonElement): FactSupply {
        val item = element as JsonObject
        return FactSupply(
            TagJson.string(item["fact"])!!,
            TagJson.number(item["strength"])!!.toInt(),
        )
    }

    private fun fields(format: AxesFormat): Set<String> =
        if (format.supplied) setOf("id", "name", "definition", SUPPLIED_BY)
        else setOf("id", "name", "definition")

    /** Why [element] is not a well-formed axis, or null when it is one. */
    private fun entryProblem(element: JsonElement, format: AxesFormat): String? {
        val entry = element as? JsonObject ?: return "not an object"
        val id = TagJson.string(entry["id"])
        val extra = entry.keys.firstOrNull { it !in fields(format) }
        return when {
            id == null -> "id must be a string"
            !idPattern.matches(id) -> "id '$id' is not lowercase letters, digits and hyphens"
            TagJson.string(entry["name"]).isNullOrBlank() -> "name must be a non-empty string"
            TagJson.string(entry["definition"]).isNullOrBlank() ->
                "definition must be a non-empty string"
            extra != null -> "unexpected field '$extra'"
            format.supplied -> supplyProblem(entry[SUPPLIED_BY])
            else -> null
        }
    }

    /** Why an axis's `suppliedBy` is malformed, or null. */
    private fun supplyProblem(element: JsonElement?): String? {
        val array = element as? JsonArray ?: return "$SUPPLIED_BY must be an array"
        return array.firstNotNullOfOrNull { item ->
            val supply = item as? JsonObject
            val strength = TagJson.number(supply?.get("strength"))
            when {
                supply == null -> "a $SUPPLIED_BY entry is not an object"
                TagJson.string(supply["fact"]) == null ->
                    "a $SUPPLIED_BY entry's fact must be a string"
                strength?.toIntOrNull() == null ->
                    "a $SUPPLIED_BY entry's strength must be a whole number"
                else ->
                    supply.keys
                        .firstOrNull { it !in supplyFields }
                        ?.let { "a $SUPPLIED_BY entry has unexpected field '$it'" }
            }
        }
    }
}
