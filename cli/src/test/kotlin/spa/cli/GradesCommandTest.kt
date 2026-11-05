package spa.cli

import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-034 criterion 4: `spa grades <pool-file> --set <code> [--data <dir>]`.
 *
 * Expected lines for trk-manabox-01 were read from the committed `fixtures/grades/trk.json`: the
 * pool's highest grades are He's Dead, Jim (trk 114) and Plasma Cascade (trk 164), both 4.0 with
 * confidence 0.9, so the name breaks the tie. The three basic lands have no grade and come last, by
 * name: Forest, Mountain, Swamp (3 copies).
 */
class GradesCommandTest {
    @TempDir lateinit var temp: Path

    private fun resource(name: String): Path =
        Path.of(requireNotNull(javaClass.getResource("/grades/$name")).toURI())

    private fun trkGrades() =
        runCli(
            "grades",
            Fixtures.pool("trk-manabox-01.csv"),
            "--set",
            "trk",
            "--data",
            Fixtures.data,
        )

    @Test
    fun `trk-manabox-01 lists all 71 entries, graded first, and exits 0`() {
        val run = trkGrades()
        val lines = run.stdoutLines()

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("", run.stderr)
        assertEquals(71, lines.size)
        assertEquals("4.0  A−  0.90  MODEL     1x He's Dead, Jim (trk 114)", lines.first())
        assertEquals("-    -   -     none      3x Swamp (trk 309)", lines.last())
        assertEquals(
            listOf(
                "-    -   -     none      1x Forest (trk 311)",
                "-    -   -     none      1x Mountain (trk 310)",
            ),
            lines.subList(68, 70),
        )
    }

    @Test
    fun `the 68 graded entries are MODEL grades, highest first`() {
        val graded = trkGrades().stdoutLines().take(68)

        assertEquals(68, graded.size)
        assertTrue(graded.all { it.substring(16, 24) == "MODEL   " }, graded.joinToString("\n"))
        val scores = graded.map { it.substring(0, 3).toDouble() }
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun `a win-rate grade is preferred over a model grade, and unresolved lines go to stderr`() {
        val data = synData(withWinRates = true)
        val pool = writeFile(temp, "pool.txt", "2 Air Elemental\nNot A Card\n")

        val run = runCli("grades", pool, "--set", "syn", "--data", data.toString())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(listOf("2.5  C+  0.80  WIN_RATE  2x Air Elemental (syn 3)"), run.stdoutLines())
        assertEquals(listOf("spa: line 2 unresolved (unknown-name): Not A Card"), run.stderrLines())
    }

    @Test
    fun `with only a model file, a card it leaves out is listed ungraded after the graded ones`() {
        val data = synData(withWinRates = false)
        val pool = writeFile(temp, "pool.txt", "Evolving Wilds\n2 Air Elemental\n")

        val run = runCli("grades", pool, "--set", "syn", "--data", data.toString())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            listOf(
                "3.5  B+  0.60  MODEL     2x Air Elemental (syn 3)",
                "-    -   -     none      1x Evolving Wilds (syn 19)",
            ),
            run.stdoutLines(),
        )
    }

    @Test
    fun `a set with no grade file prints one line naming it and exits 1`() {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        val pool = writeFile(temp, "pool.txt", "Air Elemental\n")

        val run = runCli("grades", pool, "--set", "syn", "--data", data.toString())

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(
            listOf(
                "spa: no grade file for set 'syn': ${data.resolve("grades").resolve("syn.json")}"
            ),
            run.stderrLines(),
        )
    }

    @Test
    fun `a missing pool file exits 1, a missing --set exits 1, and no pool file exits 2`() {
        val missing = temp.resolve("none.csv").toString()

        val run = runCli("grades", missing, "--set", "trk", "--data", Fixtures.data)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(listOf("spa: pool file not found: $missing"), run.stderrLines())
        assertEquals(ExitCode.ERROR, runCli("grades", Fixtures.pool("trk-manabox-01.csv")).exit)
        assertEquals(ExitCode.USAGE, runCli("grades", "--set", "trk").exit)
    }

    @Test
    fun `the letter labels follow the grade sheet's table`() {
        val table =
            mapOf(
                5.0 to "A+",
                4.5 to "A",
                4.0 to "A−",
                3.5 to "B+",
                3.0 to "B",
                2.5 to "C+",
                2.0 to "C",
                1.5 to "C−",
                1.0 to "D",
                0.5 to "D−",
                0.0 to "F",
            )

        table.forEach { (score, label) ->
            assertEquals(label, GradesCommand.label(score), "$score")
        }
    }

    @Test
    fun `help lists the grades command`() {
        assertTrue("grades <pool-file> --set <code> [--data <dir>]" in runCli("--help").stdout)
    }

    private fun synData(withWinRates: Boolean): Path {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        resource("syn-model.json")
            .copyTo(data.resolve("grades").createDirectories().resolve("syn.json"))
        if (withWinRates) {
            resource("syn-winrates.json")
                .copyTo(data.resolve("winrates").createDirectories().resolve("syn.json"))
        }
        return data
    }
}
