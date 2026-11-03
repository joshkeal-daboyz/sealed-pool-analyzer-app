package spa.cli

import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.core.brief.BriefLoader
import spa.core.cards.CardIndex

class ValidateBriefCommandTest {
    @TempDir lateinit var temp: Path

    private fun validate(vararg args: String): CliRun =
        runCli("validate-brief", *args, "--data", Fixtures.data)

    private val invalidBrief: String =
        Path.of(requireNotNull(javaClass.getResource("/briefs/invalid.json")).toURI()).toString()

    @Test
    fun `a valid brief prints OK and warns once per name the set does not have`() {
        val file = Fixtures.brief("syn")
        val run = validate(file, "--set", "syn")

        assertEquals(ExitCode.OK, run.exit)
        assertEquals("", run.stderr)
        val lines = run.stdoutLines()
        assertEquals("OK $file", lines.first())
        val warnings = lines.drop(1)
        val brief = BriefLoader.load(Path.of(file))
        val index = CardIndex.load(Fixtures.root.resolve("scryfall"), "syn")
        val missing =
            brief.archetypes
                .flatMap { it.signpostUncommons + it.keyCommons }
                .filter { index.byName(it) == null }
        assertTrue(missing.isNotEmpty(), "syn's brief names cards that are not in syn")
        assertEquals(missing.size, warnings.size)
        warnings.zip(missing).forEach { (line, name) ->
            assertTrue(line.startsWith("warning: /archetypes/"), line)
            assertTrue(line.endsWith(": '$name' is not a card in set 'syn'"), line)
        }
    }

    @Test
    fun `each warning names the pointer of the missing name`() {
        val run = validate(Fixtures.brief("syn"), "--set", "syn")

        assertTrue(
            "warning: /archetypes/0/signpostUncommons/0: 'Warden of the Cinch' is not a card in set 'syn'" in
                run.stdoutLines()
        )
    }

    @Test
    fun `names the set has raise no warning`() {
        val brief = BriefLoader.load(Path.of(Fixtures.brief("trk")))
        val index = CardIndex.load(Fixtures.root.resolve("scryfall"), "trk")

        val run = validate(Fixtures.brief("trk"), "--set", "trk")

        assertEquals(ExitCode.OK, run.exit)
        assertEquals(
            ValidateBriefCommand.missingNames(brief, index).size,
            run.stdoutLines().size - 1,
        )
        assertTrue(
            brief.archetypes.flatMap { it.signpostUncommons }.any { index.byName(it) != null }
        )
    }

    @Test
    fun `an invalid brief exits 1 and prints every error with its pointer`() {
        val run = validate(invalidBrief, "--set", "trk")

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        val expected = runCatching {
            BriefLoader.parse(Path.of(invalidBrief).toFile().readText())
        }
            .exceptionOrNull()
            .let { it as spa.core.brief.BriefValidationException }
            .errors
        assertTrue(expected.size > 1)
        val lines = run.stderrLines()
        assertEquals("$invalidBrief: ${expected.size} error(s)", lines.first())
        assertEquals(expected.map { "error: $it" }, lines.drop(1))
        assertTrue(lines.any { it.startsWith("error: /version") }, lines.joinToString("\n"))
    }

    @Test
    fun `a document that is not JSON is one error on the whole document`() {
        val file = writeFile(temp, "brief.json", "not json")
        val run = validate(file, "--set", "trk")

        assertEquals(ExitCode.ERROR, run.exit)
        assertTrue(run.stderrLines()[1].startsWith("error: (document): not JSON"))
    }

    @Test
    fun `a missing file, a missing --set, or a missing set cache exits 1`() {
        val missing = temp.resolve("nope.json")

        val noFile = validate("$missing", "--set", "trk")
        assertEquals(ExitCode.ERROR, noFile.exit)
        assertEquals(listOf("spa: brief not found: $missing"), noFile.stderrLines())
        assertEquals(ExitCode.ERROR, validate(Fixtures.brief("syn")).exit)
        val noCache = validate(Fixtures.brief("syn"), "--set", "zzz")
        assertEquals(ExitCode.ERROR, noCache.exit)
        assertTrue(noCache.stderr.startsWith("spa: no Scryfall cache for set 'zzz'"))
    }

    @Test
    fun `bad flags exit 2`() {
        assertEquals(ExitCode.USAGE, validate("--set", "syn").exit)
        assertEquals(ExitCode.USAGE, validate(Fixtures.brief("syn"), "--set", "syn", "--x").exit)
    }
}
