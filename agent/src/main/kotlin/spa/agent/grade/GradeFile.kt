package spa.agent.grade

import kotlinx.serialization.Serializable

/**
 * One set's grades, as committed under `fixtures/grades/`: a [header] naming the set, the source
 * and provenance every grade shares, and one [GradeEntry] per card in the set's sealed pool.
 */
@Serializable data class GradeFile(val header: GradeFileHeader, val grades: List<GradeEntry>)

/** [setCode] is the set graded; [createdAt] is an ISO-8601 UTC instant. */
@Serializable
data class GradeFileHeader(
    val setCode: String,
    val source: GradeSource,
    val provenance: String,
    val createdAt: String,
)

/** One card's grade. [setCode] is the printing's own set, which may be a companion set. */
@Serializable
data class GradeEntry(
    val name: String,
    val collectorNumber: String,
    val setCode: String,
    val score: Double,
    val confidence: Double,
)
