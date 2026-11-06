package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals

/** SPA-069 criteria 3 and 4: the plain text of a review. */
class ReviewTextTest {
    private val numbersLine =
        "- [key] WU: Few two drops. (cards: Bear) " +
            "(numbers: /curve/twoDrops = 5 (typical 4, range 2 to 6); /removal/count = 2)"

    private val usageLine =
        "Usage: 7 calls, input 1000, output 200, cache read 300, cache write 400 " +
            "(model claude-test)"

    @Test
    fun `a full review prints every block in order`() {
        val expected =
            listOf(
                    "Review: xyz (Test Set)",
                    "Pool: 45 cards, 30 distinct, 2 unresolved lines",
                    "Kit warning: Card count 45 is outside 80 to 100.",
                    "Kit warning: Too many rares.",
                    "",
                    "Decks",
                    "Rank 1: WU (same cards as WB)  score 12.50",
                    "  Spells (3): 2x Bear, 1x Envoy",
                    "  Lands (17): 1x Dual Land, 8x Plains, 8x Island",
                    "  Why: grades +10.00: strong; curve -1.50: light",
                    "Rank 2: WB  score 9.00",
                    "  Spells (1): 1x Bear",
                    "  Lands (17): 9x Plains, 8x Swamp",
                    "",
                    "Verdict",
                    "Register deck one.",
                    "Rank 1: Solid. (cards: Bear, Envoy)",
                    "Dropped notes: 2 (unknown rank 1, no card 1)",
                    "",
                    "Title of fake-a  (confidence 0.80)",
                    "Curve is thin.",
                    numbersLine,
                    "- [info] Envoys are fine. (cards: Envoy)",
                    "Dropped findings: 3 (no card 2, wrong number value 1)",
                    "",
                    "Title of fake-b  (confidence 0.50)",
                    "Nothing to say.",
                    "No findings kept.",
                    "",
                    "Disagreements",
                    "- Blue depth [fake-a, fake-b]: Split. (cards: Envoy)",
                    "",
                    "Missing: fake-c (${ReviewTestData.INVALID})",
                    "",
                    usageLine,
                    "",
                    ReviewTestData.ATTRIBUTION,
                )
                .joinToString("\n") + "\n"

        assertEquals(expected, ReviewText.render(ReviewTestData.review))
    }

    @Test
    fun `a review with no Explainer, no deck and no analyst prints the short forms`() {
        val expected =
            listOf(
                    "Review: xyz (Test Set)",
                    "Pool: 45 cards, 30 distinct, 0 unresolved lines",
                    "",
                    "Decks",
                    "No deck could be built from this pool.",
                    "",
                    "Verdict",
                    "Not available (no findings to explain).",
                    "",
                    "Disagreements",
                    "None.",
                    "",
                    "Missing: explainer (no findings to explain)",
                    "",
                    usageLine,
                    "",
                    ReviewTestData.ATTRIBUTION,
                )
                .joinToString("\n") + "\n"

        assertEquals(expected, ReviewText.render(ReviewTestData.bare))
    }

    @Test
    fun `the same review prints the same bytes twice`() {
        assertEquals(
            ReviewText.render(ReviewTestData.review),
            ReviewText.render(ReviewTestData.review),
        )
    }

    @Test
    fun `every reason has its words, in enum order with its count`() {
        val dropped =
            DropReason.entries.reversed().map { DroppedFinding(it, "c") } +
                DroppedFinding(DropReason.NO_CARD, "d")
        val outcome =
            AgentOutcome.Answered(
                FakeAgent("fake-a"),
                AgentAnswer("fake-a", 1.0, "S.", emptyList()),
                dropped,
            )
        val items =
            ExplainerDropReason.entries.mapIndexed { i, reason ->
                DroppedItem(if (i % 2 == 0) ItemKind.DECK else ItemKind.DISAGREEMENT, reason, "t")
            }
        val review =
            ReviewTestData.review.copy(
                sections = listOf(outcome),
                verdict = ReviewTestData.explained.copy(dropped = items),
            )

        val lines = ReviewText.render(review).lines()

        assertEquals(
            "Dropped findings: 6 (no card 2, unknown card 1, unknown number key 1, " +
                "wrong number value 1, over the limit 1)",
            lines.single { it.startsWith("Dropped findings") },
        )
        assertEquals(
            "Dropped notes: 6 (unknown rank 1, duplicate rank 1, too few agents 1, " +
                "unknown agent 1, no card 1, unknown card 1)",
            lines.single { it.startsWith("Dropped notes") },
        )
    }
}
