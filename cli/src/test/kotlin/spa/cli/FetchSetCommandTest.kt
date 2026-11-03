package spa.cli

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.core.cards.ScryfallCache
import spa.core.cards.ScryfallSetFetcher
import spa.core.cards.ScryfallTransport
import spa.core.cards.TransportResponse

class FetchSetCommandTest {
    @TempDir lateinit var temp: Path

    private val requested = mutableListOf<String>()

    /** A Scryfall that knows sets `abc` (two cards) and `def` (one card) and nothing else. */
    private val scryfall = ScryfallTransport { url, _ ->
        requested += url
        val sets = mapOf("abc" to 2, "def" to 1)
        val setCode = url.substringAfter("/sets/", "")
        val searchCode = url.substringAfter("/search/", "")
        when {
            setCode in sets -> TransportResponse(200, setJson(setCode, sets.getValue(setCode)))
            searchCode in sets ->
                TransportResponse(200, cardsJson(searchCode, sets.getValue(searchCode)))
            else -> TransportResponse(404, "{\"object\":\"error\",\"status\":404}")
        }
    }

    private fun setJson(code: String, count: Int) =
        """{"code":"$code","name":"Set $code","card_count":$count,"set_type":"expansion",""" +
            """"search_uri":"https://api.scryfall.com/search/$code"}"""

    private fun cardsJson(code: String, count: Int) =
        (1..count).joinToString(",", """{"data":[""", "]}") { """{"id":"$code-$it"}""" }

    private fun fetchSet(vararg args: String, transport: ScryfallTransport = scryfall): CliRun =
        runCli(
            "fetch-set",
            *args,
            "--data",
            "$temp",
            fetcher = { ScryfallSetFetcher(transport, sleep = {}) },
        )

    private val cacheDir: Path
        get() = temp.resolve("scryfall")

    @Test
    fun `fetches the set into the cache and prints its card count`() {
        val run = fetchSet("abc")

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(listOf("abc: 2 cards in ${cacheDir.resolve("abc")}"), run.stdoutLines())
        val cached = ScryfallCache.load(cacheDir, "abc")
        assertEquals(2, cached.cards.size)
        assertTrue(cached.fetchedAt <= Instant.now())
        assertEquals("[]\n", Files.readString(cacheDir.resolve("abc/companions.json")))
    }

    @Test
    fun `--companions writes companions json and fetches each companion`() {
        val run = fetchSet("ABC", "--companions", "def")

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            listOf(
                "abc: 2 cards in ${cacheDir.resolve("abc")}",
                "def: 1 cards in ${cacheDir.resolve("def")}",
            ),
            run.stdoutLines(),
        )
        assertEquals(listOf("def"), ScryfallCache.companions(cacheDir, "abc"))
        assertEquals(1, ScryfallCache.load(cacheDir, "def").cards.size)
    }

    @Test
    fun `without --companions an existing companions file is kept`() {
        writeFile(cacheDir, "abc/companions.json", "[\"def\"]\n")

        assertEquals(ExitCode.OK, fetchSet("abc").exit)
        assertEquals(listOf("def"), ScryfallCache.companions(cacheDir, "abc"))
        assertEquals(2, requested.size, "the companion is not fetched without --companions")
    }

    @Test
    fun `an API error exits 1 with one line`() {
        val run = fetchSet("xyz")

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(1, run.stderrLines().size)
        assertTrue(run.stderr.startsWith("spa: Scryfall error for set 'xyz': GET "), run.stderr)
    }

    @Test
    fun `an API error on a companion exits 1 after the set itself is cached`() {
        val run = fetchSet("abc", "--companions", "xyz")

        assertEquals(ExitCode.ERROR, run.exit)
        assertTrue(ScryfallCache.exists(cacheDir, "abc"))
        assertTrue(run.stderr.startsWith("spa: Scryfall error for set 'xyz'"), run.stderr)
    }

    @Test
    fun `a network error exits 1 with one line`() {
        val down = ScryfallTransport { _, _ -> throw IOException("connection refused") }
        val run = fetchSet("abc", transport = down)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(
            listOf("spa: network error for set 'abc': connection refused"),
            run.stderrLines(),
        )
    }

    @Test
    fun `a code Scryfall cannot have exits 1 without a request`() {
        val run = fetchSet("a-b")

        assertEquals(ExitCode.ERROR, run.exit)
        assertTrue(run.stderr.startsWith("spa: cannot fetch set 'a-b': "), run.stderr)
        assertEquals(emptyList(), requested)
    }

    @Test
    fun `bad flags exit 2`() {
        assertEquals(ExitCode.USAGE, fetchSet().exit)
        assertEquals(ExitCode.USAGE, fetchSet("abc", "--companions", "def,,ghi").exit)
        assertEquals(ExitCode.USAGE, fetchSet("abc", "--refresh").exit)
        assertEquals(emptyList(), requested)
    }
}
