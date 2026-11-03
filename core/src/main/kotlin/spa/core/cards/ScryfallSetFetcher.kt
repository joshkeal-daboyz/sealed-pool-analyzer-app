package spa.core.cards

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** A set object and its card objects, verbatim from Scryfall, cards in search order. */
data class FetchedSet(val set: JsonObject, val cards: List<JsonObject>) {
    /** The set's code, from the set object. */
    val code: String
        get() = ScryfallJson.reader.decodeFromJsonElement(ScryfallSetDto.serializer(), set).code
}

/** A Scryfall request that did not succeed: a non-2xx status, or a 429 after every retry. */
class ScryfallException(message: String) : RuntimeException(message)

/**
 * Fetches one set from Scryfall: the set object from `/sets/{code}`, then every page of its
 * `search_uri`, followed as given through `next_page` while `has_more` is true.
 *
 * Every request carries [HEADERS]. Requests are at least [minIntervalMillis] apart (never less than
 * [MIN_INTERVAL_MILLIS]). A 429 waits [RETRY_DELAY_MILLIS] and retries the same request, up to
 * [MAX_RETRIES] times. [clock] (monotonic nanoseconds) and [sleep] (milliseconds) are injectable so
 * tests can run on virtual time.
 */
class ScryfallSetFetcher(
    private val transport: ScryfallTransport,
    private val minIntervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val clock: () -> Long = System::nanoTime,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    private var lastRequestAt: Long? = null

    init {
        require(minIntervalMillis >= MIN_INTERVAL_MILLIS) {
            "Scryfall asks for at least $MIN_INTERVAL_MILLIS ms between requests."
        }
    }

    /** Fetches set [code] (lowercase letters and digits, as Scryfall writes set codes). */
    fun fetch(code: String): FetchedSet {
        require(code.matches(SET_CODE)) { "Not a Scryfall set code: '$code'." }
        val set = getJson("$API_ROOT/sets/$code")
        val dto = ScryfallJson.reader.decodeFromJsonElement(ScryfallSetDto.serializer(), set)
        var page: String? =
            requireNotNull(dto.searchUri) { "Set object for '$code' has no search_uri." }
        val cards = mutableListOf<JsonObject>()
        while (page != null) {
            val list =
                ScryfallJson.reader.decodeFromJsonElement(
                    ScryfallListDto.serializer(),
                    getJson(page),
                )
            cards += list.data
            page =
                if (list.hasMore) requireNotNull(list.nextPage) { "has_more without next_page." }
                else null
        }
        return FetchedSet(set, cards)
    }

    private fun getJson(url: String): JsonObject {
        var retries = 0
        while (true) {
            pace()
            val response = transport.get(url, HEADERS)
            if (response.status in HTTP_OK) {
                return ScryfallJson.reader.parseToJsonElement(response.body).jsonObject
            }
            if (response.status != HTTP_TOO_MANY_REQUESTS || retries == MAX_RETRIES) {
                throw ScryfallException(
                    "GET $url returned ${response.status} after $retries retries: " +
                        response.body.take(ERROR_BODY_CHARS)
                )
            }
            retries++
            sleep(RETRY_DELAY_MILLIS)
        }
    }

    private fun pace() {
        lastRequestAt?.let { last ->
            val waitNanos = minIntervalMillis * NANOS_PER_MILLI - (clock() - last)
            // Rounded up, so a whole-millisecond sleep never ends short of the interval.
            if (waitNanos > 0) sleep(Math.ceilDiv(waitNanos, NANOS_PER_MILLI))
        }
        lastRequestAt = clock()
    }

    companion object {
        const val API_ROOT = "https://api.scryfall.com"
        const val USER_AGENT =
            "SealedPoolAnalyzer/0.1.0 (https://github.com/joshkeal-daboyz/sealed-pool-analyzer-app)"
        val HEADERS: Map<String, String> =
            mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json")

        /** Scryfall's floor between any two requests. */
        const val MIN_INTERVAL_MILLIS = 100L

        /**
         * The default gap. Scryfall's search endpoint, which serves every page after the first,
         * rate-limits harder than its 10-per-second general rule, so the default stays well clear.
         */
        const val DEFAULT_INTERVAL_MILLIS = 500L
        const val RETRY_DELAY_MILLIS = 1_000L
        const val MAX_RETRIES = 3

        private const val NANOS_PER_MILLI = 1_000_000L
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val ERROR_BODY_CHARS = 300
        private val HTTP_OK = 200..299
        private val SET_CODE = Regex("[a-z0-9]+")
    }
}
