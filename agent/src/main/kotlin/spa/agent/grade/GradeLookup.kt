package spa.agent.grade

import spa.core.cards.Printing

/** Grades for cards, from any number of grade files, preferring win-rate grades. */
class GradeLookup(private val files: List<GradeFile>) {
    /**
     * The grade of [card] in the sealed pool of [setCode]: the `WIN_RATE` grade if one exists, else
     * the `MODEL` grade, else null.
     */
    fun grade(card: Printing, setCode: String): CardGrade? = TODO("SPA-029")
}
