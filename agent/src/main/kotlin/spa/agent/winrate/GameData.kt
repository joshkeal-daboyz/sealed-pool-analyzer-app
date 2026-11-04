package spa.agent.winrate

import java.io.InputStream
import java.nio.file.Path

/**
 * One card's game-in-hand record in a 17Lands game data file: [games] is the number of games in
 * which the card was in the opening hand or drawn, and [wins] how many of those were won.
 */
data class CardWinRate(val name: String, val games: Int, val wins: Int) {
    /** GIH WR, the share of [games] won; NaN when [games] is 0. */
    val gihwr: Double
        get() = TODO()
}

/** A game data file's [games] count and one [CardWinRate] per card column, in header order. */
data class GameDataSummary(val games: Int, val cards: List<CardWinRate>)

/** A game data file that cannot be read as 17Lands game data. */
class GameDataException(message: String) : IllegalArgumentException(message)

/** Reads 17Lands public game data (`game_data_public.<SET>.PremierDraft.csv.gz`). */
object GameData {
    /** Summarises the file at [path], gzipped or plain. */
    fun read(path: Path): GameDataSummary = TODO()

    /** Summarises the CSV in [input], gzipped or plain. */
    fun read(input: InputStream): GameDataSummary = TODO()
}
