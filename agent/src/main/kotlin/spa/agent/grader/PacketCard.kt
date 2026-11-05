package spa.agent.grader

import kotlinx.serialization.Serializable
import spa.core.cards.Face
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
        private const val FACE_SEPARATOR = " // "

        /** The packet card for [printing], with its id from [GradingPool.id]. */
        fun of(printing: Printing): PacketCard {
            val faces = printing.card.faces
            return PacketCard(
                id = GradingPool.id(printing),
                name = printing.card.name,
                manaCost = join(faces) { it.manaCost },
                typeLine = join(faces) { it.typeLine },
                rarity = printing.rarity.scryfallName,
                oracleText = join(faces) { it.oracleText },
                power = joinOptional(faces) { it.power },
                toughness = joinOptional(faces) { it.toughness },
            )
        }

        private fun join(faces: List<Face>, field: (Face) -> String): String =
            faces.joinToString(FACE_SEPARATOR, transform = field)

        private fun joinOptional(faces: List<Face>, field: (Face) -> String?): String? =
            if (faces.all { field(it) == null }) null else join(faces) { field(it).orEmpty() }
    }
}
