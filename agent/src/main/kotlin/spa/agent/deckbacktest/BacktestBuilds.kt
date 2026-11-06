package spa.agent.deckbacktest

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import spa.agent.deckpick.BuildCard
import spa.agent.deckpick.SealedBuilds
import spa.agent.winrate.CsvReader
import spa.core.cards.Printing
import spa.core.text.Color

/** Why a strong build is not used, in the order the checks run. */
enum class BacktestSkip(val label: String) {
    /** Fewer than two main colours. */
    ONE_COLOUR("one-colour"),

    /** Five main colours. */
    FIVE_COLOUR("five-colour"),

    /** A deck or sideboard name above 0 resolves to no card of the set. */
    UNRESOLVED("unresolved"),

    /** No nonland card, deck or sideboard, fits inside the player colours. */
    NO_CHOICE("no-choice"),
}

/**
 * The builds a Sealed file keeps ([builds], in file order), how many strong builds it holds
 * ([strong]), the [shapes] of the strong builds with two to four main colours, and the [skipped]
 * strong builds by reason, and the number of builds with a blank [blank] bucket (SPA-061).
 */
class BacktestSelection(
    val strong: Int,
    val shapes: Map<DeckShape, Int>,
    val skipped: Map<BacktestSkip, Int>,
    val builds: List<BacktestBuild>,
    val blank: Int = 0,
) {
    /** The `read` line of the report. */
    fun summary(): String =
        "read $strong strong builds: " +
            DeckShape.entries.joinToString(", ") { "${it.label} ${shapes[it] ?: 0}" } +
            "; skipped " +
            BacktestSkip.entries.joinToString(", ") { "${it.label} ${skipped[it] ?: 0}" }

    /**
     * [limit] builds spread evenly through the kept builds in file order: the one at position `i *
     * kept / limit` for each i below [limit]. All of them when [limit] is not below their number.
     */
    fun limited(limit: Int): List<BacktestBuild> {
        require(limit > 0) { "the limit must be above 0, got $limit" }
        return if (limit >= builds.size) builds
        else List(limit) { builds[(it.toLong() * builds.size / limit).toInt()] }
    }
}

/**
 * Reads a 17Lands public Sealed game data file (`game_data_public.<SET>.Sealed.csv.gz`) for the
 * deck backtest: one row per game, so each build, one `(draft_id, build_index)`, is read once, from
 * its first row. Unlike [SealedBuilds] it keeps builds of two to four main colours with their
 * splash colours.
 *
 * A build is strong when its `user_game_win_rate_bucket` is a number of at least the minimum, by
 * default [SealedBuilds.STRONG_BUCKET]. A strong build is skipped, under the first reason that
 * applies, when it has fewer than two or five main colours, a deck or sideboard name with a copy
 * that resolves to no card, or no nonland card inside its main and splash colours.
 */
object BacktestBuilds {
    private const val DRAFT = "draft_id"
    private const val BUILD = "build_index"
    private const val MAIN = "main_colors"
    private const val SPLASH = "splash_colors"
    private const val BUCKET = "user_game_win_rate_bucket"
    private const val DECK = "deck_"
    private const val SIDEBOARD = "sideboard_"
    private const val GZIP_MAGIC = 0x1f8b
    private const val ALL_COLOURS = 5

    /** The builds of the file at [path], gzipped or plain, each name resolved by [resolve]. */
    fun read(
        path: Path,
        resolve: (String) -> Printing?,
        minBucket: Double = SealedBuilds.STRONG_BUCKET,
    ): BacktestSelection = Files.newInputStream(path).use { read(it, resolve, minBucket) }

    /** The builds of the CSV in [input], gzipped or plain. */
    fun read(
        input: InputStream,
        resolve: (String) -> Printing?,
        minBucket: Double = SealedBuilds.STRONG_BUCKET,
    ): BacktestSelection {
        val buffered = BufferedInputStream(input)
        buffered.mark(2)
        val magic = (buffered.read() shl Byte.SIZE_BITS) or buffered.read()
        buffered.reset()
        val stream = if (magic == GZIP_MAGIC) GZIPInputStream(buffered) else buffered
        val csv = CsvReader(InputStreamReader(stream, StandardCharsets.UTF_8))
        val header = requireNotNull(csv.next()) { "the file is empty" }
        return Reader(header, resolve, minBucket).readAll(csv)
    }

    /** Reads one file rows against its header. */
    private class Reader(
        header: List<String>,
        private val resolve: (String) -> Printing?,
        private val minBucket: Double,
    ) {
        private val draft = column(header, DRAFT)
        private val build = column(header, BUILD)
        private val main = column(header, MAIN)
        private val splash = column(header, SPLASH)
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

        private var strong = 0
        private var blank = 0
        private val shapes = DeckShape.entries.associateWith { 0 }.toMutableMap()
        private val skipped = BacktestSkip.entries.associateWith { 0 }.toMutableMap()
        private val builds = mutableListOf<BacktestBuild>()

        fun readAll(csv: CsvReader): BacktestSelection {
            val seen = HashSet<Pair<String, String>>()
            var line = 1
            var row = csv.next()
            while (row != null) {
                line++
                // A blank line, such as one at the end of the file, is no game.
                val blank = row.size == 1 && row[0].isEmpty()
                require(blank || row.size == width) {
                    "line $line has ${row.size} fields, the header has $width"
                }
                if (!blank && seen.add(row[draft] to row[build])) take(row, line)
                row = csv.next()
            }
            return BacktestSelection(strong, shapes, skipped, builds, blank)
        }

        /** Counts the build in [row], and keeps it unless it is weak or skipped. */
        private fun take(row: List<String>, line: Int) {
            val value = row[bucket].trim().toDoubleOrNull()
            if (value == null) blank++
            if (value == null || value < minBucket) return
            strong++
            val mainColours = colours(row[main], line)
            val splashColours = colours(row[splash], line)
            val shape = DeckShape.of(mainColours, splashColours)
            if (shape == null) {
                val reason =
                    if (mainColours.size == ALL_COLOURS) BacktestSkip.FIVE_COLOUR
                    else BacktestSkip.ONE_COLOUR
                skipped.merge(reason, 1, Int::plus)
                return
            }
            shapes.merge(shape, 1, Int::plus)
            val cards = cards(row, line)
            val candidate =
                BacktestBuild(
                    row[draft],
                    row[build],
                    mainColours,
                    splashColours,
                    cards.filterNotNull(),
                    value,
                )
            when {
                null in cards -> skipped.merge(BacktestSkip.UNRESOLVED, 1, Int::plus)
                candidate.hasChoice -> builds += candidate
                else -> skipped.merge(BacktestSkip.NO_CHOICE, 1, Int::plus)
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

        private companion object {
            fun column(header: List<String>, name: String): Int {
                val index = header.indexOf(name)
                require(index >= 0) { "no '$name' column" }
                return index
            }

            /** The colours [text] names: distinct letters of WUBRG, or none when it is blank. */
            fun colours(text: String, line: Int): Set<Color> {
                val colours = text.trim().map { Color.fromSymbol(it) }
                require(colours.none { it == null } && colours.toSet().size == colours.size) {
                    "line $line: '$text' is not a set of colours"
                }
                return colours.filterNotNull().toSet()
            }
        }
    }
}
