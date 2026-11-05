package spa.cli

import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-042 criteria 2 to 5: `spa fixing` over the four sets' caches and the trk sample pool, and its
 * errors. Pinned numbers come from the Architect's independent script (ticket Background).
 */
class FixingCommandTest {
    @TempDir lateinit var temp: Path

    private fun setFixing(set: String) = runCli("fixing", "--set", set, "--data", Fixtures.data)

    /** The set's card lines (all but the last), keyed by card name. */
    private fun cardLines(run: CliRun): Map<String, String> =
        run.stdoutLines().dropLast(1).associateBy { it.substringBefore("  ") }

    private fun assertSet(set: String, summary: String, cards: List<String>) {
        val run = setFixing(set)
        assertEquals(0, run.exit, run.stderr)
        assertEquals(summary, run.stdoutLines().last(), set)
        val lines = cardLines(run)
        cards.forEach { line ->
            assertEquals(line, lines[line.substringBefore("  ")], "$set: $line")
        }
    }

    @Test
    fun `msh counts and five cards`() {
        assertSet(
            "msh",
            "34 fixing cards: CYCLING 5, MANA 25, SEARCH 0, TREASURE 4",
            listOf(
                "Borough Backup  CYCLING  WUBRG",
                "Asgardian Citadel  MANA  WR  land",
                "Death to Our Enemies  TREASURE  WUBRG",
                "Doc Samson, Super Psychiatrist  MANA  WUBRG",
                "Avengers Tower  MANA  WUBRG  land",
            ),
        )
    }

    @Test
    fun `hob counts and five cards`() {
        assertSet(
            "hob",
            "25 fixing cards: CYCLING 1, MANA 9, SEARCH 6, TREASURE 9",
            listOf(
                "Last Light of Durin's Day  CYCLING  R",
                "Troop of Ponies  SEARCH  WUBRG",
                "Bolg's Company  MANA  BR",
                "Smaug the Magnificent  TREASURE  WUBRG",
                "Hobbit Hole  SEARCH  WUBRG  land",
            ),
        )
    }

    @Test
    fun `fra counts, five cards, and Carnivorous Cultivator is not fixing`() {
        assertSet(
            "fra",
            "29 fixing cards: CYCLING 5, MANA 20, SEARCH 3, TREASURE 2",
            listOf(
                "Apex Witchstalker  CYCLING  WUBRG",
                "Vraska, Soul of Stone  MANA+TREASURE  WUBRG",
                "Room of Refuge  MANA  WUBRG  land",
                "Simulacrum Shaper  SEARCH  WUBRG",
                "Haunted Ridge  MANA  BR  land",
            ),
        )
        assertTrue("Carnivorous Cultivator" !in cardLines(setFixing("fra")))
    }

    @Test
    fun `trk counts, five cards, and a colourless-only land is not fixing`() {
        assertSet(
            "trk",
            "39 fixing cards: CYCLING 5, MANA 23, SEARCH 5, TREASURE 6",
            listOf(
                "Humpback Whales  CYCLING  WUBRG",
                "Final Frontier  SEARCH  WUBRG",
                "Genesis Effect  MANA  WUBRG",
                "Savvy Profiteer  TREASURE  WUBRG",
                "Bajor  MANA  WR  land",
            ),
        )
        // Isolinear Processor's only Add clause is {C}.
        assertTrue("Isolinear Processor" !in cardLines(setFixing("trk")))
    }

    @Test
    fun `set lines are sorted by name`() {
        val names = cardLines(setFixing("trk")).keys.toList()
        assertEquals(names.sorted(), names)
        assertEquals(39, names.size)
    }

    @Test
    fun `the trk sample pool's fixing, with quantities and copies per colour`() {
        val run =
            runCli(
                "fixing",
                Fixtures.pool("trk-manabox-01.csv"),
                "--set",
                "trk",
                "--data",
                Fixtures.data,
            )
        assertEquals(0, run.exit, run.stderr)
        assertEquals(
            listOf(
                "1 Bajor  MANA  WR  land",
                "1 Cardassia Prime  MANA  WB  land",
                "1 Ferenginar  MANA  BG  land",
                "2 Final Frontier  SEARCH  WUBRG",
                "1 Humpback Whales  CYCLING  WUBRG",
                "1 Romulus  MANA  UB  land",
                "2 Unexplored Territory  SEARCH  WUBRG  land",
                "2 Warp Core  SEARCH  WUBRG",
                "1 Xenobotanist  MANA  WUBRG",
                "12 fixing copies: W 10, U 9, B 11, R 9, G 9",
            ),
            run.stdoutLines(),
        )
    }

    @Test
    fun `an unknown set is one line and exit 1`() {
        val run = runCli("fixing", "--set", "zzz", "--data", Fixtures.data)
        assertEquals(1, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertEquals("", run.stdout)
    }

    @Test
    fun `an unreadable pool is one line and exit 1`() {
        val missing = temp.resolve("missing.csv").toString()
        val run = runCli("fixing", missing, "--set", "trk", "--data", Fixtures.data)
        assertEquals(1, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertEquals("", run.stdout)
    }

    @Test
    fun `a missing set is one line and exit 1`() {
        val run = runCli("fixing", "--data", Fixtures.data)
        assertEquals(1, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
    }

    @Test
    fun `help lists the command`() {
        assertTrue("fixing [<pool-file>] --set <code>" in Main.USAGE)
    }
}
