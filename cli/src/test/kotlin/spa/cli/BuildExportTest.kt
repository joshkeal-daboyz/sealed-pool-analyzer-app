package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** SPA-072 criteria 1 and 6: `spa build --export <dir>`. */
class BuildExportTest {
    @TempDir lateinit var temp: Path

    private val pool = Fixtures.pool("trk-manabox-01.csv")
    private val weights = Path.of(Fixtures.data, "weights", "deck-tuned.json").toString()

    private fun ranked(vararg extra: String) =
        runCli(
            "build",
            pool,
            "--set",
            "trk",
            "--weights",
            weights,
            "--top",
            "3",
            "--data",
            Fixtures.data,
            *extra,
        )

    private fun splash(vararg extra: String) =
        runCli(
            "build",
            pool,
            "--set",
            "trk",
            "--colours",
            "WU/R",
            "--data",
            Fixtures.data,
            *extra,
        )

    private fun names(dir: Path) =
        dir.listDirectoryEntries().map { it.fileName.toString() }.sorted()

    @Test
    fun `export leaves stdout alone and writes six files named from the headings`() {
        val out = temp.resolve("out")

        val with = ranked("--export", out.toString())
        val without = ranked()

        assertEquals(0, with.exit, with.stderr)
        assertEquals(without.stdout, with.stdout)
        val colours =
            Regex("""^Rank (\d): (\S+)""", RegexOption.MULTILINE)
                .findAll(with.stdout)
                .map { it.groupValues[1] to it.groupValues[2].replace('/', '+') }
                .toList()
        assertEquals(3, colours.size)
        val expected = colours.flatMap { (n, c) -> listOf("rank-$n-$c.csv", "rank-$n-$c.txt") }
        assertEquals(expected.sorted(), names(out))
        assertEquals(
            expected.map { "Wrote ${out.resolve(it)}" }.sorted(),
            with.stderrLines().sorted(),
        )
        assertEquals("", without.stderr)
    }

    @Test
    fun `a splash configuration is named deck-colours with a plus and the directory is made`() {
        val out = temp.resolve("made/here")

        val run = splash("--export", out.toString())

        assertEquals(0, run.exit, run.stderr)
        assertEquals(listOf("deck-WU+R.csv", "deck-WU+R.txt"), names(out))
        assertEquals(splash().stdout, run.stdout)
        val txt = Files.readAllLines(out.resolve("deck-WU+R.txt"))
        assertEquals(40, txt.sumOf { it.substringBefore(' ').toInt() })
    }

    @Test
    fun `a second run replaces the files with the same bytes`() {
        val out = temp.resolve("out")
        ranked("--export", out.toString())
        val first = names(out).associateWith { Files.readAllBytes(out.resolve(it)) }
        Files.writeString(out.resolve(first.keys.first()), "stale")

        val again = ranked("--export", out.toString())

        assertEquals(0, again.exit, again.stderr)
        assertEquals(first.keys.toList(), names(out))
        first.forEach { (name, bytes) ->
            assertTrue(bytes.contentEquals(Files.readAllBytes(out.resolve(name))), name)
        }
    }

    @Test
    fun `a file where the directory should be is one line and exit 1`() {
        val blocker = temp.resolve("blocker")
        Files.writeString(blocker, "not a directory")

        val run = ranked("--export", blocker.toString())

        assertEquals(1, run.exit, run.stderr)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("export" in run.stderr, run.stderr)
    }

    @Test
    fun `help lists export`() {
        assertTrue("--export <dir>" in runCli("--help").stdout)
    }
}
