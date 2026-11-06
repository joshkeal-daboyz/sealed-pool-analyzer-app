package spa.cli

import java.io.PrintStream
import java.util.Locale
import spa.agent.facts.CardFacts
import spa.agent.grader.GradingPool
import spa.core.cards.CardIndex

/**
 * `spa facts --set <code> [--card <name or id>] [--otags <file>] [--data <dir>]`: the card facts of
 * the set's sealed pool ([CardFacts]), with each card's otag facts when the set has otags
 * (`--otags`, [Otags]; SPA-054). Each fact with the number of cards that have it, `<fact> <count>`,
 * most common first, then by name, and a last line giving the pool size and the number of distinct
 * facts. With `--card`, one line: the card's name, its id and its facts, sorted, then its cares
 * tags, each `cares:<fact>=<strength>`, sorted by fact. A card is found by its id (`trk/207`, any
 * case) or by its name; one the pool does not hold is one line and exit 1. No network or model
 * call.
 */
object FactsCommand {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("set", "data", "card", Otags.OPTION))
        if (parsed.positionals.isNotEmpty()) {
            throw CliFailure("unexpected argument '${parsed.positionals.first()}'", ExitCode.USAGE)
        }
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val index = Commands.loadIndex(data, set)
        val otags = Otags.load(data, set, parsed.value(Otags.OPTION))
        val facts = CardFacts.load(data.scryfall, index, otags = otags?.otags.orEmpty())
        val card = parsed.value("card")
        lines(index, facts, otags?.cares.orEmpty(), set, card).forEach(stdout::println)
        stdout.flush()
        return ExitCode.OK
    }

    private fun lines(
        index: CardIndex,
        facts: Map<String, Set<String>>,
        cares: Map<String, Map<String, Int>>,
        set: String,
        card: String?,
    ): List<String> {
        if (card == null) {
            val counts = CardFacts.counts(facts)
            return counts.map { "${it.fact} ${it.cards}" } +
                "${facts.size} cards, ${counts.size} distinct facts"
        }
        val printing =
            find(index, card)
                ?: throw CliFailure("no card '$card' in the sealed pool of $set", ExitCode.ERROR)
        val id = GradingPool.id(printing)
        val tags =
            facts.getValue(id).sorted() +
                cares[id].orEmpty().toSortedMap().map { (fact, n) -> "cares:$fact=$n" }
        return listOf("${printing.card.name} ($id): ${tags.joinToString(" ")}")
    }

    /** The pool printing whose id is [card] in any case, else the one of the card named [card]. */
    private fun find(index: CardIndex, card: String) =
        GradingPool.printings(index).let { pool ->
            val key = card.lowercase(Locale.ROOT)
            pool.firstOrNull { GradingPool.id(it).lowercase(Locale.ROOT) == key }
                ?: index.byName(card)?.let { named ->
                    pool.firstOrNull { it.card.oracleId == named.oracleId }
                }
        }
}
