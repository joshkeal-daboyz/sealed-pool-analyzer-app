package spa.agent.fixing

import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.cards.Rarity

/** Hand-built cards for the fixing rules' tests. */
internal object FixingTestCards {
    /** A face named [name] with type line [type] and oracle text [text]. */
    fun face(name: String, type: String, text: String): Face =
        Face(name, "", 0.0, emptySet(), type, text, null, null)

    /** A card whose faces are [faces]; its front face's type line decides whether it is a land. */
    fun card(vararg faces: Face, layout: String = "normal"): Card {
        val front = faces.first()
        return Card(
            oracleId = "oracle-${front.name}",
            name = faces.joinToString(" // ") { it.name },
            layout = layout,
            faces = faces.toList(),
            colors = emptySet(),
            colorIdentity = emptySet(),
            manaValue = 0.0,
            typeLine = front.typeLine,
            isCreature = front.typeLine.startsWith("Creature"),
            isLand = front.typeLine.startsWith("Land"),
            isBasicLand = false,
            rarity = Rarity.COMMON,
        )
    }

    /** A single-faced card. */
    fun card(name: String, type: String, text: String): Card = card(face(name, type, text))
}
