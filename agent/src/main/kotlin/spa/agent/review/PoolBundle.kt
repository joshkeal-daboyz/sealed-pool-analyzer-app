package spa.agent.review

import java.util.Locale
import spa.core.brief.Archetype
import spa.core.cards.PoolEntry

/** Builds the pool bundle: one deterministic text of everything set-specific an agent reads. */
object PoolBundle {
    private const val FACE_JOIN = " // "
    private const val NO_COST = "-"
    private const val NONE = "None."

    /**
     * The bundle text of [input]: same input, same bytes. It holds no metric or baseline number.
     */
    fun text(input: ReviewInput): String {
        val brief = input.brief
        val header =
            listOf(
                "# Pool bundle",
                "Set: ${input.set} (${brief.setName}), format speed: " +
                    "${brief.speed.name.lowercase(Locale.ROOT)}, brief version ${brief.version}",
                "Speed notes: ${brief.speedNotes}",
                "Grades are 0.0 to 5.0 in half steps, relative to this set; 5.0 is the best.",
            )
        val cards = cardLines(input)
        val sections =
            listOf(
                header,
                section("Set mechanics", brief.mechanics.map { "- ${it.name}: ${it.rulesText}" }),
                section("Designed archetypes", brief.archetypes.map(::archetypeLine)),
                section(
                    "Generic archetypes",
                    brief.genericArchetypes.map { "- ${it.name}: ${it.description}" },
                ),
                section("Pool: ${input.pool.totalCount} cards, ${cards.size} distinct", cards),
                section("Kit warnings", input.kitWarnings.map { "- ${it.message}" }),
            )
        return sections.joinToString("\n") { lines -> lines.joinToString("") { "$it\n" } }
    }

    private fun section(title: String, lines: List<String>): List<String> =
        listOf("## $title") + lines.ifEmpty { listOf(NONE) }

    private fun archetypeLine(archetype: Archetype): String {
        val commons =
            if (archetype.keyCommons.isEmpty()) {
                ""
            } else {
                " Key commons: ${archetype.keyCommons.joinToString("; ")}."
            }
        return "- ${archetype.colors} ${archetype.name}: ${archetype.plan} " +
            "Signposts: ${archetype.signpostUncommons.joinToString("; ")}.$commons"
    }

    /** One line per card, its printings summed, sorted by name. */
    private fun cardLines(input: ReviewInput): List<String> =
        input.pool.entries
            .groupBy { it.printing.card.oracleId }
            .values
            .sortedWith(
                compareBy({ it.first().printing.card.name }, { it.first().printing.card.oracleId })
            )
            .map { printings -> cardLine(input, printings) }

    private fun cardLine(input: ReviewInput, printings: List<PoolEntry>): String {
        val first = printings.first().printing
        val card = first.card
        val score = printings.firstNotNullOfOrNull { input.gradeOf(it.printing) }
        val grade =
            if (score == null) {
                "grade none"
            } else {
                "grade ${String.format(Locale.ROOT, "%.1f", score)} (${GradeLabels.of(score)})"
            }
        val cost = card.faces.joinToString(FACE_JOIN) { it.manaCost.ifEmpty { NO_COST } }
        val rules = card.faces.joinToString(FACE_JOIN) { it.oracleText.replace("\n", " / ") }
        return "${printings.sumOf { it.count }}x " +
            listOf(
                    card.faces.joinToString(FACE_JOIN) { it.name },
                    cost,
                    card.faces.joinToString(FACE_JOIN) { it.typeLine },
                    first.rarity.scryfallName,
                    grade,
                    rules,
                )
                .joinToString(" | ")
    }
}
