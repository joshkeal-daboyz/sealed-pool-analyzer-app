package spa.agent.tags.review

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import spa.agent.tags.TagJson

/**
 * One suggested new card fact (`fact-suggestions.json`): its [fact] name, the [rule] that would
 * give it, [examples] (card ids) that should have it, and the [reason]. Reported, never applied.
 */
data class FactSuggestion(
    val fact: String,
    val rule: String,
    val examples: List<String>,
    val reason: String,
)

/** The well-formed corrections of one review file, and a line for each problem. */
data class ReviewFileResult(val corrections: List<Correction>, val problems: List<String>)

/** The fact suggestions of a review packet, and a line for each problem. */
data class SuggestionsResult(val suggestions: List<FactSuggestion>, val problems: List<String>)

/**
 * Reads the files a reviewer writes into a review packet. This checks the shape only: a review file
 * is a JSON array of corrections, each `{ "id", "action", "reason" }` plus exactly the fields its
 * action takes ([ReviewAction.fields]), strings where they belong, `strength` a number, and a
 * reason that is not empty. Whether a correction fits its card is for [ReviewApply], and whether
 * the corrected tags are allowed is for the pass-2 checks.
 */
object ReviewFiles {
    private val base = setOf("id", "action", "reason")
    private const val STRENGTH = "strength"

    /** The review file beside [cardFile], `cards-NN.review.json`; a missing one is a problem. */
    fun read(cardFile: Path, actions: List<ReviewAction> = ReviewActions.ALL): ReviewFileResult {
        val name = TagReviewPackets.reviewFileName(cardFile.name)
        val path = cardFile.resolveSibling(name)
        if (!Files.isRegularFile(path)) {
            val problem =
                "$name: missing; write one for ${cardFile.name}, an empty array when no card " +
                    "needs a correction"
            return ReviewFileResult(emptyList(), listOf(problem))
        }
        return parse(name, Files.readString(path), actions)
    }

    /** The corrections in [text], the review file [name], for the actions in [actions]. */
    fun parse(
        name: String,
        text: String,
        actions: List<ReviewAction> = ReviewActions.ALL,
    ): ReviewFileResult {
        val parsed = TagJson.parse(text)
        val element = parsed.getOrNull()
        val failed = { problem: String -> ReviewFileResult(emptyList(), listOf("$name: $problem")) }
        return when {
            element == null -> failed("not JSON: ${parsed.exceptionOrNull()?.message}")
            element !is JsonArray -> failed("not a JSON array of corrections")
            else -> {
                val byName = actions.associateBy { it.name }
                val corrections = mutableListOf<Correction>()
                val problems = mutableListOf<String>()
                element.forEachIndexed { i, entry ->
                    val number = i + 1
                    val problem = problem(entry, byName)
                    if (problem == null)
                        corrections += correction(name, number, entry as JsonObject)
                    else problems += "$name: correction $number: $problem"
                }
                ReviewFileResult(corrections, problems)
            }
        }
    }

    /** Why [element] is not a well-formed correction, or null when it is one. */
    private fun problem(element: JsonElement, actions: Map<String, ReviewAction>): String? {
        val entry = element as? JsonObject ?: return "not an object"
        val badString = base.firstOrNull { TagJson.string(entry[it]) == null }
        val name = TagJson.string(entry["action"])
        val action = actions[name]
        return when {
            badString != null -> "$badString must be a string"
            action == null -> "unknown action '$name'; use ${listing(actions.keys.toList())}"
            else -> fieldProblem(entry, action)
        }
    }

    /** Why the fields of [entry], a correction of [action], are not well formed, or null. */
    private fun fieldProblem(entry: JsonObject, action: ReviewAction): String? {
        val missing = action.fields.firstOrNull { it !in entry }
        val extra = entry.keys.firstOrNull { it !in base && it !in action.fields }
        val badField =
            action.fields.firstOrNull {
                if (it == STRENGTH) TagJson.number(entry[it]) == null
                else TagJson.string(entry[it]) == null
            }
        return when {
            missing != null -> "${action.name} needs $missing"
            extra != null -> "unexpected field '$extra' for ${action.name}"
            badField == STRENGTH -> "$STRENGTH must be a number"
            badField != null -> "$badField must be a string"
            TagJson.string(entry["reason"])!!.isBlank() -> "the reason is empty"
            else -> null
        }
    }

    private fun correction(file: String, number: Int, entry: JsonObject): Correction {
        val text = { field: String -> TagJson.string(entry[field]) }
        return Correction(
            file = file,
            number = number,
            id = text("id")!!,
            action = text("action")!!,
            axis = text("axis"),
            role = text("role"),
            partner = text("partner"),
            strength = TagJson.number(entry[STRENGTH]),
            reason = text("reason")!!,
        )
    }

    /** `a, b or c`. */
    private fun listing(names: List<String>): String =
        if (names.size <= 1) names.joinToString()
        else names.dropLast(1).joinToString(", ") + " or " + names.last()

    private val suggestionStrings = listOf("fact", "rule", "reason")

    /**
     * The suggestions in `fact-suggestions.json` in [dir], each `{ "fact", "rule", "examples":
     * [card ids], "reason" }` with no other field and no empty text; none when there is no file.
     */
    fun suggestions(dir: Path): SuggestionsResult {
        val name = TagReviewPackets.SUGGESTIONS
        val path = dir.resolve(name)
        if (!Files.isRegularFile(path)) return SuggestionsResult(emptyList(), emptyList())
        val parsed = TagJson.parse(Files.readString(path))
        val element = parsed.getOrNull()
        val failed = { problem: String ->
            SuggestionsResult(emptyList(), listOf("$name: $problem"))
        }
        return when {
            element == null -> failed("not JSON: ${parsed.exceptionOrNull()?.message}")
            element !is JsonArray -> failed("not a JSON array of suggestions")
            else -> {
                val suggestions = mutableListOf<FactSuggestion>()
                val problems = mutableListOf<String>()
                element.forEachIndexed { i, entry ->
                    val problem = suggestionProblem(entry)
                    if (problem == null) suggestions += suggestion(entry as JsonObject)
                    else problems += "$name: suggestion ${i + 1}: $problem"
                }
                SuggestionsResult(suggestions, problems)
            }
        }
    }

    private fun suggestionProblem(element: JsonElement): String? {
        val entry = element as? JsonObject ?: return "not an object"
        val badString = suggestionStrings.firstOrNull { TagJson.string(entry[it]) == null }
        val examples = entry["examples"] as? JsonArray
        val empty = suggestionStrings.firstOrNull { TagJson.string(entry[it])?.isBlank() == true }
        val extra = entry.keys.firstOrNull { it !in suggestionStrings && it != "examples" }
        return when {
            badString != null -> "$badString must be a string"
            examples == null || examples.isEmpty() || examples.any { TagJson.string(it) == null } ->
                "examples must be an array of card ids"
            extra != null -> "unexpected field '$extra'"
            empty != null -> "the $empty is empty"
            else -> null
        }
    }

    private fun suggestion(entry: JsonObject) =
        FactSuggestion(
            fact = TagJson.string(entry["fact"])!!,
            rule = TagJson.string(entry["rule"])!!,
            examples = (entry["examples"] as JsonArray).map { TagJson.string(it)!! },
            reason = TagJson.string(entry["reason"])!!,
        )
}
