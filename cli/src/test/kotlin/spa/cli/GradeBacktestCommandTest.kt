package spa.cli

import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Criteria 2 and 3: `spa grade-backtest` on two small committed syn grade files, and its errors.
 *
 * The files grade the 20 non-basic syn cards; the model file leaves out Evolving Wilds (common 19).
 * The win-rate side is ranked by `gihwr` (all distinct, so no ties), the model side by `score`
 * (average ranks for ties). Values (win-rate gihwr / model score) and their ranks, hand-checked:
 * - common (1, 5, 6, 7, 8, 11, 18): gihwr .478 .557 .512 .527 .508 .540 .497, ranks 1 7 4 5 3 6 2;
 *   model 1.5 4.0 2.5 2.5 2.0 3.0 1.0, ranks 2 7 4.5 4.5 3 6 1. Rank means 4. Deviations
 *   [-3, 3, 0, 1, -1, 2, -2] and [-2, 3, 0.5, 0.5, -1, 2, -3]: products 6 + 9 + 0 + 0.5 + 1 + 4 + 6
 *   = 26.5, squares 28 and 27.5. rho = 26.5 / sqrt(770) = 0.954994, printed 0.955.
 * - uncommon (2, 3, 4, 9, 10, 16, 17): gihwr .553 .523 .538 .542 .572 .510 .493, ranks 6 3 4 5 7 2
 *   1; model 3.0 3.5 4.0 2.5 2.0 3.0 3.0, ranks 4 6 7 2 1 4 4. Deviations [2, -1, 0, 1, 3, -2, -3]
 *   and [0, 2, 3, -2, -3, 0, 0]: products 0 - 2 + 0 - 2 - 9 + 0 + 0 = -13, squares 28 and 26. rho =
 *   -13 / sqrt(728) = -0.4818, printed -0.482.
 * - rare (12, 14, 15, 20) has 4 cards and mythic (13) has 1: under five, so n/a.
 * - all 19 matched cards, gihwr ranks 1 to 19 (mean 10, squares 19 * 360 / 12 = 570) in the order 1
 *   20 17 18 8 16 6 3 7 4 11 9 2 13 5 14 15 10 12; model ranks for those cards 3 1 10.5 2 4.5 10.5
 *   7 13 7 15 10.5 7 10.5 17.5 15 17.5 15 4.5 19 (squares 560). Products 63 + 72 - 3.5 + 48
 *     + 27.5 - 2 + 9 - 6 + 3 + 0 + 0.5 - 6 + 1.5 + 30 + 25 + 45 + 35 - 44 + 81 = 379. rho = 379 /
 *       sqrt(570 * 560) = 379 / 564.978 = 0.6708, printed 0.671. Ranked by win-rate `score`
 *       instead, the three rhos would be 0.936, -0.393 and 0.716.
 */
class GradeBacktestCommandTest {
    @TempDir lateinit var temp: Path

    private fun resource(name: String): Path =
        Path.of(requireNotNull(javaClass.getResource("/grades/$name")).toURI())

    private val model = resource("syn-model.json").toString()
    private val winRates = resource("syn-winrates.json").toString()

    private val expected =
        listOf(
            "all: rho 0.671, n 19",
            "common: rho 0.955, n 7",
            "uncommon: rho -0.482, n 7",
            "rare: rho n/a, n 4",
            "mythic: rho n/a, n 1",
            "model: claude-opus-5-5, prompt v1, 2026-11-04",
            "winrates: 17Lands public game data game_data_public.SYN.PremierDraft.csv.gz, " +
                "Last-Modified 2026-07-27",
            "missing from model: 1 card",
            "missing: Evolving Wilds (syn 19)",
        )

    @Test
    fun `it prints the five groups, both provenances, the missing card, and exits 0`() {
        val run =
            runCli(
                "grade-backtest",
                "--set",
                "syn",
                "--model",
                model,
                "--winrates",
                winRates,
                "--data",
                Fixtures.data,
            )

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("", run.stderr)
        assertEquals(expected, run.stdoutLines())
    }

    @Test
    fun `without --model and --winrates it reads grades and winrates under the data directory`() {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        resource("syn-model.json")
            .copyTo(data.resolve("grades").createDirectories().resolve("syn.json"))
        resource("syn-winrates.json")
            .copyTo(data.resolve("winrates").createDirectories().resolve("syn.json"))

        val run = runCli("grade-backtest", "--set", "SYN", "--data", data.toString())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(expected, run.stdoutLines())
    }

    @Test
    fun `a missing model file prints one line naming it and exits 1`() {
        val missing = temp.resolve("grades/syn.json").toString()

        val run =
            runCli(
                "grade-backtest",
                "--set",
                "syn",
                "--model",
                missing,
                "--winrates",
                winRates,
                "--data",
                Fixtures.data,
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(listOf("spa: model grade file not found: $missing"), run.stderrLines())
    }

    @Test
    fun `a missing win-rate file prints one line naming it and exits 1`() {
        val missing = temp.resolve("winrates/syn.json").toString()

        val run =
            runCli(
                "grade-backtest",
                "--set",
                "syn",
                "--model",
                model,
                "--winrates",
                missing,
                "--data",
                Fixtures.data,
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(listOf("spa: win-rate grade file not found: $missing"), run.stderrLines())
    }

    @Test
    fun `the default model file, when absent, is named in the one line`() {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        val expectedPath = data.resolve("grades").resolve("syn.json")

        val run = runCli("grade-backtest", "--set", "syn", "--data", data.toString())

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(listOf("spa: model grade file not found: $expectedPath"), run.stderrLines())
    }

    @Test
    fun `a grade file that breaks its schema prints one line and exits 1`() {
        val bad = writeFile(temp, "bad.json", "{\"header\": {}}")

        val run =
            runCli(
                "grade-backtest",
                "--set",
                "syn",
                "--model",
                bad,
                "--winrates",
                winRates,
                "--data",
                Fixtures.data,
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue(run.stderrLines()[0].startsWith("spa: invalid model grade file $bad: "))
    }

    @Test
    fun `a set with no Scryfall cache prints one line and exits 1`() {
        val run =
            runCli(
                "grade-backtest",
                "--set",
                "syn",
                "--model",
                model,
                "--winrates",
                winRates,
                "--data",
                temp.toString(),
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue(run.stderrLines()[0].startsWith("spa: no Scryfall cache for set 'syn'"))
    }

    @Test
    fun `a missing --set exits 1 and a stray argument exits 2`() {
        assertEquals(ExitCode.ERROR, runCli("grade-backtest").exit)
        assertEquals(ExitCode.USAGE, runCli("grade-backtest", "syn", "--set", "syn").exit)
    }

    @Test
    fun `help lists the grade-backtest command`() {
        assertTrue(
            "grade-backtest --set <code> [--model <file>] [--winrates <file>]" in
                runCli("--help").stdout
        )
    }
}
