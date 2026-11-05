package spa.cli

import java.util.Locale
import kotlin.math.abs
import spa.agent.poolgrade.CardAdjustment
import spa.agent.poolgrade.ColourPart
import spa.agent.poolgrade.CurvePart
import spa.agent.poolgrade.PairGrades
import spa.agent.poolgrade.SynergyPart
import spa.core.metrics.Curve
import spa.core.text.Color

/** The lines `spa pool-grades` prints for one pair. */
object PoolGradeLines {
    /**
     * A heading (the pair, its mean, its distinct cards and copies), then each card (adjusted
     * grade, letter, set grade, change, quantity, name, set and number) and one indented reason
     * line per nonzero part. Negative numbers use U+2212 minus, as the letter labels do.
     */
    fun lines(grades: PairGrades): List<String> = buildList {
        add(
            String.format(
                Locale.ROOT,
                "%s  mean %.2f  (%d cards, %d copies)",
                grades.pair.name,
                grades.mean,
                grades.cards.size,
                grades.copies,
            )
        )
        grades.cards.forEach { card ->
            add(cardLine(card))
            card.parts
                .filter { it.value != 0.0 }
                .forEach { part ->
                    val reason =
                        when (part) {
                            is SynergyPart -> synergy(part)
                            is CurvePart -> curve(part)
                            is ColourPart -> colour(part)
                        }
                    add(REASON_INDENT + reason)
                }
        }
    }

    private fun cardLine(card: CardAdjustment): String {
        val printing = card.printing
        return String.format(
            Locale.ROOT,
            "%.2f  %-2s  %.1f  %s  %dx %s (%s %s)",
            card.adjusted,
            GradesCommand.label(card.adjusted),
            card.setGrade,
            signed(card.change),
            card.quantity,
            printing.card.name,
            printing.setCode,
            printing.collectorNumber,
        )
    }

    private fun synergy(part: SynergyPart): String =
        "synergy ${signed(part.value)}: ${plural(part.partners, "partner")} in the pool, " +
            "${decimal(part.expected)} expected (${part.themes.joinToString(", ")})"

    private fun curve(part: CurvePart): String {
        val manaValue =
            if (part.manaValue == Curve.TOP) "${part.manaValue} or more" else "${part.manaValue}"
        return "curve ${signed(part.value)}: ${plural(part.count, "card")} at mana value " +
            "$manaValue, ${decimal(part.typical)} typical"
    }

    private fun colour(part: ColourPart): String {
        val name = COLOUR_NAMES.getValue(requireNotNull(part.colour))
        return "colour ${signed(part.value)}: $name playables ${part.playables}, " +
            "typical ${decimal(part.typical)}"
    }

    /** [value] to two decimals with a leading `+`, or U+2212 minus when below zero. */
    private fun signed(value: Double): String = (if (value < 0) "−" else "+") + decimal(abs(value))

    private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun plural(count: Int, noun: String): String =
        if (count == 1) "1 $noun" else "$count ${noun}s"

    private const val REASON_INDENT = "      "

    private val COLOUR_NAMES =
        mapOf(
            Color.W to "White",
            Color.U to "Blue",
            Color.B to "Black",
            Color.R to "Red",
            Color.G to "Green",
        )
}
