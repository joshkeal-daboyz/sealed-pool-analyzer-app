package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.deckbacktest.score.ScoreStatistic
import spa.agent.deckbacktest.score.ScoreStatistics
import spa.agent.deckbacktest.score.ScoredBuild

/**
 * SPA-061 criteria 4 and 6: `spa score-backtest` on set `syn` with hand-built Sealed files (see
 * [ScoreSyn]).
 */
class ScoreBacktestCommandTest {
    @TempDir lateinit var temp: Path

    private val data by lazy { BacktestSyn.data(temp.resolve("data")) }

    private val weights by lazy { data.resolve("weights/deck-default.json").toString() }

    private fun file(vararg rows: String, name: String = "sealed.csv"): String =
        writeFile(temp, name, BacktestSyn.csv(*rows))

    private val mixed by lazy { file(*ScoreSyn.MIXED.toTypedArray()) }

    private fun score(file: String, vararg extra: String): CliRun {
        val given = extra.toList()
        val withWeights = if ("--weights" in given) given else given + listOf("--weights", weights)
        return runCli(
            "score-backtest",
            file,
            "--set",
            "syn",
            "--data",
            data.toString(),
            *withWeights.toTypedArray(),
        )
    }

    private fun CliRun.numbers(): List<String> = stdoutLines().filterNot { it.startsWith("time ") }

    private fun assertOneLine(run: CliRun, fragment: String) {
        assertEquals(1, run.exit, run.stderr)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertEquals("", run.stdout)
        assertTrue(fragment in run.stderr, run.stderr)
    }

    private val stats =
        """spearman -?\d\.\d{3} se \d\.\d{3} top-quarter \d\.\d{3} """ +
            """bottom-quarter \d\.\d{3} gap -?\d\.\d{3}"""

    @Test
    fun `it prints the five lines in order`() {
        val run = score(mixed)

        assertEquals(0, run.exit, run.stderr)
        val lines = run.stdoutLines()
        assertEquals(5, lines.size, run.stdout)
        assertEquals(
            "read 11 builds with a bucket; skipped one-colour 1, five-colour 1, unresolved 1, " +
                "no-choice 1, no-config 1, not-40 1, off-colour 1",
            lines[0],
        )
        assertEquals("scored 4 builds", lines[1])
        assertTrue(Regex("^builder: $stats$").matches(lines[2]), lines[2])
        assertEquals(
            "baseline: spearman 1.000 se 0.577 top-quarter 0.800 bottom-quarter 0.300 gap 0.500",
            lines[3],
        )
        assertTrue(Regex("""^time \d+\.\d s$""").matches(lines[4]), lines[4])
    }

    @Test
    fun `the counts of the read line add up to the builds read`() {
        val lines = score(mixed).stdoutLines()

        val skipped = Regex("""skipped (.*)$""").find(lines[0])!!.groupValues[1]
        val counts = skipped.split(", ").sumOf { it.substringAfterLast(' ').toInt() }
        val scored = lines[1].removePrefix("scored ").substringBefore(' ').toInt()
        assertEquals(11, counts + scored)
    }

    @Test
    fun `every run prints the same numbers`() {
        assertEquals(score(mixed).numbers(), score(mixed).numbers())
    }

    @Test
    fun `a gzipped file reads the same as a plain one`() {
        val zipped = Path.of(temp.toString(), "sealed.csv.gz")
        GZIPOutputStream(Files.newOutputStream(zipped)).use {
            it.write(Files.readAllBytes(Path.of(mixed)))
        }

        assertEquals(score(mixed).numbers(), score(zipped.toString()).numbers())
    }

    private fun spread(): List<String> =
        List(8) { i ->
            val stone = 22 - 3 * i
            ScoreSyn.build(
                "g$i",
                String.format(Locale.ROOT, "%.2f", 0.30 + 0.06 * i),
                mapOf("Mind Stone" to stone, "Counterspell" to 23 - stone),
            )
        }

    @Test
    fun `a limit takes builds spread evenly through the kept builds`() {
        val rows = spread()
        val all = file(*rows.toTypedArray())
        val picked = file(rows[0], rows[2], rows[4], rows[6], name = "picked.csv")

        val limited = score(all, "--limit", "4")

        assertEquals(0, limited.exit, limited.stderr)
        assertEquals("scored 4 builds", limited.stdoutLines()[1])
        assertEquals("read 8 builds with a bucket", limited.stdoutLines()[0].substringBefore(";"))
        assertEquals(score(picked).numbers().drop(1), limited.numbers().drop(1))
        assertEquals(limited.numbers(), score(all, "--limit", "4").numbers())
        assertEquals(score(all).numbers(), score(all, "--limit", "99").numbers())
    }

    @Test
    fun `fewer than four scored builds print dashes`() {
        val run = score(file(ScoreSyn.A, ScoreSyn.B))

        assertEquals(0, run.exit, run.stderr)
        assertEquals("scored 2 builds", run.stdoutLines()[1])
        assertEquals(
            "builder: spearman - se - top-quarter - bottom-quarter - gap -",
            run.stdoutLines()[2],
        )
        assertEquals(
            "baseline: spearman - se - top-quarter - bottom-quarter - gap -",
            run.stdoutLines()[3],
        )
    }

    @Test
    fun `bad inputs are one line and exit 1`() {
        val bad = writeFile(temp, "bad.json", "{ not json")

        assertOneLine(score(temp.resolve("missing.csv").toString()), "missing.csv")
        assertOneLine(
            runCli(
                "score-backtest",
                mixed,
                "--set",
                "nope",
                "--data",
                data.toString(),
                "--weights",
                weights,
            ),
            "nope",
        )
        assertOneLine(score(mixed, "--limit", "x"), "--limit")
        assertOneLine(score(mixed, "--limit", "0"), "--limit")
        assertOneLine(score(mixed, "--limit", "-3"), "--limit")
        assertOneLine(score(mixed, "--weights", bad), "weights file")
        assertOneLine(score(mixed, "--baseline", bad), "weights file")
        assertOneLine(score(mixed, "--weights", temp.resolve("none.json").toString()), "none.json")
        assertOneLine(
            runCli("score-backtest", mixed, "--set", "syn", "--data", data.toString()),
            "deck-tuned.json",
        )
        assertOneLine(score(writeFile(temp, "empty.csv", "")), "empty.csv")
        assertOneLine(score(writeFile(temp, "cols.csv", "a,b\n1,2\n")), "cols.csv")
    }

    /** A test-only statistic: the number of builds. */
    private object Count : ScoreStatistic {
        override val key = "count"

        override fun cell(builds: List<ScoredBuild>) = "count ${builds.size}"
    }

    @Test
    fun `a statistic added to the list shows in the output with no other change`() {
        val out = ByteArrayOutputStream()
        val stdout = PrintStream(out, true, StandardCharsets.UTF_8)

        val exit =
            ScoreBacktestCommand.run(
                listOf(mixed, "--set", "syn", "--data", data.toString(), "--weights", weights),
                stdout,
                ScoreStatistics.ALL + Count,
            )

        assertEquals(0, exit)
        val lines = out.toString(StandardCharsets.UTF_8).lines()
        assertTrue(lines[2].startsWith("builder: spearman "), lines[2])
        assertTrue(lines[2].endsWith(" count 4"), lines[2])
        assertTrue(lines[3].startsWith("baseline: ") && lines[3].endsWith(" count 4"), lines[3])
    }

    @Test
    fun `the usage text lists the command`() {
        val run = runCli("--help")

        assertTrue("score-backtest <sealed-game-data.csv.gz> --set <code>" in run.stdout)
    }
}
