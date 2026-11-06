package spa.agent.review

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import spa.core.baseline.Stat

/**
 * One number an agent may quote: its [key] (a metric pointer), the pool's [value], and [typical].
 */
data class NumberEntry(val key: String, val value: Double, val typical: Stat?)

/** What [NumberTable.check] found for a quoted number. */
sealed interface NumberCheck {
    /** The key is in the table and the value is the table's. */
    data object Ok : NumberCheck

    /** The key is not in the table. */
    data object UnknownKey : NumberCheck

    /** The key is in the table with a different value, [expected]. */
    data class WrongValue(val expected: Double) : NumberCheck
}

/** The keyed numbers one agent may quote, in the order the agent sees them. */
data class NumberTable(val entries: List<NumberEntry>) {
    /** One line per entry, joined by newlines: `<key> = <value>`, then the baseline's spread. */
    fun text(): String =
        entries.joinToString("\n") { entry ->
            val typical =
                entry.typical?.let {
                    " (typical: median ${format(it.median)}, p10 ${format(it.p10)}, " +
                        "p90 ${format(it.p90)})"
                }
            "${entry.key} = ${format(entry.value)}${typical.orEmpty()}"
        }

    /** Whether [value] is the table's value for [key]; right means within 0.00005 of it. */
    fun check(key: String, value: Double): NumberCheck {
        val entry = entries.firstOrNull { it.key == key } ?: return NumberCheck.UnknownKey
        return if (abs(entry.value - value) <= TOLERANCE) {
            NumberCheck.Ok
        } else {
            NumberCheck.WrongValue(entry.value)
        }
    }

    private companion object {
        const val TOLERANCE = 0.00005
        const val DECIMALS = 4

        /** [number] with at most 4 decimals, no trailing zero and no point when whole. */
        fun format(number: Double): String =
            BigDecimal.valueOf(number)
                .setScale(DECIMALS, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
    }
}
