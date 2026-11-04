package spa.app.names

import spa.core.metrics.ColorBucket
import spa.core.text.Color

/** Colour names as the app shows them. */
object ColorNames {
    /** "White", "Blue", "Black", "Red", "Green", "Multicolour", or "Colourless". */
    fun of(bucket: ColorBucket): String =
        when (bucket) {
            ColorBucket.MULTI -> "Multicolour"
            ColorBucket.COLORLESS -> "Colourless"
            else -> of(checkNotNull(bucket.color))
        }

    /** "White", "Blue", "Black", "Red", or "Green". */
    fun of(color: Color): String =
        when (color) {
            Color.W -> "White"
            Color.U -> "Blue"
            Color.B -> "Black"
            Color.R -> "Red"
            Color.G -> "Green"
        }

    /** [colors] as names in WUBRG order, joined by ", ", such as "White, Red". */
    fun list(colors: Collection<Color>): String = colors.sorted().joinToString(", ") { of(it) }
}
