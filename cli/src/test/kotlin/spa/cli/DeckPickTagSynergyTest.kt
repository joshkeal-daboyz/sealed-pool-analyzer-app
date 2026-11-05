package spa.cli

import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-043: `deck-pick` grades through the same parts, so a weights file with `tagSynergy` above 0
 * reads the set's tag file, `<data>/tags/<code>.json`; set `syn` has none, which is one line and
 * exit 1. At `tagSynergy` 0 nothing is read (every `DeckPickCommandTest` run).
 */
class DeckPickTagSynergyTest {
    @TempDir lateinit var temp: Path

    private fun resource(name: String): String =
        Path.of(requireNotNull(javaClass.getResource("/deck-pick/$name")).toURI())
            .normalize()
            .toString()

    @Test
    fun `tagSynergy above 0 needs the set's tag file`() {
        val weights = writeFile(temp, "tag.json", """{ "tagSynergy": 0.5, "cap": 1 }""")

        val run =
            runCli(
                "deck-pick",
                resource("syn-sealed.csv"),
                "--set",
                "syn",
                "--data",
                Fixtures.data,
                "--grades",
                resource("../grades/syn-model.json"),
                "--weights",
                weights,
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("no tag file for set 'syn'" in run.stderr, run.stderr)
        assertEquals("", run.stdout)
    }
}
