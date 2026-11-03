package spa.cli

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ArgsTest {
    private val values = setOf("set", "data")
    private val flags = setOf("no-baseline")

    private fun parse(vararg args: String) = Args.parse(args.toList(), values, flags)

    private fun usageMessage(vararg args: String): String {
        val failure = assertThrows<CliFailure> { parse(*args) }
        assertEquals(ExitCode.USAGE, failure.exitCode)
        return failure.message.orEmpty()
    }

    @Test
    fun `splits positionals, spaced and inline values, and flags`() {
        val args = parse("pool.csv", "--set", "trk", "--data=dir", "--no-baseline", "-")

        assertEquals(listOf("pool.csv", "-"), args.positionals)
        assertEquals("trk", args.value("set"))
        assertEquals("dir", args.value("data"))
        assertTrue(args.has("no-baseline"))
    }

    @Test
    fun `absent options are null and absent flags are false`() {
        val args = parse("pool.csv")

        assertNull(args.value("set"))
        assertFalse(args.has("no-baseline"))
    }

    @Test
    fun `an unknown long or short option is a usage error`() {
        assertEquals("unknown option '--color'", usageMessage("--color"))
        assertEquals("unknown option '-v'", usageMessage("-v"))
    }

    @Test
    fun `a value option without a value is a usage error`() {
        assertEquals("option '--set' needs a value", usageMessage("pool.csv", "--set"))
        assertEquals("option '--set' needs a value", usageMessage("--set", "--no-baseline"))
        assertEquals("option '--set' needs a value", usageMessage("--set="))
    }

    @Test
    fun `a flag given a value is a usage error`() {
        assertEquals("option '--no-baseline' takes no value", usageMessage("--no-baseline=yes"))
    }

    @Test
    fun `an option given twice is a usage error`() {
        assertEquals("option '--set' given twice", usageMessage("--set", "a", "--set=b"))
        assertEquals(
            "option '--no-baseline' given twice",
            usageMessage("--no-baseline", "--no-baseline"),
        )
    }
}
