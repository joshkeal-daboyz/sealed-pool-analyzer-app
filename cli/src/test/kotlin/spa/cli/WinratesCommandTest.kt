package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.grade.GradeEntry
import spa.agent.grade.GradeFiles
import spa.agent.grade.GradeSource
import spa.core.cards.CardIndex

/** Criterion 5: `spa winrates` writes the table, lists unmatched names, and fails cleanly. */
class WinratesCommandTest {
    @TempDir lateinit var temp: Path

    /**
     * 1,200 games of set `syn`. Grizzly Bears is in the opening hand in games 1 to 600 and Delver
     * of Secrets is drawn in games 601 to 1200; games 1 to 300 and 601 to 1000 are won. So Grizzly
     * Bears is 300 / 600 = 0.5 and Delver 400 / 600 = 0.6667. "Bonus, Card" is no `syn` card.
     */
    private fun gameData(): String {
        val header =
            "expansion,won,opening_hand_Grizzly Bears,drawn_Grizzly Bears," +
                "opening_hand_Delver of Secrets,drawn_Delver of Secrets," +
                "\"opening_hand_Bonus, Card\",\"drawn_Bonus, Card\""
        val rows =
            (1..1200).map { game ->
                val won = if (game <= 300 || game in 601..1000) "True" else "False"
                val bears = if (game <= 600) "1,0" else "0,0"
                val delver = if (game > 600) "0,1" else "0,0"
                val bonus = if (game <= 10) "1,0" else "0,0"
                "SYN,$won,$bears,$delver,$bonus"
            }
        return (listOf(header) + rows).joinToString("\n", postfix = "\n")
    }

    private fun dataDir(): Path = Fixtures.synDataDir(temp.resolve("data"))

    @Test
    fun `it writes the set's table, lists the unmatched name, and exits 0`() {
        val data = dataDir()
        val csv = writeFile(temp, "game_data_public.SYN.PremierDraft.csv", gameData())

        val run =
            runCli(
                "winrates",
                csv,
                "--set",
                "syn",
                "--data",
                data.toString(),
                "--dataset-date",
                "2026-07-27",
            )

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("", run.stderr)
        val lines = run.stdoutLines()
        assertTrue("unmatched: Bonus, Card" in lines, run.stdout)
        assertTrue(lines.first().startsWith("syn: 1200 games, 3 card names, 2 matched, 2 graded"))
        val out = data.resolve("winrates/syn.json")
        assertTrue(lines.last() == "wrote $out", run.stdout)

        val file = GradeFiles.load(out)
        assertEquals(GradeSource.WIN_RATE, file.header.source)
        assertEquals(
            "17Lands public game data game_data_public.SYN.PremierDraft.csv, " +
                "Last-Modified 2026-07-27",
            file.header.provenance,
        )
        val index = CardIndex.load(data.resolve("scryfall"), "syn")
        val bears = index.printings("syn").first { it.card.name == "Grizzly Bears" }
        val delver = index.printings("syn").first { it.card.name.startsWith("Delver of Secrets") }
        // m = (0.5 + 0.6667) / 2 = 0.5833, s = 0.0833: the cards sit at m - s and m + s.
        assertEquals(
            listOf(
                GradeEntry("Grizzly Bears", bears.collectorNumber, "syn", 1.5, 0.231, 0.5, 600),
                GradeEntry(
                    "Delver of Secrets // Insectile Aberration",
                    delver.collectorNumber,
                    "syn",
                    3.5,
                    0.231,
                    0.666667,
                    600,
                ),
            ),
            file.grades,
        )
    }

    @Test
    fun `without --dataset-date the provenance takes the file's modified date`() {
        val data = dataDir()
        val csv = writeFile(temp, "game_data_public.SYN.PremierDraft.csv", gameData())
        Files.setLastModifiedTime(
            Path.of(csv),
            FileTime.from(Instant.parse("2026-07-27T18:11:30Z")),
        )

        val run = runCli("winrates", csv, "--set", "syn", "--data", data.toString())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val file = GradeFiles.load(data.resolve("winrates/syn.json"))
        assertTrue(file.header.provenance.endsWith("Last-Modified 2026-07-27"))
    }

    @Test
    fun `a missing game data file prints one line and exits 1`() {
        val missing = temp.resolve("game_data_public.SYN.PremierDraft.csv.gz").toString()

        val run = runCli("winrates", missing, "--set", "syn", "--data", dataDir().toString())

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(listOf("spa: game data file not found: $missing"), run.stderrLines())
    }

    @Test
    fun `a file that is not game data prints one line and exits 1`() {
        val bad = writeFile(temp, "bad.csv", "expansion,deck_A\nSYN,1\n")

        val run = runCli("winrates", bad, "--set", "syn", "--data", dataDir().toString())

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue(run.stderrLines()[0].startsWith("spa: cannot read game data $bad: "))
    }

    @Test
    fun `a missing --set exits 1 and a bad --dataset-date exits 2`() {
        val csv = writeFile(temp, "g.csv", gameData())
        assertEquals(ExitCode.ERROR, runCli("winrates", csv).exit)
        val bad = runCli("winrates", csv, "--set", "syn", "--dataset-date", "July")
        assertEquals(ExitCode.USAGE, bad.exit)
    }

    @Test
    fun `help lists the winrates command`() {
        assertTrue("winrates <game-data.csv.gz> --set <code>" in runCli("--help").stdout)
    }
}
