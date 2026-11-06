package spa.agent.review

import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import spa.agent.model.BudgetExceededException
import spa.agent.model.ModelClient
import spa.agent.model.ModelRequest
import spa.agent.model.ModelResponse

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
    /** What one run shares: the thread pool, the limit on calls in flight, and the pool's names. */
    private class Run(
        val input: ReviewInput,
        val pool: ExecutorService,
        val permits: Semaphore,
        val names: Set<String>,
    )

    /** One agent's work: its first request [base], its number [table], and the shared [run]. */
    private class Job(
        val agent: AnalysisAgent,
        val base: ModelRequest,
        val table: NumberTable,
        val run: Run,
    )

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
            val run =
                Run(
                    input,
                    pool,
                    Semaphore(config.parallel),
                    input.pool.entries.map { it.printing.card.name }.toSet(),
                )
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

    @Suppress("TooGenericExceptionCaught")
    private fun unavailableOutcome(agent: AnalysisAgent, input: ReviewInput): AgentOutcome? =
        try {
            agent.unavailable(input)?.let { AgentOutcome.Missing(agent, it) }
        } catch (error: Exception) {
            AgentOutcome.Missing(agent, describe(error))
        }

    private fun answer(agent: AnalysisAgent, run: Run): AgentOutcome =
        guarded(agent) {
            val table = agent.numbers(run.input)
            val base = AgentRequests.of(agent, run.input, config)
            attempt(Job(agent, base, table, run), 0, null)
        }

    /** Sends the request of attempt [number] (0 is the first) and checks the reply. */
    private fun attempt(job: Job, number: Int, previous: SchemaError?): AgentOutcome {
        val request = if (previous == null) job.base else retryRequest(job.base, number, previous)
        val text = send(request, job.run).text
        return try {
            val checked =
                AnswerChecker.check(
                    text,
                    job.agent,
                    job.run.names,
                    job.table,
                    config.maxFindings,
                )
            AgentOutcome.Answered(job.agent, checked.answer, checked.dropped)
        } catch (error: SchemaError) {
            if (number < config.retries) {
                attempt(job, number + 1, error)
            } else {
                AgentOutcome.Missing(job.agent, "invalid answer: ${error.message}")
            }
        }
    }

    private fun retryRequest(base: ModelRequest, number: Int, error: SchemaError): ModelRequest =
        base.copy(
            label = "${base.label}-retry$number",
            userMessage =
                base.userMessage +
                    "\n\nYour previous answer was rejected: ${error.message}. " +
                    "Answer again with the JSON object only.",
        )

    /** One call, at most `parallel` at once, cancelled after the config's timeout. */
    private fun send(request: ModelRequest, run: Run): ModelResponse {
        run.permits.acquire()
        try {
            val future = run.pool.submit<ModelResponse> { client.send(request) }
            return try {
                future.get(config.callTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            } catch (expected: TimeoutException) {
                future.cancel(true)
                throw expected
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            }
        } finally {
            run.permits.release()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun guarded(agent: AnalysisAgent, body: () -> AgentOutcome): AgentOutcome =
        try {
            body()
        } catch (error: BudgetExceededException) {
            AgentOutcome.Missing(agent, error.message.orEmpty())
        } catch (expected: TimeoutException) {
            AgentOutcome.Missing(agent, "timed out after ${config.callTimeoutSeconds}s")
        } catch (error: Exception) {
            AgentOutcome.Missing(agent, describe(error))
        }

    private fun describe(error: Exception): String {
        val firstLine = error.message.orEmpty().lines().first().take(MESSAGE_LIMIT)
        return "error: ${error.javaClass.simpleName}: $firstLine"
    }

    private companion object {
        const val MESSAGE_LIMIT = 200
    }
}
