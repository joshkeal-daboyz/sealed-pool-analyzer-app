package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.copyTo
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.grade.GradeFiles
import spa.agent.grade.GradeSource

/**
 * Criteria 3 and 4: `spa grade-import syn` on the committed responses for the `syn` packet, and on
 * each kind of broken response. The packet is generated fresh by `spa grade-packet`; `syn` has 20
 * cards, so it is one card file, `cards-01.json`, with ids `syn/1` to `syn/20`.
 */
class GradeImportCommandTest {
    @TempDir lateinit var temp: Path

    private lateinit var data: Path
    private lateinit var packet: Path
    private val provenance = "claude-fable-5-1 via Claude Code, prompt v1, 2026-11-05"
    private val gradeFile: Path
        get() = data.resolve("grades").resolve("syn.json")

    private fun resource(name: String): Path =
        Path.of(requireNotNull(javaClass.getResource("/grade-import/$name")).toURI())

    private val response: Path
        get() = packet.resolve("cards-01.response.json")

    @BeforeEach
    fun packet() {
        data = Fixtures.synDataDir(temp.resolve("data"))
        packet = temp.resolve("packet")
        val run =
            runCli("grade-packet", "syn", "--out", packet.toString(), "--data", data.toString())
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        resource("syn/cards-01.response.json").copyTo(response)
    }

    private fun args(vararg extra: String) =
        listOf(
            "syn",
            "--responses",
            packet.toString(),
            "--provenance",
            provenance,
            "--data",
            data.toString(),
        ) + extra

    private fun import(): CliRun = runCli("grade-import", *args().toTypedArray())

    /** Replaces [old] with [new] in the response file; [old] must be there. */
    private fun edit(old: String, new: String) {
        val text = response.readText()
        assertTrue(old in text, "fixture has no '$old'")
        Files.writeString(response, text.replace(old, new))
    }

    private fun assertFails(vararg problems: String) {
        val run = import()
        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(problems.map { "spa: $it" }, run.stderrLines())
        assertTrue(Files.notExists(data.resolve("grades")), "nothing is written")
    }

    @Test
    fun `complete valid responses write the expected grade file`() {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val command = GradeImportCommand { Instant.parse("2026-11-05T09:30:00.123456Z") }

        val exit =
            command.run(
                args(),
                PrintStream(out, true, StandardCharsets.UTF_8),
                PrintStream(err, true, StandardCharsets.UTF_8),
            )

        assertEquals(ExitCode.OK, exit, err.toString(StandardCharsets.UTF_8))
        assertEquals(resource("syn-expected.json").readText(), gradeFile.readText())
        val loaded = GradeFiles.load(gradeFile)
        assertEquals(20, loaded.grades.size)
        assertEquals(
            listOf("syn: 20 grades from 1 response file", "wrote $gradeFile"),
            out.toString(StandardCharsets.UTF_8).lines().filter { it.isNotBlank() },
        )
    }

    @Test
    fun `through the command line it writes a loadable MODEL file with the provenance`() {
        val run = import()

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val loaded = GradeFiles.load(gradeFile)
        assertEquals(GradeSource.MODEL, loaded.header.source)
        assertEquals(provenance, loaded.header.provenance)
        assertEquals("syn", loaded.header.setCode)
    }

    @Test
    fun `a missing response file`() {
        Files.delete(response)

        assertFails("cards-01.response.json: missing; grade every card in cards-01.json")
    }

    @Test
    fun `a card id missing`() {
        edit("  { \"id\": \"syn/7\", \"score\": 2.5, \"confidence\": 0.65 },\n", "")

        assertFails("cards-01.response.json: syn/7 is not graded")
    }

    @Test
    fun `an id graded twice`() {
        edit("\"id\": \"syn/7\"", "\"id\": \"syn/8\"")

        assertFails(
            "cards-01.response.json: syn/8 is graded 2 times",
            "cards-01.response.json: syn/7 is not graded",
        )
    }

    @Test
    fun `an unknown id`() {
        edit("\"id\": \"syn/7\"", "\"id\": \"syn/77\"")

        assertFails(
            "cards-01.response.json: syn/77 is not a card in cards-01.json",
            "cards-01.response.json: syn/7 is not graded",
        )
    }

    @Test
    fun `a score of 2_3`() {
        edit("\"id\": \"syn/7\", \"score\": 2.5", "\"id\": \"syn/7\", \"score\": 2.3")

        assertFails("cards-01.response.json: syn/7 score 2.3 is not 0.0 to 5.0 in steps of 0.5")
    }

    @Test
    fun `a confidence of 1_2`() {
        edit("\"confidence\": 0.65", "\"confidence\": 1.2")

        assertFails("cards-01.response.json: syn/7 confidence 1.2 is not in 0 to 1")
    }

    @Test
    fun `a problem leaves an existing grade file untouched`() {
        val grades = Files.createDirectories(data.resolve("grades"))
        Files.writeString(grades.resolve("syn.json"), "old")
        edit("\"confidence\": 0.65", "\"confidence\": 1.2")

        val run = import()

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("old", grades.resolve("syn.json").readText())
    }

    @Test
    fun `without --responses or --provenance it is a usage error`() {
        val noResponses =
            runCli("grade-import", "syn", "--provenance", provenance, "--data", data.toString())
        val noProvenance =
            runCli(
                "grade-import",
                "syn",
                "--responses",
                packet.toString(),
                "--data",
                data.toString(),
            )

        assertEquals(ExitCode.USAGE, noResponses.exit)
        assertEquals("spa: --responses <dir> is required", noResponses.stderrLines().first())
        assertEquals(ExitCode.USAGE, noProvenance.exit)
        assertEquals("spa: --provenance <text> is required", noProvenance.stderrLines().first())
    }

    @Test
    fun `a responses directory that does not exist prints one line and exits 1`() {
        val missing = temp.resolve("nowhere")

        val run =
            runCli(
                "grade-import",
                "syn",
                "--responses",
                missing.toString(),
                "--provenance",
                provenance,
                "--data",
                data.toString(),
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(listOf("spa: responses directory not found: $missing"), run.stderrLines())
    }
}
