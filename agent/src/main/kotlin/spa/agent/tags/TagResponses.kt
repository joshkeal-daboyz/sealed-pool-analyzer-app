package spa.agent.tags

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import spa.agent.grader.GradingPackets

/** The well-formed entries of one response file, and a line for each entry that is not. */
data class ResponseFileResult(val cards: List<ResponseCard>, val problems: List<String>)

/**
 * Reads the response file a tagger writes beside a card file. This checks only the shape (a JSON
 * array of `{ id, roles: [{ axis, role, strength }], partners: [{ id, strength }] }`, strings and
 * numbers where they belong, no other field); what the values may be is for the [TagCheck]s.
 */
object TagResponses {
    private val entryFields = setOf("id", "roles", "partners")
    private val roleFields = setOf("axis", "role", "strength")
    private val partnerFields = setOf("id", "strength")

    /** The response file beside [cardFile]. */
    fun read(cardFile: Path): ResponseFileResult {
        val path = cardFile.resolveSibling(GradingPackets.responseFileName(cardFile.name))
        val name = path.name
        if (!Files.isRegularFile(path)) {
            return failed("$name: missing; tag every card in ${cardFile.name}")
        }
        val parsed = TagJson.parse(Files.readString(path))
        val element = parsed.getOrNull()
        return when {
            element == null -> failed("$name: not JSON: ${parsed.exceptionOrNull()?.message}")
            element !is JsonArray -> failed("$name: not a JSON array of {id, roles, partners}")
            else -> entries(name, element)
        }
    }

    /** The well-formed entries of the response file [name], and a line for each other one. */
    private fun entries(name: String, element: JsonArray): ResponseFileResult {
        val cards = mutableListOf<ResponseCard>()
        val problems = mutableListOf<String>()
        element.forEachIndexed { i, entry ->
            val problem = entryProblem(entry)
            if (problem == null) cards += card(name, entry as JsonObject)
            else problems += "$name: entry ${i + 1}: $problem"
        }
        return ResponseFileResult(cards, problems)
    }

    private fun failed(problem: String) = ResponseFileResult(emptyList(), listOf(problem))

    private fun card(file: String, entry: JsonObject): ResponseCard =
        ResponseCard(
            file = file,
            id = TagJson.string(entry["id"])!!,
            roles =
                (entry["roles"] as JsonArray).map {
                    val role = it as JsonObject
                    ResponseRole(
                        TagJson.string(role["axis"])!!,
                        TagJson.string(role["role"])!!,
                        TagJson.number(role["strength"])!!,
                    )
                },
            partners =
                (entry["partners"] as JsonArray).map {
                    val partner = it as JsonObject
                    ResponsePartner(
                        TagJson.string(partner["id"])!!,
                        TagJson.number(partner["strength"])!!,
                    )
                },
        )

    /** Why [element] is not a well-formed entry, or null when it is one. */
    private fun entryProblem(element: JsonElement): String? {
        val entry = element as? JsonObject ?: return "not an object"
        val roles = entry["roles"] as? JsonArray
        val partners = entry["partners"] as? JsonArray
        return when {
            TagJson.string(entry["id"]) == null -> "id must be a string"
            roles == null -> "roles must be an array"
            partners == null -> "partners must be an array"
            else ->
                extraField(entry, entryFields)
                    ?: roles.firstNotNullOfOrNull { itemProblem("role", it, roleFields) }
                    ?: partners.firstNotNullOfOrNull { itemProblem("partner", it, partnerFields) }
        }
    }

    /** Why a role or partner [element] is malformed, or null. */
    private fun itemProblem(what: String, element: JsonElement, fields: Set<String>): String? {
        val item = element as? JsonObject ?: return "a $what is not an object"
        val badString = fields.firstOrNull { it != "strength" && TagJson.string(item[it]) == null }
        return when {
            badString != null -> "a $what's $badString must be a string"
            TagJson.number(item["strength"]) == null -> "a $what's strength must be a number"
            else -> extraField(item, fields)?.let { "a $what has $it" }
        }
    }

    private fun extraField(entry: JsonObject, fields: Set<String>): String? =
        entry.keys.firstOrNull { it !in fields }?.let { "unexpected field '$it'" }
}
