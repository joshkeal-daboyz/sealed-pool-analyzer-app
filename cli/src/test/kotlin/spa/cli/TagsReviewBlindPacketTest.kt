package spa.cli

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-052 criterion 2: the trk review packet is blind under [BlindCheck], as the tagging packets
 * are ([TagsBlindPacketTest]). [SynergyCheckIndependenceTest] covers the owner's checks.
 */
class TagsReviewBlindPacketTest {
    @TempDir lateinit var temp: Path

    private lateinit var packet: Path

    @BeforeEach
    fun generate() {
        packet = temp.resolve("review")
        val run =
            runCli("tags-review-packet", "trk", "--out", packet.toString(), "--data", Fixtures.data)
        assertEquals(ExitCode.OK, run.exit, run.stderr)
    }

    @Test
    fun `no file mentions win rates or 17Lands in any case`() {
        assertEquals(emptyList(), BlindCheck.forbiddenTextHits(packet))
    }

    @Test
    fun `no file uses a field name of the win-rate tables`() {
        val fields = BlindCheck.winRateFields() - BlindCheck.packetFields
        assertTrue("gihwr" in fields && "provenance" in fields, "the check is not empty: $fields")

        assertEquals(emptyList(), BlindCheck.fieldNameHits(packet, fields))
    }

    @Test
    fun `the instructions say to use no web or other tool`() {
        val text = packet.resolve("instructions.md").readText()

        assertTrue("Do not use the web or any tool other than" in text)
        assertTrue("Use only the files in this folder." in text)
    }
}
