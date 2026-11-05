package spa.agent.fixing

import spa.core.text.Color

/** TREASURE: `create ... Treasure` in one sentence. All five colours. */
object TreasureRule : FixingRule {
    override val kind = FixingKind.TREASURE

    override fun colours(faceText: String): Set<Color> = emptySet() // TODO SPA-042
}
