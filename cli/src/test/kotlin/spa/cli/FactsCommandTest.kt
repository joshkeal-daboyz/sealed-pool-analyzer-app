package spa.cli

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * SPA-051 criterion 2: `spa facts` over the four caches, against an independent script's numbers
 * (scratchpad `tools/facts.js`, 2026-11-05).
 */
class FactsCommandTest {
    // Since SPA-054 the pins read no otags: the four sets have otags files, whose otag facts
    // would add to the counts (OtagsCommandsTest covers them).
    private fun facts(vararg args: String): CliRun =
        runCli("facts", *args, "--otags", Otags.NONE, "--data", Fixtures.data)

    /** The `<fact> <count>` lines of [run], as a map, and its summary line. */
    private fun counts(run: CliRun): Map<String, Int> =
        run.stdoutLines().dropLast(1).associate {
            it.substringBeforeLast(' ') to it.substringAfterLast(' ').toInt()
        }

    private fun assertPins(set: String, pool: Int, distinct: Int, pins: Map<String, Int>) {
        val run = facts("--set", set)
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("", run.stderr)
        assertEquals("$pool cards, $distinct distinct facts", run.stdoutLines().last(), set)
        val counts = counts(run)
        assertEquals(distinct, counts.size, set)
        pins.forEach { (fact, n) -> assertEquals(n, counts[fact], "$set $fact") }
    }

    @Test
    fun `trk with sds matches the script`() {
        assertPins(
            "trk",
            336,
            172,
            mapOf(
                "land" to 36,
                "creature" to 189,
                "artifact" to 65,
                "enchantment" to 26,
                "instant" to 24,
                "sorcery" to 32,
                "legendary" to 130,
                "cheap" to 99,
                "enters-trigger" to 96,
                "dies-trigger" to 4,
                "makes-token" to 58,
                "kw:deathtouch" to 4,
                "kw:flying" to 10,
                "type:human" to 68,
            ),
        )
    }

    @Test
    fun `msh matches the script`() {
        assertPins(
            "msh",
            276,
            131,
            mapOf(
                "land" to 20,
                "creature" to 170,
                "legendary" to 132,
                "cheap" to 89,
                "enters-trigger" to 54,
                "dies-trigger" to 2,
                "makes-token" to 39,
            ),
        )
    }

    @Test
    fun `hob matches the script`() {
        assertPins(
            "hob",
            188,
            83,
            mapOf(
                "land" to 8,
                "creature" to 112,
                "legendary" to 55,
                "cheap" to 67,
                "enters-trigger" to 35,
                "dies-trigger" to 5,
                "makes-token" to 23,
            ),
        )
    }

    @Test
    fun `fra matches the script`() {
        assertPins(
            "fra",
            280,
            129,
            mapOf(
                "land" to 20,
                "creature" to 160,
                "planeswalker" to 8,
                "legendary" to 95,
                "cheap" to 104,
                "enters-trigger" to 61,
                "dies-trigger" to 3,
                "makes-token" to 39,
            ),
        )
    }

    @Test
    fun `facts print most common first, then by name`() {
        val lines = facts("--set", "trk").stdoutLines().dropLast(1)
        val pairs = lines.map { it.substringBeforeLast(' ') to it.substringAfterLast(' ').toInt() }

        assertEquals("creature 189", lines.first())
        assertEquals(
            pairs.sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }
            ),
            pairs,
        )
    }

    @Test
    fun `--card prints the card's facts, sorted`() {
        assertEquals(
            listOf("Moopsy (trk/207): cheap creature kw:deathtouch kw:reach type:beast"),
            facts("--set", "trk", "--card", "Moopsy").stdoutLines(),
        )
        assertEquals(
            listOf("Bajor (trk/277): enters-trigger land"),
            facts("--set", "trk", "--card", "Bajor").stdoutLines(),
        )
        assertEquals(
            listOf("Enterprise NX-01 (trk/58): artifact enters-trigger kw:station legendary"),
            facts("--set", "trk", "--card", "Enterprise NX-01").stdoutLines(),
        )
    }

    @Test
    fun `the three self-granted keywords of trk`() {
        assertTrue("kw:flying" in facts("--set", "trk", "--card", "Shuttle Ace").stdout)
        assertTrue(
            "kw:trample" in facts("--set", "trk", "--card", "Gowron, Eager for Battle").stdout
        )
    }

    @Test
    fun `--card takes an id, in any case, or a name in any case`() {
        val moopsy = "Moopsy (trk/207): cheap creature kw:deathtouch kw:reach type:beast"

        assertEquals(listOf(moopsy), facts("--set", "trk", "--card", "trk/207").stdoutLines())
        assertEquals(listOf(moopsy), facts("--set", "TRK", "--card", "TRK/207").stdoutLines())
        assertEquals(listOf(moopsy), facts("--set", "trk", "--card", "moopsy").stdoutLines())
    }

    @Test
    fun `an unknown card is one line and exit 1`() {
        val run = facts("--set", "trk", "--card", "Nobody At All")

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(
            listOf("spa: no card 'Nobody At All' in the sealed pool of trk"),
            run.stderrLines(),
        )
    }

    @Test
    fun `a missing --set, a stray argument or an unknown set fails`() {
        assertEquals(ExitCode.ERROR, facts().exit)
        assertEquals(ExitCode.USAGE, facts("trk", "--set", "trk").exit)
        val unknown = facts("--set", "zzz")
        assertEquals(ExitCode.ERROR, unknown.exit)
        assertEquals(1, unknown.stderrLines().size)
    }

    @Test
    fun `help lists the command`() {
        val help = runCli("--help").stdout

        // SPA-054 adds --otags.
        assertTrue(
            "facts --set <code> [--card <name or id>] [--otags <file>] [--data <dir>]" in help
        )
    }
}
