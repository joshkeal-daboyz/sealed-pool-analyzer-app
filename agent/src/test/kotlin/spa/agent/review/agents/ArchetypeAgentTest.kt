package spa.agent.review.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import spa.agent.review.NumberEntry
import spa.agent.review.NumberTables
import spa.agent.review.ReviewInput
import spa.agent.review.ReviewTestCards
import spa.agent.review.ReviewTestCards.card
import spa.agent.review.ReviewTestCards.entry
import spa.core.brief.Archetype
import spa.core.cards.Face
import spa.core.cards.Pool
import spa.core.metrics.MetricEngine
import spa.core.text.Color

class ArchetypeAgentTest {
    private fun creature(name: String, color: Color) =
        card(name, setOf(color), "{1}{${color.symbol}}")

    private val cloudSage =
        ReviewTestCards.doubleFaced(
            Face("Cloud Sage", "{1}{U}", 2.0, setOf(Color.U), "Creature — Bird", "", null, null),
            Face("Storm Sage", "", 0.0, setOf(Color.U), "Creature — Bird", "", null, null),
        )

    // Castable by copies: WU holds Sky Marshal 2 + Cloud Sage 1 + Wisp 1 = 4 cards (all creatures);
    // BR holds Pyre Priest 1 + Brute 2 = 3; WB holds Sky Marshal 2 + Pyre Priest 1 = 3.
    private val pool =
        Pool(
            listOf(
                entry(creature("Sky Marshal", Color.W), 2),
                entry(cloudSage, 1),
                entry(creature("Wisp", Color.U), 1),
                entry(creature("Pyre Priest", Color.B), 1),
                entry(creature("Brute", Color.R), 2),
                entry(creature("Unused", Color.G), 0),
                entry(card("Plains", cost = "", type = "Basic Land — Plains"), 3),
            )
        )

    private fun archetypes(wu: String = "UW") =
        listOf(
            Archetype("BR", "Sacrifice", "Trade.", listOf("Pyre Priest", "Grave Imp")),
            Archetype(
                wu,
                "Fliers",
                "Win in the air.",
                listOf("Sky Marshal", "Cloud Sage"),
                listOf("Wisp", "Gull"),
            ),
            Archetype("UG", "Growth", "Ramp.", listOf("Vine Lord"), listOf("Sprout", "Unused")),
        )

    private fun input(list: List<Archetype> = archetypes()) =
        ReviewInput(
            "xyz",
            pool,
            MetricEngine.compute(pool),
            null,
            ReviewTestCards.brief.copy(archetypes = list),
            { null },
            emptyList(),
        )

    @Test
    fun `the agent has its id and title and always runs`() {
        assertEquals("archetypes", ArchetypeAgent.id)
        assertEquals("Archetypes", ArchetypeAgent.title)
        assertNull(ArchetypeAgent.unavailable(input()))
    }

    @Test
    fun `the numbers count names present once each, in color pair order`() {
        val table = ArchetypeAgent.numbers(input())

        val expected =
            listOf(
                // WU: Sky Marshal (twice) and the front face of Cloud Sage count once each.
                NumberEntry("/archetypes/WU/signpostsPresent", 2.0, null),
                NumberEntry("/archetypes/WU/signpostsTotal", 2.0, null),
                NumberEntry("/archetypes/WU/keyCommonsPresent", 1.0, null),
                NumberEntry("/archetypes/WU/keyCommonsTotal", 2.0, null),
                // UG: nothing present; Unused has 0 copies.
                NumberEntry("/archetypes/UG/signpostsPresent", 0.0, null),
                NumberEntry("/archetypes/UG/signpostsTotal", 1.0, null),
                NumberEntry("/archetypes/UG/keyCommonsPresent", 0.0, null),
                NumberEntry("/archetypes/UG/keyCommonsTotal", 2.0, null),
                // BR: no key commons.
                NumberEntry("/archetypes/BR/signpostsPresent", 1.0, null),
                NumberEntry("/archetypes/BR/signpostsTotal", 2.0, null),
                NumberEntry("/archetypes/BR/keyCommonsPresent", 0.0, null),
                NumberEntry("/archetypes/BR/keyCommonsTotal", 0.0, null),
            )
        assertEquals(expected, table.entries.take(expected.size))
        assertEquals(
            NumberTables.fromMetrics(input(), listOf("/pairs/*/cards", "/pairs/*/creatures"))
                .entries,
            table.entries.drop(expected.size),
        )
        val byKey = table.entries.associateBy { it.key }
        assertEquals(4.0, byKey.getValue("/pairs/WU/cards").value)
        assertEquals(4.0, byKey.getValue("/pairs/WU/creatures").value)
        assertEquals(3.0, byKey.getValue("/pairs/BR/cards").value)
        assertEquals(3.0, byKey.getValue("/pairs/WB/cards").value)
        assertEquals(32, table.entries.size)
    }

    @Test
    fun `an archetype written WU and one written UW map to the same pair`() {
        val uw = ArchetypeAgent.numbers(input(archetypes("UW")))
        val wu = ArchetypeAgent.numbers(input(archetypes("WU")))

        assertEquals(uw, wu)
        assertEquals("/archetypes/WU/signpostsPresent", uw.entries.first().key)
    }

    @Test
    fun `the facts name what is present and what is missing`() {
        val expected =
            """
            WU Fliers: signposts present: Sky Marshal, Cloud Sage; missing: none. Key commons present: Wisp; missing: Gull.
            UG Growth: signposts present: none; missing: Vine Lord. Key commons present: none; missing: Sprout, Unused.
            BR Sacrifice: signposts present: Pyre Priest; missing: Grave Imp. Key commons present: none; missing: none.
            """
                .trimIndent()

        assertEquals(expected, ArchetypeAgent.facts(input()))
    }
}
