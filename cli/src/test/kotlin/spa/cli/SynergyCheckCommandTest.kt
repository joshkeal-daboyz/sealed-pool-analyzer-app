package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.tags.Axis
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.agent.tags.TagFiles
import spa.agent.tags.TagRole

/**
 * SPA-050 criteria 1, 2 and 4: `spa synergy-check` on the committed trk tags and owner checks,
 * against the Architect's independent script (scratchpad `tools/synergy.js`, 2026-11-05), and its
 * errors on hand-built files.
 */
class SynergyCheckCommandTest {
    /**
     * The two original trk checks, pinned by id. The totals line is not pinned here, because the
     * owner keeps adding checks to the file (Architect ruling, 2026-11-05); its format is pinned on
     * hand-built files.
     */
    @Test
    fun `trk prints the two original checks passing, with their numbers`() {
        val run = runCli("synergy-check", "--set", "trk", "--data", Fixtures.data)
        val lines = run.stdoutLines()
        val byId = { id: String -> lines.single { it.split(" ").getOrNull(1) == id } }

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("", run.stderr)
        assertEquals(
            "PASS automatons-with-itself independent  score 3  axis 0  partner 3",
            byId("automatons-with-itself"),
        )
        assertEquals(
            "PASS enters-with-beam-me-up independent  590 pairs  524 meeting min 1  " +
                "share 0.8881  mean 1.7000",
            byId("enters-with-beam-me-up"),
        )
        assertTrue(
            Regex("""^\d+ of \d+ checks pass \(\d+ independent\)$""").matches(lines.last()),
            lines.last(),
        )
        assertTrue(lines.dropLast(1).all { it.startsWith("PASS ") || it.startsWith("FAIL ") })
    }

    /** A data directory under [dir] with a small tag file for `xyz` and the check file [checks]. */
    private fun xyz(dir: Path, checks: String?): String {
        val tags =
            TagFile(
                "xyz",
                "hand-built",
                listOf(
                    Axis("tokens", "Tokens", "Makes tokens."),
                    Axis("counters", "Counters", "Uses counters."),
                    Axis("mill", "Mill", "Mills."),
                ),
                listOf(
                    TagCard("xyz/1", "Maker", listOf(TagRole("tokens", "enabler", 2)), listOf()),
                    TagCard("xyz/2", "Rewarder", listOf(TagRole("tokens", "payoff", 3)), listOf()),
                ),
            )
        writeFile(dir, "tags/xyz.json", TagFiles.write(tags))
        if (checks != null) writeFile(dir, "synergy-checks/xyz.json", checks)
        return dir.toString()
    }

    private fun pair(id: String, b: String, min: Int, kind: String = "pair") =
        """{ "id": "$id", "status": "independent", "kind": "$kind", "a": "xyz/1", "b": "$b",
             "min": $min }"""

    private fun checks(vararg checks: String) =
        """{ "set": "xyz", "checks": [ ${checks.joinToString(", ")} ] }"""

    @Test
    fun `a failing check is a result, exit 0`(@TempDir dir: Path) {
        val data = xyz(dir, checks(pair("pass", "xyz/2", 6), pair("fail", "xyz/2", 7)))
        val run = runCli("synergy-check", "--set", "xyz", "--data", data)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            listOf(
                "PASS pass independent  score 6  axis 6  partner 0",
                "FAIL fail independent  score 6  axis 6  partner 0",
                "1 of 2 checks pass (1 independent)",
            ),
            run.stdoutLines(),
        )
    }

    @Test
    fun `an unknown kind is one line and exit 1, and no check runs`(@TempDir dir: Path) {
        val data = xyz(dir, checks(pair("ok", "xyz/2", 1), pair("odd", "xyz/2", 1, "triple")))
        val run = runCli("synergy-check", "--set", "xyz", "--data", data)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("unknown kind 'triple'" in run.stderr, run.stderr)
    }

    @Test
    fun `an unknown card id is one line and exit 1, and no check runs`(@TempDir dir: Path) {
        val data = xyz(dir, checks(pair("ok", "xyz/2", 1), pair("missing", "xyz/9", 1)))
        val run = runCli("synergy-check", "--set", "xyz", "--data", data)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("xyz/9" in run.stderr, run.stderr)
    }

    @Test
    fun `a missing check file is one line and exit 1`(@TempDir dir: Path) {
        val run = runCli("synergy-check", "--set", "xyz", "--data", xyz(dir, null))

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("no synergy check file for set 'xyz'" in run.stderr, run.stderr)
    }

    @Test
    fun `a missing tag file is one line and exit 1`(@TempDir dir: Path) {
        writeFile(dir, "synergy-checks/xyz.json", checks(pair("ok", "xyz/2", 1)))
        val run = runCli("synergy-check", "--set", "xyz", "--data", dir.toString())

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("no tag file for set 'xyz'" in run.stderr, run.stderr)
    }

    @Test
    fun `an unknown set is one line and exit 1`() {
        val run = runCli("synergy-check", "--set", "zzz", "--data", Fixtures.data)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
    }

    @Test
    fun `an invalid check file, or one for another set, is one line and exit 1`(
        @TempDir dir: Path
    ) {
        val data = xyz(dir, "{ not json")
        val invalid = runCli("synergy-check", "--set", "xyz", "--data", data)
        assertEquals(ExitCode.ERROR, invalid.exit)
        assertEquals(1, invalid.stderrLines().size, invalid.stderr)
        assertTrue("invalid synergy check file" in invalid.stderr, invalid.stderr)

        Files.writeString(
            dir.resolve("synergy-checks/xyz.json"),
            """{ "set": "abc", "checks": [] }""",
        )
        val other = runCli("synergy-check", "--set", "xyz", "--data", data)
        assertEquals(ExitCode.ERROR, other.exit)
        assertEquals(1, other.stderrLines().size, other.stderr)
        assertTrue("is for set 'abc', not 'xyz'" in other.stderr, other.stderr)
    }

    @Test
    fun `a missing --set or a stray argument fails`() {
        val set = runCli("synergy-check", "--data", Fixtures.data)
        assertEquals(ExitCode.ERROR, set.exit)
        assertEquals(1, set.stderrLines().size)

        val stray = runCli("synergy-check", "extra", "--set", "trk", "--data", Fixtures.data)
        assertEquals(ExitCode.USAGE, stray.exit)
    }

    @Test
    fun `help lists the command`() {
        assertTrue("synergy-check --set <code>" in Main.USAGE)
    }

    @Test
    fun `--tags reads that file instead of the data directory's tag file`(@TempDir dir: Path) {
        val data = xyz(dir, checks(pair("pass", "xyz/2", 6), pair("fail", "xyz/2", 7)))
        val before = runCli("synergy-check", "--set", "xyz", "--data", data)
        val moved = dir.resolve("elsewhere.json")
        Files.move(dir.resolve("tags/xyz.json"), moved)

        val run = runCli("synergy-check", "--set", "xyz", "--tags", "$moved", "--data", data)

        assertEquals(ExitCode.OK, before.exit, before.stderr)
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("1 of 2 checks pass (1 independent)", run.stdoutLines().last())
        assertEquals(before.stdout, run.stdout)
    }

    @Test
    fun `a missing --tags file is one line and exit 1, and no check runs`(@TempDir dir: Path) {
        val data = xyz(dir, checks(pair("ok", "xyz/2", 1)))
        val none = dir.resolve("none.json")
        val run = runCli("synergy-check", "--set", "xyz", "--tags", "$none", "--data", data)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(listOf("spa: tag file not found: $none"), run.stderrLines())
    }

    @Test
    fun `help names --tags`() {
        assertTrue("synergy-check --set <code> [--tags <file>]" in Main.USAGE)
    }
}
