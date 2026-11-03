package spa.core.metrics

import kotlinx.serialization.Serializable
import spa.core.text.Color

/**
 * A group of a pool's nonland cards: one per color, plus multicolor and colorless. Lands are in no
 * bucket.
 *
 * @property color the bucket's color, or null for [MULTI] and [COLORLESS].
 */
@Serializable
enum class ColorBucket(val color: Color?) {
    W(Color.W),
    U(Color.U),
    B(Color.B),
    R(Color.R),
    G(Color.G),
    MULTI(null),
    COLORLESS(null);

    /**
     * Whether a nonland card of [colors] belongs here: a color bucket holds every card that has its
     * color, multicolor ones included; [MULTI] holds cards of two or more colors; [COLORLESS] holds
     * cards of none.
     */
    fun holds(colors: Set<Color>): Boolean =
        when (this) {
            MULTI -> colors.size >= 2
            COLORLESS -> colors.isEmpty()
            else -> color in colors
        }

    /**
     * Whether a card of [colors] that belongs here is a playable: in a color bucket, a card of
     * exactly that one color; in [MULTI] and [COLORLESS], every card that belongs.
     */
    fun isPlayable(colors: Set<Color>): Boolean =
        when (this) {
            MULTI,
            COLORLESS -> holds(colors)
            else -> colors == setOfNotNull(color)
        }
}

/**
 * The numbers for one [ColorBucket], every one counted by copies.
 *
 * @property cards the cards that belong to the bucket, by rarity.
 * @property playables the bucket's playables, by rarity (see [ColorBucket.isPlayable]).
 * @property creatures the bucket's creature cards.
 * @property removal the bucket's cards tagged [spa.core.text.Tag.REMOVAL].
 * @property interaction the bucket's cards with at least one interaction tag.
 */
@Serializable
data class ColorMetrics(
    val cards: RarityCounts,
    val playables: RarityCounts,
    val creatures: Int,
    val removal: Int,
    val interaction: Int,
)
