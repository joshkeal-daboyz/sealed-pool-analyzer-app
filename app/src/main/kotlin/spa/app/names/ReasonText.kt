package spa.app.names

import spa.core.intake.UnresolvedReason

/** Why a line was not read, in words, by the spec's reason-words table. */
object ReasonText {
    /** The reason's words, or null for a code the table does not name. */
    fun words(reason: UnresolvedReason): String? =
        when (reason) {
            UnresolvedReason.UNKNOWN_NAME -> "No trk or sds card has this name"
            UnresolvedReason.UNKNOWN_COLLECTOR_NUMBER ->
                "No trk or sds card has this collector number"
            UnresolvedReason.WRONG_SET -> "This card is from another set"
            else -> null
        }

    /** "<words> (<code>)", or the code alone when the table has no words for it. */
    fun of(reason: UnresolvedReason): String =
        words(reason)?.let { "$it (${reason.code})" } ?: reason.code
}
