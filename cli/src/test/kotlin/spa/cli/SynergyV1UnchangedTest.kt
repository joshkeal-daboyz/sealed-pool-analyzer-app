package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-051 criterion 4: on the committed v1 `trk` tags, `spa synergy-table --set trk` and `spa
 * synergy-check --set trk` print exactly what they printed before SPA-051. The digests are of the
 * output of commit ed11e4c (origin/main before SPA-051), with line feeds only. Since SPA-053 the v1
 * tags are read from their frozen copy, `fixtures/tags/v1/trk.json`, through `--tags`.
 *
 * Since SPA-054 both runs read no otags (`--otags none`), and the checks are read from their frozen
 * copy, `fixtures/synergy-checks/v1/trk.json` (the live check file is the owner's and will change),
 * copied into a data directory of its own as `synergy-checks/trk.json`.
 */
class SynergyV1UnchangedTest {
    @TempDir lateinit var temp: Path

    private val v1 = arrayOf("--tags", Fixtures.v1Tags("trk"), "--otags", Otags.NONE)

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `synergy-table on trk is byte-identical to before`() {
        val run = runCli("synergy-table", "--set", "trk", *v1, "--data", Fixtures.data)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(7260, run.stdoutLines().size)
        assertEquals(
            "0e28b656c056a345cce297d7d1daea84b312bdbcbcb783cbff63b9f527c1b9d6",
            sha256(run.stdout),
        )
    }

    @Test
    fun `synergy-check on trk is byte-identical to before`() {
        val checks = temp.resolve("synergy-checks").createDirectories().resolve("trk.json")
        Fixtures.root.resolve("synergy-checks/v1/trk.json").copyTo(checks)
        val run = runCli("synergy-check", "--set", "trk", *v1, "--data", temp.toString())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            "769540085d5908116ceabdf01c4da90d6336db593a0fcf7b0dea51a240e7b3a9",
            sha256(run.stdout),
        )
        assertEquals("3 of 6 checks pass (3 independent)", run.stdoutLines().last())
    }

    @Test
    fun `the frozen check file is a byte copy of the live one at SPA-054's start`() {
        // git blob 48faa22 of fixtures/synergy-checks/trk.json at c067d4e.
        val bytes = Files.readAllBytes(Fixtures.root.resolve("synergy-checks/v1/trk.json"))
        val blob = "blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8) + bytes
        val sha1 =
            MessageDigest.getInstance("SHA-1").digest(blob).joinToString("") { "%02x".format(it) }

        assertEquals("48faa22bf4cd6b5a6d8c001255f756abc560fb1a", sha1)
    }
}
