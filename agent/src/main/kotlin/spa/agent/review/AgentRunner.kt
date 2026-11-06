package spa.agent.review

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import spa.agent.model.ModelClient

/**
 * Runs the analysts of a review through [client]. It sees only [AnalysisAgent]: which agents run
 * comes from [config] (an agent the config does not list, or switches off, sends nothing and is not
 * in the result). The first agent that can run is sent alone, so it writes the prompt cache; the
 * rest then run together, at most `parallel` calls at a time. Agents never see each other's output.
 */
class AgentRunner(
    private val client: ModelClient,
    private val config: ReviewConfig,
    private val agents: List<AnalysisAgent>,
) {
    /** What one run shares: the input, the call rules on their thread pool, the pool's names. */
    private class Run(val input: ReviewInput, val calls: ModelCalls, val names: Set<String>)

    private val enabled: List<AnalysisAgent>
        get() = agents.filter { config.setting(it.id)?.enabled == true }

    /** What a run would do for each enabled agent, in order; nothing is sent. */
    fun plan(input: ReviewInput): List<PlannedCall> = enabled.map { agent ->
        agent.unavailable(input)?.let { PlannedCall.Skipped(agent, it) }
            ?: PlannedCall.Send(agent, AgentRequests.of(agent, input, config))
    }

    /** Runs every enabled agent on [input]; the outcomes are in agent order. */
    fun run(input: ReviewInput): List<AgentOutcome> {
        val listed = enabled
        val early = listed.associateWith { unavailableOutcome(it, input) }
        val ready = listed.filter { early.getValue(it) == null }
        val pool = Executors.newCachedThreadPool { task -> Thread(task).apply { isDaemon = true } }
        val results = mutableMapOf<AnalysisAgent, AgentOutcome>()
        try {
            val run = newRun(input, pool)
            ready.firstOrNull()?.let { results[it] = answer(it, run) }
            val rest =
                ready.drop(1).map { agent ->
                    agent to pool.submit<AgentOutcome> { answer(agent, run) }
                }
            rest.forEach { (agent, future) -> results[agent] = future.get() }
        } finally {
            pool.shutdownNow()
        }
        return listed.map { early[it] ?: results.getValue(it) }
    }

    private fun newRun(input: ReviewInput, pool: ExecutorService): Run =
        Run(
            input,
            ModelCalls(client, config, pool, Semaphore(config.parallel)),
            input.pool.entries.map { it.printing.card.name }.toSet(),
        )

    @Suppress("TooGenericExceptionCaught")
    private fun unavailableOutcome(agent: AnalysisAgent, input: ReviewInput): AgentOutcome? =
        try {
            agent.unavailable(input)?.let { AgentOutcome.Missing(agent, it) }
        } catch (error: Exception) {
            AgentOutcome.Missing(agent, ModelCalls.describe(error))
        }

    private fun answer(agent: AnalysisAgent, run: Run): AgentOutcome =
        run.calls.guarded({ AgentOutcome.Missing(agent, it) }) {
            val table = agent.numbers(run.input)
            val base = AgentRequests.of(agent, run.input, config)
            try {
                val checked =
                    run.calls.checked(base) { text ->
                        AnswerChecker.check(text, agent, run.names, table, config.maxFindings)
                    }
                AgentOutcome.Answered(agent, checked.answer, checked.dropped)
            } catch (error: SchemaError) {
                AgentOutcome.Missing(agent, "invalid answer: ${error.message}")
            }
        }
}
