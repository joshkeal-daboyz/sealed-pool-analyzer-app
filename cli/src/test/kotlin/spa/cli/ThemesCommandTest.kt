package spa.cli

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.agent.themes.ColourGroup
import spa.agent.themes.PoolThemes
import spa.agent.themes.Tally
import spa.agent.themes.Theme
import spa.agent.themes.ThemeCount
import spa.agent.themes.ThemeKind
import spa.core.brief.BriefLoader
import spa.core.cards.CardIndex

/** Criteria 2 and 3: the themes of `trk` (with `sds`), and `spa themes`. */
class ThemesCommandTest {
    private val scryfall = Fixtures.root.resolve("scryfall")

    private fun trkCounts(): List<ThemeCount> {
        val index = CardIndex.load(scryfall, "trk")
        val brief = BriefLoader.parse(Fixtures.root.resolve("briefs/trk.json").toFile().readText())
        return PoolThemes.count(scryfall, index, brief.mechanics.map { it.name })
    }

    @Test
    fun `the trk pool has 336 cards`() {
        val index = CardIndex.load(scryfall, "trk")
        val keywords = PoolThemes.keywords(scryfall, index.setCodes)
        assertEquals(336, PoolThemes.cards(index, keywords).size)
    }

    @Test
    fun `trk counts equal the independent script's`() {
        val counts = trkCounts().associateBy { it.theme }
        // Numbers from the Architect's independent script (G-004 Architect notes).
        val expected =
            mapOf(
                Theme("Station", ThemeKind.KEYWORD) to (17 to 1),
                Theme("Surveil", ThemeKind.KEYWORD) to (22 to 0),
                Theme("Spacecraft", ThemeKind.TYPE) to (17 to 11),
                Theme("Human", ThemeKind.TYPE) to (68 to 1),
                Theme("Equipment", ThemeKind.TYPE) to (4 to 3),
            )
        expected.forEach { (theme, numbers) ->
            val count = requireNotNull(counts[theme]) { "$theme is not kept" }
            assertEquals(numbers, count.has.total to count.refers.total, theme.name)
        }
    }

    @Test
    fun `a line gives the name, kind, counts, and the either count's splits`() {
        fun tally(total: Int, colour: List<Int>, rarity: List<Int>) =
            Tally(
                total,
                ColourGroup.entries.zip(colour).toMap(),
                Tally.RARITIES.zip(rarity).toMap(),
            )
        val count =
            ThemeCount(
                Theme("Face a dilemma", ThemeKind.KEYWORD),
                has = tally(4, listOf(1, 1, 0, 0, 0, 2, 0), listOf(2, 1, 1, 0)),
                refers = tally(1, listOf(0, 0, 0, 0, 0, 0, 1), listOf(0, 0, 0, 1)),
                either = tally(5, listOf(1, 1, 0, 0, 0, 2, 1), listOf(2, 1, 1, 1)),
            )
        assertEquals(
            "Face a dilemma keyword has=4 refers=1 either=5 " +
                "colour W=1 U=1 B=0 R=0 G=0 M=2 C=1 rarity C=2 U=1 R=1 M=1",
            ThemesCommand.line(count),
        )
    }

    private val linePattern =
        Regex(
            "^(.+) (keyword|type) has=(\\d+) refers=(\\d+) either=(\\d+) " +
                "colour W=(\\d+) U=(\\d+) B=(\\d+) R=(\\d+) G=(\\d+) M=(\\d+) C=(\\d+) " +
                "rarity C=(\\d+) U=(\\d+) R=(\\d+) M=(\\d+)$"
        )

    @Test
    fun `spa themes trk prints the kept themes, most cards first, and exits 0`() {
        val run = runCli("themes", "trk", "--data", Fixtures.data)
        assertEquals(0, run.exit, run.stderr)
        assertEquals("", run.stderr)
        val lines = run.stdoutLines()
        assertTrue(lines.size >= 5, run.stdout)
        val parsed = lines.map { requireNotNull(linePattern.matchEntire(it)) { it }.groupValues }
        val either = parsed.map { it[5].toInt() }
        assertEquals(either.sortedDescending(), either)
        parsed.forEach { g ->
            assertTrue(g[5].toInt() >= 3, g[0])
            assertEquals(g[5].toInt(), (6..12).sumOf { g[it].toInt() }, g[0])
            assertTrue((13..16).sumOf { g[it].toInt() } <= g[5].toInt(), g[0])
        }
        // A card with the keyword never refers to it, so Station's either is has plus refers.
        assertTrue(
            lines.any { it.startsWith("Station keyword has=17 refers=1 either=18 ") },
            run.stdout,
        )
        assertTrue(lines.any { it.startsWith("Human type has=68 refers=1 ") }, run.stdout)
        assertEquals(trkCounts().map { ThemesCommand.line(it) }, lines)
    }

    @Test
    fun `an unknown set prints one line and exits 1`() {
        val run = runCli("themes", "zzz", "--data", Fixtures.data)
        assertEquals(1, run.exit)
        assertEquals("", run.stdout)
        assertEquals(1, run.stderrLines().size, run.stderr)
    }

    @Test
    fun `the help text names the command`() {
        assertTrue(Main.USAGE.contains("themes <code> [--data <dir>]"))
    }
}
