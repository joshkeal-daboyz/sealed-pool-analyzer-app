package spa.core.metrics

import kotlinx.serialization.Serializable
import spa.core.text.Color

/** The ten two-color pairs, in the order WU, WB, WR, WG, UB, UR, UG, BR, BG, RG. */
@Serializable
enum class ColorPair(val first: Color, val second: Color) {
    WU(Color.W, Color.U),
    WB(Color.W, Color.B),
    WR(Color.W, Color.R),
    WG(Color.W, Color.G),
    UB(Color.U, Color.B),
    UR(Color.U, Color.R),
    UG(Color.U, Color.G),
    BR(Color.B, Color.R),
    BG(Color.B, Color.G),
    RG(Color.R, Color.G);

    /** The pair's two colors, [first] then [second]. */
    val colors: Set<Color>
        get() = setOf(first, second)
}
