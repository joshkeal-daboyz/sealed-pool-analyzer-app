package spa.agent.review

import spa.core.baseline.Baseline
import spa.core.baseline.Stat
import spa.core.brief.Archetype
import spa.core.brief.FormatBrief
import spa.core.brief.FormatSpeed
import spa.core.brief.GenericArchetype
import spa.core.brief.KitTemplate
import spa.core.brief.Mechanic
import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Printing
import spa.core.cards.Rarity
import spa.core.intake.KitWarning
import spa.core.intake.KitWarningCode
import spa.core.metrics.MetricEngine
import spa.core.text.Color

/** Small hand-built cards, pools, a brief and a baseline for the review package's tests. */
internal object ReviewTestCards {
    /** The mana value of [cost]: each number counts as itself, each other symbol as one. */
    private fun manaValue(cost: String): Double =
        Regex("\\{([^}]*)}").findAll(cost).sumOf { it.groupValues[1].toIntOrNull() ?: 1 }.toDouble()

    /** A single-faced card. [cost] is the face's mana cost, which may be empty. */
    fun card(
        name: String,
        colors: Set<Color> = emptySet(),
        cost: String = "{2}",
        type: String = "Creature — Bear",
        text: String = "",
    ): Card {
        val mv = manaValue(cost)
        val face = Face(name, cost, mv, colors, type, text, null, null)
        return Card(
            oracleId = "oracle-$name",
            name = name,
            layout = "normal",
            faces = listOf(face),
            colors = colors,
            colorIdentity = colors,
            manaValue = mv,
            typeLine = type,
            isCreature = type.startsWith("Creature"),
            isLand = type.contains("Land"),
            isBasicLand = type.startsWith("Basic Land"),
            rarity = Rarity.COMMON,
        )
    }

    /** A card with two faces; its name joins the faces' names, its other fields are the front's. */
    fun doubleFaced(front: Face, back: Face): Card =
        Card(
            oracleId = "oracle-${front.name}",
            name = "${front.name} // ${back.name}",
            layout = "transform",
            faces = listOf(front, back),
            colors = front.colors,
            colorIdentity = front.colors,
            manaValue = front.manaValue,
            typeLine = front.typeLine,
            isCreature = true,
            isLand = false,
            isBasicLand = false,
            rarity = Rarity.RARE,
        )

    fun printing(card: Card, id: String = "id-${card.name}", rarity: Rarity = Rarity.COMMON) =
        Printing(id, "xyz", id, rarity, emptyList(), card)

    fun entry(
        card: Card,
        count: Int,
        rarity: Rarity = Rarity.COMMON,
        id: String = "id-${card.name}",
    ) = PoolEntry(printing(card, id, rarity), count)

    val brief =
        FormatBrief(
            setCode = "xyz",
            setName = "Xyzzy",
            version = 3,
            speed = FormatSpeed.MEDIUM,
            speedNotes = "Games end around turn 8.",
            mechanics =
                listOf(
                    Mechanic("Dock", "Attach to a ship.", "(Reminder text.)"),
                    Mechanic("Warp", "Cast from exile."),
                ),
            archetypes =
                listOf(
                    Archetype(
                        "WU",
                        "Fliers",
                        "Win in the air.",
                        listOf("Sky Marshal", "Cloud Sage"),
                        listOf("Wisp", "Gull"),
                    ),
                    Archetype(
                        "BR",
                        "Sacrifice",
                        "Trade then drain.",
                        listOf("Pyre Priest", "Grave Imp"),
                    ),
                ),
            genericArchetypes = listOf(GenericArchetype("Artifacts", "Many artifacts.")),
            kitTemplate = KitTemplate(6, 14, emptyList(), null, true),
        )

    val kitWarning =
        KitWarning(KitWarningCode.CARD_COUNT, 90..90, 80, "The pool has 80 cards; a kit holds 90.")

    /** A baseline of set `xyz` holding [stats] by pointer. */
    fun baseline(stats: Map<String, Stat>): Baseline =
        Baseline("xyz", 3, "2026-11-01T00:00:00Z", 1L, 10, stats.toSortedMap())

    /**
     * A pool with these copies, worked out by hand:
     * - Bear: white creature, cost {1}{W}, 3 copies.
     * - Smite: white sorcery "Destroy target creature.", 2 copies (removal).
     * - Drifter: blue creature, 1 copy.
     * - Envoy: white and blue creature, 1 copy.
     * - Brute: red creature, 2 copies (not castable in white-blue).
     * - Plains: basic land, 5 copies.
     *
     * So the pool holds 3 + 2 + 1 + 1 + 2 + 5 = 14 cards, 2 removal spells, and 3 + 1 + 1 = 5
     * creatures castable in WU.
     */
    fun metricsPool(): Pool =
        Pool(
            listOf(
                entry(card("Bear", setOf(Color.W), "{1}{W}"), 3),
                entry(
                    card(
                        "Smite",
                        setOf(Color.W),
                        "{1}{W}",
                        type = "Sorcery",
                        text = "Destroy target creature.",
                    ),
                    2,
                ),
                entry(card("Drifter", setOf(Color.U), "{1}{U}"), 1),
                entry(card("Envoy", setOf(Color.W, Color.U), "{W}{U}"), 1),
                entry(card("Brute", setOf(Color.R), "{1}{R}"), 2),
                entry(
                    card(
                        "Plains",
                        cost = "",
                        type = "Basic Land — Plains",
                        text = "({T}: Add {W}.)",
                    ),
                    5,
                ),
            )
        )

    fun metrics() = MetricEngine.compute(metricsPool())
}
