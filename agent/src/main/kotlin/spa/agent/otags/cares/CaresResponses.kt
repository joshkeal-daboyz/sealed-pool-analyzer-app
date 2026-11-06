package spa.agent.otags.cares

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import spa.agent.tags.TagJson

/** One cares tag a response adds: the [fact], the [strength] as written, and the [reason]. */
data class CaresAdd(val fact: String, val strength: String, val reason: String)

/** One well-formed response entry, read from the response [file] (its name). */
data class CaresResponseCard(val file: String, val id: String, val add: List<CaresAdd>)

/** One fact of a table suggestion, with its [strength] as written. */
data class SuggestedFact(val fact: String, val strength: String)

/** One suggestion for the cares table: a Scryfall [tag], the [facts] it cares about, a [reason]. */
data class CaresSuggestion(val tag: String, val facts: List<SuggestedFact>, val reason: String)

/** What reading one response file gave: whether it exists, its well-formed entries and problems. */
data class CaresFileResult(
    val present: Boolean,
    val cards: List<CaresResponseCard>,
    val problems: List<String>,
)

/**
 * Reads the files Fable writes (SPA-055). This checks only the shape (a JSON array of `{ id, add:
 * [{ fact, strength, reason }] }`, strings and numbers where they belong); what the values may be
 * is for the [CaresCheck]s.
 */
object CaresResponses {
    /** The response file beside [cardFile]. */
    fun read(cardFile: Path): CaresFileResult {
        val path = cardFile.resolveSibling(CaresPackets.responseFileName(cardFile.name))
        if (!Files.isRegularFile(path)) return CaresFileResult(false, emptyList(), emptyList())
        val name = path.name
        val parsed = TagJson.parse(Files.readString(path))
        val element = parsed.getOrNull()
        return when {
            element == null -> failed("$name: not JSON: ${parsed.exceptionOrNull()?.message}")
            element !is JsonArray -> failed("$name: not a JSON array of { id, add }")
            else -> entries(name, element)
        }
    }

    /** The suggestions file of the packet [dir], if there is one: its entries and its problems. */
    fun suggestions(dir: Path): Pair<List<CaresSuggestion>, List<String>> {
        val path = dir.resolve(CaresPackets.SUGGESTIONS)
        if (!Files.isRegularFile(path)) return emptyList<CaresSuggestion>() to emptyList()
        val name = path.name
        val parsed = TagJson.parse(Files.readString(path))
        val element = parsed.getOrNull()
        if (element == null) {
            return emptyList<CaresSuggestion>() to
                listOf("$name: not JSON: ${parsed.exceptionOrNull()?.message}")
        }
        if (element !is JsonArray) {
            return emptyList<CaresSuggestion>() to
                listOf("$name: not a JSON array of { tag, facts, reason }")
        }
        val suggestions = mutableListOf<CaresSuggestion>()
        val problems = mutableListOf<String>()
        element.forEachIndexed { i, entry ->
            val suggestion = suggestion(entry)
            if (suggestion == null)
                problems += "$name: entry ${i + 1}: needs a tag, facts and a reason"
            else suggestions += suggestion
        }
        return suggestions to problems
    }

    private fun failed(problem: String) = CaresFileResult(true, emptyList(), listOf(problem))

    private fun entries(name: String, element: JsonArray): CaresFileResult {
        val cards = mutableListOf<CaresResponseCard>()
        val problems = mutableListOf<String>()
        element.forEachIndexed { i, entry ->
            val card = card(name, entry)
            if (card == null) {
                problems +=
                    "$name: entry ${i + 1}: needs an id and an add list of facts, each with a " +
                        "strength and a reason"
            } else {
                cards += card
            }
        }
        return CaresFileResult(true, cards, problems)
    }

    private fun card(file: String, entry: JsonElement): CaresResponseCard? {
        val fields = entry as? JsonObject
        val id = TagJson.string(fields?.get("id"))
        val adds = (fields?.get("add") as? JsonArray)?.map(::care)
        return if (id == null || adds == null || adds.any { it == null }) null
        else CaresResponseCard(file, id, adds.filterNotNull())
    }

    private fun care(element: JsonElement): CaresAdd? {
        val fields = element as? JsonObject ?: return null
        val fact = TagJson.string(fields["fact"])
        val strength = TagJson.number(fields["strength"])
        val reason = TagJson.string(fields["reason"])
        return if (fact == null || strength == null || reason == null) null
        else CaresAdd(fact, strength, reason)
    }

    private fun suggestion(element: JsonElement): CaresSuggestion? {
        val fields = element as? JsonObject ?: return null
        val tag = TagJson.string(fields["tag"])
        val reason = TagJson.string(fields["reason"])
        val facts = (fields["facts"] as? JsonArray)?.map(::suggested)
        return if (tag == null || reason == null || facts == null || facts.any { it == null }) null
        else CaresSuggestion(tag, facts.filterNotNull(), reason)
    }

    private fun suggested(element: JsonElement): SuggestedFact? {
        val fields = element as? JsonObject ?: return null
        val fact = TagJson.string(fields["fact"])
        val strength = TagJson.number(fields["strength"])
        return if (fact == null || strength == null) null else SuggestedFact(fact, strength)
    }
}
