package spa.core.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonPrimitive

class ScryfallSetFetcherTest {
    private val setUrl = "https://api.scryfall.com/sets/xyz"
    private val page2 = "https://api.scryfall.com/cards/search?page=2&q=e%3Axyz&unique=prints"
    private val page3 = "https://api.scryfall.com/cards/search?page=3&q=e%3Axyz&unique=prints"

    private fun threePages(): Map<String, List<TransportResponse>> =
        mapOf(
            setUrl to listOf(ok(setJson("xyz", 5))),
            searchUri("xyz") to listOf(ok(page(listOf(cardJson("1"), cardJson("2")), page2))),
            page2 to listOf(ok(page(listOf(cardJson("3"), cardJson("4")), page3))),
            page3 to listOf(ok(page(listOf(cardJson("5"))))),
        )

    private fun fetcher(clock: VirtualClock, transport: ScryfallTransport) =
        ScryfallSetFetcher(transport, clock = clock::now, sleep = clock::sleep)

    @Test
    fun `requests the set object then follows every search page in order`() {
        val clock = VirtualClock()
        val transport = RecordingTransport(clock::now, threePages())

        val fetched = fetcher(clock, transport).fetch("xyz")

        assertEquals(
            listOf(setUrl, searchUri("xyz"), page2, page3),
            transport.requests.map { it.url },
        )
        assertEquals(
            listOf("1", "2", "3", "4", "5"),
            fetched.cards.map { it["id"]!!.jsonPrimitive.content },
        )
        assertEquals(json(setJson("xyz", 5)), fetched.set)
        assertEquals("xyz", fetched.code)
    }

    @Test
    fun `sends the User-Agent and Accept headers on every request`() {
        val clock = VirtualClock()
        val transport = RecordingTransport(clock::now, threePages())

        fetcher(clock, transport).fetch("xyz")

        assertEquals(4, transport.requests.size)
        transport.requests.forEach {
            assertEquals(
                "SealedPoolAnalyzer/0.1.0 (https://github.com/joshkeal-daboyz/sealed-pool-analyzer-app)",
                it.headers["User-Agent"],
            )
            assertEquals("application/json", it.headers["Accept"])
        }
    }

    @Test
    fun `waits at least 100 ms between requests`() {
        val clock = VirtualClock()
        val transport = RecordingTransport(clock::now, threePages())

        fetcher(clock, transport).fetch("xyz")

        val gaps = transport.requests.zipWithNext { a, b -> (b.atNanos - a.atNanos) / 1_000_000 }
        assertEquals(3, gaps.size)
        assertTrue(gaps.all { it >= 100 }, "gaps were $gaps ms")
    }

    @Test
    fun `waits at least 100 ms between requests on the real clock`() {
        val transport = RecordingTransport(System::nanoTime, threePages())

        ScryfallSetFetcher(transport, minIntervalMillis = 100).fetch("xyz")

        val gaps = transport.requests.zipWithNext { a, b -> b.atNanos - a.atNanos }
        assertTrue(gaps.all { it >= 100_000_000 }, "gaps were $gaps ns")
    }

    @Test
    fun `does not sleep when the previous request is already old enough`() {
        val clock = VirtualClock()
        val transport = RecordingTransport(clock::now, threePages())
        val slowTransport = ScryfallTransport { url, headers ->
            transport.get(url, headers).also { clock.sleep(2_000) }
        }

        fetcher(clock, slowTransport).fetch("xyz")

        assertEquals(listOf(2_000L, 2_000L, 2_000L, 2_000L), clock.sleeps)
    }

    @Test
    fun `a 429 waits one second and retries the same request`() {
        val clock = VirtualClock()
        val script =
            threePages() +
                (page2 to
                    listOf(
                        tooManyRequests,
                        tooManyRequests,
                        ok(page(listOf(cardJson("3")), page3)),
                    ))
        val transport = RecordingTransport(clock::now, script)

        val fetched = fetcher(clock, transport).fetch("xyz")

        assertEquals(
            listOf(setUrl, searchUri("xyz"), page2, page2, page2, page3),
            transport.requests.map { it.url },
        )
        val retryGaps =
            transport.requests.zipWithNext { a, b -> (b.atNanos - a.atNanos) / 1_000_000 }
        assertTrue(retryGaps[2] >= 1_000 && retryGaps[3] >= 1_000, "gaps were $retryGaps ms")
        assertEquals(2, clock.sleeps.count { it == 1_000L })
        assertEquals(4, fetched.cards.size)
    }

    @Test
    fun `fails after three retries of a 429`() {
        val clock = VirtualClock()
        val script = threePages() + (setUrl to List(4) { tooManyRequests })
        val transport = RecordingTransport(clock::now, script)

        val error = assertFailsWith<ScryfallException> { fetcher(clock, transport).fetch("xyz") }

        assertEquals(List(4) { setUrl }, transport.requests.map { it.url })
        assertEquals(3, clock.sleeps.count { it == 1_000L })
        assertTrue("429" in error.message!!)
    }

    @Test
    fun `any other error status fails at once`() {
        val clock = VirtualClock()
        val script = mapOf(setUrl to listOf(TransportResponse(404, """{"object":"error"}""")))
        val transport = RecordingTransport(clock::now, script)

        val error = assertFailsWith<ScryfallException> { fetcher(clock, transport).fetch("xyz") }

        assertEquals(1, transport.requests.size)
        assertTrue("404" in error.message!!)
    }

    @Test
    fun `rejects a set code that is not lowercase letters and digits`() {
        assertFailsWith<IllegalArgumentException> {
            ScryfallSetFetcher(ThrowingTransport).fetch("../sets")
        }
    }

    @Test
    fun `rejects an interval shorter than Scryfall's floor`() {
        assertFailsWith<IllegalArgumentException> {
            ScryfallSetFetcher(ThrowingTransport, minIntervalMillis = 99)
        }
    }

    @Test
    fun `a set object without a search URI fails`() {
        val clock = VirtualClock()
        val noSearch =
            """{"object":"set","code":"xyz","name":"X","set_type":"expansion","card_count":0}"""
        val transport = RecordingTransport(clock::now, mapOf(setUrl to listOf(ok(noSearch))))

        assertFailsWith<IllegalArgumentException> { fetcher(clock, transport).fetch("xyz") }
    }

    @Test
    fun `a page that has more but no next page fails`() {
        val clock = VirtualClock()
        val broken = """{"object":"list","has_more":true,"data":[${cardJson("1")}]}"""
        val script =
            mapOf(setUrl to listOf(ok(setJson("xyz", 1))), searchUri("xyz") to listOf(ok(broken)))
        val transport = RecordingTransport(clock::now, script)

        assertFailsWith<IllegalArgumentException> { fetcher(clock, transport).fetch("xyz") }
    }
}
