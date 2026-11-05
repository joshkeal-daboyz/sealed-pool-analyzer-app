package spa.cli

import java.util.Locale
import kotlin.math.abs
import spa.agent.poolgrade.CardAdjustment
import spa.agent.poolgrade.PairGrades

/** The lines `spa pool-grades` prints for one pair. */
object PoolGradeLines {
    /**
     * A heading (the pair, its mean, its distinct cards and copies), then each card (adjusted
     * grade, letter, set grade, change, quantity, name, set and number) and, indented, each nonzero
     * part's own reason line ([spa.agent.poolgrade.Part.line]) in the parts' order. Negative
     * numbers use U+2212 minus, as the letter labels do.
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
            card.parts.mapNotNull { it.line }.forEach { add(REASON_INDENT + it) }
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

    /** [value] to two decimals with a leading `+`, or U+2212 minus when below zero. */
    private fun signed(value: Double): String =
        (if (value < 0) "−" else "+") + String.format(Locale.ROOT, "%.2f", abs(value))

    private const val REASON_INDENT = "      "
}
