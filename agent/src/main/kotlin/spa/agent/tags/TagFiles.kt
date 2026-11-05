package spa.agent.tags

import io.github.optimumcode.json.schema.ErrorCollector
import io.github.optimumcode.json.schema.JsonSchema
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** A tag file that breaks its schema; [problems] lists every break, one per line. */
class TagFileException(val problems: List<String>, cause: Throwable? = null) :
    IllegalArgumentException(problems.joinToString("\n"), cause)

/**
 * Reads and writes tag files, `fixtures/tags/<code>.json`. A document reads only when it passes
 * `tags.schema.json`; otherwise every problem is reported together in one [TagFileException].
 */
object TagFiles {
    /** The classpath location of the schema. */
    const val SCHEMA_RESOURCE = "/spa/agent/tags/tags.schema.json"

    /** The schema's text, as committed. */
    val schemaText: String by lazy {
        val stream =
            requireNotNull(TagFiles::class.java.getResourceAsStream(SCHEMA_RESOURCE)) {
                "Missing $SCHEMA_RESOURCE on the classpath."
            }
        stream.use { it.readBytes().decodeToString() }
    }

    private val schema: JsonSchema by lazy { JsonSchema.fromDefinition(schemaText) }

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** Reads and checks the tag file at [path]. A file that cannot be read throws its I/O error. */
    fun read(path: Path): TagFile = parse(Files.readString(path))

    /** Checks and decodes the tag file in [text]. */
    fun parse(text: String): TagFile {
        val element = step("not JSON") { json.parseToJsonElement(text) }
        val problems = validate(element)
        if (problems.isNotEmpty()) throw TagFileException(problems)
        return step("cannot decode") { json.decodeFromJsonElement(TagFile.serializer(), element) }
    }

    /** [file] as pretty-printed JSON with a final newline, the text [parse] reads back equal. */
    fun write(file: TagFile): String = json.encodeToString(TagFile.serializer(), file) + "\n"

    /** Every schema violation in [element], each as `<JSON pointer>: <message>`. */
    fun validate(element: JsonElement): List<String> {
        val problems = mutableListOf<String>()
        schema.validate(element, ErrorCollector { problems += "${it.objectPath}: ${it.message}" })
        return problems
    }

    private fun <T> step(what: String, block: () -> T): T =
        try {
            block()
        } catch (e: SerializationException) {
            throw TagFileException(listOf("$what: ${e.message?.lineSequence()?.first()}"), e)
        }
}
