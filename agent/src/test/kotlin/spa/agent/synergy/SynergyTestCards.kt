package spa.agent.synergy

import spa.agent.tags.Axis
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.agent.tags.TagPartner
import spa.agent.tags.TagRole
import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Printing
import spa.core.cards.Rarity
import spa.core.text.Color

/**
 * A small hand-built set `xyz` and its tag file, for the synergy tests. Axes: `tokens`, `counters`.
 *
 * | #   | Card       | Colour     | Tags                                                  |
 * |-----|------------|------------|-------------------------------------------------------|
 * | 1   | Maker      | W          | tokens enabler 2                                      |
 * | 2   | Rewarder   | U          | tokens payoff 3                                       |
 * | 3   | Both       | W          | tokens enabler 1, tokens payoff 2, counters enabler 1 |
 * | 4   | Namer      | W          | names Named, 2                                        |
 * | 5   | Named      | U          | names Namer, 1                                        |
 * | 6   | Selfie     | W          | names itself, 3                                       |
 * | 7   | Blank      | colourless | none                                                  |
 * | 8   | One Way    | W          | names Rewarder, 1                                     |
 * | 9   | Twin       | U          | tokens enabler 3, tokens payoff 1                     |
 * | 10  | Red Payoff | R          | tokens payoff 3 (does not fit WU)                     |
 * | 11  | Token Land | land       | tokens payoff 3 (a land: never fits)                  |
 *
 * Nine cards fit WU: 1 to 9.
 */
internal object SynergyTestCards {
    private val W = setOf(Color.W)
    private val U = setOf(Color.U)

    private const val TOKENS = "tokens"
    private const val COUNTERS = "counters"

    private fun enabler(axis: String, strength: Int) = TagRole(axis, "enabler", strength)

    private fun payoff(axis: String, strength: Int) = TagRole(axis, "payoff", strength)

    private fun names(number: Int, strength: Int) = TagPartner("xyz/$number", strength)

    private class Spec(
        val name: String,
        val colors: Set<Color>,
        val roles: List<TagRole> = emptyList(),
        val partners: List<TagPartner> = emptyList(),
        val land: Boolean = false,
    )

    private val specs =
        listOf(
            Spec("Maker", W, listOf(enabler(TOKENS, 2))),
            Spec("Rewarder", U, listOf(payoff(TOKENS, 3))),
            Spec(
                "Both",
                W,
                listOf(enabler(TOKENS, 1), payoff(TOKENS, 2), enabler(COUNTERS, 1)),
            ),
            Spec("Namer", W, partners = listOf(names(5, 2))),
            Spec("Named", U, partners = listOf(names(4, 1))),
            Spec("Selfie", W, partners = listOf(names(6, 3))),
            Spec("Blank", emptySet()),
            Spec("One Way", W, partners = listOf(names(2, 1))),
            Spec("Twin", U, listOf(enabler(TOKENS, 3), payoff(TOKENS, 1))),
            Spec("Red Payoff", setOf(Color.R), listOf(payoff(TOKENS, 3))),
            Spec("Token Land", emptySet(), listOf(payoff(TOKENS, 3)), land = true),
        )

    /** The tag file of set `xyz`. */
    val TAGS: TagFile =
        TagFile(
            set = "xyz",
            provenance = "hand-built",
            axes =
                listOf(
                    Axis(TOKENS, "Tokens", "Makes tokens."),
                    Axis(COUNTERS, "Counters", "Puts counters."),
                ),
            cards =
                specs.mapIndexed { i, spec ->
                    TagCard("xyz/${i + 1}", spec.name, spec.roles, spec.partners)
                },
        )

    /** The set's sealed-pool printings, collector numbers 1 to 11. */
    val SET: List<Printing> = specs.mapIndexed { i, spec ->
        printing(card(spec.name, spec.colors, spec.land), "${i + 1}")
    }

    /** The tag card of [name]. */
    fun tag(name: String): TagCard = TAGS.cards.single { it.name == name }

    /** The printing of [name] in [SET]. */
    fun printing(name: String): Printing = SET.single { it.card.name == name }

    /** A pool of [counts]: each named card of [SET] with its number of copies. */
    fun pool(vararg counts: Pair<String, Int>): Pool =
        Pool(counts.map { (name, count) -> PoolEntry(printing(name), count) })

    fun card(name: String, colors: Set<Color>, land: Boolean = false): Card {
        val type = if (land) "Land" else "Creature — Test"
        val cost = if (land) "" else "{2}"
        val face = Face(name, cost, if (land) 0.0 else 2.0, colors, type, "", null, null)
        return Card(
            oracleId = "oracle-$name",
            name = name,
            layout = "normal",
            faces = listOf(face),
            colors = colors,
            colorIdentity = colors,
            manaValue = if (land) 0.0 else 2.0,
            typeLine = type,
            isCreature = !land,
            isLand = land,
            isBasicLand = false,
            rarity = Rarity.COMMON,
        )
    }

    fun printing(card: Card, number: String, set: String = "xyz"): Printing =
        Printing("id-${card.name}", set, number, Rarity.COMMON, emptyList(), card)
}
