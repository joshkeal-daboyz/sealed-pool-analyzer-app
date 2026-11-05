package spa.agent.tags.review

import io.github.optimumcode.json.schema.ErrorCollector
import io.github.optimumcode.json.schema.JsonSchema
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-052 criterion 3: what a review file and `fact-suggestions.json` may hold, as the import reads
 * them and as the packet's two schemas describe them.
 */
class ReviewFilesTest {
    @TempDir lateinit var temp: Path

    private val name = "cards-01.review.json"

    private val valid =
        """
        [
          { "id": "syn/2", "action": "add-role", "axis": "flying", "role": "enabler", "strength": 2, "reason": "It flies." },
          { "id": "syn/2", "action": "remove-role", "axis": "flying", "role": "payoff", "reason": "No reward." },
          { "id": "syn/2", "action": "set-strength", "axis": "flying", "role": "enabler", "strength": 3, "reason": "Core." },
          { "id": "syn/5", "action": "add-partner", "partner": "syn/6", "strength": 1, "reason": "Feeds it." },
          { "id": "syn/6", "action": "remove-partner", "partner": "syn/5", "reason": "Unrelated." }
        ]
        """
            .trimIndent()

    private fun problems(text: String) = ReviewFiles.parse(name, text).problems

    @Test
    fun `a valid file reads every correction in order, with its number and strength as written`() {
        val read = ReviewFiles.parse(name, valid)

        assertEquals(emptyList(), read.problems)
        assertEquals(
            listOf(
                Correction(
                    name,
                    1,
                    "syn/2",
                    "add-role",
                    "flying",
                    "enabler",
                    null,
                    "2",
                    "It flies.",
                ),
                Correction(
                    name,
                    2,
                    "syn/2",
                    "remove-role",
                    "flying",
                    "payoff",
                    null,
                    null,
                    "No reward.",
                ),
                Correction(
                    name,
                    3,
                    "syn/2",
                    "set-strength",
                    "flying",
                    "enabler",
                    null,
                    "3",
                    "Core.",
                ),
                Correction(name, 4, "syn/5", "add-partner", null, null, "syn/6", "1", "Feeds it."),
                Correction(
                    name,
                    5,
                    "syn/6",
                    "remove-partner",
                    null,
                    null,
                    "syn/5",
                    null,
                    "Unrelated.",
                ),
            ),
            read.corrections,
        )
    }

    @Test
    fun `an empty list is valid`() {
        assertEquals(ReviewFileResult(emptyList(), emptyList()), ReviewFiles.parse(name, "[]"))
    }

    @Test
    fun `an empty reason is named`() {
        assertEquals(
            listOf(
                "$name: correction 1: the reason is empty",
                "$name: correction 2: the reason is empty",
            ),
            problems(
                """[{ "id": "syn/6", "action": "remove-partner", "partner": "syn/5", "reason": "" },
                    { "id": "syn/6", "action": "remove-partner", "partner": "syn/5", "reason": "  " }]"""
            ),
        )
    }

    @Test
    fun `a malformed correction is named with its position, and the rest still read`() {
        val read =
            ReviewFiles.parse(
                name,
                """[
                  { "id": "syn/1", "action": "swap-role", "reason": "x" },
                  { "id": "syn/1", "action": "add-role", "axis": "flying", "role": "enabler", "reason": "x" },
                  { "id": "syn/1", "action": "remove-partner", "partner": "syn/2", "strength": 1, "reason": "x" },
                  { "id": "syn/1", "action": "add-partner", "partner": "syn/2", "strength": "2", "reason": "x" },
                  { "id": 1, "action": "remove-partner", "partner": "syn/2", "reason": "x" },
                  { "id": "syn/1", "action": "remove-partner", "partner": "syn/2" },
                  "syn/1",
                  { "id": "syn/1", "action": "remove-partner", "partner": "syn/2", "reason": "fine" }
                ]""",
            )

        assertEquals(
            listOf(
                "$name: correction 1: unknown action 'swap-role'; use add-role, remove-role, " +
                    "set-strength, add-partner or remove-partner",
                "$name: correction 2: add-role needs strength",
                "$name: correction 3: unexpected field 'strength' for remove-partner",
                "$name: correction 4: strength must be a number",
                "$name: correction 5: id must be a string",
                "$name: correction 6: reason must be a string",
                "$name: correction 7: not an object",
            ),
            read.problems,
        )
        assertEquals(listOf(8), read.corrections.map { it.number })
    }

    @Test
    fun `a file that is not a JSON array is one line`() {
        assertEquals(1, problems("{").size)
        assertTrue(problems("{").single().startsWith("$name: not JSON"), problems("{").single())
        assertEquals(
            listOf("$name: not a JSON array of corrections"),
            problems("""{ "id": "syn/1" }"""),
        )
    }

    @Test
    fun `a missing review file is named against its card file`() {
        val cardFile = temp.resolve("cards-01.json")
        Files.writeString(cardFile, "[]")

        assertEquals(
            listOf(
                "$name: missing; write one for cards-01.json, an empty array when no card " +
                    "needs a correction"
            ),
            ReviewFiles.read(cardFile).problems,
        )
        Files.writeString(temp.resolve(name), "[]")
        assertEquals(ReviewFileResult(emptyList(), emptyList()), ReviewFiles.read(cardFile))
    }

    @Test
    fun `fact suggestions are optional, read in order, and a malformed one is named`() {
        assertEquals(SuggestionsResult(emptyList(), emptyList()), ReviewFiles.suggestions(temp))

        Files.writeString(
            temp.resolve("fact-suggestions.json"),
            """[{ "fact": "gains-life", "rule": "Its text says you gain life.",
                  "examples": ["syn/9", "syn/10"], "reason": "Several cards gain life." },
                { "fact": "gains-life", "rule": "x", "examples": "syn/9", "reason": "x" },
                { "fact": "", "rule": "x", "examples": ["syn/9"], "reason": "x" }]""",
        )
        val read = ReviewFiles.suggestions(temp)

        assertEquals(
            listOf(
                FactSuggestion(
                    "gains-life",
                    "Its text says you gain life.",
                    listOf("syn/9", "syn/10"),
                    "Several cards gain life.",
                )
            ),
            read.suggestions,
        )
        assertEquals(
            listOf(
                "fact-suggestions.json: suggestion 2: examples must be an array of card ids",
                "fact-suggestions.json: suggestion 3: the fact is empty",
            ),
            read.problems,
        )
    }

    private fun schema(name: String): JsonSchema {
        val stream = requireNotNull(javaClass.getResourceAsStream("/spa/agent/tags/$name"))
        return JsonSchema.fromDefinition(stream.use { it.readBytes().decodeToString() })
    }

    private fun violations(schema: JsonSchema, text: String): List<String> {
        val found = mutableListOf<String>()
        schema.validate(Json.parseToJsonElement(text), ErrorCollector { found += it.message })
        return found
    }

    @Test
    fun `the review schema accepts every action and an empty file, and rejects a bad one`() {
        val schema = schema(TagReviewPackets.REVIEW_SCHEMA)

        assertEquals(emptyList(), violations(schema, valid))
        assertEquals(emptyList(), violations(schema, "[]"))
        listOf(
                """[{ "id": "syn/1", "action": "swap-role", "reason": "x" }]""",
                """[{ "id": "syn/1", "action": "remove-partner", "partner": "syn/2" }]""",
                """[{ "id": "syn/1", "action": "remove-partner", "partner": "syn/2", "strength": 1, "reason": "x" }]""",
                """[{ "id": "syn/1", "action": "add-partner", "partner": "syn/2", "strength": 4, "reason": "x" }]""",
                """[{ "id": "syn/1", "action": "add-role", "axis": "a", "role": "maker", "strength": 1, "reason": "x" }]""",
                """[{ "id": "syn/1", "action": "remove-partner", "partner": "syn/2", "reason": "" }]""",
            )
            .forEach { assertTrue(violations(schema, it).isNotEmpty(), it) }
    }

    @Test
    fun `the fact suggestions schema accepts a suggestion and rejects one without examples`() {
        val schema = schema(TagReviewPackets.SUGGESTIONS_SCHEMA)
        val good =
            """[{ "fact": "gains-life", "rule": "r", "examples": ["syn/9"], "reason": "x" }]"""

        assertEquals(emptyList(), violations(schema, good))
        assertEquals(emptyList(), violations(schema, "[]"))
        assertTrue(violations(schema, good.replace("[\"syn/9\"]", "[]")).isNotEmpty())
        assertTrue(violations(schema, good.replace("\"rule\": \"r\", ", "")).isNotEmpty())
    }
}
