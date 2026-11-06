package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.build.DeckParts

/**
 * SPA-044 criterion 2 (every part's printed value sums to the printed score, every spell line has a
 * reason) and the command's errors.
 */
class BuildCommandTest {
    @TempDir lateinit var temp: Path

    private val pool = Fixtures.pool("trk-manabox-01.csv")

    private fun build(vararg extra: String, colours: String = "WU") =
        runCli("build", pool, "--set", "trk", "--colours", colours, "--data", Fixtures.data, *extra)

    private val heading = Regex("""^Deck (\S+): score (-?\d+\.\d{3})$""")
    private val partLine = Regex("""^ {2}([A-Za-z]+) ([+-]\d+\.\d{3})(?:: (.+))?$""")
    private val spellLine = Regex("""^ {2}(\d+) (.+) {2}grade (\d\.\d) {2}(\S.*)$""")

    @Test
    fun `help lists build`() {
        val run = runCli("--help")

        assertTrue("build <pool-file> --set <code> --colours <main[/splash]>" in run.stdout)
    }

    @Test
    fun `the printed parts sum to the printed score and every spell has a reason`() {
        val run = build()

        assertEquals(0, run.exit, run.stderr)
        val lines = run.stdoutLines()
        val head = requireNotNull(heading.matchEntire(lines.first())) { lines.first() }
        assertEquals("WU", head.groupValues[1])
        val score = head.groupValues[2].toDouble()
        val parts =
            lines
                .drop(1)
                .takeWhile { !it.startsWith("Spells") }
                .map { requireNotNull(partLine.matchEntire(it)) { "part line: $it" } }
        assertEquals(DeckParts.ALL.map { it.key }, parts.map { it.groupValues[1] })
        assertTrue(
            abs(score - parts.sumOf { it.groupValues[2].toDouble() }) <= 0.005,
            "parts ${parts.map { it.groupValues[2] }} against $score",
        )
        parts
            .filter { it.groupValues[2].toDouble() != 0.0 }
            .forEach { assertTrue(it.groupValues[3].isNotBlank(), "reason: ${it.value}") }

        val spellsIndex = lines.indexOfFirst { it.startsWith("Spells") }
        val landsIndex = lines.indexOfFirst { it.startsWith("Lands") }
        val spells = lines.subList(spellsIndex + 1, landsIndex)
        val spellRows = spells.filter { !it.startsWith("Mana value") }
        spellRows.forEach { assertTrue(spellLine.matches(it), "spell line: $it") }
        val spellCopies = spellRows.sumOf { spellLine.matchEntire(it)!!.groupValues[1].toInt() }
        assertEquals("Spells ($spellCopies)", lines[spellsIndex])
        val landCopies =
            lines[landsIndex].substringAfter(": ").split(", ").sumOf {
                it.substringBefore(' ').toInt()
            }
        assertEquals(40, spellCopies + landCopies)
        assertTrue(lines[landsIndex].startsWith("Lands ($landCopies):"))
    }

    @Test
    fun `the same inputs print the same deck`() {
        assertEquals(build().stdout, build().stdout)
    }

    @Test
    fun `a splash configuration builds and the sideboard counts the off colour cards`() {
        val run = build(colours = "WU/R")

        assertEquals(0, run.exit, run.stderr)
        assertTrue(run.stdout.lines().first().startsWith("Deck WU/R: score"))
        assertTrue(run.stdoutLines().any { it.startsWith("Sideboard") })
        assertTrue(run.stdoutLines().any { it.startsWith("Off-colour:") })
    }

    @Test
    fun `weights, tags and otags options are read`() {
        val weights = Files.readString(Path.of(Fixtures.data, "weights", "deck-default.json"))
        val doubled = weights.replace(Regex(""""grades":\s*[0-9.]+"""), "\"grades\": 2")
        val file = writeFile(temp, "grades2.json", doubled)

        val plain = build()
        val custom = build("--weights", file)
        val tags = build("--tags", Path.of(Fixtures.data, "tags", "trk.json").toString())
        val noOtags = build("--otags", "none")

        assertEquals(0, custom.exit, custom.stderr)
        assertTrue(plain.stdoutLines().first() != custom.stdoutLines().first())
        assertEquals(0, tags.exit, tags.stderr)
        assertEquals(0, noOtags.exit, noOtags.stderr)
    }

    private fun assertOneLine(run: CliRun, fragment: String) {
        assertEquals(1, run.exit, run.stderr)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue(fragment in run.stderr, run.stderr)
    }

    @Test
    fun `a missing or bad colours option is one line and exit 1`() {
        assertOneLine(runCli("build", pool, "--set", "trk", "--data", Fixtures.data), "--colours")
        assertOneLine(build(colours = "XY"), "colours")
        assertOneLine(build(colours = "WU/WR"), "colours")
    }

    @Test
    fun `a bad weights file is one line and exit 1`() {
        assertOneLine(build("--weights", writeFile(temp, "bad.json", "{")), "weights file")
        val unknown =
            Files.readString(Path.of(Fixtures.data, "weights", "deck-default.json"))
                .replace("\"grades\"", "\"gradez\"")
        assertOneLine(build("--weights", writeFile(temp, "unknown.json", unknown)), "gradez")
        assertOneLine(build("--weights", temp.resolve("missing.json").toString()), "not found")
    }

    @Test
    fun `a pool with too few candidates is one line and exit 1`() {
        assertOneLine(build(colours = "W"), "22")
    }
}
