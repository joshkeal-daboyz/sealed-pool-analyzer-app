package spa.agent.grader

import kotlinx.serialization.Serializable
import spa.core.cards.Printing

/**
 * One card as the grading packet shows it. For a card with more than one face, every text field
 * joins the faces' values with ` // `; [power] and [toughness] are null when no face has one.
 */
@Serializable
data class PacketCard(
    val id: String,
    val name: String,
    val manaCost: String,
    val typeLine: String,
    val rarity: String,
    val oracleText: String,
    val power: String?,
    val toughness: String?,
) {
    companion object {
        /** The packet card for [printing], with its id from [GradingPool.id]. */
        fun of(printing: Printing): PacketCard = TODO()
    }
}
