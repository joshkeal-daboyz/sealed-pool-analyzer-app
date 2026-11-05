package spa.agent.fixing

import spa.core.text.Color

/**
 * SEARCH: a `search your library for ...` clause, up to the next period or line end, that names a
 * `land card` and does not say `into your graveyard`. All five colours.
 */
object SearchRule : FixingRule {
    override val kind = FixingKind.SEARCH

    override fun colours(faceText: String): Set<Color> = emptySet() // TODO SPA-042
}
