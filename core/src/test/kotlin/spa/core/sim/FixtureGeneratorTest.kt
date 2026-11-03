package spa.core.sim

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import spa.core.brief.SlotKind
import spa.core.brief.briefFixturesDir
import spa.core.cards.fixturesDir
import spa.core.intake.PoolFormat
import spa.core.intake.syn

class FixtureGeneratorTest {
    @TempDir lateinit var out: Path

    private fun generate(dir: Path = out) =
        FixtureGenerator.generate(fixturesDir, briefFixturesDir, dir)

    private fun snapshot(dir: Path): Map<String, String> =
        dir.listDirectoryEntries().sortedBy { it.name }.associate { it.name to it.readText() }

    @Test
    fun `the plan names every sampled fixture with its own seed`() {
        val byKind = FixtureGenerator.plan.groupingBy { it.set to it.format }.eachCount()
        assertEquals(10, byKind["trk" to PoolFormat.MANABOX])
        assertEquals(5, byKind["trk" to PoolFormat.TEXT])
        assertEquals(5, byKind["trk" to PoolFormat.COLLECTOR])
        assertEquals(2, byKind["syn" to PoolFormat.TEXT])
        assertEquals(1, byKind["syn" to PoolFormat.COLLECTOR])
        val plan = FixtureGenerator.plan
        assertEquals(plan.size, plan.map { it.seed }.toSet().size)
        assertEquals(plan.size, plan.map { it.file }.toSet().size)
        assertEquals(
            (1..10).map { "trk-manabox-%02d.csv".format(it) },
            plan.filter { it.format == PoolFormat.MANABOX }.map { it.file },
        )
    }

    @Test
    fun `generating twice gives identical files`() {
        val first = generate()
        val before = snapshot(out)
        val second = generate()
        assertEquals(first, second)
        assertEquals(before, snapshot(out))
        assertEquals(FixtureGenerator.plan.size + 1, before.size)
        assertTrue(first.fixtures.all { it.seed != null && it.expectedUnresolved.isEmpty() })
    }

    @Test
    fun `the committed fixtures are what the generator writes`() {
        generate()
        val committed = FixtureManifest.load(poolFixturesDir)
        FixtureGenerator.plan.forEach { fixture ->
            assertEquals(
                poolFixturesDir.resolve(fixture.file).readText(),
                out.resolve(fixture.file).readText(),
                fixture.file,
            )
        }
        assertEquals(
            FixtureManifest.load(out).fixtures,
            committed.fixtures.filter { it.seed != null },
        )
        // With the hand-edited entries in place, regenerating writes the committed manifest.
        Files.writeString(out.resolve(FixtureManifest.FILE_NAME), committed.encode())
        generate()
        assertEquals(
            poolFixturesDir.resolve(FixtureManifest.FILE_NAME).readText(),
            out.resolve(FixtureManifest.FILE_NAME).readText(),
        )
    }

    @Test
    fun `hand-edited entries survive regeneration and stale seeded entries do not`() {
        val hand =
            FixtureEntry("zz-hand.txt", "syn", "text", null, 3, listOf(ExpectedUnresolved(2, "x")))
        val stale = FixtureEntry("old.txt", "syn", "text", 99, 1, emptyList())
        val replaced = FixtureEntry("syn-text-01.txt", "syn", "text", null, 0, emptyList())
        Files.createDirectories(out)
        Files.writeString(
            out.resolve(FixtureManifest.FILE_NAME),
            FixtureManifest(listOf(stale, hand, replaced)).encode(),
        )
        val manifest = generate()
        assertEquals(hand, manifest.fixtures.last())
        assertEquals(FixtureGenerator.plan.size + 1, manifest.fixtures.size)
        assertTrue(manifest.fixtures.none { it.file == "old.txt" })
        assertEquals(
            FixtureGenerator.plan.single { it.file == "syn-text-01.txt" }.seed,
            manifest.fixtures.single { it.file == "syn-text-01.txt" }.seed,
        )
    }

    @Test
    fun `main writes into the directory it is given and rejects other arguments`() {
        val dir = out.resolve("pools")
        main(arrayOf(fixturesDir.toString(), briefFixturesDir.toString(), dir.toString()))
        assertEquals(FixtureGenerator.plan.size, FixtureManifest.load(dir).fixtures.size)
        assertFailsWith<IllegalArgumentException> { main(arrayOf(dir.toString())) }
    }

    @Test
    fun `every second multi-face card is written by its front face`() {
        val kit =
            SampledKit(
                boosters =
                    listOf(
                        listOf("11", "1", "12", "16", "11", "13").map {
                            SampledCard(syn(it), SlotKind.COMMON, foil = false)
                        }
                    ),
                promo = null,
            )
        // Multi-face cards in pool order: Delver 11, Huntmaster 12, Fire // Ice 16, Agadeem 13.
        assertEquals(
            setOf(syn("12").card.oracleId, syn("13").card.oracleId),
            FixtureGenerator.frontFaceOnly(kit),
        )
    }

    @Test
    fun `the manifest reads back what it writes and an empty directory has an empty manifest`() {
        val manifest =
            FixtureManifest(listOf(FixtureEntry("a.csv", "trk", "manabox", 1, 85, emptyList())))
        assertEquals(manifest, FixtureManifest.decode(manifest.encode()))
        assertTrue(manifest.encode().endsWith("}\n"))
        assertEquals(PoolFormat.MANABOX, manifest.fixtures.single().poolFormat)
        assertEquals(FixtureManifest(emptyList()), FixtureManifest.load(out))
    }
}
