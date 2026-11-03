package spa.cli

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MainTest {
    @Test
    fun `--help exits 0 and lists the three commands on stdout`() {
        val run = runCli("--help")

        assertEquals(ExitCode.OK, run.exit)
        listOf("analyze", "validate-brief", "fetch-set").forEach {
            assertTrue(it in run.stdout, "help lists $it")
        }
        assertEquals("", run.stderr)
    }

    @Test
    fun `-h and help print the same text as --help`() {
        val help = runCli("--help")

        assertEquals(help, runCli("-h"))
        assertEquals(help, runCli("help"))
    }

    @Test
    fun `--help after a command prints usage and exits 0`() {
        val run = runCli("analyze", "--help")

        assertEquals(ExitCode.OK, run.exit)
        assertEquals(Main.USAGE + System.lineSeparator(), run.stdout)
    }

    @Test
    fun `no arguments prints usage to stderr and exits 2`() {
        val run = runCli()

        assertEquals(ExitCode.USAGE, run.exit)
        assertEquals("", run.stdout)
        assertTrue("spa: no command given" in run.stderr)
        assertTrue(Main.USAGE in run.stderr)
    }

    @Test
    fun `an unknown command prints usage to stderr and exits 2`() {
        val run = runCli("analyse", "pool.csv")

        assertEquals(ExitCode.USAGE, run.exit)
        assertEquals("", run.stdout)
        assertTrue("spa: unknown command 'analyse'" in run.stderr)
        assertTrue(Main.USAGE in run.stderr)
    }

    @Test
    fun `a bad flag on a command exits 2 and points at --help`() {
        val run = runCli("validate-brief", "x.json", "--set", "trk", "--verbose")

        assertEquals(ExitCode.USAGE, run.exit)
        assertEquals(
            listOf("spa: unknown option '--verbose'", "Run 'spa --help' for usage."),
            run.stderrLines(),
        )
    }

    @Test
    fun `the version comes from the Gradle build`() {
        assertTrue(Regex("\\d+\\.\\d+\\.\\d+").matches(Output.version), Output.version)
    }
}
