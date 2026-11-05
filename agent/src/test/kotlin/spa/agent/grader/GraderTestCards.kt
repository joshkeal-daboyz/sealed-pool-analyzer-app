package spa.agent.grader

import spa.core.brief.Archetype
import spa.core.brief.FormatBrief
import spa.core.brief.FormatSpeed
import spa.core.brief.KitTemplate
import spa.core.brief.Mechanic
import spa.core.cards.Card
import spa.core.cards.CardIndex
import spa.core.cards.Face
import spa.core.cards.Printing
import spa.core.cards.Rarity

/** Small hand-made cards, indexes and a brief for the grader's tests. */
internal object GraderTestCards {
    /** A face; [stats] is `power/toughness`, or null for a face with neither. */
    fun face(
        name: String,
        cost: String = "{1}{G}",
        type: String = "Creature — Bear",
        text: String = "",
        stats: String? = "2/2",
    ) =
        Face(
            name,
            cost,
            2.0,
            emptySet(),
            type,
            text,
            stats?.substringBefore('/'),
            stats?.substringAfter('/'),
        )

    fun card(name: String, vararg faces: Face, basic: Boolean = false): Card {
        val all = if (faces.isEmpty()) listOf(face(name)) else faces.toList()
        return Card(
            oracleId = "oracle-$name",
            name = name,
            layout = if (all.size > 1) "transform" else "normal",
            faces = all,
            colors = emptySet(),
            colorIdentity = emptySet(),
            manaValue = 2.0,
            typeLine = all.first().typeLine,
            isCreature = true,
            isLand = basic,
            isBasicLand = basic,
            rarity = Rarity.COMMON,
        )
    }

    fun printing(
        card: Card,
        setCode: String,
        number: String,
        rarity: Rarity = Rarity.COMMON,
        promoTypes: List<String> = emptyList(),
    ) = Printing("id-$setCode-$number", setCode, number, rarity, promoTypes, card)

    /** An index of set `xyz` with [count] distinct cards numbered 1 to [count]. */
    fun numbered(count: Int): CardIndex =
        CardIndex(listOf("xyz"), (1..count).map { printing(card("Card $it"), "xyz", "$it") })

    fun brief(setCode: String = "xyz"): FormatBrief =
        FormatBrief(
            setCode = setCode,
            setName = "Test Set",
            version = 1,
            speed = FormatSpeed.FAST,
            speedNotes = "Most of them end by turn six.",
            mechanics =
                listOf(
                    Mechanic("Kindle", "Kindle N deals N damage.", "(Reminder for kindle.)"),
                    Mechanic("Overgrow", "Overgrow checks for seven lands."),
                ),
            archetypes =
                listOf(
                    Archetype("WU", "Tether Control", "Tether and fly.", listOf("Warden One")),
                    Archetype("BR", "Sacrifice Aggro", "Trade and burn.", listOf("Cinder A", "B")),
                ),
            genericArchetypes = emptyList(),
            kitTemplate = KitTemplate(6, 14, emptyList(), null, true),
        )
}
