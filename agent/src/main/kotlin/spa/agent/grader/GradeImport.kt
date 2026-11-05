package spa.agent.grader

import java.nio.file.Path
import spa.agent.grade.GradeFile
import spa.core.cards.CardIndex

/** What an import gives: the grade [file] when there are no [problems], else null. */
data class ImportResult(val file: GradeFile?, val problems: List<String>)

/**
 * Turns a grader's response files into a grade file with source `MODEL`.
 *
 * Every `cards-NN.json` in the packet directory must have its `cards-NN.response.json` beside it,
 * grading each of its ids exactly once and no other, with a score on the scale and a confidence in
 * [0, 1]; and the card files together must hold exactly the set's [GradingPool]. Every problem is
 * one line naming the file and the card.
 */
object GradeImport {
    fun run(
        packetDir: Path,
        index: CardIndex,
        setCode: String,
        provenance: String,
        createdAt: String,
    ): ImportResult = TODO()
}
