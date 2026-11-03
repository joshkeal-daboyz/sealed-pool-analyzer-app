package spa.core.cards

import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** The committed `fixtures/scryfall` directory, passed in by the Gradle test task. */
val fixturesDir: Path =
    Path.of(
        requireNotNull(System.getProperty("spa.scryfall.fixtures")) {
            "Run through Gradle: the test task sets spa.scryfall.fixtures."
        }
    )

/** A real Scryfall card object stored under `src/test/resources/scryfall/cards/`. */
fun resourceCard(slug: String): JsonObject {
    val stream =
        requireNotNull(
            ScryfallCache::class.java.getResourceAsStream("/scryfall/cards/$slug.json")
        ) {
            "No test card $slug.json"
        }
    return Json.parseToJsonElement(stream.use { it.readBytes().decodeToString() }).jsonObject
}

fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

/** One request a [RecordingTransport] received, with the clock reading when it arrived. */
data class RecordedRequest(val url: String, val headers: Map<String, String>, val atNanos: Long)

/** Virtual monotonic time: sleeping advances it, nothing else does. */
class VirtualClock {
    var nanos = 0L
        private set

    val sleeps = mutableListOf<Long>()

    fun now(): Long = nanos

    fun sleep(millis: Long) {
        sleeps += millis
        nanos += millis * 1_000_000
    }
}

/**
 * Answers each URL from a script of responses, in order, and records every request. A URL with no
 * responses left fails the test.
 */
class RecordingTransport(
    private val clock: () -> Long,
    script: Map<String, List<TransportResponse>>,
) : ScryfallTransport {
    private val remaining = script.mapValues { it.value.toMutableList() }.toMutableMap()
    val requests = mutableListOf<RecordedRequest>()

    override fun get(url: String, headers: Map<String, String>): TransportResponse {
        requests += RecordedRequest(url, headers, clock())
        val queue = remaining[url]
        check(!queue.isNullOrEmpty()) { "Unexpected request to $url" }
        return queue.removeAt(0)
    }
}

/** A transport that fails the test if anything calls it. */
object ThrowingTransport : ScryfallTransport {
    override fun get(url: String, headers: Map<String, String>): TransportResponse =
        throw AssertionError("Network call to $url; the cache should have been used.")
}

fun ok(body: String) = TransportResponse(200, body)

val tooManyRequests = TransportResponse(429, """{"object":"error","status":429}""")

/** A minimal Scryfall card object for paging tests. */
fun cardJson(id: String, set: String = "xyz", number: String = id) =
    """{"object":"card","id":"$id","oracle_id":"oracle-$id","name":"Card $id","layout":"normal",""" +
        """"set":"$set","collector_number":"$number","rarity":"common","cmc":1.0,""" +
        """"colors":["G"],"color_identity":["G"],"type_line":"Creature — Elf","mana_cost":"{G}"}"""

/** A set object with [cardCount] and a search URI. */
fun setJson(code: String, cardCount: Int, searchUri: String = searchUri(code)) =
    """{"object":"set","code":"$code","name":"Set $code","set_type":"expansion",""" +
        """"card_count":$cardCount,"search_uri":"$searchUri"}"""

fun searchUri(code: String) = "https://api.scryfall.com/cards/search?q=e%3A$code&unique=prints"

fun page(cards: List<String>, nextPage: String? = null) =
    """{"object":"list","has_more":${nextPage != null},""" +
        (nextPage?.let { """"next_page":"$it",""" } ?: "") +
        """"data":[${cards.joinToString(",")}]}"""
