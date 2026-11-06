package spa.agent.model

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.io.TempDir

class RecordingModelClientTest {
    private val request =
        ModelRequest(
            "claude-opus-5-5",
            512,
            "system",
            "user",
            cachedContext = "pool",
            label = "curve",
        )
    private val response =
        ModelResponse("claude-opus-5-5", "answer", "end_turn", ModelUsage(10, 5, 3, 2))

    private fun names(dir: Path): List<String> =
        Files.list(dir).use { paths -> paths.map { it.fileName.toString() }.sorted().toList() }

    @Test
    fun `a send is recorded as label dot json and decodes to the same exchange`(
        @TempDir tmp: Path
    ) {
        val dir = tmp.resolve("made/for/the/test")
        val client = RecordingModelClient({ response }, dir)
        assertEquals(response, client.send(request))
        assertEquals(listOf("curve.json"), names(dir))
        val recording =
            Json.decodeFromString(
                Recording.serializer(),
                Files.readString(dir.resolve("curve.json")),
            )
        assertEquals(Recording(request, response), recording)
    }

    @Test
    fun `what it writes replays through ReplayModelClient`(@TempDir dir: Path) {
        val other = request.copy(label = "synergy", userMessage = "other")
        val client = RecordingModelClient({ response }, dir)
        client.send(request)
        client.send(other)
        val replay = ReplayModelClient.fromDirectory(dir)
        assertEquals(response, replay.send(request))
        assertEquals(response, replay.send(other))
    }

    @Test
    fun `an empty label is numbered 01, 02 in call order`(@TempDir dir: Path) {
        val client = RecordingModelClient({ response }, dir)
        client.send(request.copy(label = ""))
        client.send(request.copy(label = "", userMessage = "second"))
        assertEquals(listOf("01.json", "02.json"), names(dir))
    }

    @Test
    fun `a failed send writes nothing and does not use up a number`(@TempDir dir: Path) {
        var fail = true
        val client =
            RecordingModelClient(
                {
                    if (fail) error("boom")
                    response
                },
                dir,
            )
        assertFailsWith<IllegalStateException> { client.send(request.copy(label = "")) }
        assertFailsWith<IllegalStateException> { client.send(request) }
        assertEquals(emptyList(), if (Files.exists(dir)) names(dir) else emptyList())
        fail = false
        client.send(request.copy(label = ""))
        client.send(request)
        assertEquals(listOf("01.json", "curve.json"), names(dir))
    }

    @Test
    fun `a repeated label fails naming the file and overwrites nothing`(@TempDir dir: Path) {
        val client = RecordingModelClient({ response }, dir)
        client.send(request)
        val before = Files.readString(dir.resolve("curve.json"))
        val e =
            assertFailsWith<IllegalStateException> {
                client.send(request.copy(userMessage = "different"))
            }
        assertTrue("curve.json" in e.message.orEmpty(), e.message)
        assertEquals(before, Files.readString(dir.resolve("curve.json")))
    }

    @Test
    fun `a file already on disk is not overwritten by a new client`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("curve.json"), "earlier run")
        val e =
            assertFailsWith<IllegalStateException> {
                RecordingModelClient({ response }, dir).send(request)
            }
        assertTrue("curve.json" in e.message.orEmpty(), e.message)
        assertEquals("earlier run", Files.readString(dir.resolve("curve.json")))
    }

    @Test
    fun `the file holds only the request and the response`(@TempDir dir: Path) {
        RecordingModelClient({ response }, dir).send(request)
        val root = Json.parseToJsonElement(Files.readString(dir.resolve("curve.json"))).jsonObject
        assertEquals(setOf("request", "response"), root.keys)
    }

    @Test
    fun `a label that is not a plain file name is refused before anything is sent`(
        @TempDir dir: Path
    ) {
        var sent = 0
        val client =
            RecordingModelClient(
                {
                    sent++
                    response
                },
                dir,
            )
        assertFailsWith<IllegalArgumentException> { client.send(request.copy(label = "../escape")) }
        assertFailsWith<IllegalArgumentException> { client.send(request.copy(label = "a/b")) }
        assertEquals(0, sent)
    }
}
