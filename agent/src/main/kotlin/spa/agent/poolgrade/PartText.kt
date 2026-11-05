package spa.agent.poolgrade

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Number formats and the ratio the parts share. */
internal object PartText {
    /** [value] to two decimals with a leading `+`, or U+2212 minus when below zero. */
    fun signed(value: Double): String = (if (value < 0) "−" else "+") + decimal(abs(value))

    fun decimal(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"

    /** [difference] / max([base], 1), clamped to -1 and 1. */
    fun ratio(difference: Double, base: Double): Double =
        (difference / max(base, 1.0)).coerceIn(-1.0, 1.0)
}
