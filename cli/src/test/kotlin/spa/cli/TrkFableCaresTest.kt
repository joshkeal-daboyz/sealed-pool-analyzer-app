package spa.cli

import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import spa.agent.otags.cares.FableCaresFile

/**
 * SPA-058 criteria 1 to 3: the frozen copy of trk's otags (`fixtures/otags/v1/trk.json`), trk's
 * Fable cares file (`fixtures/otags/trk.fable.json`, written by `spa cares-import` from a Claude
 * Fable 5.1 run on 2026-11-06) pinned by hash, card and addition counts, and the cares cards the
 * live and the frozen otags give.
 */
class TrkFableCaresTest {
    private val data = DataDir(Fixtures.root)
    private val fableFile = Fixtures.root.resolve("otags/trk.fable.json")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `the frozen trk otags are a byte copy of the live file`() {
        assertContentEquals(
            Files.readAllBytes(Fixtures.root.resolve("otags/trk.json")),
            Files.readAllBytes(Fixtures.root.resolve("otags/v1/trk.json")),
        )
    }

    @Test
    fun `the frozen otags directory holds no Fable file`() {
        assertEquals(
            emptyList(),
            Files.list(Fixtures.root.resolve("otags/v1")).use { stream ->
                stream.map { it.fileName.toString() }.filter { "fable" in it }.toList()
            },
        )
    }

    @Test
    fun `the trk Fable file is the Fable run's file, with 105 cards and 180 additions`() {
        assertEquals(
            "a263a75882e36065525199042aa4969551bd2a36af9ed7a8770a6555a2359e96",
            sha256(Files.readAllBytes(fableFile)),
        )
        val file = FableCaresFile.parse(Files.readString(fableFile))

        assertEquals("trk", file.set)
        assertEquals(105, file.cards.size)
        assertEquals(180, file.cards.sumOf { it.cares.size })
    }

    private fun caresCards(inputs: OtagInputs?): Int =
        requireNotNull(inputs).cares.values.count { it.isNotEmpty() }

    @Test
    fun `the live trk otags with the Fable file give 186 cards with cares, the frozen copy 109`() {
        assertEquals(186, caresCards(Otags.load(data, "trk", null)))

        val frozen = Fixtures.root.resolve("otags/v1/trk.json").toString()
        assertEquals(109, caresCards(Otags.load(data, "trk", frozen, fable = false)))
    }
}
