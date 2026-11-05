package spa.agent.grader

import spa.core.cards.CardIndex
import spa.core.cards.Printing

/**
 * The cards a grader grades: a set's sealed pool (the set and its companions), basic lands
 * excluded, one printing per distinct card.
 */
object GradingPool {
    /**
     * One printing per distinct card of [index], basics excluded. Printings are taken in set order
     * (the main set first, then each companion) and, within a set, in collector order; a printing
     * whose card (its Scryfall oracle id) an earlier printing already holds is a variant, such as a
     * showcase or borderless printing, and is left out.
     */
    fun printings(index: CardIndex): List<Printing> =
        index.setCodes
            .flatMap { set ->
                index.printings(set).sortedWith(compareBy(collectorOrder) { it.collectorNumber })
            }
            .filter { !it.card.isBasicLand }
            .distinctBy { it.card.oracleId }

    /** The packet id of [printing]: `<setCode>/<collectorNumber>`. */
    fun id(printing: Printing): String = "${printing.setCode}/${printing.collectorNumber}"

    /**
     * Collector order: by the number's leading digits as a number, then by the rest of it, so `9`
     * comes before `10` and `101` before `101b`. A number with no leading digits comes last.
     */
    val collectorOrder: Comparator<String> = compareBy<String> { leadingNumber(it) }.thenBy { it }

    private fun leadingNumber(number: String): Long =
        number.takeWhile { it.isDigit() }.take(MAX_DIGITS).toLongOrNull() ?: Long.MAX_VALUE

    private const val MAX_DIGITS = 18
}
