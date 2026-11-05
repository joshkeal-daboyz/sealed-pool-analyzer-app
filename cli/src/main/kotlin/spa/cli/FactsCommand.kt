package spa.cli

import java.io.PrintStream
import java.util.Locale
import spa.agent.facts.CardFacts
import spa.agent.grader.GradingPool
import spa.core.cards.CardIndex

/**
 * `spa facts --set <code> [--card <name or id>] [--data <dir>]`: the card facts of the set's sealed
 * pool ([CardFacts]). Each fact with the number of cards that have it, `<fact> <count>`, most
 * common first, then by name, and a last line giving the pool size and the number of distinct
 * facts. With `--card`, one line: the card's name, its id and its facts, sorted. A card is found by
 * its id (`trk/207`, any case) or by its name; one the pool does not hold is one line and exit 1.
 * No network or model call.
 */
object FactsCommand {
    fun run(args: List<String>, stdout: PrintStream): Int {
        val parsed = Args.parse(args, valueOptions = setOf("set", "data", "card"))
        if (parsed.positionals.isNotEmpty()) {
            throw CliFailure("unexpected argument '${parsed.positionals.first()}'", ExitCode.USAGE)
        }
        val set = Commands.requiredSet(parsed)
        val data = Commands.dataDir(parsed)
        val index = Commands.loadIndex(data, set)
        val facts = CardFacts.load(data.scryfall, index)
        val card = parsed.value("card")
        lines(index, facts, set, card).forEach(stdout::println)
        stdout.flush()
        return ExitCode.OK
    }

    private fun lines(
        index: CardIndex,
        facts: Map<String, Set<String>>,
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
        return listOf(
            "${printing.card.name} ($id): ${facts.getValue(id).sorted().joinToString(" ")}"
        )
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
