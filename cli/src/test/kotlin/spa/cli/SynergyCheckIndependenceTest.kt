package spa.cli

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.toList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.tags.TagFiles

/**
 * SPA-050 criterion 3: the owner's checks stay held out. No file of the `trk` tags packets (both
 * passes) or the `trk` grade packet, and no prompt resource on the classpath (every resource under
 * `spa/agent/`), contains a check id from `fixtures/synergy-checks/` or the word `synergy-checks`.
 */
class SynergyCheckIndependenceTest {
    @TempDir lateinit var temp: Path

    /** Every check id in every committed check file, and the directory's name. */
    private val forbidden: List<String> by lazy {
        val ids =
            Fixtures.root.resolve("synergy-checks").listDirectoryEntries("*.json").flatMap { file ->
                Json.parseToJsonElement(file.readText())
                    .jsonObject
                    .getValue("checks")
                    .jsonArray
                    .map { it.jsonObject.getValue("id").jsonPrimitive.content }
            }
        ids + "synergy-checks"
    }

    /** `<file>: <word>` for each forbidden word in a regular file under [dir], at any depth. */
    private fun hits(dir: Path): List<String> =
        Files.walk(dir).use { paths ->
            paths
                .filter { it.isRegularFile() }
                .sorted()
                .toList()
                .flatMap { file ->
                    val text = file.readText()
                    forbidden.filter { it in text }.map { "${dir.relativize(file)}: $it" }
                }
        }

    private fun packets(): List<Path> {
        val axesPacket = temp.resolve("axes")
        val rolesPacket = temp.resolve("roles")
        val gradePacket = temp.resolve("grades")
        val reviewPacket = temp.resolve("review")
        // The pass-2 packet is v1's, so its axes are the v1 trk axes, frozen since SPA-053.
        val axes = TagFiles.read(Path.of(Fixtures.v1Tags("trk"))).axes
        val axesFile =
            writeFile(
                temp,
                "trk-axes.json",
                axes.joinToString(",\n", "[\n", "\n]") {
                    """{ "id": ${q(it.id)}, "name": ${q(it.name)}, "definition": ${q(it.definition)} }"""
                },
            )
        val runs =
            listOf(
                runCli(
                    "tags-packet",
                    "trk",
                    "--out",
                    axesPacket.toString(),
                    "--data",
                    Fixtures.data,
                ),
                runCli(
                    "tags-packet",
                    "trk",
                    "--axes",
                    axesFile,
                    "--out",
                    rolesPacket.toString(),
                    "--data",
                    Fixtures.data,
                    // SPA-051 made v2 the default; these axes are v1's.
                    "--prompt",
                    "v1",
                ),
                runCli(
                    "grade-packet",
                    "trk",
                    "--out",
                    gradePacket.toString(),
                    "--data",
                    Fixtures.data,
                ),
                // SPA-052: the review packet, built from the committed trk tag file.
                runCli(
                    "tags-review-packet",
                    "trk",
                    "--out",
                    reviewPacket.toString(),
                    "--data",
                    Fixtures.data,
                ),
            )
        runs.forEach { assertEquals(ExitCode.OK, it.exit, it.stderr) }
        return listOf(axesPacket, rolesPacket, gradePacket, reviewPacket)
    }

    private fun q(text: String) = Json.encodeToString(String.serializer(), text)

    @Test
    fun `the check ids are read, so the test is not empty`() {
        assertTrue("automatons-with-itself" in forbidden, "$forbidden")
        assertTrue("enters-with-beam-me-up" in forbidden, "$forbidden")
    }

    @Test
    fun `the search finds a planted check id`() {
        writeFile(temp, "planted/instructions.md", "see enters-with-beam-me-up")

        assertEquals(
            listOf("instructions.md: enters-with-beam-me-up"),
            hits(temp.resolve("planted")),
        )
    }

    @Test
    fun `no file of the trk tags packets or grade packet names a check`() {
        val packets = packets()

        packets.forEach { packet ->
            assertTrue(packet.listDirectoryEntries().size > 1, "$packet holds files")
            assertEquals(emptyList(), hits(packet), "$packet")
        }
    }

    @Test
    fun `no prompt resource names a check`() {
        val uri = requireNotNull(TagFiles::class.java.getResource(TagFiles.SCHEMA_RESOURCE)).toURI()
        val found =
            if (uri.scheme == "jar") {
                FileSystems.newFileSystem(
                        URI.create(uri.toString().substringBefore("!")),
                        mapOf<String, Any>(),
                    )
                    .use { fs -> resourceHits(fs.getPath("/spa/agent")) }
            } else {
                resourceHits(Path.of(uri).parent.parent)
            }

        assertTrue(
            found.first.containsAll(
                listOf(
                    "prompt-v1.md",
                    "prompt-v2.md",
                    "tags-axes-v1.md",
                    "tags-roles-v1.md",
                    "tags-review-v1.md",
                )
            ),
            "${found.first}",
        )
        assertEquals(emptyList(), found.second)
    }

    /** The names of every resource file under [root], and the forbidden words found in them. */
    private fun resourceHits(root: Path): Pair<List<String>, List<String>> {
        val names =
            Files.walk(root).use { s ->
                s.filter { Files.isRegularFile(it) }.map { it.name }.toList()
            }
        return names to hits(root)
    }
}
