package spa.agent.fixing

import spa.core.text.Color

/**
 * CYCLING: `Basic landcycling` supplies all five colours; `Plainscycling`, `Islandcycling`,
 * `Swampcycling`, `Mountaincycling` and `Forestcycling` supply their land type's colour.
 */
object CyclingRule : FixingRule {
    override val kind = FixingKind.CYCLING

    private val BASIC = Regex("""\bbasic landcycling\b""", RegexOption.IGNORE_CASE)

    private val BY_TYPE =
        mapOf(
                "Plains" to Color.W,
                "Island" to Color.U,
                "Swamp" to Color.B,
                "Mountain" to Color.R,
                "Forest" to Color.G,
            )
            .map { (type, colour) ->
                Regex("""\b${type}cycling\b""", RegexOption.IGNORE_CASE) to colour
            }

    override fun colours(faceText: String): Set<Color> {
        if (BASIC.containsMatchIn(faceText)) return ALL_COLOURS
        return BY_TYPE.filter { (pattern, _) -> pattern.containsMatchIn(faceText) }
            .map { it.second }
            .toSet()
    }
}
