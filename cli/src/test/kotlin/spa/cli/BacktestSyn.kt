package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * A data directory for set `syn` and hand-built Sealed files for `spa deck-backtest` (SPA-046):
 * the committed `syn` Scryfall cache, the `syn` model grades, a pairs-only deck weights file with
 * the synergy part off as `deck-default.json`, and the committed `deck-baseline.json`.
 */
internal object BacktestSyn {
    /** The deck builder's weights for the tests: the default numbers, pairs only, no synergy. */
    const val WEIGHTS =
        """
        {
          "version": 1,
          "weights": {
            "grades": 1.0, "curve": 1.0, "creatures": 1.0, "removal": 1.0,
            "castability": 1.0, "fixing": 1.0, "synergy": 0
          },
          "params": {
            "curve": { "target1": 1, "target2": 5, "target3": 4, "target4": 3, "target5": 2, "target6": 1 },
            "creatures": { "minCreatures": 14 },
            "removal": { "cap": 6 }
          },
          "lands": { "lowCurve": 2.6, "highCurve": 3.4, "low": 16, "normal": 17, "high": 18 },
          "splash": { "maxCards": 3, "maxPips": 1 },
          "configs": { "pairs": { "enabled": true } }
        }
        """

    /** The `syn` data directory under [dir]. */
    fun data(dir: Path): Path {
        Fixtures.synDataDir(dir)
        dir.resolve("grades").createDirectories()
        val grades = requireNotNull(javaClass.getResource("/grades/syn-model.json"))
        Files.write(dir.resolve("grades/syn.json"), grades.readBytes())
        dir.resolve("weights").createDirectories()
        Files.writeString(dir.resolve("weights/deck-default.json"), WEIGHTS)
        Files.copy(
            Path.of(Fixtures.data, "weights", "deck-baseline.json"),
            dir.resolve("weights/deck-baseline.json"),
        )
        return dir
    }

    private val NAMES =
        listOf(
            "Air Elemental",
            "Counterspell",
            "Delver of Secrets",
            "Doom Blade",
            "Gravedigger",
            "Murderous Rider",
            "Mind Stone",
            "Serra Angel",
            "Island",
            "Swamp",
            "Bonus Reprint",
        )

    /** One game's row of a build; the counts not given are 0. */
    @Suppress("LongParameterList")
    fun row(
        draft: String,
        main: String,
        splash: String = "",
        bucket: String = "0.7",
        deck: Map<String, Int> = emptyMap(),
        side: Map<String, Int> = emptyMap(),
        build: String = "0",
    ): String =
        (listOf("SYN", "Sealed", draft, build, main, splash, "True") +
                NAMES.flatMap { listOf("${deck[it] ?: 0}", "${side[it] ?: 0}") } +
                bucket)
            .joinToString(",")

    /** The Sealed file text of [rows]. */
    fun csv(vararg rows: String): String {
        val fixed =
            listOf(
                "expansion",
                "event_type",
                "draft_id",
                "build_index",
                "main_colors",
                "splash_colors",
                "won",
            )
        val counts = NAMES.flatMap { listOf("deck_$it", "sideboard_$it") }
        val header = (fixed + counts + "user_game_win_rate_bucket").joinToString(",")
        return (listOf(header) + rows).joinToString("\n") + "\n"
    }

    private val BLUE_BLACK =
        mapOf(
            "Air Elemental" to 5,
            "Counterspell" to 4,
            "Delver of Secrets" to 3,
            "Doom Blade" to 5,
            "Gravedigger" to 4,
            "Murderous Rider" to 2,
            "Island" to 8,
            "Swamp" to 9,
        )

    /** Two colours: 23 blue and black spells, 17 lands, three more cards aside. */
    val TWO = row("t1", "UB", deck = BLUE_BLACK, side = mapOf("Air Elemental" to 2, "Mind Stone" to 1))

    /** Two plus a splash: the same cards, with a splash of red the pool has no cards for. */
    val SPLASH =
        row(
            "t2",
            "UB",
            splash = "R",
            bucket = "0.62",
            deck = BLUE_BLACK + mapOf("Air Elemental" to 6, "Doom Blade" to 4),
            side = mapOf("Mind Stone" to 1),
        )

    /** Three main colours: 22 spells, 16 lands. */
    val MULTI =
        row(
            "t3",
            "UBG",
            bucket = "0.8",
            deck =
                mapOf(
                    "Air Elemental" to 4,
                    "Counterspell" to 4,
                    "Doom Blade" to 6,
                    "Gravedigger" to 5,
                    "Murderous Rider" to 3,
                    "Island" to 8,
                    "Swamp" to 8,
                ),
            side = mapOf("Delver of Secrets" to 2, "Mind Stone" to 2, "Air Elemental" to 1),
        )

    /** Blue and black cards too few to fill a UB deck, and plenty of white ones. */
    val UNBUILDABLE =
        row(
            "t4",
            "UB",
            deck =
                mapOf(
                    "Doom Blade" to 6,
                    "Air Elemental" to 4,
                    "Serra Angel" to 14,
                    "Island" to 8,
                    "Swamp" to 8,
                ),
            side = mapOf("Serra Angel" to 16),
        )

    /** The builds that are kept, in file order. */
    val KEPT = listOf(TWO, SPLASH, MULTI, UNBUILDABLE)

    /** Builds the reader skips: weak, one colour, unresolved, no choice, five colours. */
    val SKIPPED =
        listOf(
            row("w1", "UB", bucket = "0.5", deck = BLUE_BLACK),
            row("s1", "U", deck = BLUE_BLACK),
            row("s2", "UB", deck = BLUE_BLACK, side = mapOf("Bonus Reprint" to 1)),
            row("s3", "UB", deck = mapOf("Island" to 17), side = mapOf("Serra Angel" to 4)),
            row("s4", "WUBRG", deck = BLUE_BLACK),
        )
}
