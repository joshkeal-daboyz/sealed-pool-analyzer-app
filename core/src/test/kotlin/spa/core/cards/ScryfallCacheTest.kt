package spa.core.cards

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir

class ScryfallCacheTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `loads the committed syn cache without calling the transport`() {
        val fetcher = ScryfallSetFetcher(ThrowingTransport)

        val cached = ScryfallCache.loadOrFetch(fixturesDir, "syn", fetcher)

        assertEquals("syn", cached.code)
        assertEquals(25, cached.cards.size)
        assertEquals(25, cached.cardCount)
        assertEquals("syn", cached.set["code"]!!.jsonPrimitive.content)
        assertEquals(Instant.parse("2026-11-03T18:10:00Z"), cached.fetchedAt)
        assertEquals(ScryfallCache.load(fixturesDir, "syn"), cached)
    }

    @Test
    fun `the committed trk and sds caches hold exactly card_count cards`() {
        for ((code, expected) in listOf("trk" to 581, "sds" to 30)) {
            val cached = ScryfallCache.load(fixturesDir, code)
            assertEquals(expected, cached.cardCount, code)
            assertEquals(cached.cardCount, cached.cards.size, code)
            assertTrue(cached.cards.all { it["set"]!!.jsonPrimitive.content == code }, code)
        }
    }

    @Test
    fun `companions come from companions json`() {
        assertEquals(listOf("sds"), ScryfallCache.companions(fixturesDir, "trk"))
        assertEquals(emptyList(), ScryfallCache.companions(fixturesDir, "sds"))
        assertEquals(emptyList(), ScryfallCache.companions(fixturesDir, "syn"))
        assertEquals(emptyList(), ScryfallCache.companions(temp, "nope"))
    }

    @Test
    fun `write then load gives back the objects verbatim`() {
        val cards = listOf(json(cardJson("1")), resourceCard("valki-god-of-lies"))
        val fetched = FetchedSet(json(setJson("xyz", 2)), cards)

        val dir = ScryfallCache.write(temp, fetched, Instant.parse("2026-11-03T12:34:56.789Z"))
        val loaded = ScryfallCache.load(temp, "xyz")

        assertEquals(temp.resolve("xyz"), dir)
        assertEquals(fetched.set, loaded.set)
        assertEquals(cards, loaded.cards)
        assertEquals(Instant.parse("2026-11-03T12:34:56Z"), loaded.fetchedAt)
        assertEquals("2026-11-03T12:34:56Z\n", dir.resolve("fetched-at.txt").readText())
        // Numbers keep Scryfall's spelling, and each card is one line.
        val lines = dir.resolve("cards.json").readLines()
        assertEquals(4, lines.size)
        assertTrue("\"cmc\":1.0" in lines[1])
        assertTrue("Tibalt, Cosmic Impostor" in lines[2])
    }

    @Test
    fun `write leaves companions json alone and handles an empty set`() {
        Files.createDirectories(temp.resolve("xyz"))
        Files.writeString(temp.resolve("xyz/companions.json"), """["abc"]""")

        ScryfallCache.write(temp, FetchedSet(json(setJson("xyz", 0)), emptyList()), Instant.EPOCH)

        assertEquals(listOf("abc"), ScryfallCache.companions(temp, "xyz"))
        assertEquals("[]\n", temp.resolve("xyz/cards.json").readText())
        assertEquals(emptyList(), ScryfallCache.load(temp, "xyz").cards)
    }

    @Test
    fun `loadOrFetch fetches and writes a missing set once`() {
        val clock = VirtualClock()
        val script =
            mapOf(
                "https://api.scryfall.com/sets/xyz" to listOf(ok(setJson("xyz", 1))),
                searchUri("xyz") to listOf(ok(page(listOf(cardJson("1"))))),
            )
        val transport = RecordingTransport(clock::now, script)
        val fetcher = ScryfallSetFetcher(transport, clock = clock::now, sleep = clock::sleep)
        val now = Instant.parse("2026-11-03T00:00:00Z")

        val first = ScryfallCache.loadOrFetch(temp, "xyz", fetcher) { now }
        val second = ScryfallCache.loadOrFetch(temp, "xyz", fetcher) { now }

        assertEquals(2, transport.requests.size)
        assertEquals(1, first.cards.size)
        assertEquals(now, first.fetchedAt)
        assertEquals(first, second)
    }

    @Test
    fun `a missing cache is an error, not an empty set`() {
        assertFalse(ScryfallCache.exists(temp, "xyz"))
        assertFailsWith<IllegalArgumentException> { ScryfallCache.load(temp, "xyz") }
    }
}
