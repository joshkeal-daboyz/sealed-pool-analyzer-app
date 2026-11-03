package spa.core.brief

/**
 * One problem with a brief: where it is, as a JSON pointer (`""` is the whole document), and why.
 */
data class BriefError(val pointer: String, val message: String) {
    override fun toString(): String = "${pointer.ifEmpty { "(document)" }}: $message"
}

/** A brief that failed to load. [errors] lists every problem found, never only the first. */
class BriefValidationException(val errors: List<BriefError>, cause: Throwable? = null) :
    IllegalArgumentException(
        "Format brief has ${errors.size} error(s):\n" + errors.joinToString("\n") { "  $it" },
        cause,
    )
