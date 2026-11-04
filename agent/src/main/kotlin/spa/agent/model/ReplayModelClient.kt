package spa.agent.model

import java.nio.file.Path
import kotlinx.serialization.Serializable

/** One recorded exchange: the request the code sent and the response the API gave. */
@Serializable data class Recording(val request: ModelRequest, val response: ModelResponse)

/**
 * Raised when code sends a request that has no recording. It is an [AssertionError], so it fails
 * the test even through code that catches exceptions.
 */
class UnrecordedRequestException(message: String) : AssertionError(message)

/** A [ModelClient] that answers only from recordings, so tests never reach the network. */
class ReplayModelClient(recordings: List<Recording>) : ModelClient {
    /** Every request sent so far, in order. */
    val requests: List<ModelRequest>
        get() = TODO("SPA-029")

    override fun send(request: ModelRequest): ModelResponse = TODO("SPA-029")

    companion object {
        /** A client answering from every `*.json` recording in [directory]. */
        fun fromDirectory(directory: Path): ReplayModelClient = TODO("SPA-029")
    }
}
