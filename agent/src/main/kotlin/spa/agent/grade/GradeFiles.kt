package spa.agent.grade

import java.nio.file.Path

/** One problem in a grade file, at a JSON pointer into the document. */
data class GradeProblem(val pointer: String, val message: String)

/** A grade file that breaks its schema; the message lists every [problems] entry. */
class GradeFileException(val problems: List<GradeProblem>, cause: Throwable? = null) :
    IllegalArgumentException(problems.joinToString("\n"), cause)

/** Reads and writes grade files, checked against `grades.schema.json`. */
object GradeFiles {
    /** The classpath location of the schema. */
    const val SCHEMA_RESOURCE = "/spa/agent/grades.schema.json"

    /** Reads and checks the grade file at [path]. */
    fun load(path: Path): GradeFile = TODO("SPA-029")

    /** Checks and decodes the grade file in [text]. */
    fun parse(text: String): GradeFile = TODO("SPA-029")

    /** [file] as the text [parse] reads back to an equal object. */
    fun write(file: GradeFile): String = TODO("SPA-029")
}
