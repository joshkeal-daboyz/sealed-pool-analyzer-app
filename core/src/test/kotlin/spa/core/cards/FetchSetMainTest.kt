package spa.core.cards

import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class FetchSetMainTest {
    @TempDir lateinit var temp: Path

    private val now = Instant.parse("2026-11-03T09:00:00Z")

    private fun fetcher(cardCount: Int, cards: List<String>): ScryfallSetFetcher {
        val clock = VirtualClock()
        val script =
            mapOf(
                "https://api.scryfall.com/sets/xyz" to listOf(ok(setJson("xyz", cardCount))),
                searchUri("xyz") to listOf(ok(page(cards))),
            )
        return ScryfallSetFetcher(
            RecordingTransport(clock::now, script),
            clock = clock::now,
            sleep = clock::sleep,
        )
    }

    @Test
    fun `writes the cache for the set and reports the counts`() {
        val out = mutableListOf<String>()

        val dir =
            fetchSet(
                listOf("xyz", temp.toString()),
                fetcher(2, listOf(cardJson("1"), cardJson("2"))),
                { now },
                out::add,
            )

        assertEquals(temp.resolve("xyz"), dir)
        val cached = ScryfallCache.load(temp, "xyz")
        assertEquals(2, cached.cards.size)
        assertEquals(now, cached.fetchedAt)
        assertEquals(listOf("Wrote 2 cards for 'xyz' (card_count 2) to $dir."), out)
    }

    @Test
    fun `warns when the card count differs from card_count`() {
        val out = mutableListOf<String>()

        fetchSet(
            listOf("xyz", temp.toString()),
            fetcher(3, listOf(cardJson("1"))),
            { now },
            out::add,
        )

        assertEquals(2, out.size)
        assertTrue(out[1].startsWith("WARNING: 'xyz' has 1 cards but card_count is 3"))
    }

    @Test
    fun `bad arguments fail with the usage line before any request`() {
        val throwing = ScryfallSetFetcher(ThrowingTransport)

        for (args in listOf(emptyList(), listOf("xyz"), listOf("", temp.toString()))) {
            val error =
                assertFailsWith<IllegalArgumentException> { fetchSet(args, throwing, { now }) {} }
            assertTrue(error.message!!.startsWith("Usage:"))
        }
        assertFailsWith<IllegalArgumentException> { main(emptyArray()) }
    }
}
