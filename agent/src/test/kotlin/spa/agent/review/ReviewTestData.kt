package spa.agent.review

import spa.agent.model.UsageTotals
import spa.core.baseline.Stat

/** Hand-built reviews for the printers' tests. */
internal object ReviewTestData {
    const val ATTRIBUTION =
        "Card data from Scryfall (scryfall.com). Card text and images are property of " +
            "Wizards of the Coast."
    const val INVALID = "invalid answer: the answer is not JSON: no object found"

    val usage = UsageTotals(7, 1000, 200, 300, 400)

    val header =
        ReviewHeader(
            "xyz",
            "Test Set",
            45,
            30,
            2,
            listOf("Card count 45 is outside 80 to 100.", "Too many rares."),
        )

    val deck1 =
        DeckSummary(
            1,
            "WU",
            listOf("WB"),
            12.5,
            listOf(NameCount("Bear", 2), NameCount("Envoy", 1)),
            17,
            listOf(NameCount("Dual Land", 1), NameCount("Plains", 8), NameCount("Island", 8)),
            "grades +10.00: strong; curve -1.50: light",
        )

    val deck2 =
        DeckSummary(
            2,
            "WB",
            emptyList(),
            9.0,
            listOf(NameCount("Bear", 1)),
            17,
            listOf(NameCount("Plains", 9), NameCount("Swamp", 8)),
            "",
        )

    val explained =
        ExplainerOutcome.Explained(
            ExplainerResult(
                0.7,
                "Register deck one.",
                listOf(DeckNote(1, "Solid.", listOf("Bear", "Envoy"))),
                listOf(
                    Disagreement(
                        "Blue depth",
                        listOf("fake-a", "fake-b"),
                        listOf("Envoy"),
                        "Split.",
                    )
                ),
            ),
            listOf(
                DroppedItem(ItemKind.DECK, ExplainerDropReason.UNKNOWN_RANK, "Odd."),
                DroppedItem(ItemKind.DECK, ExplainerDropReason.NO_CARD, "Bare."),
            ),
        )

    private val answeredA =
        AgentOutcome.Answered(
            FakeAgent("fake-a"),
            AgentAnswer(
                "fake-a",
                0.8,
                "Curve is thin.",
                listOf(
                    Finding(
                        "Few two drops.",
                        "WU",
                        Severity.KEY,
                        listOf("Bear"),
                        listOf(
                            AnswerNumber("/curve/twoDrops", 5.0, Stat(4.0, 2.0, 6.0, 4.0)),
                            AnswerNumber("/removal/count", 2.0, null),
                        ),
                    ),
                    Finding("Envoys are fine.", null, Severity.INFO, listOf("Envoy"), emptyList()),
                ),
            ),
            listOf(
                DroppedFinding(DropReason.NO_CARD, "Nobody."),
                DroppedFinding(DropReason.WRONG_VALUE, "Seven."),
                DroppedFinding(DropReason.NO_CARD, "Nothing."),
            ),
        )

    private val answeredB =
        AgentOutcome.Answered(
            FakeAgent("fake-b"),
            AgentAnswer("fake-b", 0.5, "Nothing to say.", emptyList()),
            emptyList(),
        )

    private val missingC = AgentOutcome.Missing(FakeAgent("fake-c"), INVALID)

    /** Two kit warnings, two decks, an explained verdict, three analysts and a disagreement. */
    val review =
        Review(
            header,
            listOf(deck1, deck2),
            explained,
            listOf(answeredA, answeredB, missingC),
            usage,
            "claude-test",
        )

    /** No kit warning, no deck, no analyst section and no Explainer. */
    val bare =
        Review(
            header.copy(kitWarnings = emptyList(), unresolved = 0),
            emptyList(),
            ExplainerOutcome.Missing("no findings to explain"),
            emptyList(),
            usage,
            "claude-test",
        )
}
