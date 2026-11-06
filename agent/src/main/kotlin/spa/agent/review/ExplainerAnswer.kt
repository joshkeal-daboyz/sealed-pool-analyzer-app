package spa.agent.review

/** The Explainer's note on the deck ranked [rank]: one [note] about the pool [cards]. */
data class DeckNote(val rank: Int, val note: String, val cards: List<String>)

/**
 * A place where two analysts' findings pull against each other: the [topic], the [agents] (ids, at
 * least two), the pool [cards] involved and what decides it, in [note].
 */
data class Disagreement(
    val topic: String,
    val agents: List<String>,
    val cards: List<String>,
    val note: String,
)

/**
 * A validated Explainer answer: its [confidence] (0 to 1), the [verdict] for the player, the kept
 * deck notes and the kept disagreements.
 */
data class ExplainerResult(
    val confidence: Double,
    val verdict: String,
    val decks: List<DeckNote>,
    val disagreements: List<Disagreement>,
)

/** Which list an item came from. */
enum class ItemKind {
    DECK,
    DISAGREEMENT,
}

/** Why an Explainer item was thrown away, in the order the reasons are tried. */
enum class ExplainerDropReason {
    UNKNOWN_RANK,
    DUPLICATE_RANK,
    TOO_FEW_AGENTS,
    UNKNOWN_AGENT,
    NO_CARD,
    UNKNOWN_CARD,
}

/** An item that was thrown away: its [kind], the [reason] and its [text] (note or topic). */
data class DroppedItem(val kind: ItemKind, val reason: ExplainerDropReason, val text: String)

/** What [ExplainerChecker.check] made of an answer: the kept [result] and what it [dropped]. */
data class CheckedExplainer(val result: ExplainerResult, val dropped: List<DroppedItem>)

/** What running the Explainer came to. */
sealed interface ExplainerOutcome {
    /** The Explainer gave a valid [result]; [dropped] are the items the checks threw away. */
    data class Explained(val result: ExplainerResult, val dropped: List<DroppedItem>) :
        ExplainerOutcome

    /** There is no explanation for this run; the review says so with [reason]. */
    data class Missing(val reason: String) : ExplainerOutcome
}
