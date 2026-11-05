package spa.agent.deckpick

import spa.agent.poolgrade.PoolGradeTestCards
import spa.core.cards.Card
import spa.core.cards.Printing
import spa.core.text.Color

/**
 * The hand-built cards of `small-sealed.csv`, and a resolver over them. `Bonus Reprint` resolves to
 * nothing, as a bonus-sheet reprint missing from a set's Scryfall cache does.
 */
internal object DeckPickTestCards {
    private val W = setOf(Color.W)
    private val U = setOf(Color.U)
    private val B = setOf(Color.B)

    val sunKnight = PoolGradeTestCards.card("Sun Knight", W, type = "Creature — Knight")
    val dawnCleric = PoolGradeTestCards.card("Dawn Cleric", W, type = "Creature — Cleric")
    val tideCaller = PoolGradeTestCards.card("Tide Caller", U, type = "Creature — Merfolk")
    val graveRat = PoolGradeTestCards.card("Grave Rat", B, type = "Creature — Rat")
    val skyMarshal =
        PoolGradeTestCards.card("Sky Marshal, the Bold", W + U, type = "Creature — Bird")
    val duskPaladin = PoolGradeTestCards.card("Dusk Paladin", W + B, type = "Creature — Knight")
    val ironGolem = PoolGradeTestCards.card("Iron Golem", type = "Artifact Creature — Golem")
    val coastalRuin = PoolGradeTestCards.card("Coastal Ruin", type = "Land")
    val plains: Card =
        PoolGradeTestCards.card("Plains", type = "Land", text = "").copy(isBasicLand = true)

    private val ALL =
        listOf(
            sunKnight,
            dawnCleric,
            tideCaller,
            graveRat,
            skyMarshal,
            duskPaladin,
            ironGolem,
            coastalRuin,
            plains,
        )

    private val BY_NAME: Map<String, Printing> = ALL.associate {
        it.name to PoolGradeTestCards.printing(it)
    }

    /** The resolver the reader gets: these cards by exact name, `Bonus Reprint` unresolved. */
    val resolve: (String) -> Printing? = { BY_NAME[it] }

    fun printing(card: Card): Printing = BY_NAME.getValue(card.name)
}
