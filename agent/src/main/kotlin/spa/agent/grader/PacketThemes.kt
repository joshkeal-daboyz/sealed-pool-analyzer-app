package spa.agent.grader

import kotlinx.serialization.Serializable
import spa.agent.themes.ThemeCard
import spa.agent.themes.ThemeCount

/** One of a card's themes in the packet: the theme's [name], and [role] `has` or `refers`. */
@Serializable data class PacketTheme(val name: String, val role: String)

/**
 * The themes a packet carries when built with them: the pool's kept theme [counts] (see
 * [spa.agent.themes.SetThemes]), and each card's kept themes by oracle id.
 */
class PacketThemes(val counts: List<ThemeCount>, val byOracleId: Map<String, List<PacketTheme>>) {
    /** The kept themes of the card with [oracleId], in [counts] order; none when it has none. */
    fun of(oracleId: String): List<PacketTheme> = TODO("SPA-036")

    /** The `## Themes in this set` section of `instructions.md`. */
    fun render(): String = TODO("SPA-036")

    companion object {
        const val HAS = "has"
        const val REFERS = "refers"

        /** The packet themes of [cards], with the brief's [mechanics] (see SetThemes.count). */
        fun of(cards: List<ThemeCard>, mechanics: Collection<String>): PacketThemes =
            TODO("SPA-036")
    }
}
