package spa.core.metrics

import kotlinx.serialization.Serializable

/**
 * Copies of cards by printing rarity. [total] is every copy counted, so it also holds the special
 * and bonus rarities, which have no field of their own.
 */
@Serializable
data class RarityCounts(
    val common: Int,
    val uncommon: Int,
    val rare: Int,
    val mythic: Int,
    val total: Int,
)
