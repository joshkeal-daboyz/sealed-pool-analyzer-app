package spa.agent.fixing

import spa.core.text.Color

/**
 * CYCLING: `Basic landcycling` supplies all five colours; `Plainscycling`, `Islandcycling`,
 * `Swampcycling`, `Mountaincycling` and `Forestcycling` supply their land type's colour.
 */
object CyclingRule : FixingRule {
    override val kind = FixingKind.CYCLING

    override fun colours(faceText: String): Set<Color> = emptySet() // TODO SPA-042
}
