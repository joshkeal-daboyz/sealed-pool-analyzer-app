package spa.cli

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Criterion 3: both `trk` tagging packets are blind, checked the way [BlindPacketTest] checks the
 * grading packet (see [BlindCheck]): no file names win rates or 17Lands in any case, no file uses a
 * field name of the win-rate tables, and the instructions forbid the web and other tools.
 */
class TagsBlindPacketTest {
    @TempDir lateinit var temp: Path

    private lateinit var axesPacket: Path
    private lateinit var rolesPacket: Path

    @BeforeEach
    fun generate() {
        axesPacket = temp.resolve("axes")
        rolesPacket = temp.resolve("roles")
        val axes =
            writeFile(
                temp,
                "trk-axes.json",
                """
                [
                  { "id": "tokens", "name": "Tokens", "definition": "Makes creature tokens." },
                  { "id": "artifacts", "name": "Artifacts", "definition": "An artifact." },
                  { "id": "sacrifice", "name": "Sacrifice", "definition": "Sacrifices a permanent." }
                ]
                """
                    .trimIndent(),
            )
        val pass1 =
            runCli("tags-packet", "trk", "--out", axesPacket.toString(), "--data", Fixtures.data)
        val pass2 =
            runCli(
                "tags-packet",
                "trk",
                "--axes",
                axes,
                "--out",
                rolesPacket.toString(),
                "--data",
                Fixtures.data,
                // SPA-051 made v2 the default; this axes file is v1's (TagsV2CommandTest: v2).
                "--prompt",
                "v1",
            )
        assertEquals(ExitCode.OK, pass1.exit, pass1.stderr)
        assertEquals(ExitCode.OK, pass2.exit, pass2.stderr)
    }

    @Test
    fun `no file of either packet mentions win rates or 17Lands in any case`() {
        assertEquals(emptyList(), BlindCheck.forbiddenTextHits(axesPacket))
        assertEquals(emptyList(), BlindCheck.forbiddenTextHits(rolesPacket))
    }

    @Test
    fun `no file of either packet uses a field name of the win-rate tables`() {
        val fields = BlindCheck.winRateFields() - BlindCheck.packetFields
        assertTrue("gihwr" in fields && "games" in fields, "the check is not empty: $fields")

        assertEquals(emptyList(), BlindCheck.fieldNameHits(axesPacket, fields))
        assertEquals(emptyList(), BlindCheck.fieldNameHits(rolesPacket, fields))
    }

    @Test
    fun `both instructions say to use no web or other tool`() {
        listOf(axesPacket, rolesPacket).forEach { packet ->
            val text = packet.resolve("instructions.md").readText()
            assertTrue("Do not use the web or any tool other than" in text, "$packet")
            assertTrue("Use only the files in this folder." in text, "$packet")
        }
    }
}
