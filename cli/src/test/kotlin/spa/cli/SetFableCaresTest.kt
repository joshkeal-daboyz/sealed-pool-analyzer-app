package spa.cli

import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import spa.agent.otags.cares.FableCaresFile

/**
 * SPA-059 criteria 1 to 3: the frozen copies of the msh, hob and fra otags (`fixtures/otags/v1`),
 * the three Fable cares files (`fixtures/otags/<code>.fable.json`, written by `spa cares-import`
 * from a Claude Fable 5.1 run on 2026-11-06) pinned by hash, card and addition counts, and the
 * cares cards the frozen copies give without them.
 */
class SetFableCaresTest {
    private val data = DataDir(Fixtures.root)

    private class Pin(
        val set: String,
        val sha256: String,
        val cards: Int,
        val additions: Int,
        val frozenCares: Int,
    )

    private val pins =
        listOf(
            Pin(
                "msh",
                "7b4cb89e013e1728ae12be88637d7da9ea02b1c4a07aeab7812d0b4f8c3359b6",
                91,
                126,
                66,
            ),
            Pin(
                "hob",
                "7ca15069d4867ca378f5c9c6f9ad9f63087522cdb453b2ff9dbb6de79731f009",
                77,
                104,
                54,
            ),
            Pin(
                "fra",
                "9daf2bb5e5d0c529e27167d39ef1b6bf9666dae9b5464d8492131dab2edd3c95",
                115,
                184,
                67,
            ),
        )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `the frozen msh, hob and fra otags are byte copies of the live files`() {
        for (pin in pins) {
            assertContentEquals(
                Files.readAllBytes(Fixtures.root.resolve("otags/${pin.set}.json")),
                Files.readAllBytes(Fixtures.root.resolve("otags/v1/${pin.set}.json")),
                pin.set,
            )
        }
    }

    @Test
    fun `each Fable file is the Fable run's file, with the pinned card and addition counts`() {
        for (pin in pins) {
            val path = Fixtures.root.resolve("otags/${pin.set}.fable.json")
            assertEquals(pin.sha256, sha256(Files.readAllBytes(path)), pin.set)
            val file = FableCaresFile.parse(Files.readString(path))

            assertEquals(pin.set, file.set)
            assertEquals(pin.cards, file.cards.size, pin.set)
            assertEquals(pin.additions, file.cards.sumOf { it.cares.size }, pin.set)
        }
    }

    @Test
    fun `the frozen copies give 66, 54 and 67 cards with a cares tag`() {
        for (pin in pins) {
            val frozen = Fixtures.root.resolve("otags/v1/${pin.set}.json").toString()
            val inputs = requireNotNull(Otags.load(data, pin.set, frozen, fable = false))

            assertEquals(pin.frozenCares, inputs.cares.values.count { it.isNotEmpty() }, pin.set)
        }
    }
}
