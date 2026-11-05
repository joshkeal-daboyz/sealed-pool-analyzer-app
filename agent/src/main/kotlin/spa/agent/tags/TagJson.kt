package spa.agent.tags

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** The JSON reading the tag files share. */
internal object TagJson {
    /** [text] as JSON, or the parser's first message line as a failure. */
    fun parse(text: String): Result<JsonElement> =
        try {
            Result.success(Json.parseToJsonElement(text))
        } catch (e: SerializationException) {
            Result.failure(IllegalArgumentException(e.message?.lineSequence()?.first(), e))
        }

    /** The string [element] holds, or null when it is not a JSON string. */
    fun string(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** The number [element] holds as written, or null when it is not a JSON number. */
    fun number(element: JsonElement?): String? =
        (element as? JsonPrimitive)
            ?.takeIf { !it.isString && it.content.toDoubleOrNull() != null }
            ?.content
}
