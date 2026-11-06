package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import spa.agent.build.DeckWeights
import spa.agent.deckbacktest.tune.DeckGrid

/**
 * SPA-060 criteria 2 and 4: the default grid file, and `spa deck-tune` on set `syn` with a
 * hand-built Sealed file (see [BacktestSyn]): four kept builds and five the reader skips.
 */
class DeckTuneCommandTest {
    @TempDir lateinit var temp: Path

    private val data by lazy { BacktestSyn.data(temp.resolve("data")) }

    private val all by lazy {
        writeFile(
            temp,
            "sealed.csv",
            BacktestSyn.csv(*(BacktestSyn.KEPT + BacktestSyn.SKIPPED).toTypedArray()),
        )
    }

    private val grid by lazy {
        writeFile(
            temp,
            "grid.json",
            """{"weights.creatures": [0, 1, 2], "params.creatures.minCreatures": [10, 14]}""",
        )
    }

    private val out by lazy { temp.resolve("tuned.json") }

    private fun tune(file: String = all, vararg extra: String) =
        runCli(
            "deck-tune",
            file,
            "--set",
            "syn",
            "--data",
            data.toString(),
            *(if ("--grid" in extra) emptyArray() else arrayOf("--grid", grid)),
            "--out",
            out.toString(),
            *extra,
        )

    private fun assertOneLine(run: CliRun, fragment: String) {
        assertEquals(1, run.exit, run.stderr)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertEquals("", run.stdout)
        assertTrue(fragment in run.stderr, run.stderr)
    }

    private val evaluationLine =
        Regex("""^round (\d+) (\S+) (?:(\S+) )?objective (-?\d+\.\d{6})$""")

    private fun evaluations(run: CliRun) =
        run.stdoutLines().filter { it.startsWith("round ") }.map {
            requireNotNull(evaluationLine.matchEntire(it)) { it }
        }

    /** The objective of the builder's overall line `colour <c> ... overlap <x> of <y>`. */
    private fun objectiveOf(line: String): Double {
        val colour = Regex("""colour (\d+\.\d)""").find(line)!!.groupValues[1].toDouble()
        val overlap = Regex("""overlap (\d+\.\d\d) of (\d+\.\d\d)""").find(line)!!.groupValues
        return overlap[1].toDouble() / overlap[2].toDouble() + colour / 100
    }

    private fun backtestBuilder(weights: String): List<String> =
        runCli("deck-backtest", all, "--set", "syn", "--data", data.toString(), "--weights", weights)
            .stdoutLines()
            .filter { it.startsWith("builder ") }

    @Test
    fun `the default grid has the knobs of the table and its start file check passes`() {
        val weights = Fixtures.root.resolve("weights")
        val parsed =
            DeckGrid.parse(
                Files.readString(weights.resolve("deck-grid.json")),
                Files.readString(weights.resolve("deck-default.json")),
            )

        val four = listOf(0.0, 0.5, 1.0, 2.0)
        val expected =
            listOf(
                "weights.curve" to four,
                "weights.creatures" to four,
                "weights.removal" to four,
                "weights.castability" to four,
                "weights.fixing" to four,
                "weights.synergy" to listOf(0.0, 0.05, 0.1, 0.2, 0.4),
                "params.creatures.minCreatures" to listOf(12.0, 13.0, 14.0, 15.0),
                "params.removal.cap" to listOf(4.0, 6.0, 8.0),
                "lands.lowCurve" to listOf(2.4, 2.6, 2.8),
                "lands.highCurve" to listOf(3.2, 3.4, 3.6),
                "splash.maxCards" to listOf(2.0, 3.0, 4.0),
                "splash.maxPips" to listOf(1.0, 2.0),
                "configs.fourColour.minFixing" to listOf(2.0, 4.0, 6.0),
            )
        assertEquals(expected, parsed.knobs.map { it.path to it.values })
    }

    @Test
    fun `it prints one line per evaluation, then the final block, and writes the weights`() {
        val run = tune(all, "--rounds", "1")

        assertEquals(0, run.exit, run.stderr)
        val lines = run.stdoutLines()
        assertEquals("syn: used 4 builds", lines.first { it.startsWith("syn: used") })

        // The start, then creatures 0 and 2, then minCreatures 10.
        val steps = evaluations(run)
        assertEquals(
            listOf("0 start", "1 weights.creatures", "1 weights.creatures", "1 params.creatures.minCreatures"),
            steps.map { "${it.groupValues[1]} ${it.groupValues[2]}" },
        )
        assertEquals(listOf("0", "2", "10"), steps.drop(1).map { it.groupValues[3] })

        val block = lines.subList(lines.indexOfFirst { it.startsWith("start objective") }, lines.size)
        assertTrue(Regex("""^start objective \d+\.\d{6}$""").matches(block[0]), block[0])
        assertTrue(Regex("""^final objective \d+\.\d{6}$""").matches(block[1]), block[1])
        assertEquals("syn start", block[2])
        assertEquals(listOf("overall", "two", "splash", "multi"), block.subList(3, 7).map { it.split(' ')[1] })
        assertEquals("syn final", block[7])
        assertEquals(listOf("overall", "two", "splash", "multi"), block.subList(8, 12).map { it.split(' ')[1] })
        assertTrue(Regex("""^evaluations 4, time \d+\.\d s$""").matches(block[12]), block[12])
        assertEquals(13, block.size)
        assertTrue(out.toFile().isFile)
    }

    @Test
    fun `the written file loads as deck weights and scores no lower than the start`() {
        val run = tune(all)

        assertEquals(0, run.exit, run.stderr)
        DeckWeights.parse(Files.readString(out))
        val start = Regex("""start objective (\S+)""").find(run.stdout)!!.groupValues[1].toDouble()
        val final = Regex("""final objective (\S+)""").find(run.stdout)!!.groupValues[1].toDouble()
        assertTrue(final >= start, "final $final, start $start")

        // The block's lines are what deck-backtest reports for the same files.
        val startFile = data.resolve("weights/deck-default.json").toString()
        val lines = run.stdoutLines()
        val first = lines.indexOf("syn start") + 1
        val second = lines.indexOf("syn final") + 1
        assertEquals(backtestBuilder(startFile), lines.subList(first, first + 4))
        assertEquals(backtestBuilder(out.toString()), lines.subList(second, second + 4))
        assertTrue(
            objectiveOf(backtestBuilder(out.toString())[0]) >=
                objectiveOf(backtestBuilder(startFile)[0]) - 0.01
        )
    }

    @Test
    fun `a run twice gives the same output and the same file`() {
        val first = tune(all)
        val written = Files.readString(out)
        val second = tune(all)

        fun numbers(run: CliRun) = run.stdoutLines().map { it.replace(Regex("""time \d+\.\d s"""), "") }
        assertEquals(numbers(first), numbers(second))
        assertEquals(written, Files.readString(out))
    }

    @Test
    fun `one thread and two threads print identical numbers`() {
        fun numbers(run: CliRun) = run.stdoutLines().map { it.replace(Regex("""time \d+\.\d s"""), "") }

        assertEquals(numbers(tune(all, "--threads", "1")), numbers(tune(all, "--threads", "2")))
    }

    @Test
    fun `the weights are written before the first evaluation ends the run, and with no move too`() {
        // A one-value grid: nothing to try. The start weights are still written.
        val still = writeFile(temp, "still.json", """{"weights.creatures": [1]}""")
        val run = runCli(
            "deck-tune", all, "--set", "syn", "--data", data.toString(),
            "--grid", still, "--out", out.toString(),
        )

        assertEquals(0, run.exit, run.stderr)
        assertEquals(1, evaluations(run).size)
        DeckWeights.parse(Files.readString(out))
    }

    @Test
    fun `a limit keeps that many builds per set`() {
        val run = tune(all, "--limit", "2", "--rounds", "1")

        assertEquals(0, run.exit, run.stderr)
        assertTrue("syn: used 2 builds" in run.stdoutLines())
    }

    @Test
    fun `the held-out sets are refused`() {
        for (set in listOf("fra", "trk", "FRA")) {
            val run =
                runCli("deck-tune", all, "--set", set, "--out", out.toString(), "--data", data.toString())

            assertOneLine(run, set.lowercase())
            assertTrue("held out" in run.stderr, run.stderr)
        }
        val mixed =
            runCli("deck-tune", all, all, "--set", "syn,fra", "--out", out.toString(), "--data", data.toString())
        assertOneLine(mixed, "fra")
        assertTrue(Files.notExists(out))
    }

    @Test
    fun `a set given twice, a missing set and a missing out are one line each`() {
        assertOneLine(
            runCli("deck-tune", all, all, "--set", "syn,syn", "--out", out.toString(), "--data", data.toString()),
            "syn",
        )
        assertOneLine(runCli("deck-tune", all, "--out", out.toString(), "--data", data.toString()), "--set")
        assertOneLine(runCli("deck-tune", all, "--set", "syn", "--data", data.toString()), "--out")
    }

    @Test
    fun `a missing Sealed file is one line`() {
        val run = tune(temp.resolve("nope.csv.gz").toString())

        assertOneLine(run, "nope.csv.gz")
        assertTrue(Files.notExists(out))
    }

    @Test
    fun `a number of files that differs from the number of sets is one line`() {
        assertOneLine(tune(all, all), "1 set")
        assertOneLine(
            runCli("deck-tune", all, "--set", "syn,hob", "--out", out.toString(), "--data", data.toString()),
            "2 sets",
        )
        assertOneLine(
            runCli("deck-tune", "--set", "syn", "--out", out.toString(), "--data", data.toString()),
            "0 files",
        )
    }

    @Test
    fun `a bad number is one line naming the option`() {
        for ((option, value) in
            listOf(
                "--limit" to "0",
                "--limit" to "x",
                "--threads" to "0",
                "--threads" to "1.5",
                "--rounds" to "0",
                "--rounds" to "-1",
                "--rounds" to "many",
            )) {
            val run = tune(all, option, value)

            assertOneLine(run, option)
        }
    }

    @Test
    fun `a bad grid or start file is one line naming the file`() {
        val badGrid = writeFile(temp, "bad-grid.json", """{"weights.bogus": [1]}""")
        val withBad =
            runCli(
                "deck-tune", all, "--set", "syn", "--data", data.toString(),
                "--grid", badGrid, "--out", out.toString(),
            )
        assertOneLine(withBad, "bad-grid.json")
        assertTrue("weights.bogus" in withBad.stderr, withBad.stderr)

        val badStart = writeFile(temp, "bad-start.json", """{"version": 2}""")
        assertOneLine(tune(all, "--start", badStart), "bad-start.json")
        assertOneLine(tune(all, "--start", temp.resolve("none.json").toString()), "none.json")
        assertOneLine(tune(all, "--grid", temp.resolve("none-grid.json").toString()), "none-grid.json")
    }

    @Test
    fun `a failure inside a worker thread is one line and exit 1`() {
        // Synergy above 0 needs the tag file, which the data directory lacks: the builder
        // asks for it on a worker thread.
        val synergy =
            writeFile(
                temp,
                "synergy.json",
                BacktestSyn.WEIGHTS.replace("\"synergy\": 0", "\"synergy\": 0.1"),
            )

        val run = tune(all, "--start", synergy)

        // The Sealed file was read before the search began, so stdout is not empty.
        assertEquals(1, run.exit, run.stderr)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("no tag file" in run.stderr, run.stderr)
    }

    @Test
    fun `a worker failure is reached through its causes, and any other failure is named`() {
        val cli = CliFailure("no tag file for set 'syn'", ExitCode.ERROR)

        assertSame(cli, DeckTuneCommand.oneLine(ExecutionException(RuntimeException("x", cli))))
        val other = DeckTuneCommand.oneLine(ExecutionException(IllegalStateException("boom\nmore")))
        assertEquals(ExitCode.ERROR, other.exitCode)
        assertEquals("deck-tune failed: IllegalStateException: boom", other.message)
    }
}
