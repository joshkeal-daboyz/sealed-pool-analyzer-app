package spa.agent.winrate

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream

/**
 * One card's game-in-hand record in a 17Lands game data file: [games] is the number of games in
 * which the card was in the opening hand or drawn, and [wins] how many of those were won.
 */
data class CardWinRate(val name: String, val games: Int, val wins: Int) {
    /** GIH WR, the share of [games] won; NaN when [games] is 0. */
    val gihwr: Double
        get() = if (games == 0) Double.NaN else wins.toDouble() / games
}

/** A game data file's [games] count and one [CardWinRate] per card column, in header order. */
data class GameDataSummary(val games: Int, val cards: List<CardWinRate>)

/** A game data file that cannot be read as 17Lands game data. */
class GameDataException(message: String) : IllegalArgumentException(message)

/**
 * Reads 17Lands public game data (`game_data_public.<SET>.PremierDraft.csv.gz`): one row per game,
 * a `won` column of `True` or `False`, and per card the columns `opening_hand_<name>` and
 * `drawn_<name>` (and others this reader ignores). A card counts in a game when its opening hand
 * and drawn counts sum to at least 1.
 */
object GameData {
    private const val WON = "won"
    private const val OPENING = "opening_hand_"
    private const val DRAWN = "drawn_"
    private const val GZIP_MAGIC = 0x1f8b

    /** Summarises the file at [path], gzipped or plain. */
    fun read(path: Path): GameDataSummary = Files.newInputStream(path).use { read(it) }

    /** Summarises the CSV in [input], gzipped or plain. */
    fun read(input: InputStream): GameDataSummary {
        val buffered = BufferedInputStream(input)
        buffered.mark(2)
        val magic = (buffered.read() shl Byte.SIZE_BITS) or buffered.read()
        buffered.reset()
        val stream = if (magic == GZIP_MAGIC) GZIPInputStream(buffered) else buffered
        return summarise(CsvReader(InputStreamReader(stream, StandardCharsets.UTF_8)))
    }

    private fun summarise(csv: CsvReader): GameDataSummary {
        val tally = Tally(csv.next() ?: throw GameDataException("the file is empty"))
        var line = 1
        var row = csv.next()
        while (row != null) {
            line++
            // A blank line, such as one at the end of the file, is no game.
            if (row.size != 1 || row[0].isNotEmpty()) tally.add(row, line)
            row = csv.next()
        }
        return tally.summary()
    }

    /** The running counts of each card column in [header]. */
    private class Tally(private val header: List<String>) {
        private val won = header.indexOf(WON)
        private val names =
            header.filter { it.startsWith(OPENING) }.map { it.removePrefix(OPENING) }
        private val opening = IntArray(names.size) { header.indexOf(OPENING + names[it]) }
        private val drawn = IntArray(names.size) { header.indexOf(DRAWN + names[it]) }
        private val games = IntArray(names.size)
        private val wins = IntArray(names.size)
        private var rows = 0

        init {
            if (won < 0) throw GameDataException("no '$WON' column")
        }

        /** Counts the game in [row], the file's line [line]. */
        fun add(row: List<String>, line: Int) {
            if (row.size != header.size) {
                throw GameDataException(
                    "line $line has ${row.size} fields, the header has ${header.size}"
                )
            }
            rows++
            val isWin = won(row[won], line)
            for (i in names.indices) {
                val inHand = count(row, opening[i], line) + count(row, drawn[i], line) >= 1
                if (inHand) games[i]++
                if (inHand && isWin) wins[i]++
            }
        }

        fun summary() =
            GameDataSummary(rows, names.indices.map { CardWinRate(names[it], games[it], wins[it]) })

        private fun won(value: String, line: Int): Boolean =
            when (value) {
                "True" -> true
                "False" -> false
                else -> throw GameDataException("line $line: '$WON' is '$value', not True or False")
            }

        /** The count in column [index] of [row], or 0 when the file has no such column. */
        private fun count(row: List<String>, index: Int, line: Int): Int {
            if (index < 0) return 0
            val value = row[index]
            return value.toIntOrNull()
                ?: throw GameDataException("line $line: '$value' is not a card count")
        }
    }
}
