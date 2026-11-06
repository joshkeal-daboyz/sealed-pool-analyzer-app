package spa.cli

import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import spa.agent.facts.CardFacts
import spa.agent.facts.EntersTriggerRule
import spa.agent.facts.FactCard
import spa.agent.facts.FactRule
import spa.agent.facts.FactRules
import spa.agent.grader.GradingPool

/**
 * SPA-056 criterion 2: on trk, the shortened legendary name and "<self> or another … enters" add
 * `enters-trigger` to exactly six cards, and no other card's facts change.
 */
class FactsSelfNameTrkTest {
    private val data = DataDir(Path.of(Fixtures.data))
    private val index = Commands.loadIndex(data, "trk")

    /**
     * The SPA-051 enters rule, rebuilt here: `When(ever)`, a "this …" subject, the full name or the
     * name before the first comma, then "enters".
     */
    private val spa051Enters = FactRule { card ->
        val names =
            (EntersTriggerRule.SUBJECTS + card.fullName + card.fullName.substringBefore(','))
                .distinct()
                .sortedByDescending { it.length }
                .joinToString("|", "(?:", ")") { Regex.escape(it) }
        if (Regex("\\b(?:When|Whenever)\\s+$names\\s+enters\\b").containsMatchIn(card.frontText))
            setOf("enters-trigger")
        else emptySet()
    }

    private fun spa051(card: FactCard): Set<String> = spa051Enters.facts(card)

    @Test
    fun `six trk cards gain enters-trigger and no other card's facts change`() {
        val now = CardFacts.load(data.scryfall, index)
        val before =
            CardFacts.load(
                data.scryfall,
                index,
                FactRules.ALL.map { if (it === EntersTriggerRule) FactRule(::spa051) else it },
            )
        val names = GradingPool.printings(index).associate { GradingPool.id(it) to it.card.name }

        val changed = now.keys.filter { now[it] != before[it] }
        changed.forEach { assertEquals(before.getValue(it) + "enters-trigger", now[it], it) }
        assertEquals(
            setOf(
                "Ensign Pavel Chekov",
                "Commander Raffi Musiker",
                "Captain James T. Kirk",
                "Captain Jonathan Archer",
                "Captain Kathryn Janeway",
                "Dr. Leonard \"Bones\" McCoy",
            ),
            changed.map { names.getValue(it) }.toSet(),
        )
        assertEquals(6, changed.size)
        // Every fact of every trk card on main before SPA-056 (1349), plus the six.
        assertEquals(1349 + 6, now.values.sumOf { it.size })
    }
}
