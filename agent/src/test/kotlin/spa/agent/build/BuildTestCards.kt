package spa.agent.build

import spa.agent.fixing.CardFixing
import spa.agent.fixing.FixingKind
import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.cards.Rarity
import spa.core.text.Color

/** Hand-built cards, setups and weights for the deck builder's tests. */
internal object BuildTestCards {
    val W = setOf(Color.W)
    val U = setOf(Color.U)
    val R = setOf(Color.R)

    @Suppress("LongParameterList")
    private fun card(
        name: String,
        colours: Set<Color>,
        cost: String,
        mv: Int,
        type: String,
        text: String,
    ): Card =
        Card(
            oracleId = "oracle-$name",
            name = name,
            layout = "normal",
            faces = listOf(Face(name, cost, mv.toDouble(), colours, type, text, null, null)),
            colors = colours,
            colorIdentity = colours,
            manaValue = mv.toDouble(),
            typeLine = type,
            isCreature = type.startsWith("Creature"),
            isLand = type.startsWith("Land"),
            isBasicLand = false,
            rarity = Rarity.COMMON,
        )

    /** A spell; its id is `t/<name>`, so cards order by name. */
    @Suppress("LongParameterList")
    fun spell(
        name: String,
        colours: Set<Color>,
        cost: String,
        mv: Int,
        grade: Double = 3.0,
        type: String = "Creature — Test",
        text: String = "",
        fixes: Set<Color>? = null,
    ): DeckCard {
        val card = card(name, colours, cost, mv, type, text)
        return DeckCard("t/$name", card, grade, fixes?.let { fixing(card, it) })
    }

    /** A nonbasic land that can supply [supplies]. */
    fun land(name: String, supplies: Set<Color>): DeckCard {
        val card = card(name, emptySet(), "", 0, "Land", "")
        return DeckCard("t/$name", card, 0.0, fixing(card, supplies))
    }

    private fun fixing(card: Card, colours: Set<Color>) =
        CardFixing(card, listOf(FixingKind.MANA), Color.entries.filter { it in colours })

    /** [count] distinct white cards named [prefix] 01, 02, ... */
    fun whites(prefix: String, count: Int, grade: Double = 3.0, mv: Int = 3): List<DeckCard> =
        (1..count).map {
            spell("$prefix ${"%02d".format(it)}", W, "{${mv - 1}}{W}", mv, grade = grade)
        }

    fun candidates(cards: List<DeckCard>, quantity: Int = 1): List<Candidate> = cards.map {
        Candidate(it, quantity)
    }

    /** Weights with the given part weights, the default parameters and rules, and no others. */
    fun weights(
        vararg weights: Pair<String, Double>,
        lands: LandRule = LandRule(2.6, 3.4, 16, 17, 18),
        splash: SplashRule = SplashRule(3, 1.0),
    ): DeckWeights =
        DeckWeights(
            weights.toMap(),
            mapOf(
                CurveDeckPart.KEY to
                    mapOf(
                        "target1" to 1.0,
                        "target2" to 5.0,
                        "target3" to 4.0,
                        "target4" to 3.0,
                        "target5" to 2.0,
                        "target6" to 1.0,
                    ),
                CreaturesDeckPart.KEY to mapOf("minCreatures" to 14.0),
                RemovalDeckPart.KEY to mapOf("cap" to 6.0),
            ),
            lands,
            splash,
        )

    fun setup(
        cards: List<Candidate>,
        config: String = "W",
        splash: SplashRule = SplashRule(3, 1.0),
        pairs: PairScorer = PairScorer.NONE,
    ) = BuildSetup(BuildConfig.parse(config), splash, cards, pairs)

    fun deck(setup: BuildSetup, spells: List<DeckCard>, lands: Int = 17) =
        Deck(setup, spells, lands)
}
