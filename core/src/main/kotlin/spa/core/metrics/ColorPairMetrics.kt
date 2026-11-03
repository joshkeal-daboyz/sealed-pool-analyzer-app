package spa.core.metrics

import kotlinx.serialization.Serializable
import spa.core.text.Color

/**
 * The numbers for one [ColorPair], counted by copies over the cards castable in it: nonland cards
 * whose colors are a subset of the pair's, colorless cards included.
 *
 * @property cards the castable cards.
 * @property creatures the castable creature cards.
 * @property noncreatures the castable cards that are not creatures.
 * @property curve the castable cards by mana value.
 * @property removal the castable cards tagged [spa.core.text.Tag.REMOVAL].
 * @property interaction the castable cards with at least one interaction tag.
 * @property fixingSources copies of the pool's fixing sources that produce either pair color,
 *   castable or not.
 * @property splashCandidates the pool's splash candidates for this pair, by name then color.
 * @property landRecommendation [spa.core.math.LandMath.recommendLands] for 17 lands over the pips
 *   of the castable cards.
 * @property castProbability for each pair color, the chance of at least one source of it in the
 *   first 9 cards of a 40-card deck, rounded to 4 decimals.
 */
@Serializable
data class ColorPairMetrics(
    val cards: Int,
    val creatures: Int,
    val noncreatures: Int,
    val curve: Curve,
    val removal: Int,
    val interaction: Int,
    val fixingSources: Int,
    val splashCandidates: List<SplashCandidate>,
    val landRecommendation: Map<Color, Int>,
    val castProbability: Map<Color, Double>,
)
