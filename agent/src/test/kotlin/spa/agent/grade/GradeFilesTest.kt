package spa.agent.grade

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class GradeFilesTest {
    private fun resource(name: String): String {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/spa/agent/grades/$name.json")) {
                "missing test resource $name.json"
            }
        return stream.use { it.readBytes().decodeToString() }
    }

    private fun problems(name: String): List<GradeProblem> =
        assertFailsWith<GradeFileException> { GradeFiles.parse(resource(name)) }.problems

    private val sample =
        GradeFile(
            GradeFileHeader(
                setCode = "trk",
                source = GradeSource.WIN_RATE,
                provenance = "17Lands PremierDraft trk 2026-11-20",
                createdAt = "2026-11-21T08:30:00Z",
            ),
            listOf(
                GradeEntry("A Card", "1", "trk", 4.5, 0.9),
                GradeEntry("Another Card", "12a", "sds", 1.0, 0.25),
            ),
        )

    @Test
    fun `the committed example loads with its header and every grade`() {
        val file = GradeFiles.parse(resource("valid"))
        assertEquals("trk", file.header.setCode)
        assertEquals(GradeSource.MODEL, file.header.source)
        assertEquals("claude-opus-5-5, prompt v1, 2026-11-04", file.header.provenance)
        assertEquals("2026-11-04T12:00:00Z", file.header.createdAt)
        assertEquals(4, file.grades.size)
        assertEquals(GradeEntry("He's Dead, Jim", "114", "trk", 3.5, 0.75), file.grades[1])
        assertEquals("sds", file.grades[3].setCode)
    }

    @Test
    fun `writing a loaded file gives back the same text`() {
        val text = resource("valid")
        assertEquals(text, GradeFiles.write(GradeFiles.parse(text)))
    }

    @Test
    fun `a written file loads back equal`() {
        assertEquals(sample, GradeFiles.parse(GradeFiles.write(sample)))
    }

    @Test
    fun `a file on disk round-trips through write and load`(@TempDir dir: Path) {
        val path = dir.resolve("trk.json")
        Files.writeString(path, GradeFiles.write(sample))
        assertEquals(sample, GradeFiles.load(path))
    }

    @Test
    fun `an unknown field fails at that field`() {
        assertEquals(listOf("/grades/1/winRate"), problems("unknown-field").map { it.pointer })
    }

    @Test
    fun `a score of 6 fails at that score`() {
        assertEquals(listOf("/grades/0/score"), problems("score-six").map { it.pointer })
    }

    @Test
    fun `a whole score written without a decimal point is on the scale, and 2_3 is not`() {
        fun withScore(score: String) =
            resource("score-six").replace("\"score\": 6", "\"score\": $score")
        assertEquals(3.0, GradeFiles.parse(withScore("3")).grades[0].score)
        val e = assertFailsWith<GradeFileException> { GradeFiles.parse(withScore("2.3")) }
        assertEquals(listOf("/grades/0/score"), e.problems.map { it.pointer })
    }

    @Test
    fun `a duplicate card fails at the second entry and names the first`() {
        val found = problems("duplicate-card")
        assertEquals(listOf("/grades/2"), found.map { it.pointer })
        assertTrue("/grades/0" in found[0].message, found[0].message)
    }

    @Test
    fun `every problem is listed, each with its pointer, in the exception message`() {
        val e = assertFailsWith<GradeFileException> { GradeFiles.parse(resource("many-problems")) }
        assertEquals(
            setOf(
                "/header/source",
                "/header/provenance",
                "/header/createdAt",
                "/grades/0/score",
                "/grades/0/confidence",
                "/notes",
            ),
            e.problems.map { it.pointer }.toSet(),
        )
        for (problem in e.problems) {
            assertTrue(problem.pointer in e.message.orEmpty(), e.message)
        }
    }

    @Test
    fun `text that is not JSON is one problem on the whole document`() {
        val e = assertFailsWith<GradeFileException> { GradeFiles.parse("{ not json") }
        assertEquals(listOf(""), e.problems.map { it.pointer })
    }

    @Test
    fun `the schema is draft 2020-12 and forbids unknown fields`() {
        val stream = requireNotNull(javaClass.getResourceAsStream(GradeFiles.SCHEMA_RESOURCE))
        val schema = stream.use { it.readBytes().decodeToString() }
        assertTrue("https://json-schema.org/draft/2020-12/schema" in schema)
        assertTrue("\"additionalProperties\": false" in schema)
    }
}
