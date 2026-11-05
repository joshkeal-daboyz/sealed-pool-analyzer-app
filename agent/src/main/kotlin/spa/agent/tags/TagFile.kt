package spa.agent.tags

import kotlinx.serialization.Serializable

/**
 * One synergy axis of a set: its [id] (lowercase letters, digits, hyphens), [name], [definition].
 */
@Serializable data class Axis(val id: String, val name: String, val definition: String)

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
 * A set's synergy tag file, `fixtures/tags/<code>.json`: the [set] code, the [provenance] (model,
 * prompt version and date), the set's [axes], and every sealed-pool card's tags in collector order.
 */
@Serializable
data class TagFile(
    val set: String,
    val provenance: String,
    val axes: List<Axis>,
    val cards: List<TagCard>,
)
