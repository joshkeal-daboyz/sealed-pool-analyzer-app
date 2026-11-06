package spa.cli

import java.util.Locale
import spa.agent.deckbacktest.tune.TuneResult

/** The lines `spa deck-tune` prints after the search (SPA-060). */
internal object DeckTuneReport {
    /** The final block: both objectives, each set builder lines for the start and the result. */
    fun block(result: TuneResult, evaluator: DeckTuneEvaluator, seconds: Double): List<String> {
        val start = evaluator.run(result.startWeights)
        val final = evaluator.run(result.weights)
        val lines = mutableListOf(objective("start", result.startObjective))
        lines += objective("final", result.finalObjective)
        for ((label, evaluation) in listOf("start" to start, "final" to final)) {
            evaluation.report.forEach { (set, report) ->
                lines += "$set $label"
                lines += report
            }
        }
        lines +=
            String.format(Locale.ROOT, "evaluations %d, time %.1f s", result.evaluations, seconds)
        return lines
    }

    private fun objective(label: String, value: Double): String =
        String.format(Locale.ROOT, "%s objective %.6f", label, value)
}

/** How `spa deck-tune` turns a failure in the search or on a worker thread into one line. */
internal object DeckTuneFailures {
    private const val MAX_CAUSES = 20

    /** [block], with a failure on a worker thread, or in the search, turned into a [CliFailure]. */
    @Suppress("TooGenericExceptionCaught")
    fun <T> guarded(block: () -> T): T =
        try {
            block()
        } catch (e: CliFailure) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw CliFailure("deck-tune: ${firstLine(e.message)}", ExitCode.ERROR, e)
        } catch (e: Exception) {
            throw oneLine(e)
        }

    /**
     * The failure [e] as one line: the first [CliFailure] among its causes as it is, else the root
     * cause, named, as `deck-tune failed: <class>: <message>`.
     */
    fun oneLine(e: Throwable): CliFailure {
        val causes = generateSequence(e) { it.cause }.take(MAX_CAUSES).toList()
        causes.filterIsInstance<CliFailure>().firstOrNull()?.let {
            return it
        }
        val root = causes.last()
        return CliFailure(
            "deck-tune failed: ${root.javaClass.simpleName}: ${firstLine(root.message)}",
            ExitCode.ERROR,
            e,
        )
    }

    private fun firstLine(text: String?): String = text.orEmpty().lineSequence().first()
}
