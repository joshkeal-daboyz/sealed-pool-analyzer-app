package spa.app.names

import spa.core.intake.UnresolvedReason

/** Why a line was not read, in words, by the spec's reason-words table. */
object ReasonText {
    /** The reason's words, or null for a code the table does not name. */
    fun words(reason: UnresolvedReason): String? = TODO()

    /** "<words> (<code>)", or the code alone when the table has no words for it. */
    fun of(reason: UnresolvedReason): String = TODO()
}
