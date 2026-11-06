package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.otags.cares.CaresCheck
import spa.agent.otags.cares.CaresChecks

/**
 * SPA-055 criterion 5, the seam: a test-only import check is added to the list with no other code
 * change, and the import runs it with the others.
 */
class CaresImportSeamTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `a test-only check joins the import with no other code change`() {
        val data = CaresSyn.data(temp.resolve("data"))
        val packet = temp.resolve("packet")
        assertEquals(
            ExitCode.OK,
            runCli("cares-packet", "syn", "--out", "$packet", "--data", "$data").exit,
        )
        Files.writeString(
            packet.resolve("cards-01.cares.json"),
            """[ { "id": "syn/3", "add": [{ "fact": "creature", "strength": 2, "reason": "r" }] } ]""",
        )
        val args = listOf("syn", "--packet", "$packet", "--provenance", "seam", "--data", "$data")

        val plain = run(CaresImportCommand(CaresChecks.ALL), args)
        assertEquals(ExitCode.OK, plain.first, plain.third)
        Files.delete(data.resolve("otags/syn.fable.json"))

        val noSyn3 = CaresCheck { input ->
            input.responses.filter { it.id == "syn/3" }.map { "${it.file}: ${it.id}: test-only" }
        }
        val seam = run(CaresImportCommand(CaresChecks.ALL + noSyn3), args)

        assertEquals(ExitCode.ERROR, seam.first)
        assertEquals("spa: cards-01.cares.json: syn/3: test-only", seam.third.trim())
        assertTrue(Files.notExists(data.resolve("otags/syn.fable.json")))
    }

    private fun run(command: CaresImportCommand, args: List<String>): Triple<Int, String, String> {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val exit =
            command.run(
                args,
                PrintStream(out, true, StandardCharsets.UTF_8),
                PrintStream(err, true, StandardCharsets.UTF_8),
            )
        return Triple(
            exit,
            out.toString(StandardCharsets.UTF_8),
            err.toString(StandardCharsets.UTF_8),
        )
    }
}
