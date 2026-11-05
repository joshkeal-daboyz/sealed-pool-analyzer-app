package spa.agent.grader

import kotlinx.serialization.Serializable
import spa.agent.themes.ColourGroup
import spa.agent.themes.SetThemes
import spa.agent.themes.ThemeCard
import spa.agent.themes.ThemeCount
import spa.agent.themes.ThemeRules

/** One of a card's themes in the packet: the theme's [name], and [role] `has` or `refers`. */
@Serializable data class PacketTheme(val name: String, val role: String)

/**
 * The themes a packet carries when built with them: the pool's kept theme [counts] (see
 * [spa.agent.themes.SetThemes]), and each card's kept themes by oracle id.
 */
class PacketThemes(val counts: List<ThemeCount>, val byOracleId: Map<String, List<PacketTheme>>) {
    /** The kept themes of the card with [oracleId], in [counts] order; none when it has none. */
    fun of(oracleId: String): List<PacketTheme> = byOracleId[oracleId].orEmpty()

    /**
     * The `## Themes in this set` section of `instructions.md`: one line per kept theme, with its
     * kind, its has and refers counts, and the either count by colour group.
     */
    fun render(): String = buildString {
        appendLine("## Themes in this set")
        appendLine()
        appendLine(
            "Each line is a theme of the cards in this packet, a keyword or a subtype: how many " +
                "cards have it, how many refer to it in their rules text, and how many cards that " +
                "have it or refer to it are in each colour (W, U, B, R, G, M multicoloured, " +
                "C colourless). Each card's `themes` lists the themes below that it has or refers to."
        )
        appendLine()
        counts.forEach { count ->
            val colours =
                ColourGroup.entries.joinToString(" ") {
                    "${it.label}=${count.either.byColour[it] ?: 0}"
                }
            appendLine(
                "- ${count.theme.name} (${count.theme.kind.label}): has ${count.has.total}, " +
                    "refers ${count.refers.total}; colours $colours"
            )
        }
    }

    companion object {
        const val HAS = "has"
        const val REFERS = "refers"

        /**
         * The packet themes of [cards], with the brief's [mechanics] (see [SetThemes.count]). A
         * card that both has a theme and refers to it lists it once, as `has`.
         */
        fun of(cards: List<ThemeCard>, mechanics: Collection<String>): PacketThemes {
            val counts = SetThemes.count(cards, mechanics)
            val byOracleId = cards.associate { card ->
                card.card.oracleId to
                    counts.mapNotNull { count ->
                        when {
                            ThemeRules.has(card, count.theme) -> PacketTheme(count.theme.name, HAS)
                            ThemeRules.refersTo(card, count.theme) ->
                                PacketTheme(count.theme.name, REFERS)
                            else -> null
                        }
                    }
            }
            return PacketThemes(counts, byOracleId)
        }
    }
}
