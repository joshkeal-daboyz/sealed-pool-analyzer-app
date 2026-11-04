package spa.agent.grade

import spa.core.cards.Printing

/** Grades for cards, from any number of grade files, preferring win-rate grades. */
class GradeLookup(private val files: List<GradeFile>) {
    /**
     * The grade of [card] in the sealed pool of [setCode]: the `WIN_RATE` grade if one exists, else
     * the `MODEL` grade, else null. Only files whose header names [setCode] are read, so a
     * companion set's card is found in the main set's files. A card is matched by its printing's
     * set and collector number, or else by name, so another printing of a graded card finds its
     * grade.
     */
    fun grade(card: Printing, setCode: String): CardGrade? =
        PREFERENCE.firstNotNullOfOrNull { source ->
            find(card, setCode, source)
        }

    private fun find(card: Printing, setCode: String, source: GradeSource): CardGrade? {
        val candidates = files.filter { it.header.setCode == setCode && it.header.source == source }
        for (file in candidates) {
            val entry =
                file.grades.firstOrNull {
                    it.setCode == card.setCode && it.collectorNumber == card.collectorNumber
                } ?: file.grades.firstOrNull { it.name == card.card.name }
            if (entry != null) {
                return CardGrade(entry.score, entry.confidence, source, file.header.provenance)
            }
        }
        return null
    }

    private companion object {
        val PREFERENCE = listOf(GradeSource.WIN_RATE, GradeSource.MODEL)
    }
}
