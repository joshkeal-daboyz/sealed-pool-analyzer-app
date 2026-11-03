package spa.core.intake

import kotlin.test.assertEquals
import spa.core.cards.CardIndex
import spa.core.cards.Printing
import spa.core.cards.fixturesDir

/** The `syn` index: 25 printings, no companions. */
val synIndex: CardIndex = CardIndex.load(fixturesDir, "syn")

/** The syn printing with collector number [number]. */
fun syn(number: String): Printing =
    requireNotNull(synIndex.byCollectorNumber("syn", number)) { "No syn $number" }

/**
 * syn with `sds` as a companion, plus three test-only printings that share syn's cards: Llanowar
 * Elves as `syn 7s` and `syn 103`, and Grizzly Bears as `sds 0`, loaded first so load order cannot
 * decide which printing a name resolves to.
 */
val synWithCompanion: CardIndex by lazy {
    val sds = CardIndex.load(fixturesDir, "sds")
    val elves = syn("7")
    val extras =
        listOf(
            syn("1").copy(scryfallId = "test-sds-bears-0", setCode = "sds", collectorNumber = "0"),
            elves.copy(scryfallId = "test-syn-elves-103", collectorNumber = "103"),
            elves.copy(scryfallId = "test-syn-elves-7s", collectorNumber = "7s"),
        )
    CardIndex(listOf("syn", "sds"), extras + synIndex.printings + sds.printings)
}

/** The text of a file under `src/test/resources/intake/`, byte-order mark included. */
fun intakeResource(name: String): String {
    val stream =
        requireNotNull(PoolParser::class.java.getResourceAsStream("/intake/$name")) {
            "No intake resource $name"
        }
    return stream.use { it.readBytes().decodeToString() }
}

/** The pool as `set number` to count, in entry order. */
fun ParseResult.counts(): List<Pair<String, Int>> =
    pool.entries.map { "${it.printing.setCode} ${it.printing.collectorNumber}" to it.count }

/** The unresolved lines as line number to reason code. */
fun ParseResult.reasons(): List<Pair<Int, String>> = unresolved.map {
    it.lineNumber to it.reason.code
}

/**
 * Resolved and unresolved line numbers, together with [structural] lines such as a header, are
 * exactly the non-blank, non-comment lines of [text], each once.
 */
fun assertEveryLineAccounted(
    text: String,
    result: ParseResult,
    structural: List<Int> = emptyList(),
) {
    val content =
        PoolLines.split(text).withIndex().filter { (_, line) ->
            line.isNotBlank() && !PoolLines.isComment(line)
        }
    val accounted = result.resolvedLines + result.unresolved.map { it.lineNumber } + structural
    assertEquals(content.map { it.index + 1 }, accounted.sorted())
    assertEquals(PoolLines.split(text).size - content.size, result.skippedLines)
}
