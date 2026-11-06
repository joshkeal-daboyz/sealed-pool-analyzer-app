package spa.cli

import java.nio.file.Path
import spa.agent.build.BuildConfig
import spa.agent.build.BuildSetup
import spa.agent.build.Candidate
import spa.agent.build.ConfigRules
import spa.agent.build.DeckBuilder
import spa.agent.build.DeckCard
import spa.agent.build.DeckRanking
import spa.agent.build.DeckWeights
import spa.agent.build.PairScorer
import spa.agent.build.RankedDeck
import spa.agent.build.SynergyDeckPart
import spa.agent.fixing.FixingClassifier
import spa.agent.grade.GradeLookup
import spa.agent.grader.GradingPool
import spa.core.cards.CardIndex
import spa.core.cards.Pool

/**
 * What `spa build` reads once for a set (SPA-044): the card index, the set's grades, the deck
 * [weights] and, when the synergy weight is above 0, the synergy table. [setup] turns a pool and
 * colours into a [BuildSetup], and [builder] builds the deck.
 */
internal class BuildSession
private constructor(
    val set: String,
    val index: CardIndex,
    val weights: DeckWeights,
    private val lookup: GradeLookup,
    private val pairs: Lazy<PairScorer>,
) {
    val builder = DeckBuilder(weights)

    private val classifier = FixingClassifier()

    /** [pool]'s nonbasic cards as candidates, with their grades and fixing. */
    fun candidates(pool: Pool): List<Candidate> {
        val counts = pool.cardCounts()
        return pool.entries
            .filter { !it.printing.card.isBasicLand }
            .distinctBy { it.printing.card.oracleId }
            .map {
                val card = it.printing.card
                val grade = lookup.grade(it.printing, set)?.score ?: 0.0
                val deckCard =
                    DeckCard(GradingPool.id(it.printing), card, grade, classifier.classify(card))
                Candidate(deckCard, counts.getValue(card.oracleId))
            }
    }

    /** The nonbasic cards [candidates] as a [BuildSetup] for [config]. */
    fun setup(candidates: List<Candidate>, config: BuildConfig): BuildSetup {
        val scorer =
            if (weights.weight(SynergyDeckPart.KEY) == 0.0) PairScorer.NONE else pairs.value
        return BuildSetup(config, weights.splash, candidates, scorer)
    }

    /** [pool]'s nonbasic cards as a [BuildSetup] for [config]. */
    fun setup(pool: Pool, config: BuildConfig): BuildSetup = setup(candidates(pool), config)

    /** The configurations the weights' rules give for [candidates]. */
    fun configs(candidates: List<Candidate>): List<BuildConfig> =
        ConfigRules.configs(candidates, weights.configs)

    /** The ranked decks of every configuration [pool] supports. */
    fun rank(pool: Pool): List<RankedDeck> {
        val candidates = candidates(pool)
        return DeckRanking(builder).rank(configs(candidates)) { setup(candidates, it) }
    }

    companion object {
        /** The deck weights used without `--weights`. */
        fun defaultWeights(data: DataDir): Path =
            data.root.resolve("weights").resolve("deck-default.json")

        /**
         * A session for [set] under [data]: [weightsFile] (else the default), the grade file as
         * `pool-grades` finds it, and the tags and otags as `synergy-table` reads them ([tagsFile],
         * [otags]; read only when the synergy weight is above 0).
         */
        fun open(
            data: DataDir,
            set: String,
            weightsFile: Path? = null,
            tagsFile: Path? = null,
            otags: String? = null,
        ): BuildSession {
            val path = weightsFile ?: defaultWeights(data)
            val text = Commands.readFile(path, "weights file")
            val weights = Commands.guard("invalid weights file $path") { DeckWeights.parse(text) }
            val index = Commands.loadIndex(data, set)
            val lookup =
                PoolGradesCommand.lookup(PoolGradesCommand.defaultGradesFile(data, set), set)
            val pairs = lazy { synergy(data, set, index, tagsFile, otags) }
            return BuildSession(set, index, weights, lookup, pairs)
        }

        private fun synergy(
            data: DataDir,
            set: String,
            index: CardIndex,
            tagsFile: Path?,
            otags: String?,
        ): PairScorer {
            val tags = SynergyTableCommand.tagFile(data, set, tagsFile)
            val table = SynergyTableCommand.table(data, set, tags, Otags.load(data, set, otags))
            val ids =
                GradingPool.printings(index).associate { it.card.oracleId to GradingPool.id(it) }
            return PairScorer { a, b ->
                val first = ids[a.card.oracleId]
                val second = ids[b.card.oracleId]
                if (first == null || second == null) 0 else table.score(first, second)
            }
        }
    }
}
