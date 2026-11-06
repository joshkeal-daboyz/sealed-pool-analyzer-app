package spa.agent.review

import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import spa.agent.model.BudgetExceededException
import spa.agent.model.ModelClient
import spa.agent.model.ModelRequest
import spa.agent.model.ModelResponse

/**
 * The rules every call of the review follows, shared by the analysts' runner and the Explainer: one
 * call at a time per permit, cancelled after the config's timeout, retried with the rejection
 * sentence when the answer fails its schema, and turned into a one-line reason when it cannot be
 * had. Calls run on [pool]; at most the permits of [permits] are in flight.
 */
internal class ModelCalls(
    private val client: ModelClient,
    private val config: ReviewConfig,
    private val pool: ExecutorService,
    private val permits: Semaphore,
) {
    /** One call, cancelled after the config's timeout. */
    fun send(request: ModelRequest): ModelResponse {
        permits.acquire()
        try {
            val future = pool.submit<ModelResponse> { client.send(request) }
            return try {
                future.get(config.callTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            } catch (expected: TimeoutException) {
                future.cancel(true)
                throw expected
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            }
        } finally {
            permits.release()
        }
    }

    /**
     * Sends [base] and hands the reply text to [check]. When [check] throws a [SchemaError] the
     * request is sent again with the rejection added, up to the config's retries; the last
     * [SchemaError] is then thrown. Any other failure is thrown at once.
     */
    fun <T> checked(base: ModelRequest, check: (String) -> T): T = attempt(base, check, 0, null)

    private fun <T> attempt(
        base: ModelRequest,
        check: (String) -> T,
        number: Int,
        previous: SchemaError?,
    ): T {
        val request = if (previous == null) base else retryRequest(base, number, previous)
        val text = send(request).text
        return try {
            check(text)
        } catch (error: SchemaError) {
            if (number >= config.retries) throw error
            attempt(base, check, number + 1, error)
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

    /** Runs [body]; a failure of the call becomes [missing] of its reason. */
    @Suppress("TooGenericExceptionCaught")
    fun <T> guarded(missing: (String) -> T, body: () -> T): T =
        try {
            body()
        } catch (error: BudgetExceededException) {
            missing(error.message.orEmpty())
        } catch (expected: TimeoutException) {
            missing("timed out after ${config.callTimeoutSeconds}s")
        } catch (error: Exception) {
            missing(describe(error))
        }

    companion object {
        private const val MESSAGE_LIMIT = 200

        /** `error: <class>: <first line of the message, at most 200 characters>`. */
        fun describe(error: Exception): String {
            val firstLine = error.message.orEmpty().lines().first().take(MESSAGE_LIMIT)
            return "error: ${error.javaClass.simpleName}: $firstLine"
        }
    }
}
