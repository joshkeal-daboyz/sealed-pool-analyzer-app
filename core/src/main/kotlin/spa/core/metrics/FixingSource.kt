package spa.core.metrics

import kotlinx.serialization.Serializable
import spa.core.text.Color

/**
 * A card that fixes mana: one tagged [spa.core.text.Tag.FIXING], or a land that produces two or
 * more colors.
 *
 * @property name the card's full name.
 * @property colors the colors it produces, from every face, in WUBRG order.
 * @property count the copies of it in the pool.
 */
@Serializable data class FixingSource(val name: String, val colors: Set<Color>, val count: Int)
