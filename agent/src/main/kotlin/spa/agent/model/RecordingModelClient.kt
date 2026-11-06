package spa.agent.model

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * A [ModelClient] that sends through [inner] and writes each successful exchange to [directory] as
 * a [Recording] JSON file, so a live run becomes a test fixture that [ReplayModelClient] can read.
 * The file is `<label>.json`, or `<NN>.json` (two digits, counting recorded calls from 01) when the
 * request has no label. The file holds the request and the response and nothing else. An existing
 * file is never overwritten: a repeated label fails with [IllegalStateException] before sending.
 */
class RecordingModelClient(private val inner: ModelClient, private val directory: Path) :
    ModelClient {
    private val claimed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val lock = Any()
    private var recorded = 0

    override fun send(request: ModelRequest): ModelResponse {
        val label = request.label
        require(label.isEmpty() || LABEL.matches(label)) {
            "label is not a plain file name: $label"
        }
        val claimedName = if (label.isEmpty()) null else "$label.json"
        if (claimedName != null) {
            check(Files.notExists(directory.resolve(claimedName)) && claimed.add(claimedName)) {
                "recording $claimedName already exists in $directory"
            }
        }
        try {
            val response = inner.send(request)
            write(claimedName, Recording(request, response))
            return response
        } catch (e: Throwable) {
            if (claimedName != null) claimed.remove(claimedName)
            throw e
        }
    }

    private fun write(labelledName: String?, recording: Recording) =
        synchronized(lock) {
            val name = labelledName ?: "%02d.json".format(recorded + 1)
            Files.createDirectories(directory)
            try {
                Files.writeString(
                    directory.resolve(name),
                    json.encodeToString(Recording.serializer(), recording) + "\n",
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                )
            } catch (e: FileAlreadyExistsException) {
                throw IllegalStateException("recording $name already exists in $directory", e)
            }
            if (labelledName == null) recorded += 1
        }

    private companion object {
        val LABEL = Regex("[A-Za-z0-9._-]+")

        @OptIn(ExperimentalSerializationApi::class)
        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }
}
