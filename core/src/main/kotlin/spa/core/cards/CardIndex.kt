package spa.core.cards

import java.nio.file.Path
import java.util.Locale

/**
 * Every printing of a set's sealed pool, with exact lookups. Built by [load] from the cache of a
 * set and of each of its companions.
 */
class CardIndex(
    /** The loaded set codes: the main set first, then its companions. */
    val setCodes: List<String>,
    /** Every printing, in load order. */
    val printings: List<Printing>,
) {
    /** The distinct cards, in the order their first printing was loaded. */
    val cards: List<Card> = printings.map { it.card }.distinctBy { it.oracleId }

    private val byId = printings.associateBy { it.scryfallId }
    private val bySetAndNumber = printings.associateBy { numberKey(it.setCode, it.collectorNumber) }
    private val bySet = printings.groupBy { it.setCode }
    private val byFullName = keyed(cards) { listOf(it.name) }
    private val byFaceName = keyed(cards) { card -> card.faces.map { it.name } }

    /** The printing with Scryfall id [id], or null. */
    fun byScryfallId(id: String): Printing? = byId[id]

    /**
     * The card whose full name or any single face name is [name], ignoring case, surrounding
     * whitespace, and curly versus straight apostrophes. A full-name match wins over a face name.
     * No fuzzy matching: a misspelled name is null.
     */
    fun byName(name: String): Card? = nameKey(name).let { byFullName[it] ?: byFaceName[it] }

    /**
     * The printing of set [setCode] with collector number [number], or null. Both match ignoring
     * letter case, so `1701d` finds `1701D`; the printing keeps its printed number.
     */
    fun byCollectorNumber(setCode: String, number: String): Printing? =
        bySetAndNumber[numberKey(setCode, number)]

    /** The printings of one loaded set, in search order; empty for a set not in this index. */
    fun printings(setCode: String): List<Printing> = bySet[setCode.lowercase(Locale.ROOT)].orEmpty()

    companion object {
        /**
         * The index of [code]'s sealed pool: [code] and every set in its companions.json, each read
         * from its cache under [dataDir]. Makes no network call.
         */
        fun load(dataDir: Path, code: String): CardIndex {
            val codes = listOf(code) + ScryfallCache.companions(dataDir, code)
            val shared = mutableMapOf<String, Card>()
            val printings = codes.flatMap {
                CardNormalizer.printings(ScryfallCache.load(dataDir, it).cards, shared)
            }
            return CardIndex(codes, printings)
        }

        /** The lookup key for a name: trimmed, lowercase, curly apostrophes made straight. */
        internal fun nameKey(name: String): String =
            name.trim().replace('’', '\'').replace('‘', '\'').lowercase(Locale.ROOT)

        /** The lookup key for a set code and collector number: both lowercase. */
        private fun numberKey(setCode: String, number: String): Pair<String, String> =
            setCode.lowercase(Locale.ROOT) to number.lowercase(Locale.ROOT)

        private fun keyed(cards: List<Card>, names: (Card) -> List<String>): Map<String, Card> {
            val map = mutableMapOf<String, Card>()
            cards.forEach { card -> names(card).forEach { map.putIfAbsent(nameKey(it), card) } }
            return map
        }
    }
}
