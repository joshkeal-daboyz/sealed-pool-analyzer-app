package spa.agent.grade

import io.github.optimumcode.json.schema.ErrorCollector
import io.github.optimumcode.json.schema.JsonSchema
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One problem in a grade file, at a JSON pointer into the document ("" is the whole document). */
data class GradeProblem(val pointer: String, val message: String) {
    override fun toString(): String = "${pointer.ifEmpty { "(document)" }}: $message"
}

/** A grade file that breaks its schema; the message lists every [problems] entry, one per line. */
class GradeFileException(val problems: List<GradeProblem>, cause: Throwable? = null) :
    IllegalArgumentException(problems.joinToString("\n"), cause)

/**
 * Reads and writes grade files. A document loads only when it passes `grades.schema.json` and has
 * no duplicate card, which the schema cannot express; otherwise every problem is reported together
 * in one [GradeFileException].
 */
object GradeFiles {
    /** The classpath location of the schema. */
    const val SCHEMA_RESOURCE = "/spa/agent/grades.schema.json"

    /** The schema's text, as committed. */
    val schemaText: String by lazy {
        val stream =
            requireNotNull(GradeFiles::class.java.getResourceAsStream(SCHEMA_RESOURCE)) {
                "Missing $SCHEMA_RESOURCE on the classpath."
            }
        stream.use { it.readBytes().decodeToString() }
    }

    private val schema: JsonSchema by lazy { JsonSchema.fromDefinition(schemaText) }

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * Reads and checks the grade file at [path]. A file that cannot be read throws its I/O error.
     */
    fun load(path: Path): GradeFile = parse(Files.readString(path))

    /** Checks and decodes the grade file in [text]. */
    fun parse(text: String): GradeFile {
        val element = documentStep("not JSON") { json.parseToJsonElement(text) }
        val problems = validate(element)
        if (problems.isNotEmpty()) throw GradeFileException(problems)
        return documentStep("cannot decode") {
            json.decodeFromJsonElement(GradeFile.serializer(), element)
        }
    }

    /** [file] as pretty-printed JSON with a final newline, the text [parse] reads back equal. */
    fun write(file: GradeFile): String = json.encodeToString(GradeFile.serializer(), file) + "\n"

    /** Every schema violation in [element], then every duplicate card. */
    fun validate(element: JsonElement): List<GradeProblem> {
        val problems = mutableListOf<GradeProblem>()
        schema.validate(
            element,
            ErrorCollector { problems += GradeProblem(it.objectPath.toString(), it.message) },
        )
        return problems + duplicates(element)
    }

    /** One problem per entry that repeats an earlier entry's setCode and collectorNumber. */
    private fun duplicates(element: JsonElement): List<GradeProblem> {
        val grades = ((element as? JsonObject)?.get("grades") as? JsonArray) ?: return emptyList()
        val firstSeen = mutableMapOf<Pair<String, String>, Int>()
        return grades.mapIndexedNotNull { index, grade ->
            val key = cardKey(grade) ?: return@mapIndexedNotNull null
            val first = firstSeen.putIfAbsent(key, index) ?: return@mapIndexedNotNull null
            GradeProblem(
                "/grades/$index",
                "duplicate card ${key.first} ${key.second}, first graded at /grades/$first",
            )
        }
    }

    /** The entry's setCode and collectorNumber, or null when either is not a string. */
    private fun cardKey(grade: JsonElement): Pair<String, String>? {
        val entry = grade as? JsonObject
        val setCode = stringField(entry, "setCode")
        val number = stringField(entry, "collectorNumber")
        return if (setCode != null && number != null) setCode to number else null
    }

    private fun stringField(entry: JsonObject?, name: String): String? =
        (entry?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Runs [block], reporting a serialization failure as one problem on the whole document. */
    private fun <T> documentStep(what: String, block: () -> T): T =
        try {
            block()
        } catch (e: SerializationException) {
            throw GradeFileException(listOf(GradeProblem("", "$what: ${e.message}")), e)
        }
}
