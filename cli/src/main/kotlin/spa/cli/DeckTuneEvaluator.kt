package spa.cli

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.atomic.AtomicInteger
import spa.agent.build.BuildConfig
import spa.agent.build.Candidate
import spa.agent.build.Deck
import spa.agent.build.DeckRanking
import spa.agent.build.DeckWeights
import spa.agent.deckbacktest.Backtest
import spa.agent.deckbacktest.BacktestBuild
import spa.agent.deckbacktest.BacktestReport
import spa.agent.deckbacktest.BuildOutcome
import spa.agent.deckbacktest.BuiltDeck
import spa.agent.deckbacktest.RankedColours
import spa.agent.deckbacktest.tune.TuneEvaluator
import spa.agent.deckbacktest.tune.TuneObjective
import spa.agent.deckbacktest.tune.TuneObjectives
import spa.agent.deckbacktest.tune.WeightsEdit

/** One set of a tuning run: its [code], its [session] (index, grades, tags) and its [builds]. */
internal class TuneSet(val code: String, val session: BuildSession, val builds: List<BacktestBuild>)

/**
 * The objective of a deck weights file on the builds of [sets] (SPA-060): the deck builder
 * ([BuildSession]) run on every build's pool on [pool], then [objective] over the builder outcomes.
 * No baseline is run, which is half of `deck-backtest`'s work.
 *
 * With [cache], what a weights change cannot affect is not built again:
 * - each build's candidates (the grades and fixing of its cards) do not depend on the weights;
 * - the deck for a build and a configuration depends on every weight but the `configs` section
 *   (which only says which configurations to try), so a change to `configs` builds only the decks
 *   of the new configurations;
 * - weights already evaluated are answered from memory.
 *
 * The decks are kept as the ranking and the measures read them (total score, card list, copies and
 * land count), for the most recent [MAX_TABLES] weights, not as whole decks. The cached numbers are
 * the uncached ones, which a test pins.
 */
internal class DeckTuneEvaluator(
    private val sets: List<TuneSet>,
    private val pool: ForkJoinPool,
    private val objective: TuneObjective = TuneObjectives.DEFAULT,
    private val cache: Boolean = true,
) : TuneEvaluator {
    /** An evaluation: its [objective] and, by set code, the builder report lines. */
    class Evaluation(val objective: Double, val report: Map<String, List<String>>)

    private class DeckResult(val total: Double, val cards: String, val built: BuiltDeck)

    // A ConcurrentHashMap holds no null, and a configuration with too few cards has no deck.
    private class Slot(val result: DeckResult?)

    private data class DeckKey(val set: Int, val build: Int, val config: BuildConfig)

    private val memo = HashMap<String, Evaluation>()
    private val tables =
        object : LinkedHashMap<String, ConcurrentHashMap<DeckKey, Slot>>(ACCESS_ORDER, LOAD, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ConcurrentHashMap<DeckKey, Slot>>
            ): Boolean = size > MAX_TABLES
        }
    private val candidates = ConcurrentHashMap<Pair<Int, Int>, List<Candidate>>()
    private val counter = AtomicInteger()

    /** How many deck searches ran so far. */
    val searches: Int
        get() = counter.get()

    override fun evaluate(weights: String): Double = run(weights).objective

    /** The builder report lines of [weights], by set code. */
    fun report(weights: String): Map<String, List<String>> = run(weights).report

    /** [weights] evaluated on every build of every set. */
    fun run(weights: String): Evaluation {
        if (cache)
            memo[weights]?.let {
                return it
            }
        val parsed = DeckWeights.parse(weights)
        val sessions = sets.map { it.session.withWeights(parsed) }
        val table =
            if (cache) tables.getOrPut(WeightsEdit.withoutConfigs(weights)) { ConcurrentHashMap() }
            else null
        val units = sets.indices.flatMap { s -> sets[s].builds.indices.map { s to it } }
        val work = Callable {
            units.parallelStream().map { (s, b) -> outcome(s, b, sessions[s], table) }.toList()
        }
        val outcomes = pool.invoke(ForkJoinTask.adapt(work))
        val bySet =
            sets.indices.associate { s ->
                sets[s].code to units.indices.filter { units[it].first == s }.map { outcomes[it] }
            }
        val evaluation =
            Evaluation(
                objective.value(bySet),
                bySet.mapValues { (_, list) -> BacktestReport.lines("builder", list) },
            )
        if (cache) memo[weights] = evaluation
        return evaluation
    }

    private fun outcome(
        s: Int,
        b: Int,
        session: BuildSession,
        table: ConcurrentHashMap<DeckKey, Slot>?,
    ): BuildOutcome {
        val build = sets[s].builds[b]
        return if (table == null) plain(build, session) else cached(s, b, build, session, table)
    }

    /** The outcome as `deck-backtest` makes the builder's: the reference the cached one equals. */
    private fun plain(build: BacktestBuild, session: BuildSession): BuildOutcome {
        val pooled = session.candidates(build.pool())
        val ranked =
            DeckRanking(session.builder).rank(session.configs(pooled)) {
                counter.incrementAndGet()
                session.setup(pooled, it)
            }
        val deck =
            build.config?.let {
                counter.incrementAndGet()
                session.builder.build(session.setup(pooled, it))
            }
        return Backtest.outcome(build.player, ranked, deck)
    }

    /** The same outcome, from decks found once per weights without the `configs` section. */
    private fun cached(
        s: Int,
        b: Int,
        build: BacktestBuild,
        session: BuildSession,
        table: ConcurrentHashMap<DeckKey, Slot>,
    ): BuildOutcome {
        val pooled = candidates.getOrPut(s to b) { sets[s].session.candidates(build.pool()) }

        fun result(config: BuildConfig): DeckResult? =
            table.getOrPut(DeckKey(s, b, config)) { Slot(search(session, pooled, config)) }.result

        // DeckRanking.rank: by total, highest first, ties by the configuration's text; decks with
        // the same cards are one, the first.
        val ranked =
            session
                .configs(pooled)
                .distinct()
                .mapNotNull { config -> result(config)?.let { config to it } }
                .sortedWith(
                    compareByDescending<Pair<BuildConfig, DeckResult>> { it.second.total }
                        .thenBy { it.first.toString() }
                )
                .distinctBy { it.second.cards }
                .map { RankedColours(it.first.main, it.first.splash) }
        return BuildOutcome(build.player, ranked, build.config?.let { result(it)?.built })
    }

    private fun search(
        session: BuildSession,
        pooled: List<Candidate>,
        config: BuildConfig,
    ): DeckResult? {
        counter.incrementAndGet()
        val scored = session.builder.build(session.setup(pooled, config)) ?: return null
        val copies = scored.deck.spells.groupingBy { it.card.oracleId }.eachCount()
        return DeckResult(
            scored.total,
            cards(scored.deck),
            BuiltDeck(copies, scored.deck.landCount),
        )
    }

    /** The deck's cards as text, as `DeckRanking` compares decks: same copies, same text. */
    private fun cards(deck: Deck): String =
        listOf(
                deck.spells.map { it.id }.sorted(),
                deck.nonbasics.map { it.id }.sorted(),
                deck.basics.filterValues { it > 0 }.map { "${it.key}${it.value}" }.sorted(),
            )
            .joinToString("|")

    private companion object {
        /** The weights whose decks are kept; the search revisits only the latest few. */
        const val MAX_TABLES = 6
        const val ACCESS_ORDER = 16
        const val LOAD = 0.75f
    }
}
