package spa.app.names

import spa.core.text.Color

/** The basic land that makes each colour. */
object BasicLandNames {
    /** "Plains", "Island", "Swamp", "Mountain", or "Forest". */
    fun of(color: Color): String =
        when (color) {
            Color.W -> "Plains"
            Color.U -> "Island"
            Color.B -> "Swamp"
            Color.R -> "Mountain"
            Color.G -> "Forest"
        }
}
