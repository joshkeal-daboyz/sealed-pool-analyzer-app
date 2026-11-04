package spa.app.names

import spa.core.metrics.ColorBucket
import spa.core.text.Color

/** Colour names as the app shows them. */
object ColorNames {
    /** "White", "Blue", "Black", "Red", "Green", "Multicolour", or "Colourless". */
    fun of(bucket: ColorBucket): String = TODO()

    /** "White", "Blue", "Black", "Red", or "Green". */
    fun of(color: Color): String = TODO()

    /** [colors] as names in WUBRG order, joined by ", ", such as "White, Red". */
    fun list(colors: Collection<Color>): String = TODO()
}
