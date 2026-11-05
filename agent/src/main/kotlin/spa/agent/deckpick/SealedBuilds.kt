package spa.agent.deckpick

import java.io.InputStream
import java.nio.file.Path
import spa.core.cards.Pool
import spa.core.cards.Printing
import spa.core.metrics.ColorPair

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
        get() = TODO()

    /** The candidates' copies, deck and sideboard. */
    val candidateCopies: Int
        get() = TODO()

    /** N: the candidates' deck copies. */
    val n: Int
        get() = TODO()

    /** The build's pool: every deck and sideboard copy that is not a basic land. */
    fun pool(): Pool = TODO()
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

    /**
     * The builds of the file at [path], gzipped or plain, with each card name resolved by [resolve]
     * (null: not a card of the set).
     */
    fun read(path: Path, resolve: (String) -> Printing?): BuildSelection = TODO()

    /** The builds of the CSV in [input], gzipped or plain. */
    fun read(input: InputStream, resolve: (String) -> Printing?): BuildSelection = TODO()
}
