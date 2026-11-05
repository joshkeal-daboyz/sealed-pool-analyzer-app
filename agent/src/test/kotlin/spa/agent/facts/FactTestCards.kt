package spa.agent.facts

import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.cards.Rarity

/** Hand-built cards for the fact rules' tests. */
internal object FactTestCards {
    /** A face named [name] with type line [type] and oracle text [text]. */
    fun face(name: String, type: String = "Creature — Bear", text: String = ""): Face =
        Face(name, "", 0.0, emptySet(), type, text, null, null)

    /** A card whose faces are [faces], with mana value [manaValue]. */
    fun card(vararg faces: Face, manaValue: Double = 4.0): Card {
        val front = faces.first()
        return Card(
            oracleId = "oracle-${front.name}",
            name = faces.joinToString(" // ") { it.name },
            layout = if (faces.size > 1) "transform" else "normal",
            faces = faces.toList(),
            colors = emptySet(),
            colorIdentity = emptySet(),
            manaValue = manaValue,
            typeLine = faces.joinToString(" // ") { it.typeLine },
            isCreature = "Creature" in front.typeLine,
            isLand = "Land" in front.typeLine,
            isBasicLand = false,
            rarity = Rarity.COMMON,
        )
    }

    /** The facts all rules give a one-faced card. */
    fun facts(
        name: String = "Test Card",
        type: String = "Creature — Bear",
        text: String = "",
        keywords: List<String> = emptyList(),
        manaValue: Double = 4.0,
    ): Set<String> =
        CardFacts.of(FactCard(card(face(name, type, text), manaValue = manaValue), keywords))
}
