package spa.app.pair

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.json.JSONArray
import spa.app.PoolUiState
import spa.app.results.LocalBaseline
import spa.app.results.ResultsKit
import spa.app.ui.theme.SpaTheme

/**
 * Shared by the Pair detail tests. Expected values are read from the goldens and the baseline with
 * org.json (through [ResultsKit]), independently of the app's model.
 */
object PairKit {
    const val FILE = "trk-manabox-01.csv"

    /** The ten pair codes, in the order WU ... RG. */
    val PAIRS = listOf("WU", "WB", "WR", "WG", "UB", "UR", "UG", "BR", "BG", "RG")

    /** The six pair counts under Cards, as pointer segments, in display order. */
    val COUNTS =
        listOf("cards", "creatures", "noncreatures", "removal", "interaction", "fixingSources")

    /** The rows' labels for [COUNTS], from the spec. */
    val COUNT_LABELS =
        listOf("Cards", "Creatures", "Noncreatures", "Removal", "Interaction", "Fixing sources")

    /** The basic land per colour, from the spec, typed here independently of the app. */
    val LANDS =
        mapOf("W" to "Plains", "U" to "Island", "B" to "Swamp", "R" to "Mountain", "G" to "Forest")

    /** The colour names, from the spec. */
    val COLOURS =
        mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green")

    /** The curve buckets, 0 to 7. */
    val BUCKETS = 0..7

    /** The pair's two colour codes, such as "W" and "U" for "WU". */
    fun colours(pair: String): List<String> = pair.map { "$it" }

    /** The background's pointers for [pair], in display order. */
    fun pointers(pair: String): List<String> {
        val at = "/pairs/$pair"
        return colours(pair).map { "$at/landRecommendation/$it" } +
            COUNTS.map { "$at/$it" } +
            BUCKETS.flatMap {
                listOf("$at/curve/buckets/$it/creatures", "$at/curve/buckets/$it/noncreatures")
            } +
            "$at/splashCandidates/count"
    }

    /** One golden splash candidate: name, colour code, reason code. */
    data class Splash(val name: String, val color: String, val reason: String)

    fun goldenSplash(file: String, pair: String): List<Splash> {
        val metrics = ResultsKit.golden(file).getJSONObject("metrics")
        val list = ResultsKit.at(metrics, "/pairs/$pair/splashCandidates") as JSONArray
        return list.objects().map {
            Splash(it.getString("name"), it.getString("color"), it.getString("reason"))
        }
    }

    /** The golden's unresolved line count. */
    fun goldenUnresolved(file: String): Int =
        ResultsKit.golden(file).getJSONObject("pool").getJSONArray("unresolved").length()

    /** The spec's reason words. */
    fun reasonWords(code: String): String =
        when (code) {
            "REMOVAL" -> "removal"
            "RARE_OR_MYTHIC" -> "rare or mythic"
            else -> code
        }

    /** Pair detail for [pair] of [pool], against the fixtures' baseline. */
    @Composable
    fun Screen(
        pool: PoolUiState.Loaded?,
        pair: String = "WU",
        onBack: () -> Unit = {},
        onAbout: () -> Unit = {},
    ) {
        SpaTheme {
            CompositionLocalProvider(LocalBaseline provides ResultsKit.baseline) {
                PairDetailScreen(pair = pair, pool = pool, onBack = onBack, onAbout = onAbout)
            }
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    /** A pool whose WU has no cards: two black removal spells, castable in no white or blue pair. */
    const val EMPTY_WU_POOL = "1 Impossible Choice\n1 He's Dead, Jim"

    /** A pool whose WU has no splash candidates: one white and one blue creature, nothing else. */
    const val NO_SPLASH_WU_POOL = "1 Andromedan Automatons\n1 Cetacean Ops"
}
