package spa.agent.model

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One recorded exchange: the request the code sent and the response the API gave. */
@Serializable data class Recording(val request: ModelRequest, val response: ModelResponse)

/**
 * Raised when code sends a request that has no recording. It is an [AssertionError], so it fails
 * the test even through code that catches exceptions.
 */
class UnrecordedRequestException(message: String) : AssertionError(message)

/**
 * A [ModelClient] that answers only from recordings, so tests never reach the network. A request
 * matches a recording only when every field is equal.
 */
class ReplayModelClient(recordings: List<Recording>) : ModelClient {
    private val responses: Map<ModelRequest, ModelResponse> = recordings.associate {
        it.request to it.response
    }

    private val sent = mutableListOf<ModelRequest>()

    init {
        require(responses.size == recordings.size) { "Two recordings share one request." }
    }

    /** Every request sent so far, in order. */
    val requests: List<ModelRequest>
        get() = sent.toList()

    override fun send(request: ModelRequest): ModelResponse {
        sent += request
        return responses[request]
            ?: throw UnrecordedRequestException(
                "No recording for request: ${json.encodeToString(ModelRequest.serializer(), request)}"
            )
    }

    companion object {
        private val json = Json

        /** A client answering from every `*.json` recording in [directory]. */
        fun fromDirectory(directory: Path): ReplayModelClient {
            val files =
                Files.list(directory).use { paths ->
                    paths.filter { it.extension == "json" }.sorted().toList()
                }
            val byRequest = mutableMapOf<ModelRequest, Path>()
            val recordings = files.map { file ->
                val recording =
                    json.decodeFromString(Recording.serializer(), Files.readString(file))
                val earlier = byRequest.putIfAbsent(recording.request, file)
                require(earlier == null) {
                    "${earlier?.name} and ${file.name} record the same request."
                }
                recording
            }
            return ReplayModelClient(recordings)
        }
    }
}
