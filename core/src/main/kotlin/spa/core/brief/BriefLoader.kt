package spa.core.brief

import io.github.optimumcode.json.schema.ErrorCollector
import io.github.optimumcode.json.schema.JsonSchema
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Reads a format brief. A document loads only when it passes `format-brief.schema.json` and the
 * checks the schema cannot express (see [BriefRules]); otherwise every problem found is reported
 * together in one [BriefValidationException].
 */
object BriefLoader {
    /** The classpath location of the schema. */
    const val SCHEMA_RESOURCE = "/spa/core/brief/format-brief.schema.json"

    /** The schema's text, as committed. */
    val schemaText: String by lazy {
        val stream =
            requireNotNull(BriefLoader::class.java.getResourceAsStream(SCHEMA_RESOURCE)) {
                "Missing $SCHEMA_RESOURCE on the classpath."
            }
        stream.use { it.readBytes().decodeToString() }
    }

    private val schema: JsonSchema by lazy { JsonSchema.fromDefinition(schemaText) }

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** Reads and checks the brief at [path]. A file that cannot be read throws its I/O error. */
    fun load(path: Path): FormatBrief = parse(Files.readString(path))

    /** Checks and decodes the brief in [text]. */
    fun parse(text: String): FormatBrief {
        val element = documentStep("not JSON") { json.parseToJsonElement(text) }
        val errors = validate(element)
        if (errors.isNotEmpty()) throw BriefValidationException(errors)
        // The schema admits a few values Kotlin cannot hold, such as an integer written 14.0.
        return documentStep("cannot decode") {
            json.decodeFromJsonElement(FormatBrief.serializer(), element)
        }
    }

    /** Runs [block], reporting a serialization failure as one error on the whole document. */
    private fun <T> documentStep(what: String, block: () -> T): T =
        try {
            block()
        } catch (e: SerializationException) {
            throw BriefValidationException(listOf(BriefError("", "$what: ${e.message}")), e)
        }

    /** Every schema violation in [element], then every [BriefRules] violation. */
    fun validate(element: JsonElement): List<BriefError> {
        val errors = mutableListOf<BriefError>()
        schema.validate(
            element,
            ErrorCollector { errors += BriefError(it.objectPath.toString(), it.message) },
        )
        return errors + BriefRules.check(element)
    }

    /** [brief] as the pretty-printed JSON [parse] reads back to an equal object. */
    fun encodeToString(brief: FormatBrief): String =
        json.encodeToString(FormatBrief.serializer(), brief)
}
