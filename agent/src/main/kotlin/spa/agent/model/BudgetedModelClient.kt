package spa.agent.model

/**
 * Raised, before anything is sent, when a request's reservation would pass a token cap. A plain
 * exception, so grading code that handles failures can handle it.
 */
class BudgetExceededException(message: String) : RuntimeException(message)

/**
 * What a [BudgetedModelClient] has sent so far: [calls] answered, and the API's own token counts.
 * [input] is the API's `inputTokens` only; cache reads and writes are separate numbers.
 */
data class UsageTotals(
    val calls: Long,
    val input: Long,
    val output: Long,
    val cacheRead: Long,
    val cacheWrite: Long,
)

/**
 * A [ModelClient] that enforces per-review token caps around [inner]. Before sending, it reserves
 * [TokenEstimate.of] input tokens and `maxTokens` output tokens; if either would pass its cap it
 * throws [BudgetExceededException] and sends nothing. After the response, the reservation is
 * replaced by the actual usage (input counts cache reads and writes in full). A failed call
 * releases its reservation. Safe to call from several threads: reservation and settlement happen
 * under one lock, and the inner call runs outside it.
 */
class BudgetedModelClient(
    private val inner: ModelClient,
    private val maxInputTokens: Long,
    private val maxOutputTokens: Long,
) : ModelClient {
    private val lock = Any()
    private var committedInput = 0L
    private var committedOutput = 0L
    private var calls = 0L
    private var input = 0L
    private var output = 0L
    private var cacheRead = 0L
    private var cacheWrite = 0L

    /** The usage of every call that was sent and answered. */
    val totals: UsageTotals
        get() = synchronized(lock) { UsageTotals(calls, input, output, cacheRead, cacheWrite) }

    override fun send(request: ModelRequest): ModelResponse {
        val reservedInput = TokenEstimate.of(request)
        val reservedOutput = request.maxTokens
        reserve(reservedInput, reservedOutput)
        val response =
            try {
                inner.send(request)
            } catch (e: Throwable) {
                release(reservedInput, reservedOutput)
                throw e
            }
        settle(reservedInput, reservedOutput, response.usage)
        return response
    }

    private fun reserve(inputTokens: Long, outputTokens: Long) =
        synchronized(lock) {
            val neededInput = committedInput + inputTokens
            if (neededInput > maxInputTokens) {
                throw BudgetExceededException(
                    "budget: input cap would be passed ($neededInput of $maxInputTokens)"
                )
            }
            val neededOutput = committedOutput + outputTokens
            if (neededOutput > maxOutputTokens) {
                throw BudgetExceededException(
                    "budget: output cap would be passed ($neededOutput of $maxOutputTokens)"
                )
            }
            committedInput = neededInput
            committedOutput = neededOutput
        }

    private fun release(inputTokens: Long, outputTokens: Long) =
        synchronized(lock) {
            committedInput -= inputTokens
            committedOutput -= outputTokens
        }

    private fun settle(reservedInput: Long, reservedOutput: Long, usage: ModelUsage) =
        synchronized(lock) {
            val actualInput =
                usage.inputTokens + usage.cacheReadInputTokens + usage.cacheCreationInputTokens
            committedInput += actualInput - reservedInput
            committedOutput += usage.outputTokens - reservedOutput
            calls += 1
            input += usage.inputTokens
            output += usage.outputTokens
            cacheRead += usage.cacheReadInputTokens
            cacheWrite += usage.cacheCreationInputTokens
        }
}
