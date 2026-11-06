package spa.cli

import spa.agent.model.ModelClient
import spa.agent.model.ModelRequest
import spa.agent.model.TokenEstimate
import spa.agent.review.AgentRunner
import spa.agent.review.AnalysisAgent
import spa.agent.review.PlannedCall
import spa.agent.review.PoolBundle

/**
 * What `spa review --dry-run` and `--show-input` print (SPA-070): the calls a review would make and
 * what the agents are given. Neither sends anything: the runner they plan with has a client that
 * fails when asked to send.
 */
internal object ReviewPreview {
    private const val CHARACTERS_PER_TOKEN = 4L
    private const val TOKENS_PER_FINDING = 60L

    private val noCalls = ModelClient { error("a preview sends no call") }

    private fun plan(setup: ReviewSetup, agents: List<AnalysisAgent>): List<PlannedCall> =
        AgentRunner(noCalls, setup.config, agents).plan(setup.input)

    /** The bundle text, then each enabled agent's heading and the message it is sent. */
    fun showInput(setup: ReviewSetup, agents: List<AnalysisAgent>): String {
        val blocks =
            plan(setup, agents).map { call ->
                val heading = "=== ${call.agent.id} (${call.agent.title}) ==="
                when (call) {
                    is PlannedCall.Send -> "$heading\n${call.request.userMessage}\n"
                    is PlannedCall.Skipped -> "$heading not run: ${call.reason}\n"
                }
            }
        return (listOf(PoolBundle.text(setup.input)) + blocks).joinToString("\n")
    }

    /** The calls a review of [file] would make, estimated, and how they stand against the caps. */
    fun dryRun(file: String, setup: ReviewSetup, agents: List<AnalysisAgent>): String {
        val config = setup.config
        val planned = plan(setup, agents)
        val bundle = TokenEstimate.of(ModelRequest("", 0, "", "", PoolBundle.text(setup.input)))
        val sent = planned.filterIsInstance<PlannedCall.Send>()
        val lines =
            mutableListOf(
                "Dry run: review of $file (${setup.header.set}), no calls made.",
                "Model: ${config.model}",
                "Cached bundle: ~$bundle tokens (estimated)",
            )
        var input = 0L
        var output = 0L
        var number = 0
        for (call in planned) {
            when (call) {
                is PlannedCall.Send -> {
                    val estimate = TokenEstimate.of(call.request)
                    number += 1
                    input += estimate
                    output += call.request.maxTokens
                    lines += callLine(number, call.agent.id, estimate, call.request.maxTokens, "")
                }
                is PlannedCall.Skipped -> lines += "Not run: ${call.agent.id} (${call.reason})"
            }
        }
        val decksText = setup.decks.joinToString("\n") { it.text() }
        val skipped =
            when {
                setup.decks.isEmpty() -> "no deck could be built"
                sent.isEmpty() -> "no findings to explain"
                !config.explainer.enabled -> "switched off in the config"
                else -> null
            }
        if (skipped != null) {
            lines += "Not run: explainer ($skipped)"
        } else {
            val estimate =
                bundle +
                    decksText.length / CHARACTERS_PER_TOKEN +
                    TOKENS_PER_FINDING * config.maxFindings * sent.size
            val max = config.explainer.maxTokens
            input += estimate
            output += max
            lines +=
                callLine(number + 1, "explainer", estimate, max, " (after the analysts; estimated)")
        }
        lines += total(input, output, config.maxInputTokens, config.maxOutputTokens)
        return lines.joinToString("\n") + "\n"
    }

    private fun callLine(number: Int, id: String, input: Long, output: Long, note: String) =
        "Call $number: $id, ~$input input tokens, up to $output output tokens$note"

    private fun total(input: Long, output: Long, inputCap: Long, outputCap: Long): List<String> {
        val over =
            listOfNotNull(
                if (input > inputCap) "Over the input cap by ~${input - inputCap} tokens."
                else null,
                if (output > outputCap) "Over the output cap by ${output - outputCap} tokens."
                else null,
            )
        return listOf(
            "Total: ~$input input tokens of $inputCap allowed, " +
                "up to $output output tokens of $outputCap allowed"
        ) + over.ifEmpty { listOf("Within the caps.") }
    }
}
