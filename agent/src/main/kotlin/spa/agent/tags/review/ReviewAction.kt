package spa.agent.tags.review

import spa.agent.tags.ResponsePartner
import spa.agent.tags.ResponseRole

/**
 * One correction as a review file gives it: the review [file] it is in and its 1-based [number]
 * there, the card [id], the [action], the fields the action takes ([axis], [role], [partner], and
 * [strength] as the number was written; null when absent), and the reviewer's [reason].
 */
data class Correction(
    val file: String,
    val number: Int,
    val id: String,
    val action: String,
    val axis: String? = null,
    val role: String? = null,
    val partner: String? = null,
    val strength: String? = null,
    val reason: String,
)

/**
 * A card's tags while a review is applied: its [id], [name], [roles] and [partners], each strength
 * as written, so that a corrected strength the pass-2 checks reject reaches them as given.
 */
data class ReviewCard(
    val id: String,
    val name: String,
    val roles: List<ResponseRole>,
    val partners: List<ResponsePartner>,
)

/** What one correction does to a card: the corrected card, or why it cannot apply. */
sealed interface ReviewOutcome {
    data class Applied(val card: ReviewCard) : ReviewOutcome

    data class Rejected(val problem: String) : ReviewOutcome
}

/**
 * One kind of correction a reviewer may write, named by [name] in a correction's `action`. A
 * correction of this kind must give exactly the [fields] (of `axis`, `role`, `partner` and
 * `strength`) besides `id`, `action` and `reason`; the review files' reader checks that, so [apply]
 * sees them all. [apply] gives the corrected card, or a one-line problem naming the card when the
 * correction targets something the card lacks or adds something it already has. Whether the
 * corrected tags are allowed (axis, role, strength, partner count) is for the pass-2 checks.
 */
interface ReviewAction {
    val name: String
    val fields: Set<String>

    fun apply(card: ReviewCard, correction: Correction): ReviewOutcome
}

/** The correction actions, in order. A new action is one file and one entry here. */
object ReviewActions {
    val ALL: List<ReviewAction> =
        listOf(
            AddRoleAction,
            RemoveRoleAction,
            SetStrengthAction,
            AddPartnerAction,
            RemovePartnerAction,
        )
}
