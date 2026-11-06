package spa.cli

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.PrintStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.zip.GZIPOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-054 criterion 1: `spa fetch-tags` on a small hand-built bulk file for set `syn` (20 pool
 * cards). The network is never used: a download goes through a fake [OtagSource].
 *
 * | Tag            | Type         | Parents        | Cards            |
 * |----------------|--------------|----------------|------------------|
 * | card-advantage | oracle       | none           | none             |
 * | draw           | oracle       | card-advantage | Air Elemental    |
 * | cantrip        | oracle       | draw           | Grizzly Bears    |
 * | bear-art       | illustration | none           | Grizzly Bears    |
 * | legends-matter | oracle       | none           | Serra Angel      |
 * | basic          | oracle       | none           | Plains (no pool) |
 */
class FetchTagsCommandTest {
    @TempDir lateinit var temp: Path

    private val bulkText =
        listOf(
                tag("t1", "card-advantage"),
                tag("t2", "draw", parents = listOf("t1"), cards = listOf(AIR_ELEMENTAL)),
                tag("t3", "cantrip", parents = listOf("t2"), cards = listOf(BEARS)),
                tag("t4", "bear-art", type = "illustration", cards = listOf(BEARS)),
                tag("t5", "legends-matter", cards = listOf(SERRA_ANGEL)),
                tag("t6", "basic", cards = listOf(PLAINS)),
            )
            .joinToString("\n", postfix = "\n")

    private val cares =
        """
        { "cares": {
          "legends-matter": [{ "fact": "legendary", "strength": 3 }],
          "draw-matters": [{ "fact": "otag:draw", "strength": 2 }]
        } }
        """

    private fun data(caresText: String? = cares): Path {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        if (caresText != null) writeFile(data, "otags/cares.json", caresText)
        return data
    }

    private fun bulk(name: String = "oracle-tags.jsonl", gzip: Boolean = false): Path {
        val file = temp.resolve(name)
        if (gzip) {
            GZIPOutputStream(Files.newOutputStream(file)).use {
                it.write(bulkText.toByteArray(StandardCharsets.UTF_8))
            }
        } else {
            Files.writeString(file, bulkText)
        }
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-11-05T22:00:00.250Z")))
        return file
    }

    private fun run(data: Path, vararg args: String, command: FetchTagsCommand? = null): CliRun {
        val all = listOf("--set", "syn", "--data", data.toString()) + args
        if (command == null) return runCli("fetch-tags", *all.toTypedArray())
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val exit =
            try {
                command.run(
                    all,
                    PrintStream(out, true, StandardCharsets.UTF_8),
                    PrintStream(err, true, StandardCharsets.UTF_8),
                )
            } catch (e: CliFailure) {
                PrintStream(err, true, StandardCharsets.UTF_8).println("spa: ${e.message}")
                e.exitCode
            }
        return CliRun(
            exit,
            out.toString(StandardCharsets.UTF_8),
            err.toString(StandardCharsets.UTF_8),
        )
    }

    private fun written(data: Path): String = Files.readString(data.resolve("otags/syn.json"))

    @Test
    fun `writes the set's otags file from the bulk file, with parents and oracle tags only`() {
        val data = data()
        val run = run(data, "--bulk", bulk().toString())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val path = data.resolve("otags").resolve("syn.json")
        assertEquals(
            listOf("syn: 20 cards, 3 with tags, 6 tags with parents, 4 distinct, written to $path"),
            run.stdoutLines(),
        )
        val lines = written(data).lines()
        assertEquals(
            listOf(
                "{",
                "  \"set\": \"syn\",",
                "  \"source\": \"Scryfall bulk data oracle_tags, oracle-tags.jsonl\",",
                "  \"updatedAt\": \"2026-11-05T22:00:00Z\",",
                "  \"attribution\": \"Card tags from Scryfall Tagger (tagger.scryfall.com)\",",
                "  \"cards\": [",
                "    { \"id\": \"syn/1\", \"otags\": [\"cantrip\", \"card-advantage\", \"draw\"] },",
                "    { \"id\": \"syn/2\", \"otags\": [\"legends-matter\"] },",
                "    { \"id\": \"syn/3\", \"otags\": [\"card-advantage\", \"draw\"] },",
                "    { \"id\": \"syn/4\", \"otags\": [] },",
            ),
            lines.take(10),
        )
        assertEquals(
            listOf("    { \"id\": \"syn/20\", \"otags\": [] }", "  ]", "}", ""),
            lines.takeLast(4),
        )
        assertEquals(6 + 20 + 2, lines.size - 1)
    }

    @Test
    fun `rerunning gives a byte-identical file, and a gzipped bulk file the same cards`() {
        val data = data()
        val file = bulk().toString()
        assertEquals(ExitCode.OK, run(data, "--bulk", file).exit)
        val first = Files.readAllBytes(data.resolve("otags/syn.json"))
        assertEquals(ExitCode.OK, run(data, "--bulk", file).exit)

        assertContentEquals(first, Files.readAllBytes(data.resolve("otags/syn.json")))

        assertEquals(
            ExitCode.OK,
            run(data, "--bulk", bulk("tags.jsonl.gz", gzip = true).toString()).exit,
        )
        assertEquals(
            String(first, StandardCharsets.UTF_8).replace("oracle-tags.jsonl", "tags.jsonl.gz"),
            written(data),
        )
    }

    @Test
    fun `an unknown fact in cares json is named, one line each, exit 1, and nothing is written`() {
        val data =
            data(
                """
                { "cares": {
                  "a-matters": [{ "fact": "legendary", "strength": 1 }, { "fact": "nonsense", "strength": 1 }],
                  "b-matters": [{ "fact": "otag:draw", "strength": 1 }, { "fact": "otag:missing", "strength": 2 }]
                } }
                """
            )
        val run = run(data, "--bulk", bulk().toString())

        assertEquals(ExitCode.ERROR, run.exit)
        val cares = data.resolve("otags").resolve("cares.json")
        assertEquals(
            listOf(
                "spa: $cares: tag 'a-matters': unknown fact 'nonsense'",
                "spa: $cares: tag 'b-matters': unknown fact 'otag:missing'",
            ),
            run.stderrLines(),
        )
        assertFalse(Files.exists(data.resolve("otags/syn.json")))
        assertEquals("", run.stdout)
    }

    @Test
    fun `without a cares table there is nothing to check`() {
        val data = data(caresText = null)

        assertEquals(ExitCode.OK, run(data, "--bulk", bulk().toString()).exit)
        assertTrue(Files.exists(data.resolve("otags/syn.json")))
    }

    @Test
    fun `an invalid cares table, a missing bulk file, a bad bulk file or no set is one line, exit 1`() {
        val bad = data("""{ "cares": [] }""")
        val badRun = run(bad, "--bulk", bulk().toString())
        assertEquals(ExitCode.ERROR, badRun.exit)
        assertEquals(1, badRun.stderrLines().size, badRun.stderr)
        assertTrue(badRun.stderrLines().single().startsWith("spa: invalid cares table "))

        val data = data()
        val missing = run(data, "--bulk", temp.resolve("absent.jsonl").toString())
        assertEquals(ExitCode.ERROR, missing.exit)
        assertEquals(
            listOf("spa: bulk file not found: ${temp.resolve("absent.jsonl")}"),
            missing.stderrLines(),
        )

        val junk = temp.resolve("junk.jsonl")
        Files.writeString(junk, "not json\n")
        val junkRun = run(data, "--bulk", junk.toString())
        assertEquals(ExitCode.ERROR, junkRun.exit)
        assertTrue(
            junkRun.stderrLines().single().startsWith("spa: invalid bulk file $junk: line 1")
        )

        val noSet = runCli("fetch-tags", "--bulk", bulk().toString())
        assertEquals(ExitCode.ERROR, noSet.exit)
        assertEquals(listOf("spa: --set <code> is required"), noSet.stderrLines())

        val extra = runCli("fetch-tags", "extra", "--set", "syn")
        assertEquals(ExitCode.USAGE, extra.exit)
    }

    /** A fake Scryfall: the bulk index and one gzipped bulk file, recording every request. */
    private inner class FakeScryfall(private val index: String) : OtagSource {
        val requests = mutableListOf<String>()

        override fun get(uri: URI): InputStream {
            requests += uri.toString()
            val body =
                when (uri.toString()) {
                    FetchTagsCommand.BULK_INDEX -> index.toByteArray(StandardCharsets.UTF_8)
                    DOWNLOAD ->
                        ByteArrayOutputStream()
                            .also { bytes ->
                                GZIPOutputStream(bytes).use {
                                    it.write(bulkText.toByteArray(StandardCharsets.UTF_8))
                                }
                            }
                            .toByteArray()
                    else -> throw IOException("no such page: $uri")
                }
            return ByteArrayInputStream(body)
        }
    }

    private val index =
        """
        { "object": "list", "has_more": false, "data": [
          { "object": "bulk_data", "type": "oracle_cards", "updated_at": "2026-11-05T09:00:00.000+00:00",
            "download_uri": "https://data.scryfall.io/oracle-cards/oracle-cards.json" },
          { "object": "bulk_data", "type": "oracle_tags", "updated_at": "2026-11-05T22:00:12.345+00:00",
            "download_uri": "https://data.scryfall.io/oracle-tags/oracle-tags.json",
            "jsonl_download_uri": "$DOWNLOAD" }
        ] }
        """

    @Test
    fun `without --bulk it downloads the current file to the cache, dated by Scryfall`() {
        val data = data()
        val cache = temp.resolve("cache")
        val fake = FakeScryfall(index)
        val run = run(data, command = FetchTagsCommand({ fake }, cache))

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(listOf(FetchTagsCommand.BULK_INDEX, DOWNLOAD), fake.requests)
        val cached = cache.resolve("oracle-tags-20261105220012.jsonl.gz")
        assertTrue(Files.isRegularFile(cached))
        val text = written(data)
        assertTrue(
            "  \"source\": \"Scryfall bulk data oracle_tags, oracle-tags-20261105220012.jsonl.gz\"," in
                text.lines(),
            text,
        )
        assertTrue("  \"updatedAt\": \"2026-11-05T22:00:12Z\"," in text.lines(), text)

        // The cached file, given as --bulk, gives the same bytes.
        val again = run(data, "--bulk", cached.toString())
        assertEquals(ExitCode.OK, again.exit, again.stderr)
        assertEquals(text, written(data))
    }

    @Test
    fun `a network error or an index with no oracle tags entry is one line, exit 1`() {
        val data = data()
        val broken =
            run(
                data,
                command = FetchTagsCommand({ OtagSource { throw IOException("down") } }, temp),
            )
        assertEquals(ExitCode.ERROR, broken.exit)
        assertEquals(
            listOf("spa: network error fetching Scryfall's oracle tags: down"),
            broken.stderrLines(),
        )

        val noTags = FakeScryfall("""{ "object": "list", "data": [] }""")
        val run = run(data, command = FetchTagsCommand({ noTags }, temp))
        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(
            listOf(
                "spa: Scryfall's bulk data index has no oracle_tags file with a jsonl_download_uri"
            ),
            run.stderrLines(),
        )
        assertFalse(Files.exists(data.resolve("otags/syn.json")))
    }

    @Test
    fun `help names the command`() {
        val help = runCli("--help").stdout

        assertTrue("fetch-tags --set <code> [--bulk <file>] [--data <dir>]" in help, help)
    }

    companion object {
        const val BEARS = "14c8f55d-d177-4c25-a931-ebeb9e6062a0"
        const val SERRA_ANGEL = "4b7ac066-e5c7-43e6-9e7e-2739b24a905d"
        const val AIR_ELEMENTAL = "7744bae4-a8b7-44a5-9b4c-0048ad4cc448"
        const val PLAINS = "bc71ebf6-2056-41f7-be35-b2e5c34afa99"
        const val DOWNLOAD =
            "https://data.scryfall.io/oracle-tags/oracle-tags-20261105220012.jsonl.gz"

        /** One bulk-file line. */
        fun tag(
            id: String,
            slug: String,
            type: String = "oracle",
            parents: List<String> = emptyList(),
            cards: List<String> = emptyList(),
        ): String {
            val parentIds = parents.joinToString(",") { "\"$it\"" }
            val taggings =
                cards.joinToString(",") { "{\"oracle_id\":\"$it\",\"weight\":\"median\"}" }
            return "{\"object\":\"tag\",\"id\":\"$id\",\"label\":\"$slug\",\"slug\":\"$slug\"," +
                "\"type\":\"$type\",\"description\":null,\"parent_ids\":[$parentIds]," +
                "\"child_ids\":[],\"aliases\":[],\"taggings\":[$taggings]}"
        }
    }
}
