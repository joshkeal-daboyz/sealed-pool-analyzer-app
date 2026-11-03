package spa.core.metrics

import kotlinx.serialization.Serializable
import spa.core.text.Color

/** Why a card is worth splashing. */
@Serializable
enum class SplashReason {
    /** It is tagged [spa.core.text.Tag.REMOVAL]. This reason wins when both apply. */
    REMOVAL,

    /** Its printing is rare or mythic. */
    RARE_OR_MYTHIC,
}

/**
 * A card outside a pair that needs exactly one pip of one other color and is worth that pip.
 *
 * @property name the card's full name.
 * @property color the one color it needs outside the pair.
 */
@Serializable
data class SplashCandidate(val name: String, val color: Color, val reason: SplashReason)
