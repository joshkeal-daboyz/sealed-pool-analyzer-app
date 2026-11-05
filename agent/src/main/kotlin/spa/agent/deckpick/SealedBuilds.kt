package spa.agent.deckpick

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import spa.agent.winrate.CsvReader
import spa.core.cards.Card
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Printing
import spa.core.metrics.ColorPair
import spa.core.text.Color

/** Why a build of a 17Lands Sealed game data file is not used, in the order the checks run. */
enum class SkipReason(val label: String) {
    /** `user_game_win_rate_bucket` is blank or not a number. */
    NO_BUCKET("no win-rate bucket"),

    /** The bucket is below [SealedBuilds.STRONG_BUCKET]. */
    WEAK("bucket below 0.60"),

    /** `main_colors` is not exactly two letters of WUBRG. */
    COLOURS("not two main colours"),

    /** A deck or sideboard name with a count above 0 resolves to no card of the set. */
    UNRESOLVED("unresolved names"),

    /** N is 0, or every candidate copy is in the deck. */
    NO_CHOICE("no choice (N is 0 or every candidate)"),
}

/** One card of a build: its [printing], and its [deck] and [sideboard] copies. */
data class BuildCard(val printing: Printing, val deck: Int, val sideboard: Int)

/**
 * One build, a `(draft_id, build_index)` of a strong player: its colour [pair] (`main_colors`) and
 * every card with a deck or sideboard count above 0, in the file's column order.
 */
data class Build(
    val draftId: String,
    val buildIndex: String,
    val pair: ColorPair,
    val cards: List<BuildCard>,
) {
    /** The cards that are not lands and whose colours are a subset of [pair] (colourless too). */
    val candidates: List<BuildCard>
        get() = cards.filter { fits(it.printing.card) }

    /** The candidates' copies, deck and sideboard. */
    val candidateCopies: Int
        get() = candidates.sumOf { it.deck + it.sideboard }

    /** N: the candidates' deck copies. */
    val n: Int
        get() = candidates.sumOf { it.deck }

    /** The build's pool: every deck and sideboard copy that is not a basic land. */
    fun pool(): Pool =
        Pool(
            cards
                .filterNot { it.printing.card.isBasicLand }
                .map { PoolEntry(it.printing, it.deck + it.sideboard) }
        )

    private fun fits(card: Card): Boolean = !card.isLand && pair.colors.containsAll(card.colors)
}

/** The builds a file keeps, and how many it skipped for each [SkipReason]. */
data class BuildSelection(val builds: List<Build>, val skipped: Map<SkipReason, Int>)

/**
 * Reads a 17Lands public Sealed game data file (`game_data_public.<SET>.Sealed.csv.gz`): one row
 * per game, so each build, one `(draft_id, build_index)`, is read once, from its first row.
 *
 * A build is kept when, checked in this order (a build counts under the first it fails): its
 * `user_game_win_rate_bucket` is a number of at least [STRONG_BUCKET]; its `main_colors` are
 * exactly two letters of WUBRG; every `deck_<name>` and `sideboard_<name>` above 0 resolves; and
 * its N is neither 0 nor its candidate copies.
 */
object SealedBuilds {
    /** The lowest win-rate bucket of a strong player (G-004 Decided 2). */
    const val STRONG_BUCKET = 0.60

    private const val DRAFT = "draft_id"
    private const val BUILD = "build_index"
    private const val COLOURS = "main_colors"
    private const val BUCKET = "user_game_win_rate_bucket"
    private const val DECK = "deck_"
    private const val SIDEBOARD = "sideboard_"
    private const val GZIP_MAGIC = 0x1f8b

    /**
     * The builds of the file at [path], gzipped or plain, with each card name resolved by [resolve]
     * (null: not a card of the set).
     */
    fun read(path: Path, resolve: (String) -> Printing?): BuildSelection =
        Files.newInputStream(path).use { read(it, resolve) }

    /** The builds of the CSV in [input], gzipped or plain. */
    fun read(input: InputStream, resolve: (String) -> Printing?): BuildSelection {
        val buffered = BufferedInputStream(input)
        buffered.mark(2)
        val magic = (buffered.read() shl Byte.SIZE_BITS) or buffered.read()
        buffered.reset()
        val stream = if (magic == GZIP_MAGIC) GZIPInputStream(buffered) else buffered
        val csv = CsvReader(InputStreamReader(stream, StandardCharsets.UTF_8))
        val header = requireNotNull(csv.next()) { "the file is empty" }
        return Reader(header, resolve).readAll(csv)
    }

    /** Reads one file's rows against its [header]. */
    private class Reader(header: List<String>, private val resolve: (String) -> Printing?) {
        private val draft = column(header, DRAFT)
        private val build = column(header, BUILD)
        private val colours = column(header, COLOURS)
        private val bucket = column(header, BUCKET)
        private val names =
            header
                .mapNotNull { column ->
                    when {
                        column.startsWith(DECK) -> column.removePrefix(DECK)
                        column.startsWith(SIDEBOARD) -> column.removePrefix(SIDEBOARD)
                        else -> null
                    }
                }
                .distinct()
        private val deck = IntArray(names.size) { header.indexOf(DECK + names[it]) }
        private val sideboard = IntArray(names.size) { header.indexOf(SIDEBOARD + names[it]) }
        private val printings = mutableMapOf<String, Printing?>()
        private val width = header.size

        fun readAll(csv: CsvReader): BuildSelection {
            val seen = HashSet<Pair<String, String>>()
            val builds = mutableListOf<Build>()
            val skipped = SkipReason.entries.associateWith { 0 }.toMutableMap()
            var line = 1
            var row = csv.next()
            while (row != null) {
                line++
                // A blank line, such as one at the end of the file, is no game.
                val blank = row.size == 1 && row[0].isEmpty()
                require(blank || row.size == width) {
                    "line $line has ${row.size} fields, the header has $width"
                }
                val first = !blank && seen.add(row[draft] to row[build])
                when (val result = if (first) outcome(row, line) else null) {
                    is Outcome.Kept -> builds += result.build
                    is Outcome.Skipped -> skipped.merge(result.reason, 1, Int::plus)
                    null -> Unit
                }
                row = csv.next()
            }
            return BuildSelection(builds, skipped)
        }

        /** The build in [row], or the first check it fails. */
        private fun outcome(row: List<String>, line: Int): Outcome {
            val value = row[bucket].trim().toDoubleOrNull()
            val pair = pair(row[colours])
            val early =
                when {
                    value == null -> SkipReason.NO_BUCKET
                    value < STRONG_BUCKET -> SkipReason.WEAK
                    pair == null -> SkipReason.COLOURS
                    else -> null
                }
            if (early != null || pair == null) {
                return Outcome.Skipped(early ?: SkipReason.COLOURS)
            }
            val cards = cards(row, line)
            val build = Build(row[draft], row[build], pair, cards.filterNotNull())
            return when {
                null in cards -> Outcome.Skipped(SkipReason.UNRESOLVED)
                build.n in 1..<build.candidateCopies -> Outcome.Kept(build)
                else -> Outcome.Skipped(SkipReason.NO_CHOICE)
            }
        }

        /** Each name of [row] with a copy, as a [BuildCard], or null when it does not resolve. */
        private fun cards(row: List<String>, line: Int): List<BuildCard?> =
            names.indices
                .map { i -> Triple(i, count(row, deck[i], line), count(row, sideboard[i], line)) }
                .filter { (_, d, s) -> d + s > 0 }
                .map { (i, d, s) -> printing(names[i])?.let { BuildCard(it, d, s) } }

        private fun printing(name: String): Printing? {
            if (name !in printings) printings[name] = resolve(name)
            return printings[name]
        }

        /** The count in column [index] of [row]; 0 when the file has no such column. */
        private fun count(row: List<String>, index: Int, line: Int): Int {
            if (index < 0) return 0
            val value = row[index]
            return requireNotNull(value.toIntOrNull()) {
                "line $line: '$value' is not a card count"
            }
        }

        /** A row's build, or the reason it is skipped. */
        private sealed interface Outcome {
            class Kept(val build: Build) : Outcome

            class Skipped(val reason: SkipReason) : Outcome
        }

        private companion object {
            fun column(header: List<String>, name: String): Int {
                val index = header.indexOf(name)
                require(index >= 0) { "no '$name' column" }
                return index
            }

            /** The pair [text] names as exactly two different letters of WUBRG, else null. */
            fun pair(text: String): ColorPair? {
                if (text.length != 2 || text.any { it !in "WUBRG" }) return null
                val colours = text.mapNotNull { Color.fromSymbol(it) }.toSet()
                return ColorPair.entries.firstOrNull { it.colors == colours }
            }
        }
    }
}
