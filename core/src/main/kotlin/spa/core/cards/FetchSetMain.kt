package spa.core.cards

import java.nio.file.Path
import java.time.Instant

/**
 * `./gradlew :core:fetchSet -Pset=<code> [-PdataDir=<dir>]`: fetches one set from Scryfall into
 * `<dataDir>/<code>/`. Arguments: the set code, then the data directory.
 */
fun main(args: Array<String>) {
    fetchSet(args.toList(), ScryfallSetFetcher(HttpClientTransport()), Instant::now, ::println)
}

/**
 * Fetches the set named by [args] (set code, data directory) with [fetcher], writes its cache
 * stamped with [now], reports to [out], and returns the cache directory. A card count that differs
 * from the set's `card_count` is reported, not fatal: the cache tests catch it.
 */
internal fun fetchSet(
    args: List<String>,
    fetcher: ScryfallSetFetcher,
    now: () -> Instant,
    out: (String) -> Unit,
): Path {
    require(args.size == 2 && args[0].isNotBlank()) {
        "Usage: ./gradlew :core:fetchSet -Pset=<code> [-PdataDir=<dir>]"
    }
    val (code, dataDir) = args
    val fetched = fetcher.fetch(code)
    val dir = ScryfallCache.write(Path.of(dataDir), fetched, now())
    val cardCount = ScryfallCache.load(Path.of(dataDir), fetched.code).cardCount
    out("Wrote ${fetched.cards.size} cards for '$code' (card_count $cardCount) to $dir.")
    if (fetched.cards.size != cardCount) {
        out("WARNING: '$code' has ${fetched.cards.size} cards but card_count is $cardCount.")
    }
    return dir
}
