package spa.core.cards

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A set read from its cache: the verbatim Scryfall objects and when they were fetched. */
data class CachedSet(
    val code: String,
    val set: JsonObject,
    val cards: List<JsonObject>,
    val fetchedAt: Instant,
) {
    /** The set object's `card_count`. */
    val cardCount: Int
        get() =
            ScryfallJson.reader.decodeFromJsonElement(ScryfallSetDto.serializer(), set).cardCount
}

/**
 * The on-disk cache of one set, under `<dataDir>/<code>/`:
 * - [SET_FILE], the set object verbatim;
 * - [CARDS_FILE], a JSON array of the card objects verbatim, in search order, one per line;
 * - [FETCHED_AT_FILE], an ISO-8601 UTC timestamp;
 * - [COMPANIONS_FILE], written by hand, the codes of the sets that share this set's sealed pool.
 */
object ScryfallCache {
    const val SET_FILE = "set.json"
    const val CARDS_FILE = "cards.json"
    const val FETCHED_AT_FILE = "fetched-at.txt"
    const val COMPANIONS_FILE = "companions.json"

    /** True when [code]'s three fetched files exist under [dataDir]. */
    fun exists(dataDir: Path, code: String): Boolean =
        listOf(SET_FILE, CARDS_FILE, FETCHED_AT_FILE).all {
            Files.isRegularFile(dataDir.resolve(code).resolve(it))
        }

    /** Reads [code]'s cache from [dataDir]. Makes no network call. */
    fun load(dataDir: Path, code: String): CachedSet {
        val dir = dataDir.resolve(code)
        require(exists(dataDir, code)) { "No Scryfall cache for '$code' in $dir." }
        val set = ScryfallJson.reader.parseToJsonElement(Files.readString(dir.resolve(SET_FILE)))
        val cards =
            ScryfallJson.reader.parseToJsonElement(Files.readString(dir.resolve(CARDS_FILE)))
        return CachedSet(
            code = code,
            set = set.jsonObject,
            cards = cards.jsonArray.map { it.jsonObject },
            fetchedAt = Instant.parse(Files.readString(dir.resolve(FETCHED_AT_FILE)).trim()),
        )
    }

    /** [code]'s companion set codes; a set without a companions file has none. */
    fun companions(dataDir: Path, code: String): List<String> {
        val file = dataDir.resolve(code).resolve(COMPANIONS_FILE)
        if (!Files.isRegularFile(file)) return emptyList()
        return ScryfallJson.reader.decodeFromString(
            ListSerializer(String.serializer()),
            Files.readString(file),
        )
    }

    /**
     * Writes [fetched] to `<dataDir>/<code>/`, replacing set.json, cards.json, and fetched-at.txt
     * (to the second). Leaves companions.json alone. Returns the set's directory.
     */
    fun write(dataDir: Path, fetched: FetchedSet, fetchedAt: Instant): Path {
        val dir = Files.createDirectories(dataDir.resolve(fetched.code))
        Files.writeString(
            dir.resolve(SET_FILE),
            ScryfallJson.pretty.encodeToString(
                JsonObject.serializer(),
                fetched.set,
            ) + "\n",
        )
        Files.writeString(dir.resolve(CARDS_FILE), cardsJson(fetched.cards))
        Files.writeString(
            dir.resolve(FETCHED_AT_FILE),
            fetchedAt.truncatedTo(ChronoUnit.SECONDS).toString() + "\n",
        )
        return dir
    }

    /** Loads [code] from [dataDir] if cached; otherwise fetches it with [fetcher] and caches it. */
    fun loadOrFetch(
        dataDir: Path,
        code: String,
        fetcher: ScryfallSetFetcher,
        now: () -> Instant = Instant::now,
    ): CachedSet {
        if (!exists(dataDir, code)) write(dataDir, fetcher.fetch(code), now())
        return load(dataDir, code)
    }

    /** One card object per line, so a refresh diffs card by card. */
    private fun cardsJson(cards: List<JsonObject>): String =
        if (cards.isEmpty()) "[]\n"
        else
            cards.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]\n") {
                ScryfallJson.reader.encodeToString(JsonObject.serializer(), it)
            }
}
