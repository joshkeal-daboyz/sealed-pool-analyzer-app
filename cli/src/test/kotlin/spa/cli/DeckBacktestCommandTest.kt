package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.deckbacktest.BuildOutcome
import spa.agent.deckbacktest.Measures
import spa.agent.deckbacktest.RateMeasure

/**
 * SPA-046 criteria 3 and 6: `spa deck-backtest` on set `syn` with a hand-built Sealed file (see
 * [BacktestSyn]): four kept builds (two colours, two plus a splash, three colours, and one whose
 * pool cannot fill its own colours) and five the reader skips.
 */
class DeckBacktestCommandTest {
    @TempDir lateinit var temp: Path

    private val data by lazy { BacktestSyn.data(temp.resolve("data")) }

    private fun file(vararg rows: String, name: String = "sealed.csv"): String =
        writeFile(temp, name, BacktestSyn.csv(*rows))

    private val all by lazy { file(*(BacktestSyn.KEPT + BacktestSyn.SKIPPED).toTypedArray()) }

    private fun backtest(file: String, vararg extra: String) =
        runCli("deck-backtest", file, "--set", "syn", "--data", data.toString(), *extra)

    /** The lines without the time line, which is the only one that changes between runs. */
    private fun CliRun.numbers(): List<String> = stdoutLines().filterNot { it.startsWith("time ") }

    private fun assertOneLine(run: CliRun, fragment: String) {
        assertEquals(1, run.exit, run.stderr)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertEquals("", run.stdout)
        assertTrue(fragment in run.stderr, run.stderr)
    }

    private val percent = """(?:-|\d{1,3}\.\d)"""
    private val mean = """(?:-|\d+\.\d\d)"""
    private val groupLine =
        Regex(
            """^(builder|baseline) (overall|two|splash|multi) n (\d+) colour $percent top3 $percent """ +
                """splash $percent overlap $mean of $mean lands $percent$"""
        )

    @Test
    fun `it prints the read line, the used line, the eight group lines and the time line`() {
        val run = backtest(all)

        assertEquals(0, run.exit, run.stderr)
        val lines = run.stdoutLines()
        assertEquals(11, lines.size, run.stdout)
        assertEquals(
            "read 8 strong builds: two 4, splash 1, multi 1; " +
                "skipped one-colour 1, five-colour 1, unresolved 1, no-choice 1",
            lines[0],
        )
        assertEquals("used 4 builds, unbuildable 1", lines[1])
        val expected =
            listOf("builder", "baseline").flatMap { b ->
                listOf("overall", "two", "splash", "multi").map { b to it }
            }
        val groups = lines.subList(2, 10).map { requireNotNull(groupLine.matchEntire(it)) { it } }
        assertEquals(expected, groups.map { it.groupValues[1] to it.groupValues[2] })
        assertEquals(listOf(4, 2, 1, 1, 4, 2, 1, 1), groups.map { it.groupValues[3].toInt() })
        assertTrue(Regex("""^time \d+\.\d+ s, \d+\.\d+ builds/s$""").matches(lines[10]), lines[10])
    }

    @Test
    fun `the numbers are the builder's and the baseline's on the hand-built builds`() {
        val run = backtest(all)

        assertEquals(0, run.exit, run.stderr)
        assertEquals(PINNED, run.numbers().drop(2))
    }

    @Test
    fun `a limit takes builds spread evenly and the same ones every run`() {
        val first = backtest(all, "--limit", "2")
        val again = backtest(all, "--limit", "2")
        val picked = file(BacktestSyn.TWO, BacktestSyn.MULTI, name = "picked.csv")
        val expected = backtest(picked)

        assertEquals(0, first.exit, first.stderr)
        assertEquals("used 2 builds, unbuildable 0", first.stdoutLines()[1])
        assertEquals(first.numbers(), again.numbers())
        assertEquals(expected.numbers().drop(1), first.numbers().drop(1))
        assertEquals(
            "used 1 builds, unbuildable 0",
            backtest(all, "--limit", "1").stdoutLines()[1],
        )
        assertEquals(
            backtest(all).numbers().drop(1),
            backtest(all, "--limit", "99").numbers().drop(1),
        )
    }

    @Test
    fun `one thread and two threads print identical numbers`() {
        val one = backtest(all, "--threads", "1")
        val two = backtest(all, "--threads", "2")
        val default = backtest(all)

        assertEquals(0, one.exit, one.stderr)
        assertEquals(one.numbers(), two.numbers())
        assertEquals(one.numbers(), default.numbers())
    }

    @Test
    fun `a gzipped file reads the same as a plain one`() {
        val plain = backtest(all)
        val zipped = Path.of(temp.toString(), "sealed.csv.gz")
        GZIPOutputStream(Files.newOutputStream(zipped)).use {
            it.write(Files.readAllBytes(Path.of(all)))
        }

        assertEquals(plain.numbers(), backtest(zipped.toString()).numbers())
    }

    @Test
    fun `bad inputs are one line and exit 1`() {
        val bad = writeFile(temp, "bad.json", "{ not json")

        assertOneLine(backtest(temp.resolve("missing.csv").toString()), "missing.csv")
        assertOneLine(
            runCli("deck-backtest", all, "--set", "nope", "--data", data.toString()),
            "nope",
        )
        assertOneLine(backtest(all, "--limit", "x"), "--limit")
        assertOneLine(backtest(all, "--limit", "0"), "--limit")
        assertOneLine(backtest(all, "--limit", "-3"), "--limit")
        assertOneLine(backtest(all, "--threads", "0"), "--threads")
        assertOneLine(backtest(all, "--threads", "two"), "--threads")
        assertOneLine(backtest(all, "--weights", bad), "weights file")
        assertOneLine(backtest(all, "--baseline", bad), "weights file")
        assertOneLine(backtest(all, "--weights", temp.resolve("none.json").toString()), "none.json")
    }

    @Test
    fun `an empty file or one without the columns is one line and exit 1`() {
        assertOneLine(backtest(writeFile(temp, "empty.csv", "")), "empty.csv")
        assertOneLine(backtest(writeFile(temp, "cols.csv", "a,b\n1,2\n")), "cols.csv")
    }

    /** A test-only measure: every build hits. */
    private object Always : RateMeasure("always") {
        override fun hit(outcome: BuildOutcome): Boolean = true
    }

    @Test
    fun `a measure added to the list shows in the output with no other change`() {
        val out = ByteArrayOutputStream()
        val stdout = PrintStream(out, true, StandardCharsets.UTF_8)

        val exit =
            DeckBacktestCommand.run(
                listOf(all, "--set", "syn", "--data", data.toString()),
                stdout,
                Measures.ALL + Always,
            )

        assertEquals(0, exit)
        val groups =
            out.toString(StandardCharsets.UTF_8).lines().filter {
                it.startsWith("builder ") || it.startsWith("baseline ")
            }
        assertEquals(8, groups.size)
        groups.forEach { line ->
            val n = Regex(""" n (\d+) """).find(line)?.groupValues?.get(1)
            val rate = if (n == "0") "-" else "100.0"
            assertTrue(line.endsWith(" always $rate"), line)
        }
    }

    private companion object {
        /** The group lines of [BacktestSyn.KEPT] with the test weights, builder then baseline. */
        val PINNED: List<String> =
            listOf(
                "builder overall n 4 colour 50.0 top3 50.0 splash 75.0 overlap 21.33 of 22.67 lands 100.0",
                "builder two n 2 colour 50.0 top3 50.0 splash 100.0 overlap 21.00 of 23.00 lands 100.0",
                "builder splash n 1 colour 100.0 top3 100.0 splash 0.0 overlap 23.00 of 23.00 lands 100.0",
                "builder multi n 1 colour 0.0 top3 0.0 splash 100.0 overlap 20.00 of 22.00 lands 100.0",
                "baseline overall n 4 colour 50.0 top3 50.0 splash 75.0 overlap 21.00 of 23.00 lands 100.0",
                "baseline two n 2 colour 50.0 top3 50.0 splash 100.0 overlap 20.50 of 23.50 lands 100.0",
                "baseline splash n 1 colour 100.0 top3 100.0 splash 0.0 overlap 23.00 of 23.00 lands 100.0",
                "baseline multi n 1 colour 0.0 top3 0.0 splash 100.0 overlap 20.00 of 22.00 lands 100.0",
            )
    }
}
