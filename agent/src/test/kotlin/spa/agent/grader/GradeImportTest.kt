package spa.agent.grader

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import spa.agent.grade.GradeEntry
import spa.agent.grade.GradeFileHeader
import spa.agent.grade.GradeFiles
import spa.agent.grade.GradeSource
import spa.agent.grader.GraderTestCards.brief
import spa.agent.grader.GraderTestCards.numbered

/**
 * Criteria 3 and 4: importing response files. The packet is set `xyz`'s five cards, three per file:
 * `cards-01.json` holds xyz/1 to xyz/3 and `cards-02.json` holds xyz/4 and xyz/5.
 */
class GradeImportTest {
    @TempDir lateinit var temp: Path

    private val index = numbered(5)
    private val provenance = "claude-fable-5-1 via Claude Code, prompt v1, 2026-11-05"
    private val createdAt = "2026-11-05T09:00:00Z"

    private fun entry(id: Int, score: String = "3.0", confidence: String = "0.5") =
        """{"id": "xyz/$id", "score": $score, "confidence": $confidence}"""

    private fun respond(file: Int, vararg entries: String) {
        Files.writeString(
            temp.resolve("cards-0$file.response.json"),
            entries.joinToString(",\n", "[\n", "\n]\n"),
        )
    }

    private fun run() = GradeImport.run(temp, index, "xyz", provenance, createdAt)

    private fun problems(): List<String> {
        val result = run()
        assertNull(result.file)
        return result.problems
    }

    @BeforeTest
    fun packet() {
        GradingPackets.write(GradingPackets.build(index, brief(), cardsPerFile = 3), temp)
        respond(1, entry(1, "4.5", "0.8"), entry(2, "3", "1"), entry(3, "0.0", "0"))
        respond(2, entry(4, "2.5", "0.35"), entry(5, "5", "0.6"))
    }

    @Test
    fun `complete valid responses give a MODEL grade file in packet order`() {
        val result = run()

        assertEquals(emptyList(), result.problems)
        val file = assertNotNull(result.file)
        assertEquals(GradeFileHeader("xyz", GradeSource.MODEL, provenance, createdAt), file.header)
        assertEquals(
            listOf(
                GradeEntry("Card 1", "1", "xyz", 4.5, 0.8),
                GradeEntry("Card 2", "2", "xyz", 3.0, 1.0),
                GradeEntry("Card 3", "3", "xyz", 0.0, 0.0),
                GradeEntry("Card 4", "4", "xyz", 2.5, 0.35),
                GradeEntry("Card 5", "5", "xyz", 5.0, 0.6),
            ),
            file.grades,
        )
        assertEquals(file, GradeFiles.parse(GradeFiles.write(file)))
    }

    @Test
    fun `a missing response file is named`() {
        temp.resolve("cards-02.response.json").deleteExisting()

        assertEquals(
            listOf("cards-02.response.json: missing; grade every card in cards-02.json"),
            problems(),
        )
    }

    @Test
    fun `a card id missing is named`() {
        respond(1, entry(1), entry(3))

        assertEquals(listOf("cards-01.response.json: xyz/2 is not graded"), problems())
    }

    @Test
    fun `an id graded twice is named`() {
        respond(2, entry(4), entry(5), entry(4, "2.0"))

        assertEquals(listOf("cards-02.response.json: xyz/4 is graded 2 times"), problems())
    }

    @Test
    fun `an unknown id is named, including an id of another card file`() {
        respond(2, entry(4), entry(5), entry(9), entry(1))

        assertEquals(
            listOf(
                "cards-02.response.json: xyz/9 is not a card in cards-02.json",
                "cards-02.response.json: xyz/1 is not a card in cards-02.json",
            ),
            problems(),
        )
    }

    @Test
    fun `a score off the scale is named`() {
        respond(1, entry(1, "2.3"), entry(2, "5.5"), entry(3, "-0.5"))

        assertEquals(
            listOf(
                "cards-01.response.json: xyz/1 score 2.3 is not 0.0 to 5.0 in steps of 0.5",
                "cards-01.response.json: xyz/2 score 5.5 is not 0.0 to 5.0 in steps of 0.5",
                "cards-01.response.json: xyz/3 score -0.5 is not 0.0 to 5.0 in steps of 0.5",
            ),
            problems(),
        )
    }

    @Test
    fun `a confidence outside 0 to 1 is named`() {
        respond(1, entry(1, confidence = "1.2"), entry(2), entry(3, confidence = "-0.1"))

        assertEquals(
            listOf(
                "cards-01.response.json: xyz/1 confidence 1.2 is not in 0 to 1",
                "cards-01.response.json: xyz/3 confidence -0.1 is not in 0 to 1",
            ),
            problems(),
        )
    }

    @Test
    fun `every problem in every file is listed, one line each`() {
        respond(1, entry(1, "2.3"), entry(1), entry(7))
        temp.resolve("cards-02.response.json").deleteExisting()

        assertEquals(
            listOf(
                "cards-01.response.json: xyz/1 score 2.3 is not 0.0 to 5.0 in steps of 0.5",
                "cards-01.response.json: xyz/7 is not a card in cards-01.json",
                "cards-01.response.json: xyz/1 is graded 2 times",
                "cards-01.response.json: xyz/2 is not graded",
                "cards-01.response.json: xyz/3 is not graded",
                "cards-02.response.json: missing; grade every card in cards-02.json",
            ),
            problems(),
        )
    }

    @Test
    fun `a response that is not a JSON array is one problem`() {
        Files.writeString(temp.resolve("cards-01.response.json"), "{\"id\": 1}")

        assertEquals(
            listOf("cards-01.response.json: not a JSON array of {id, score, confidence}"),
            problems(),
        )
    }

    @Test
    fun `a response that is not JSON is one problem`() {
        Files.writeString(temp.resolve("cards-01.response.json"), "[{")

        val found = problems()

        assertEquals(1, found.size, found.toString())
        assertEquals(true, found[0].startsWith("cards-01.response.json: not JSON: "), found[0])
    }

    @Test
    fun `a malformed entry is named by its position`() {
        respond(
            1,
            entry(1),
            entry(2),
            entry(3),
            "\"text\"",
            """{"score": 3.0, "confidence": 0.5}""",
            """{"id": "xyz/1", "score": "high", "confidence": 0.5}""",
            """{"id": "xyz/2", "score": 3.0}""",
            """{"id": "xyz/3", "score": 3.0, "confidence": 0.5, "why": "good"}""",
        )

        assertEquals(
            listOf(
                "cards-01.response.json: entry 4: not an object",
                "cards-01.response.json: entry 5: id must be a string",
                "cards-01.response.json: entry 6: score must be a number",
                "cards-01.response.json: entry 7: confidence must be a number",
                "cards-01.response.json: entry 8: unexpected field 'why'",
            ),
            problems(),
        )
    }

    @Test
    fun `a packet directory with no card files is one problem`() {
        val empty = Files.createDirectories(temp.resolve("empty"))

        val result = GradeImport.run(empty, index, "xyz", provenance, createdAt)

        assertNull(result.file)
        assertEquals(
            listOf("$empty: no cards-NN.json files; run 'spa grade-packet xyz' first"),
            result.problems,
        )
    }

    @Test
    fun `card files that do not hold the sealed pool are named`() {
        val other = numbered(6)

        val result = GradeImport.run(temp, other, "xyz", provenance, createdAt)

        assertEquals(
            listOf(
                "xyz/6 is in the sealed pool but in no card file; run 'spa grade-packet xyz' again"
            ),
            result.problems,
        )
        val smaller = GradeImport.run(temp, numbered(4), "xyz", provenance, createdAt)
        assertEquals(
            listOf(
                "cards-02.json: xyz/5 is not in the sealed pool of xyz; " +
                    "run 'spa grade-packet xyz' again"
            ),
            smaller.problems,
        )
    }

    @Test
    fun `a card file that is not a list of cards is one problem`() {
        Files.writeString(temp.resolve("cards-02.json"), "{}")

        val found = problems()

        assertEquals(listOf("cards-02.json: not a card file"), found.take(1))
    }

    @Test
    fun `a grade file that would break its schema is reported, not returned`() {
        val result = GradeImport.run(temp, index, "xyz", " ", createdAt)

        assertNull(result.file)
        assertEquals(1, result.problems.size, result.problems.toString())
        assertEquals(true, result.problems[0].startsWith("grade file: /header/provenance"))
    }
}
