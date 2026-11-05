package spa.cli

import java.security.MessageDigest
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * SPA-051 criterion 4: on the committed v1 `trk` tags, `spa synergy-table --set trk` and `spa
 * synergy-check --set trk` print exactly what they printed before SPA-051. The digests are of the
 * output of commit ed11e4c (origin/main before SPA-051), with line feeds only.
 */
class SynergyV1UnchangedTest {
    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `synergy-table on trk is byte-identical to before`() {
        val run = runCli("synergy-table", "--set", "trk", "--data", Fixtures.data)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(7260, run.stdoutLines().size)
        assertEquals(
            "0e28b656c056a345cce297d7d1daea84b312bdbcbcb783cbff63b9f527c1b9d6",
            sha256(run.stdout),
        )
    }

    @Test
    fun `synergy-check on trk is byte-identical to before`() {
        val run = runCli("synergy-check", "--set", "trk", "--data", Fixtures.data)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            "769540085d5908116ceabdf01c4da90d6336db593a0fcf7b0dea51a240e7b3a9",
            sha256(run.stdout),
        )
        assertEquals("3 of 6 checks pass (3 independent)", run.stdoutLines().last())
    }
}
