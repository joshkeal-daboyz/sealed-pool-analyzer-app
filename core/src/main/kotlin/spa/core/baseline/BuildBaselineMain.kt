package spa.core.baseline

import java.nio.file.Files
import java.nio.file.Path

private const val ARGUMENTS = 6
private const val SET = 3
private const val SEED = 4
private const val KITS = 5

/**
 * `./gradlew :core:buildBaseline -Pset=<code> -Pseed=<seed> -Pkits=<kits>`: writes
 * `<baselinesDir>/<code>.json`. Arguments: the Scryfall cache directory, the briefs directory, the
 * baselines directory, the set code, the seed, and the kit count.
 */
fun main(args: Array<String>) {
    require(args.size == ARGUMENTS) {
        "Usage: BuildBaselineMain <scryfallDir> <briefsDir> <baselinesDir> <set> <seed> <kits>"
    }
    val (scryfallDir, briefsDir, baselinesDir) = args.take(SET).map { Path.of(it) }
    val set = args[SET]
    val seed =
        requireNotNull(args[SEED].toLongOrNull()) { "seed must be a whole number: ${args[SEED]}" }
    val kits =
        requireNotNull(args[KITS].toIntOrNull()) { "kits must be a whole number: ${args[KITS]}" }
    val baseline = BaselineBuilder.load(scryfallDir, briefsDir, set).build(seed, kits)
    Files.createDirectories(baselinesDir)
    val file = baselinesDir.resolve("$set.json")
    Files.writeString(file, baseline.encode())
    println("Wrote ${baseline.stats.size} stats over $kits kits to $file.")
}
