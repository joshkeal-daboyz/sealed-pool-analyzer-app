package spa.agent.tags.review

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import spa.agent.tags.CardFileIds
import spa.agent.tags.ResponsePartner
import spa.agent.tags.ResponseRole

/**
 * SPA-052 criteria 3 and 5: each correction action on hand-built cards, the problems each one
 * names, the order corrections apply in, and a test-only action added to the list.
 */
class ReviewApplyTest {
    private val file = "cards-01.review.json"

    private val cards =
        listOf(
            ReviewCard("syn/1", "Grizzly Bears", emptyList(), emptyList()),
            ReviewCard(
                "syn/2",
                "Serra Angel",
                listOf(ResponseRole("flying", "payoff", "1")),
                listOf(ResponsePartner("syn/3", "2")),
            ),
            ReviewCard("syn/3", "Air Elemental", emptyList(), emptyList()),
        )

    private val cardFiles =
        listOf(
            CardFileIds("cards-01.json", listOf("syn/1", "syn/2")),
            CardFileIds("cards-02.json", listOf("syn/3")),
        )

    private var number = 0

    @Suppress("LongParameterList")
    private fun c(
        id: String,
        action: String,
        axis: String? = null,
        role: String? = null,
        partner: String? = null,
        strength: String? = null,
        inFile: String = file,
    ) = Correction(inFile, ++number, id, action, axis, role, partner, strength, "a reason")

    private fun apply(
        vararg corrections: Correction,
        actions: List<ReviewAction> = ReviewActions.ALL,
    ) = ReviewApply.apply(cards, cardFiles, corrections.toList(), actions)

    private fun card(result: ReviewApplied, id: String) = result.cards.single { it.id == id }

    @Test
    fun `the five actions, in order`() {
        assertEquals(
            listOf("add-role", "remove-role", "set-strength", "add-partner", "remove-partner"),
            ReviewActions.ALL.map { it.name },
        )
        assertEquals(setOf("axis", "role", "strength"), AddRoleAction.fields)
        assertEquals(setOf("axis", "role"), RemoveRoleAction.fields)
        assertEquals(setOf("axis", "role", "strength"), SetStrengthAction.fields)
        assertEquals(setOf("partner", "strength"), AddPartnerAction.fields)
        assertEquals(setOf("partner"), RemovePartnerAction.fields)
    }

    @Test
    fun `add-role appends the role`() {
        val result = apply(c("syn/2", "add-role", "flying", "enabler", strength = "2"))

        assertEquals(emptyList(), result.problems)
        assertEquals(
            listOf(ResponseRole("flying", "payoff", "1"), ResponseRole("flying", "enabler", "2")),
            card(result, "syn/2").roles,
        )
        assertEquals(cards[0], card(result, "syn/1"))
    }

    @Test
    fun `remove-role takes the role off`() {
        val result = apply(c("syn/2", "remove-role", "flying", "payoff"))

        assertEquals(emptyList(), result.problems)
        assertEquals(emptyList(), card(result, "syn/2").roles)
        assertEquals(cards[1].partners, card(result, "syn/2").partners)
    }

    @Test
    fun `set-strength changes the strength in place`() {
        val result = apply(c("syn/2", "set-strength", "flying", "payoff", strength = "3"))

        assertEquals(emptyList(), result.problems)
        assertEquals(listOf(ResponseRole("flying", "payoff", "3")), card(result, "syn/2").roles)
    }

    @Test
    fun `add-partner appends the partner, and remove-partner takes one off`() {
        val added = apply(c("syn/1", "add-partner", partner = "syn/1", strength = "3"))
        val removed = apply(c("syn/2", "remove-partner", partner = "syn/3"))

        assertEquals(emptyList(), added.problems + removed.problems)
        assertEquals(listOf(ResponsePartner("syn/1", "3")), card(added, "syn/1").partners)
        assertEquals(emptyList(), card(removed, "syn/2").partners)
        assertEquals(cards[1].roles, card(removed, "syn/2").roles)
    }

    @Test
    fun `targeting what a card lacks, or adding what it has, is named`() {
        val result =
            apply(
                c("syn/1", "remove-role", "flying", "payoff"),
                c("syn/2", "remove-role", "flying", "enabler"),
                c("syn/1", "set-strength", "flying", "payoff", strength = "2"),
                c("syn/1", "remove-partner", partner = "syn/2"),
                c("syn/2", "add-role", "flying", "payoff", strength = "3"),
                c("syn/2", "add-partner", partner = "syn/3", strength = "1"),
            )

        assertEquals(
            listOf(
                "$file: correction 1: syn/1 has no payoff role on flying to remove",
                "$file: correction 3: syn/1 has no payoff role on flying to set the strength of",
                "$file: correction 4: syn/1 has no partner syn/2 to remove",
                "$file: correction 2: syn/2 has no enabler role on flying to remove",
                "$file: correction 5: syn/2 already has the payoff role on flying",
                "$file: correction 6: syn/2 already has the partner syn/3",
            ),
            result.problems,
        )
        assertEquals(cards, result.cards, "a rejected correction changes nothing")
        assertEquals(emptyList(), result.applied)
    }

    @Test
    fun `a correction for a card outside its file is named`() {
        val result =
            apply(
                c("syn/3", "remove-role", "flying", "payoff"),
                c("syn/9", "add-partner", partner = "syn/1", strength = "1"),
                c("syn/2", "remove-partner", partner = "syn/3", inFile = "cards-02.review.json"),
            )

        assertEquals(
            listOf(
                "$file: correction 1: syn/3 is not a card in cards-01.json",
                "$file: correction 2: syn/9 is not a card in cards-01.json",
                "cards-02.review.json: correction 3: syn/2 is not a card in cards-02.json",
            ),
            result.problems,
        )
        assertEquals(cards, result.cards)
    }

    @Test
    fun `corrections apply in file order, then card order, each on the card as corrected so far`() {
        val result =
            apply(
                c(
                    "syn/3",
                    "add-role",
                    "flying",
                    "payoff",
                    strength = "1",
                    inFile = "cards-02.review.json",
                ),
                c("syn/2", "add-role", "flying", "enabler", strength = "1"),
                c("syn/1", "add-role", "flying", "payoff", strength = "2"),
                c("syn/2", "set-strength", "flying", "enabler", strength = "3"),
                c("syn/2", "remove-role", "flying", "payoff"),
            )

        assertEquals(emptyList(), result.problems)
        assertEquals(listOf(3, 2, 4, 5, 1), result.applied.map { it.number })
        assertEquals(listOf(ResponseRole("flying", "enabler", "3")), card(result, "syn/2").roles)
        assertEquals(listOf(ResponseRole("flying", "payoff", "1")), card(result, "syn/3").roles)
    }

    @Test
    fun `a strength the pass-2 checks reject is applied as written, for them to name`() {
        val result = apply(c("syn/2", "set-strength", "flying", "payoff", strength = "4"))

        assertEquals(emptyList(), result.problems)
        assertEquals(listOf(ResponseRole("flying", "payoff", "4")), card(result, "syn/2").roles)
    }

    /** A test-only action: takes every partner off a card. */
    private object ClearPartners : ReviewAction {
        override val name = "clear-partners"
        override val fields = emptySet<String>()

        override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome =
            ReviewOutcome.Applied(card.copy(partners = emptyList()))
    }

    @Test
    fun `a test-only action added to the list applies with no other code change`() {
        val actions = ReviewActions.ALL + ClearPartners
        val text = """[{ "id": "syn/2", "action": "clear-partners", "reason": "None of them." }]"""
        val read = ReviewFiles.parse(file, text, actions)

        val result = ReviewApply.apply(cards, cardFiles, read.corrections, actions)

        assertEquals(emptyList(), read.problems + result.problems)
        assertEquals(emptyList(), card(result, "syn/2").partners)
        assertEquals(listOf("clear-partners"), result.applied.map { it.action })
        assertEquals(
            listOf(
                "$file: correction 1: unknown action 'clear-partners'; use add-role, " +
                    "remove-role, set-strength, add-partner or remove-partner"
            ),
            ReviewFiles.parse(file, text).problems,
            "without it in the list the action is unknown",
        )
    }
}
