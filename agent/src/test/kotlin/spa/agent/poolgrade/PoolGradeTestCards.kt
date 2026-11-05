package spa.agent.poolgrade

import spa.agent.themes.Theme
import spa.agent.themes.ThemeCard
import spa.agent.themes.ThemeKind
import spa.core.baseline.Baseline
import spa.core.baseline.Stat
import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Printing
import spa.core.cards.Rarity
import spa.core.metrics.ColorBucket
import spa.core.metrics.ColorPair
import spa.core.text.Color

/**
 * Small hand-built cards, a set, pools and baselines for the pool adjustment's tests.
 *
 * The set `xyz` has two themes, Spacecraft (a type) and Station (a keyword). Its sealed-pool cards:
 * - Docking Bay (W): refers to Spacecraft.
 * - Shuttle One, Shuttle Two, Shuttle Three (W, U, colourless): have Spacecraft.
 * - Beacon (U): refers to Spacecraft.
 * - Filler One, Filler Two, Filler Three (W, U, colourless): no theme.
 * - Admiral (W and U): no theme.
 * - Red Ship (R): has Spacecraft, but does not fit WU.
 * - Spaceport (a land): has Spacecraft, but is a land.
 * - Outpost (U): has Station.
 *
 * In WU, nine nonland cards fit besides Docking Bay; three of them (the shuttles) are its partners,
 * so its q is 3/9. Shuttle One's partners among the other nine are Docking Bay, Shuttle Two,
 * Shuttle Three and Beacon: q = 4/9.
 */
internal object PoolGradeTestCards {
    val SPACECRAFT = Theme("Spacecraft", ThemeKind.TYPE)
    val STATION = Theme("Station", ThemeKind.KEYWORD)
    val THEMES = listOf(SPACECRAFT, STATION)

    /** A card; [mv] also sets the cost `{mv}`, [creature] the type line's card type. */
    fun card(
        name: String,
        colors: Set<Color> = emptySet(),
        mv: Int = 2,
        subtype: String? = null,
        text: String = "",
        creature: Boolean = false,
        land: Boolean = false,
    ): Card {
        val base =
            when {
                land -> "Land"
                creature -> "Creature"
                else -> "Artifact"
            }
        val type = if (subtype == null) base else "$base — $subtype"
        val cost = if (land) "" else "{$mv}"
        val face = Face(name, cost, mv.toDouble(), colors, type, text, null, null)
        return Card(
            oracleId = "oracle-$name",
            name = name,
            layout = "normal",
            faces = listOf(face),
            colors = colors,
            colorIdentity = colors,
            manaValue = if (land) 0.0 else mv.toDouble(),
            typeLine = type,
            isCreature = creature,
            isLand = land,
            isBasicLand = false,
            rarity = Rarity.COMMON,
        )
    }

    private val W = setOf(Color.W)
    private val U = setOf(Color.U)

    val dockingBay =
        card("Docking Bay", W, text = "Whenever a Spacecraft you control attacks, scry 1.")
    val shuttleOne = card("Shuttle One", W, subtype = "Spacecraft")
    val shuttleTwo = card("Shuttle Two", U, subtype = "Spacecraft")
    val shuttleThree = card("Shuttle Three", subtype = "Spacecraft")
    val beacon = card("Beacon", U, text = "Spacecraft you control get +1/+1.")
    val fillerOne = card("Filler One", W)
    val fillerTwo = card("Filler Two", U)
    val fillerThree = card("Filler Three")
    val admiral = card("Admiral", setOf(Color.W, Color.U))
    val redShip = card("Red Ship", setOf(Color.R), subtype = "Spacecraft")
    val spaceport = card("Spaceport", subtype = "Spacecraft", land = true)
    val outpost = card("Outpost", U)

    /** Keywords by oracle id: only Outpost has one, Station. */
    val KEYWORDS: Map<String, List<String>> = mapOf(outpost.oracleId to listOf("Station"))

    /** The set's sealed-pool cards. */
    val SET: List<ThemeCard> =
        listOf(
                dockingBay,
                shuttleOne,
                shuttleTwo,
                shuttleThree,
                beacon,
                fillerOne,
                fillerTwo,
                fillerThree,
                admiral,
                redShip,
                spaceport,
                outpost,
            )
            .map { ThemeCard(it, Rarity.COMMON, KEYWORDS[it.oracleId].orEmpty()) }

    fun printing(card: Card): Printing =
        Printing("id-${card.name}", "xyz", card.name, Rarity.COMMON, emptyList(), card)

    /** A pool of [counts]: each card with its number of copies. */
    fun pool(vararg counts: Pair<Card, Int>): Pool =
        Pool(counts.map { (card, count) -> PoolEntry(printing(card), count) })

    /**
     * A baseline of set `xyz` whose every stat's mean is 0, except the [means] given by pointer.
     */
    fun baseline(means: Map<String, Double> = emptyMap()): Baseline {
        val pointers =
            ColorPair.entries.flatMap { pair ->
                (0..7).flatMap { b ->
                    listOf(
                        "/pairs/${pair.name}/curve/buckets/$b/creatures",
                        "/pairs/${pair.name}/curve/buckets/$b/noncreatures",
                    )
                }
            } + ColorBucket.entries.map { "/byColor/${it.name}/playables/total" }
        val stats = pointers.associateWith { pointer ->
            val mean = means[pointer] ?: 0.0
            Stat(mean, mean, mean, mean)
        }
        return Baseline("xyz", 1, "2026-11-01T00:00:00Z", 1L, 10, stats.toSortedMap())
    }

    fun grader(baseline: Baseline = baseline(), weights: Weights = Weights.DEFAULT) =
        PoolGrader(SET, THEMES, KEYWORDS, baseline, weights)

    /** Every card's set grade is [grade]. */
    fun flat(grade: Double): (Printing) -> Double? = { grade }

    /** The adjustment of [card] in [this]. */
    fun PairGrades.of(card: Card): CardAdjustment = cards.single {
        it.printing.card.oracleId == card.oracleId
    }
}
