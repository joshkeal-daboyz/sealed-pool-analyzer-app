package spa.agent.review

/**
 * The pool's card names as an answer may write them: a full name, or the front face's name of a
 * multi-face card.
 */
internal class PoolNames(poolNames: Collection<String>) {
    private val full = poolNames.toSet()
    private val fronts =
        poolNames
            .filter { FACE_JOIN in it }
            .groupBy { it.substringBefore(FACE_JOIN) }
            .mapValues { it.value.first() }

    /** The full name [name] stands for, or null when it names no pool card. */
    fun resolve(name: String): String? = if (name in full) name else fronts[name]

    private companion object {
        const val FACE_JOIN = " // "
    }
}
