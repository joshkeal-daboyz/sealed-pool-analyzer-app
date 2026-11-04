package spa.app.pair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import spa.app.PoolUiState
import spa.app.R
import spa.app.names.PairNames
import spa.app.results.LocalBaseline
import spa.app.results.MetricRow
import spa.app.results.ROW_GAP
import spa.app.results.SectionHeading
import spa.app.results.rememberBundledBaseline
import spa.app.ui.SpaTopBar
import spa.app.ui.theme.LocalSpaColors
import spa.core.metrics.ColorPair

/**
 * Pair detail for [pair] (`WU` to `RG`) of the loaded [pool]: the top bar with Back, the pair's
 * name, and About; then one vertical scroll: the unread-lines reminder when the pool has any, Lands,
 * Cards, Curve, and Splash candidates, all rendered from [PairDetailUi] against the
 * [LocalBaseline] (the bundled one by default). With no pool, or an unknown pair, the body is
 * empty: the screen opens only from loaded Results.
 */
@Composable
fun PairDetailScreen(
    pair: String,
    pool: PoolUiState.Loaded?,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorPair = ColorPair.entries.firstOrNull { it.name == pair }
    Scaffold(
        modifier = modifier,
        topBar = {
            SpaTopBar(
                title = colorPair?.let(PairNames::of) ?: pair,
                onAbout = onAbout,
                onBack = onBack,
            )
        },
        containerColor = LocalSpaColors.current.ground,
    ) { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SIDE.dp, vertical = TOP.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
        ) {
            if (colorPair != null && pool != null) {
                val baseline = LocalBaseline.current ?: rememberBundledBaseline()
                val ui =
                    remember(pool, colorPair, baseline) {
                        baseline?.let { PairDetailUi.from(pool.result, colorPair, it) }
                    }
                if (ui != null) PairDetailBody(ui)
            }
        }
    }
}

/** Everything below the top bar, in the spec's order. */
@Composable
private fun PairDetailBody(ui: PairDetailUi) {
    if (ui.unresolvedLines > 0) UnreadLinesReminder(ui.unresolvedLines)
    LandRecommendationRows(ui.lands)
    SectionHeading(stringResource(R.string.pair_cards_heading))
    ui.counts.forEach { MetricRow(stringResource(countLabel(it.pointer)), it) }
    CurveTable(ui.curve)
    SplashCandidateList(ui.splashCount, ui.splashCandidates, Modifier.padding(bottom = TOP.dp))
}

/** The Cards section's row label for [pointer], by its last segment. */
private fun countLabel(pointer: String): Int =
    when (pointer.substringAfterLast('/')) {
        PairDetailUi.CARDS -> R.string.pair_cards
        PairDetailUi.CREATURES -> R.string.pair_creatures
        PairDetailUi.NONCREATURES -> R.string.pair_noncreatures
        PairDetailUi.REMOVAL -> R.string.pair_removal
        PairDetailUi.INTERACTION -> R.string.pair_interaction
        PairDetailUi.FIXING_SOURCES -> R.string.pair_fixing_sources
        else -> error("no label for $pointer")
    }

private const val SIDE = 16
private const val TOP = 12
