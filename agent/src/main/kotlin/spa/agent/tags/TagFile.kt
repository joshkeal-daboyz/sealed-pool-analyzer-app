package spa.agent.tags

import kotlinx.serialization.Serializable

/** A card fact that supplies an axis by itself (tags prompt v2), with a [strength] of 1 to 3. */
@Serializable data class FactSupply(val fact: String, val strength: Int)

/**
 * One synergy axis of a set: its [id] (lowercase letters, digits, hyphens), [name], [definition],
 * and, from tags prompt v2, the card facts it is [suppliedBy] (null for a v1 axis, which has none).
 */
@Serializable
data class Axis(
    val id: String,
    val name: String,
    val definition: String,
    val suppliedBy: List<FactSupply>? = null,
)

/** A card's [role] on one [axis], `enabler` or `payoff`, with a [strength] of 1 to 3. */
@Serializable data class TagRole(val axis: String, val role: String, val strength: Int)

/** A named partner: the card with [id] that this card works with, with a [strength] of 1 to 3. */
@Serializable data class TagPartner(val id: String, val strength: Int)

/** One card's tags: its packet [id] and [name], its [roles] and its named [partners]. */
@Serializable
data class TagCard(
    val id: String,
    val name: String,
    val roles: List<TagRole>,
    val partners: List<TagPartner>,
)

/**
 * One correction a review applied (SPA-052): the card [id], the [action] (`add-role`, ...), the
 * fields the action takes ([axis], [role], [partner], [strength]; null when it takes none), and the
 * reviewer's one-line [reason].
 */
@Serializable
data class TagCorrection(
    val id: String,
    val action: String,
    val axis: String? = null,
    val role: String? = null,
    val partner: String? = null,
    val strength: Int? = null,
    val reason: String,
)

/**
 * The review pass's record in a tag file (SPA-052): the review's [provenance], the number of
 * applied corrections by action ([counts], in the actions' order), and every applied correction in
 * the order it was applied.
 */
@Serializable
data class TagReview(
    val provenance: String,
    val counts: Map<String, Int>,
    val corrections: List<TagCorrection>,
)

/**
 * A set's synergy tag file, `fixtures/tags/<code>.json`: the [set] code, the [provenance] (model,
 * prompt version and date), the set's [axes], every sealed-pool card's tags in collector order,
 * and, once `spa tags-review-import` has run, the [review] (null, and absent from the file,
 * before).
 */
@Serializable
data class TagFile(
    val set: String,
    val provenance: String,
    val axes: List<Axis>,
    val cards: List<TagCard>,
    val review: TagReview? = null,
)
