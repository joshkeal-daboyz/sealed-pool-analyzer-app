package spa.agent.tags

import java.nio.file.Path
import kotlinx.serialization.json.JsonElement

/** A tag file that breaks its schema; [problems] lists every break, one per line. */
class TagFileException(val problems: List<String>, cause: Throwable? = null) :
    IllegalArgumentException(problems.joinToString("\n"), cause)

/** Reads and writes tag files, checked against `tags.schema.json`. */
object TagFiles {
    const val SCHEMA_RESOURCE = "/spa/agent/tags/tags.schema.json"

    val schemaText: String
        get() = TODO("SPA-047")

    fun read(path: Path): TagFile = TODO("SPA-047 $path")

    fun parse(text: String): TagFile = TODO("SPA-047 $text")

    fun write(file: TagFile): String = TODO("SPA-047 $file")

    fun validate(element: JsonElement): List<String> = TODO("SPA-047 $element")
}
