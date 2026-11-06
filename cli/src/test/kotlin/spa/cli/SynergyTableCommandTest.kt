package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.tags.TagFiles

/**
 * SPA-048 criteria 2 and 6: `spa synergy-table` on the v1 trk tags, frozen since SPA-053 as
 * `fixtures/tags/v1/trk.json`, against an independent script's numbers (scratchpad
 * `tools/synergy.js`, 2026-11-05), and its errors.
 */
class SynergyTableCommandTest {
    // Since SPA-054 the pinned runs read no otags, as these numbers were computed without them.
    private val v1 = listOf("--tags", Fixtures.v1Tags("trk"), "--otags", Otags.NONE)

    private val trk: CliRun by lazy {
        runCli("synergy-table", "--set", "trk", *v1.toTypedArray(), "--data", Fixtures.data)
    }

    @Test
    fun `trk ends with the script's summary line`() {
        assertEquals(ExitCode.OK, trk.exit)
        assertEquals("", trk.stderr)
        assertEquals(
            "336 cards, 56616 pairs with the diagonal, 7259 nonzero, 41 nonzero on the diagonal, " +
                "maximum 18",
            trk.stdoutLines().last(),
        )
    }

    @Test
    fun `trk's pinned pairs`() {
        val lines = trk.stdoutLines()

        assertEquals(
            "18  axis 18  partner 0  Gowron, Eager for Battle (trk/237) with itself",
            lines.first(),
        )
        assertTrue(
            "17  axis 13  partner 4  Miles O'Brien, Transporter Ops (trk/24) with " +
                "Scotty, Miracle Worker (trk/79)" in lines
        )
        assertTrue(
            "6  axis 6  partner 0  Enterprise NX-01 (trk/58) with " +
                "Hemmer, Fixing What Is Broken (trk/239)" in lines
        )
    }

    @Test
    fun `trk prints every nonzero pair, highest first, each once`() {
        val pairs = trk.stdoutLines().dropLast(1)
        val scores = pairs.map { it.substringBefore(" ").toInt() }

        assertEquals(7259, pairs.size)
        assertEquals(scores.sortedDescending(), scores)
        assertEquals(pairs.size, pairs.toSet().size)
        assertEquals(41, pairs.count { it.endsWith(" with itself") })
        pairs.forEach { line ->
            val (score, axis, partner) =
                Regex("""^(\d+) {2}axis (\d+) {2}partner (\d+) {2}.+""")
                    .matchEntire(line)!!
                    .destructured
            assertEquals(score.toInt(), axis.toInt() + partner.toInt(), line)
        }
    }

    @Test
    fun `--min keeps only pairs scoring at least it`() {
        val run =
            runCli(
                "synergy-table",
                "--set",
                "trk",
                "--min",
                "12",
                *v1.toTypedArray(),
                "--data",
                Fixtures.data,
            )
        val pairs = run.stdoutLines().dropLast(1)

        assertEquals(ExitCode.OK, run.exit)
        assertTrue(pairs.isNotEmpty())
        assertTrue(pairs.all { it.substringBefore(" ").toInt() >= 12 })
        assertEquals(trk.stdoutLines().last(), run.stdoutLines().last())
        val all =
            runCli(
                "synergy-table",
                "--set",
                "trk",
                "--min",
                "0",
                *v1.toTypedArray(),
                "--data",
                Fixtures.data,
            )
        assertEquals(56616 + 1, all.stdoutLines().size)
    }

    @Test
    fun `ties print by the first card's then the second card's collector order`() {
        val sixes = trk.stdoutLines().filter { it.startsWith("6  ") }
        val ids = sixes.map { line ->
            Regex("""\(([a-z]+/[0-9a-z]+)\)""").findAll(line).map { it.groupValues[1] }.toList()
        }
        val order = TagFiles.read(Path.of(Fixtures.v1Tags("trk"))).cards.map { it.id }
        val position = { id: String -> order.indexOf(id) }
        val keys = ids.map { position(it.first()) to position(it.last()) }

        assertTrue(keys.all { it.first >= 0 && it.second >= 0 })
        assertEquals(keys.sortedWith(compareBy({ it.first }, { it.second })), keys)
        assertTrue(keys.all { it.first <= it.second })
    }

    @Test
    fun `a missing tag file is one line and exit 1`(@TempDir dir: Path) {
        val run = runCli("synergy-table", "--set", "trk", "--data", dir.toString())

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(1, run.stderrLines().size)
        assertTrue("no tag file for set 'trk'" in run.stderr, run.stderr)
    }

    @Test
    fun `an unknown set is one line and exit 1`() {
        val run = runCli("synergy-table", "--set", "zzz", "--data", Fixtures.data)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size)
    }

    @Test
    fun `an invalid tag file or one for another set is one line and exit 1`(@TempDir dir: Path) {
        writeFile(dir, "tags/abc.json", "{ \"set\": \"abc\" }")
        val invalid = runCli("synergy-table", "--set", "abc", "--data", dir.toString())
        assertEquals(ExitCode.ERROR, invalid.exit)
        assertEquals(1, invalid.stderrLines().size)
        assertTrue("invalid tag file" in invalid.stderr, invalid.stderr)

        val trkText = Files.readString(Fixtures.root.resolve("tags/trk.json"))
        writeFile(dir, "tags/abc.json", trkText)
        val other = runCli("synergy-table", "--set", "abc", "--data", dir.toString())
        assertEquals(ExitCode.ERROR, other.exit)
        assertEquals(1, other.stderrLines().size)
        assertTrue("is for set 'trk', not 'abc'" in other.stderr, other.stderr)
    }

    @Test
    fun `a bad --min, a missing --set or a stray argument fails`() {
        val min = runCli("synergy-table", "--set", "trk", "--min", "lots", "--data", Fixtures.data)
        assertEquals(ExitCode.ERROR, min.exit)
        assertEquals(1, min.stderrLines().size)

        val set = runCli("synergy-table", "--data", Fixtures.data)
        assertEquals(ExitCode.ERROR, set.exit)
        assertEquals(1, set.stderrLines().size)

        val stray = runCli("synergy-table", "extra", "--set", "trk", "--data", Fixtures.data)
        assertEquals(ExitCode.USAGE, stray.exit)
    }

    @Test
    fun `help lists the command`() {
        assertTrue("synergy-table --set <code>" in Main.USAGE)
    }

    @Test
    fun `--tags reads that file, as if it were the data directory's tag file`(@TempDir dir: Path) {
        val tagged =
            runCli(
                "synergy-table",
                "--set",
                "trk",
                "--tags",
                Fixtures.v1Tags("trk"),
                "--data",
                "$dir",
            )
        writeFile(dir, "tags/trk.json", Files.readString(Path.of(Fixtures.v1Tags("trk"))))
        val default = runCli("synergy-table", "--set", "trk", "--data", "$dir")

        assertEquals(ExitCode.OK, tagged.exit, tagged.stderr)
        assertEquals(
            "336 cards, 56616 pairs with the diagonal, 7259 nonzero, 41 nonzero on the diagonal, " +
                "maximum 18",
            tagged.stdoutLines().last(),
        )
        assertEquals(ExitCode.OK, default.exit, default.stderr)
        assertEquals(default.stdout, tagged.stdout)
    }

    @Test
    fun `a missing --tags file is one line and exit 1`(@TempDir dir: Path) {
        val none = dir.resolve("none.json")
        val run =
            runCli("synergy-table", "--set", "trk", "--tags", "$none", "--data", Fixtures.data)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(listOf("spa: tag file not found: $none"), run.stderrLines())
    }

    @Test
    fun `help names --tags`() {
        assertTrue("synergy-table --set <code> [--min <score>] [--tags <file>]" in Main.USAGE)
    }
}
