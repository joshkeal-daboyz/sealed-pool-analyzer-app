package spa.core.cards

/** [count] copies of one [printing] in a pool. */
data class PoolEntry(val printing: Printing, val count: Int)

/** A sealed pool: the printings opened and how many of each. */
data class Pool(val entries: List<PoolEntry>) {
    /** Every copy in the pool, basic lands included. */
    val totalCount: Int
        get() = entries.sumOf { it.count }

    /** Copies per card, keyed by oracle id, summed across printings, in first-seen order. */
    fun cardCounts(): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        entries.forEach { counts.merge(it.printing.card.oracleId, it.count, Int::plus) }
        return counts
    }
}
