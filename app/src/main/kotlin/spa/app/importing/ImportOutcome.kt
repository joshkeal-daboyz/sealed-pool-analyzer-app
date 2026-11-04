package spa.app.importing

import spa.core.intake.ParseResult
import spa.core.intake.PoolFormat
import spa.core.intake.UnresolvedLine
import spa.core.metrics.PoolMetrics

/** Where a pool's text came from. */
sealed interface PoolSource {
    /** A file the user opened or shared, by its display [name]. */
    data class File(val name: String) : PoolSource

    /** Text pasted into the app. */
    data object Pasted : PoolSource
}

/** Why an import gave no pool, as the Pool screen's error cards classify it. */
enum class ImportFailureKind {
    /** The text resolved no entries and had no unresolved lines. */
    EMPTY,

    /** The text resolved no entries and every unresolved line is from another set. */
    WRONG_SET,

    /** The text resolved no entries and some unresolved line is not from another set. */
    NOT_A_POOL,

    /** The bytes could not be read as UTF-8 text. */
    UNREADABLE,

    /** The remembered pool, re-read at launch, gave no pool, or its file could not be read. */
    SAVED_POOL_UNREADABLE,
}

/** What one import gave. */
sealed interface ImportOutcome {
    /**
     * The pool resolved at least one entry. [format] is the format it was read as, and [text] the
     * exact text that was parsed.
     */
    data class Success(
        val result: ParseResult,
        val metrics: PoolMetrics,
        val format: PoolFormat,
        val source: PoolSource,
        val text: String,
    ) : ImportOutcome

    /** The import gave no pool; [unresolved] holds the lines that did not resolve, if any. */
    data class Failure(
        val kind: ImportFailureKind,
        val source: PoolSource,
        val unresolved: List<UnresolvedLine>,
    ) : ImportOutcome

    /** The bundled set data did not load, so no pool can be read. [cause] says why. */
    data class DataMissing(val cause: Throwable) : ImportOutcome
}
