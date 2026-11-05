package spa.agent.synergy.check

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import spa.agent.synergy.SynergyTable
import spa.agent.tags.Axis
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.agent.tags.TagPartner
import spa.agent.tags.TagRole

/**
 * SPA-050 criteria 1 and 4: each check kind on a small hand-built tag file and check file, the
 * errors, and the seam (a test-only kind run from a check file with no other code change).
 *
 * The tag file `xyz`, and the scores that follow from it (axis term, partner term):
 *
 * | #   | Card     | Tags             |
 * |-----|----------|------------------|
 * | 1   | Maker    | tokens enabler 2 |
 * | 2   | Rewarder | tokens payoff 3  |
 * | 3   | Selfie   | names itself, 1  |
 * | 4   | Blank    | none             |
 * | 5   | Minor    | tokens payoff 1  |
 *
 * score(1,2) = 6 (axis 6), score(1,5) = 2 (axis 2), score(3,3) = 1 (partner 1); every other pair
 * scores 0.
 */
class SynergyChecksTest {
    private val table =
        SynergyTable(
            TagFile(
                set = "xyz",
                provenance = "hand-built",
                axes =
                    listOf(
                        Axis("tokens", "Tokens", "Makes tokens."),
                        Axis("counters", "Counters", "Uses counters."),
                        Axis("mill", "Mill", "Mills."),
                    ),
                cards =
                    listOf(
                        TagCard(
                            "xyz/1",
                            "Maker",
                            listOf(TagRole("tokens", "enabler", 2)),
                            listOf(),
                        ),
                        TagCard(
                            "xyz/2",
                            "Rewarder",
                            listOf(TagRole("tokens", "payoff", 3)),
                            listOf(),
                        ),
                        TagCard("xyz/3", "Selfie", listOf(), listOf(TagPartner("xyz/3", 1))),
                        TagCard("xyz/4", "Blank", listOf(), listOf()),
                        TagCard("xyz/5", "Minor", listOf(TagRole("tokens", "payoff", 1)), listOf()),
                    ),
            )
        )

    private fun file(vararg checks: String) =
        """{ "set": "xyz", "checks": [ ${checks.joinToString(", ")} ] }"""

    private fun pair(id: String, a: Int, b: Int, min: Int, status: String = "independent") =
        """{ "id": "$id", "owner": "words", "added": "2026-11-05", "status": "$status",
             "kind": "pair", "a": "xyz/$a", "b": "xyz/$b", "min": $min }"""

    private fun groups(
        id: String,
        groupA: List<Int>,
        groupB: List<Int>,
        share: Double,
        min: Int = 1,
    ): String {
        fun group(numbers: List<Int>) =
            numbers.joinToString(", ", "[", "]") { """{ "id": "xyz/$it", "name": "n$it" }""" }
        return """{ "id": "$id", "owner": "words", "added": "2026-11-05", "status": "independent",
             "kind": "groups", "share": $share, "min": $min, "listRule": "by hand",
             "groupA": ${group(groupA)}, "groupB": ${group(groupB)} }"""
    }

    private fun run(text: String, kinds: List<CheckKind> = CheckKinds.ALL): List<String> =
        SynergyChecks.lines(SynergyChecks.parse(text, kinds), table)

    @Test
    fun `pair passes at min, the boundary, and fails above it`() {
        val lines = run(file(pair("at-min", 1, 2, 6), pair("above", 1, 2, 7, status = "seen")))

        assertEquals(
            listOf(
                "PASS at-min independent  score 6  axis 6  partner 0",
                "FAIL above seen  score 6  axis 6  partner 0",
                "1 of 2 checks pass (1 independent)",
            ),
            lines,
        )
    }

    @Test
    fun `pair reads a card with itself and either order`() {
        val lines = run(file(pair("self", 3, 3, 1), pair("reversed", 5, 1, 2)))

        assertEquals(
            listOf(
                "PASS self independent  score 1  axis 0  partner 1",
                "PASS reversed independent  score 2  axis 2  partner 0",
                "2 of 2 checks pass (2 independent)",
            ),
            lines,
        )
    }

    @Test
    fun `groups passes above share and fails at share, the boundary`() {
        // Pairs (1,2) 6, (1,5) 2, (3,2) 0, (3,5) 0: 4 pairs, 2 meeting min 1, share 0.5, mean 2.
        val lines =
            run(
                file(
                    groups("below", listOf(1, 3), listOf(2, 5), 0.4999),
                    groups("at", listOf(1, 3), listOf(2, 5), 0.5),
                )
            )

        assertEquals(
            listOf(
                "PASS below independent  4 pairs  2 meeting min 1  share 0.5000  mean 2.0000",
                "FAIL at independent  4 pairs  2 meeting min 1  share 0.5000  mean 2.0000",
                "1 of 2 checks pass (1 independent)",
            ),
            lines,
        )
    }

    @Test
    fun `groups counts min against each pair's score`() {
        // Pairs (1,2) 6 and (1,5) 2: with min 3 only the first meets it.
        val lines = run(file(groups("min3", listOf(1), listOf(2, 5), 0.5, min = 3)))

        assertEquals(
            "FAIL min3 independent  2 pairs  1 meeting min 3  share 0.5000  mean 4.0000",
            lines.first(),
        )
    }

    @Test
    fun `groups skips a card in both groups as a pair with itself`() {
        // Selfie (3) is in both groups. Pairs (1,3) 0, (1,5) 2, (3,5) 0; (3,3), which scores 1, is
        // skipped. With it, 2 of 4 would meet min (0.5 > 0.4, a pass); without it, 1 of 3.
        val lines = run(file(groups("both", listOf(1, 3), listOf(3, 5), 0.4)))

        assertEquals(
            listOf(
                "FAIL both independent  3 pairs  1 meeting min 1  share 0.3333  mean 0.6667",
                "0 of 1 checks pass (0 independent)",
            ),
            lines,
        )
    }

    @Test
    fun `the last line counts passes, and the passing checks that are independent`() {
        val lines =
            run(
                file(
                    pair("a", 1, 2, 1, status = "seen"),
                    pair("b", 1, 2, 1),
                    pair("c", 1, 4, 1),
                )
            )

        assertEquals("2 of 3 checks pass (1 independent)", lines.last())
        assertEquals(4, lines.size)
    }

    @Test
    fun `an unknown kind is a one-line error`() {
        val text =
            file(pair("ok", 1, 2, 1), """{ "id": "odd", "status": "seen", "kind": "triple" }""")

        val e = assertFailsWith<IllegalArgumentException> { SynergyChecks.parse(text) }

        assertEquals(1, e.message!!.lines().size)
        assertTrue("unknown kind 'triple'" in e.message!!, e.message)
        assertTrue("odd" in e.message!!, e.message)
    }

    @Test
    fun `a card id the tag file does not hold is a one-line error, before any check runs`() {
        val parsed =
            SynergyChecks.parse(file(pair("ok", 1, 2, 1), groups("bad", listOf(1), listOf(9), 0.5)))

        val e = assertFailsWith<IllegalArgumentException> { SynergyChecks.lines(parsed, table) }

        assertEquals(1, e.message!!.lines().size)
        assertTrue("xyz/9" in e.message!!, e.message)
        assertTrue("bad" in e.message!!, e.message)
    }

    @Test
    fun `a bad status, a missing field or another set's file is an error`() {
        assertFailsWith<IllegalArgumentException> {
            SynergyChecks.parse(file(pair("s", 1, 2, 1, status = "maybe")))
        }
        assertFailsWith<IllegalArgumentException> {
            SynergyChecks.parse(
                file("""{ "id": "m", "status": "seen", "kind": "pair", "a": "xyz/1" }""")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SynergyChecks.parse(file(pair("d", 1, 2, 1), pair("d", 1, 2, 1)))
        }
        assertFailsWith<IllegalArgumentException> { SynergyChecks.parse("not json") }
        assertFailsWith<IllegalArgumentException> { SynergyChecks.parse("""{ "set": "xyz" }""") }
    }

    /** A test-only kind: passes when a card's score with itself is at least `min`. */
    private object SelfKind : CheckKind {
        override val kind = "self"

        override fun parse(check: JsonObject): KindCheck {
            val card = check.getValue("card").jsonPrimitive.content
            val min = check.getValue("min").jsonPrimitive.int
            return object : KindCheck {
                override val cardIds = listOf(card)

                override fun run(table: SynergyTable): CheckResult {
                    val score = table.score(card, card)
                    return CheckResult(score >= min, listOf("self $score"))
                }
            }
        }
    }

    @Test
    fun `a new kind added to the list runs from a check file with no other change`() {
        val text =
            file(
                pair("p", 1, 2, 1),
                """{ "id": "s", "status": "independent", "kind": "self", "card": "xyz/3", "min": 1 }""",
            )

        assertEquals(
            listOf(
                "PASS p independent  score 6  axis 6  partner 0",
                "PASS s independent  self 1",
                "2 of 2 checks pass (2 independent)",
            ),
            run(text, CheckKinds.ALL + SelfKind),
        )
        assertFailsWith<IllegalArgumentException> { SynergyChecks.parse(text) }
    }

    @Test
    fun `the shipped kinds are pair and groups`() {
        assertEquals(listOf("pair", "groups"), CheckKinds.ALL.map { it.kind })
    }
}
