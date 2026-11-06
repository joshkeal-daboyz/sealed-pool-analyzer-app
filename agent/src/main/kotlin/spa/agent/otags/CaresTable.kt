package spa.agent.otags

/** One fact a cares tag cares about, with its [strength]. */
data class CaresEntry(val fact: String, val strength: Int)

/** The cares table, `fixtures/otags/cares.json` (SPA-054). */
class CaresTable(val cares: Map<String, List<CaresEntry>>) {
    /** The cares tags of a card with [otags]: each fact with its largest strength. */
    fun of(otags: Collection<String>): Map<String, Int> = emptyMap()

    /** [of] for each card of [otags], by id. */
    fun pool(otags: Map<String, List<String>>): Map<String, Map<String, Int>> = emptyMap()

    /** One line for each fact of the table that is not a known fact name. */
    fun unknown(bulkSlugs: Set<String>): List<String> = emptyList()

    companion object {
        val EMPTY = CaresTable(emptyMap())

        /** The table in [text]. */
        fun parse(text: String): CaresTable = EMPTY
    }
}
