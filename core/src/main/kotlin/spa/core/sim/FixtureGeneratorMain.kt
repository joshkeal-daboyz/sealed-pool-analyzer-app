package spa.core.sim

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.jsonPrimitive
import spa.core.brief.BriefLoader
import spa.core.cards.CardIndex
import spa.core.cards.ScryfallCache
import spa.core.intake.PoolFormat

/**
 * `./gradlew :core:generateFixtures`: regenerates `fixtures/pools/` from the committed Scryfall
 * caches and briefs. Arguments: the Scryfall cache directory, the briefs directory, and the pools
 * directory.
 */
fun main(args: Array<String>) {
    require(args.size == FixtureGenerator.ARGUMENTS) {
        "Usage: FixtureGeneratorMain <scryfallDir> <briefsDir> <poolsDir>"
    }
    val (scryfallDir, briefsDir, poolsDir) = args.map { Path.of(it) }
    val manifest = FixtureGenerator.generate(scryfallDir, briefsDir, poolsDir)
    println("Wrote ${manifest.fixtures.size} fixture entries to $poolsDir.")
}

/**
 * One sampled fixture: its [file] name, its [set], its [format], and the [seed] of its kit. When
 * [alternateFaces] is set, a text list writes every second multi-face card by its front face only.
 */
data class FixturePlan(
    val file: String,
    val set: String,
    val format: PoolFormat,
    val seed: Long,
    val alternateFaces: Boolean = false,
)

/**
 * Samples every fixture in [plan] with its set's kit template and writes it, then writes the
 * manifest. Hand-edited fixtures, the manifest entries with a null seed, are kept as they are.
 * Running it twice leaves the directory unchanged.
 */
object FixtureGenerator {
    const val ARGUMENTS = 3

    private const val TRK_MANABOX_SEEDS = 1001L
    private const val TRK_TEXT_SEEDS = 2019L
    private const val TRK_COLLECTOR_SEEDS = 3013L
    private const val SYN_TEXT_SEEDS = 4003L
    private const val SYN_COLLECTOR_SEEDS = 5001L
    private const val TRK_MANABOX_FILES = 10
    private const val TRK_LIST_FILES = 5
    private const val SYN_TEXT_FILES = 2

    /** Every sampled fixture, in manifest order. */
    val plan: List<FixturePlan> =
        series("trk", PoolFormat.MANABOX, TRK_MANABOX_FILES, TRK_MANABOX_SEEDS) +
            series("trk", PoolFormat.TEXT, TRK_LIST_FILES, TRK_TEXT_SEEDS) +
            series("trk", PoolFormat.COLLECTOR, TRK_LIST_FILES, TRK_COLLECTOR_SEEDS) +
            series("syn", PoolFormat.TEXT, SYN_TEXT_FILES, SYN_TEXT_SEEDS, alternateFaces = true) +
            series("syn", PoolFormat.COLLECTOR, 1, SYN_COLLECTOR_SEEDS)

    /** Writes every fixture in [plan] into [poolsDir] and returns the manifest it wrote there. */
    fun generate(scryfallDir: Path, briefsDir: Path, poolsDir: Path): FixtureManifest {
        Files.createDirectories(poolsDir)
        val planned = plan.map { it.file }.toSet()
        val handEdited =
            FixtureManifest.load(poolsDir)
                .fixtures
                .filter { it.seed == null && it.file !in planned }
                .sortedBy { it.file }
        val sets = mutableMapOf<String, SetSampler>()
        val generated = plan.map { fixture ->
            val sampler =
                sets.getOrPut(fixture.set) { SetSampler(scryfallDir, briefsDir, fixture.set) }
            val kit = sampler.sampler.sampleKit(fixture.seed)
            Files.writeString(poolsDir.resolve(fixture.file), sampler.write(fixture, kit))
            FixtureEntry(
                file = fixture.file,
                set = fixture.set,
                format = FixtureEntry.formatName(fixture.format),
                seed = fixture.seed,
                expectedCards = kit.pool.totalCount,
                expectedUnresolved = emptyList(),
            )
        }
        val manifest = FixtureManifest(generated + handEdited)
        Files.writeString(poolsDir.resolve(FixtureManifest.FILE_NAME), manifest.encode())
        return manifest
    }

    /** The oracle ids [alternateFaces] writes by front face: every second multi-face card. */
    internal fun frontFaceOnly(kit: SampledKit): Set<String> =
        kit.pool.entries
            .map { it.printing.card }
            .filter { it.faces.size > 1 }
            .distinctBy { it.oracleId }
            .filterIndexed { at, _ -> at % 2 == 1 }
            .mapTo(mutableSetOf()) { it.oracleId }

    private fun series(
        set: String,
        format: PoolFormat,
        files: Int,
        firstSeed: Long,
        alternateFaces: Boolean = false,
    ): List<FixturePlan> =
        (1..files).map { number ->
            val name = FixtureEntry.formatName(format)
            val extension = if (format == PoolFormat.MANABOX) "csv" else "txt"
            FixturePlan(
                file = "$set-$name-${number.toString().padStart(2, '0')}.$extension",
                set = set,
                format = format,
                seed = firstSeed + number - 1,
                alternateFaces = alternateFaces,
            )
        }

    /** One set's index, kit sampler, and set names, loaded once per run. */
    private class SetSampler(scryfallDir: Path, briefsDir: Path, set: String) {
        val index = CardIndex.load(scryfallDir, set)
        val sampler =
            KitSampler(index, BriefLoader.load(briefsDir.resolve("$set.json")).kitTemplate)
        val setNames =
            index.setCodes.associateWith {
                ScryfallCache.load(scryfallDir, it).set.getValue("name").jsonPrimitive.content
            }

        fun write(fixture: FixturePlan, kit: SampledKit): String =
            when (fixture.format) {
                PoolFormat.MANABOX -> PoolWriters.manaBox(kit.pool, setNames, kit.foilCounts())
                PoolFormat.TEXT ->
                    PoolWriters.text(
                        kit.pool,
                        if (fixture.alternateFaces) frontFaceOnly(kit) else emptySet(),
                    )
                PoolFormat.COLLECTOR -> PoolWriters.collector(kit.pool)
            }
    }
}
