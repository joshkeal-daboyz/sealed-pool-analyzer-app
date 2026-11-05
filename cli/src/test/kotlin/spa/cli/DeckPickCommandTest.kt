package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.deckpick.TunedWeights
import spa.agent.deckpick.Tuning

/**
 * Criterion 1, through the command line: `spa deck-pick` on the committed `syn-sealed.csv`.
 *
 * Its builds: c1/0 (UB, bucket 0.7, two rows) is kept; c2/0 has bucket 0.5; c3/0 has `Bonus
 * Reprint`, which is no card of `syn`. c1/0 names Delver of Secrets and Murderous Rider by their
 * front faces, as 17Lands does. Its UB candidates, with `syn-model.json`'s grades: deck Air
 * Elemental 3.5, Doom Blade 4.0, Gravedigger 2.5, Delver of Secrets 3.0; sideboard Counterspell
 * 4.0, Mind Stone 1.0 (colourless), Murderous Rider 4.0. Out: Grizzly Bears (G), Swamp, Island.
 *
 * 7 copies, N = 4: random 4/7 = 0.5714. Set grades: the 4.0 group (Doom Blade, Counterspell,
 * Murderous Rider) holds 1 deck copy and fills 3 places; Air Elemental fills the 4th: 2 / 4 = 0.5.
 */
class DeckPickCommandTest {
    @TempDir lateinit var temp: Path

    private val csv = resource("syn-sealed.csv")
    private val grades = resource("../grades/syn-model.json")

    private fun resource(name: String): String =
        Path.of(requireNotNull(javaClass.getResource("/deck-pick/$name")).toURI())
            .normalize()
            .toString()

    private fun zeroWeights(): String {
        val file = temp.resolve("zero.json")
        file.writeText("""{"synergy": 0, "curve": 0, "colour": 0, "cap": 1}""")
        return file.toString()
    }

    private fun deckPick(vararg extra: String) =
        runCli(
            "deck-pick",
            csv,
            "--set",
            "syn",
            "--data",
            Fixtures.data,
            "--grades",
            grades,
            *extra,
        )

    @Test
    fun `the report lists the builds, the skips and each ranking's mean overlap`() {
        val run = deckPick("--weights", zeroWeights())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            listOf(
                "set: syn",
                "builds used: 1",
                "skipped, no win-rate bucket: 0",
                "skipped, bucket below 0.60: 1",
                "skipped, not two main colours: 0",
                "skipped, unresolved names: 1",
                "skipped, no choice (N is 0 or every candidate): 0",
                "weights: synergy 0.0, curve 0.0, colour 0.0, cap 1.0",
                "random: 0.5714",
                "set grade: 0.5000",
                "adjusted: 0.5000",
                "adjusted minus set grade: +0.0000",
            ),
            run.stdoutLines(),
        )
    }

    @Test
    fun `without --weights the default weights are used`() {
        val run = deckPick()

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val lines = run.stdoutLines()
        assertTrue("weights: synergy 0.5, curve 0.25, colour 0.25, cap 1.0" in lines, run.stdout)
        assertTrue(lines.any { Regex("""adjusted: \d\.\d{4}""").matches(it) }, run.stdout)
        assertTrue(
            lines.any { Regex("""adjusted minus set grade: [+-]\d\.\d{4}""").matches(it) },
            run.stdout,
        )
    }

    @Test
    fun `a weights file with a comment is read`() {
        val file = temp.resolve("tuned.json")
        file.writeText(
            """{"synergy": 0, "curve": 0, "colour": 0, "cap": 1, "comment": "a tuned file"}"""
        )

        val run = deckPick("--weights", file.toString())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertTrue("adjusted: 0.5000" in run.stdoutLines(), run.stdout)
    }

    @Test
    fun `--tune prints every grid point and writes the best with a comment`() {
        val out = temp.resolve("tuned.json")

        val run =
            runCli(
                "deck-pick",
                csv,
                csv,
                "--set",
                "syn,syn",
                "--data",
                Fixtures.data,
                "--grades",
                "$grades,$grades",
                "--tune",
                "--out",
                out.toString(),
            )

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val lines = run.stdoutLines()
        val grid = lines.filter { it.startsWith("synergy ") }
        assertEquals(Tuning.GRID.size, grid.size, run.stdout)
        val written = TunedWeights.parse(out.readText())
        assertTrue(written in Tuning.GRID)
        val comment = Regex(""""comment": "([^"]*)"""").find(out.readText())!!.groupValues[1]
        assertTrue("syn (1 builds)" in comment, comment)
        assertTrue(lines.any { it.startsWith("best: ") }, run.stdout)
    }

    @Test
    fun `--tune needs one set per file`() {
        val run =
            runCli(
                "deck-pick",
                csv,
                csv,
                "--set",
                "syn",
                "--data",
                Fixtures.data,
                "--tune",
                "--out",
                temp.resolve("x.json").toString(),
            )

        assertEquals(ExitCode.USAGE, run.exit)
        assertTrue(Files.notExists(temp.resolve("x.json")))
    }

    @Test
    fun `a grade file for another set is one error line`() {
        val other = Fixtures.root.resolve("grades/hob.v2.json").toString()

        val run =
            runCli("deck-pick", csv, "--set", "syn", "--data", Fixtures.data, "--grades", other)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
    }

    @Test
    fun `the help text names the command`() {
        assertTrue("deck-pick <sealed-game-data.csv.gz>" in Main.USAGE)
    }
}
