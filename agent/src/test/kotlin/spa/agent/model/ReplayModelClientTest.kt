package spa.agent.model

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ReplayModelClientTest {
    private val recordings: Path =
        Path.of(requireNotNull(javaClass.getResource("/spa/agent/replay")).toURI())

    private val system = "You grade Magic: The Gathering cards for sealed, relative to their set."

    private fun request(userMessage: String) =
        ModelRequest("claude-opus-5-5", 1024, system, userMessage)

    /** A stand-in for grading code: it only knows the seam, not which client it has. */
    private fun gradeText(client: ModelClient, card: String): String =
        client.send(request("Grade $card.")).text

    @Test
    fun `a recorded request gets its recorded response`() {
        val client = ReplayModelClient.fromDirectory(recordings)
        val response = client.send(request("Grade Battle-Scarred Survivalist (trk 95)."))
        assertEquals(
            ModelResponse(
                model = "claude-opus-5-5",
                text = "{\"score\": 2.5, \"confidence\": 0.6}",
                stopReason = "end_turn",
                usage = ModelUsage(120, 40, 0, 0),
            ),
            response,
        )
    }

    @Test
    fun `every recording in the directory is loaded and served through the seam`() {
        val client = ReplayModelClient.fromDirectory(recordings)
        assertEquals(
            "{\"score\": 3.5, \"confidence\": 0.75}",
            gradeText(client, "He's Dead, Jim (trk 114)"),
        )
        assertEquals(
            "{\"score\": 2.5, \"confidence\": 0.6}",
            gradeText(client, "Battle-Scarred Survivalist (trk 95)"),
        )
        assertEquals(
            listOf(
                request("Grade He's Dead, Jim (trk 114)."),
                request("Grade Battle-Scarred Survivalist (trk 95)."),
            ),
            client.requests,
        )
    }

    @Test
    fun `an unrecorded request fails the test and names the request`() {
        val client = ReplayModelClient.fromDirectory(recordings)
        val e =
            assertFailsWith<UnrecordedRequestException> {
                gradeText(client, "Suurok-Class Science Vessel (trk 83)")
            }
        assertTrue("Suurok-Class Science Vessel (trk 83)" in e.message.orEmpty(), e.message)
        assertTrue("claude-opus-5-5" in e.message.orEmpty(), e.message)
        // It is an AssertionError, so a catch of Exception in grading code cannot swallow it.
        assertTrue(e is AssertionError)
    }

    @Test
    fun `a request that differs only in its model is unrecorded`() {
        val client = ReplayModelClient.fromDirectory(recordings)
        assertFailsWith<UnrecordedRequestException> {
            client.send(
                ModelRequest("claude-sonnet-5-5", 1024, system, "Grade He's Dead, Jim (trk 114).")
            )
        }
    }

    @Test
    fun `two recordings of the same request are an error`(@TempDir dir: Path) {
        val text = Files.readString(recordings.resolve("grade-jim.json"))
        Files.writeString(dir.resolve("a.json"), text)
        Files.writeString(dir.resolve("b.json"), text)
        val e = assertFailsWith<IllegalArgumentException> { ReplayModelClient.fromDirectory(dir) }
        assertTrue("a.json" in e.message.orEmpty() && "b.json" in e.message.orEmpty(), e.message)
    }

    private fun recordedResponse() =
        ModelResponse("claude-opus-5-5", "recorded", "end_turn", ModelUsage(1, 1))

    @Test
    fun `requests differing only in cachedContext or label are different keys`() {
        val withContext = request("Grade He's Dead, Jim (trk 114).").copy(cachedContext = "pool")
        val client = ReplayModelClient(listOf(Recording(withContext, recordedResponse())))
        assertEquals("recorded", client.send(withContext).text)
        assertFailsWith<UnrecordedRequestException> {
            client.send(withContext.copy(cachedContext = "other pool"))
        }
        assertFailsWith<UnrecordedRequestException> {
            client.send(withContext.copy(cachedContext = null))
        }
        assertFailsWith<UnrecordedRequestException> {
            client.send(withContext.copy(label = "curve"))
        }
    }

    @Test
    fun `a request with a label and cached context replays from a directory`(@TempDir dir: Path) {
        val labelled = request("Grade X.").copy(cachedContext = "pool", label = "curve")
        Files.writeString(
            dir.resolve("a.json"),
            kotlinx.serialization.json.Json.encodeToString(
                Recording.serializer(),
                Recording(labelled, recordedResponse()),
            ),
        )
        assertEquals("recorded", ReplayModelClient.fromDirectory(dir).send(labelled).text)
    }

    @Test
    fun `files that are not json recordings are ignored`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("README.txt"), "not a recording")
        Files.copy(recordings.resolve("grade-jim.json"), dir.resolve("grade-jim.json"))
        val client = ReplayModelClient.fromDirectory(dir)
        assertEquals(38, client.send(request("Grade He's Dead, Jim (trk 114).")).usage.outputTokens)
    }
}
