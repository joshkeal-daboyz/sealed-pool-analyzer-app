package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-055 criterion 6: `spa fetch-tags` also writes `<data>/otags/<code>.tags.json`, on a small
 * hand-built bulk file for set `syn` (with descriptions), and the four committed files.
 *
 * | Tag            | Type         | Parents                  | Cards         |
 * |----------------|--------------|--------------------------|---------------|
 * | card-advantage | oracle       | none                     | none          |
 * | draw           | oracle       | card-advantage           | Air Elemental |
 * | cantrip        | oracle       | draw                     | Grizzly Bears |
 * | bear-art       | illustration | none                     | Grizzly Bears |
 * | bear-theme     | oracle       | bear-art, card-advantage | Grizzly Bears |
 * | legends-matter | oracle       | none                     | Serra Angel   |
 * | basic          | oracle       | none                     | Plains        |
 */
class FetchTagsTagsFileTest {
    @TempDir lateinit var temp: Path

    private fun line(
        id: String,
        slug: String,
        description: String?,
        type: String = "oracle",
        parents: List<String> = emptyList(),
        cards: List<String> = emptyList(),
    ): String {
        val parentIds = parents.joinToString(",") { "\"$it\"" }
        val taggings = cards.joinToString(",") { "{\"oracle_id\":\"$it\",\"weight\":\"median\"}" }
        val text = description?.let { "\"$it\"" } ?: "null"
        return "{\"object\":\"tag\",\"id\":\"$id\",\"label\":\"$slug\",\"slug\":\"$slug\"," +
            "\"type\":\"$type\",\"description\":$text,\"parent_ids\":[$parentIds]," +
            "\"child_ids\":[],\"aliases\":[],\"taggings\":[$taggings]}"
    }

    private val bulkText =
        listOf(
                line("t1", "card-advantage", "Gains cards."),
                line("t2", "draw", "Draws cards.", parents = listOf("t1"), cards = listOf(AIR)),
                line("t3", "cantrip", null, parents = listOf("t2"), cards = listOf(BEARS)),
                line(
                    "t4",
                    "bear-art",
                    "Art of bears.",
                    type = "illustration",
                    cards = listOf(BEARS),
                ),
                line(
                    "t5",
                    "bear-theme",
                    "Bear things.",
                    parents = listOf("t4", "t1"),
                    cards = listOf(BEARS),
                ),
                line("t6", "legends-matter", "Cares about \\\"legends\\\".", cards = listOf(SERRA)),
                line("t7", "basic", "A basic land.", cards = listOf(PLAINS)),
            )
            .joinToString("\n", postfix = "\n")

    private fun data(): Path {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        writeFile(
            data,
            "otags/cares.json",
            """{ "cares": { "legends-matter": [{ "fact": "legendary", "strength": 3 }] } }""",
        )
        return data
    }

    private fun bulk(): Path {
        val file = temp.resolve("oracle-tags.jsonl")
        Files.writeString(file, bulkText)
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-11-05T22:00:00.250Z")))
        return file
    }

    private fun run(data: Path) =
        runCli("fetch-tags", "--set", "syn", "--bulk", "${bulk()}", "--data", "$data")

    @Test
    fun `writes the tags file with each present tag, its description or null, and its parents`() {
        val data = data()
        val run = run(data)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val path = data.resolve("otags/syn.tags.json")
        assertEquals(
            listOf(
                "{",
                "  \"set\": \"syn\",",
                "  \"source\": \"Scryfall bulk data oracle_tags, oracle-tags.jsonl\",",
                "  \"updatedAt\": \"2026-11-05T22:00:00Z\",",
                "  \"attribution\": \"Card tags from Scryfall Tagger (tagger.scryfall.com)\",",
                "  \"tags\": [",
                "    { \"slug\": \"bear-theme\", \"description\": \"Bear things.\", " +
                    "\"parents\": [\"card-advantage\"] },",
                "    { \"slug\": \"cantrip\", \"description\": null, \"parents\": [\"draw\"] },",
                "    { \"slug\": \"card-advantage\", \"description\": \"Gains cards.\", " +
                    "\"parents\": [] },",
                "    { \"slug\": \"draw\", \"description\": \"Draws cards.\", " +
                    "\"parents\": [\"card-advantage\"] },",
                "    { \"slug\": \"legends-matter\", " +
                    "\"description\": \"Cares about \\\"legends\\\".\", \"parents\": [] }",
                "  ]",
                "}",
                "",
            ),
            Files.readString(path).lines(),
        )
        assertTrue(run.stdoutLines().any { "5 tags" in it && "$path" in it }, run.stdout)
    }

    @Test
    fun `a rerun gives the same bytes in both files`() {
        val data = data()
        assertEquals(ExitCode.OK, run(data).exit)
        val tags = Files.readAllBytes(data.resolve("otags/syn.tags.json"))
        val otags = Files.readAllBytes(data.resolve("otags/syn.json"))
        assertEquals(ExitCode.OK, run(data).exit)

        assertContentEquals(tags, Files.readAllBytes(data.resolve("otags/syn.tags.json")))
        assertContentEquals(otags, Files.readAllBytes(data.resolve("otags/syn.json")))
        assertTrue(
            "\"syn/1\", \"otags\": [\"bear-theme\", \"cantrip\", \"card-advantage\", \"draw\"]" in
                String(otags, Charsets.UTF_8)
        )
    }

    @Test
    fun `an unknown fact in cares json writes neither file`() {
        val data = data()
        writeFile(
            data,
            "otags/cares.json",
            """{ "cares": { "x-matters": [{ "fact": "nonsense", "strength": 1 }] } }""",
        )
        val run = run(data)

        assertEquals(ExitCode.ERROR, run.exit)
        assertFalse(Files.exists(data.resolve("otags/syn.json")))
        assertFalse(Files.exists(data.resolve("otags/syn.tags.json")))
    }

    private fun committed(set: String) =
        Json.parseToJsonElement(Files.readString(Fixtures.root.resolve("otags/$set.tags.json")))
            .jsonObject

    @Test
    fun `the committed files hold the script's counts`() {
        val counts =
            mapOf(
                "trk" to (686 to 421),
                "msh" to (636 to 381),
                "hob" to (521 to 333),
                "fra" to (618 to 386),
            )
        for ((set, expected) in counts) {
            val tags = committed(set).getValue("tags").jsonArray.map { it.jsonObject }
            val described = tags.count { it.getValue("description") !is JsonNull }

            assertEquals(expected, tags.size to described, set)
        }
    }

    @Test
    fun `each committed file names its set's tags, sorted, and every parent is in the file`() {
        for (set in listOf("trk", "msh", "hob", "fra")) {
            val file = committed(set)
            val otags =
                Json.parseToJsonElement(Files.readString(Fixtures.root.resolve("otags/$set.json")))
                    .jsonObject
            val tags = file.getValue("tags").jsonArray.map { it.jsonObject }
            val slugs = tags.map { it.getValue("slug").jsonPrimitive.content }

            assertEquals(set, file.getValue("set").jsonPrimitive.content)
            for (key in listOf("source", "updatedAt", "attribution")) {
                assertEquals(otags.getValue(key), file.getValue(key), "$set $key")
            }
            assertEquals(slugs.sorted(), slugs, set)
            val present =
                otags
                    .getValue("cards")
                    .jsonArray
                    .flatMap { c ->
                        c.jsonObject.getValue("otags").jsonArray.map { it.jsonPrimitive.content }
                    }
                    .toSet()
            assertEquals(present.sorted(), slugs, set)
            for (tag in tags) {
                val parents = tag.getValue("parents").jsonArray.map { it.jsonPrimitive.content }
                val name = "$set ${tag.getValue("slug")}"
                assertEquals(parents.sorted(), parents, name)
                assertEquals(emptyList(), parents.filterNot { it in slugs }, name)
            }
        }
    }

    @Test
    fun `help names the tags file`() {
        assertTrue("<code>.tags.json" in Main.USAGE)
    }

    companion object {
        const val BEARS = FetchTagsCommandTest.BEARS
        const val SERRA = FetchTagsCommandTest.SERRA_ANGEL
        const val AIR = FetchTagsCommandTest.AIR_ELEMENTAL
        const val PLAINS = FetchTagsCommandTest.PLAINS
    }
}
