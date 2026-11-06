package spa.agent.review

import spa.core.baseline.MetricFlattener

/** Builds the number tables the analysis agents quote from. */
object NumberTables {
    /**
     * The entries of the flattened metrics of [input] whose pointer matches any of [patterns],
     * sorted by pointer, each with the baseline's stat for its pointer (null when there is none). A
     * pattern matches a pointer when it equals it, or ends in `/` and the pointer starts with it;
     * `*` matches exactly one segment.
     */
    fun fromMetrics(input: ReviewInput, patterns: List<String>): NumberTable {
        val stats = input.baseline?.stats.orEmpty()
        val entries =
            MetricFlattener.flatten(input.metrics)
                .filterKeys { pointer -> patterns.any { matches(it, pointer) } }
                .toSortedMap()
                .map { (pointer, value) -> NumberEntry(pointer, value, stats[pointer]) }
        return NumberTable(entries)
    }

    private fun matches(pattern: String, pointer: String): Boolean {
        val wanted = pattern.split('/')
        val actual = pointer.split('/')
        val prefix = pattern.endsWith('/')
        val segments = if (prefix) wanted.dropLast(1) else wanted
        val sizeFits = if (prefix) actual.size > segments.size else actual.size == segments.size
        return sizeFits &&
            segments.indices.all { segments[it] == "*" || segments[it] == actual[it] }
    }
}
