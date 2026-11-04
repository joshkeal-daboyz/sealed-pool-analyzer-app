package spa.app.results

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import spa.app.PoolUiState
import spa.app.R
import spa.app.ui.SpaTopBar
import spa.app.ui.theme.LocalSpaColors
import spa.core.metrics.ColorPair

/** [ResultsScreen] with its actions passed one by one; pair rows do nothing. */
@Composable
fun ResultsScreen(
    state: PoolUiState,
    onNewPool: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadedShown: (PoolUiState.Loaded) -> Unit = {},
) {
    ResultsScreen(
        state,
        ResultsActions(onNewPool, onAbout, onLoadedShown = onLoadedShown),
        modifier,
    )
}

/**
 * Results: the top bar "Your pool" with the About icon, then one vertical scroll: the pool header
 * card, Problems, the legend, Pool at a glance, Colour pairs, Cards per colour, and Fixing sources,
 * all rendered from [ResultsUi] against the [LocalBaseline] (the bundled one by default). While an
 * import runs, the Loading state instead.
 */
@Composable
fun ResultsScreen(state: PoolUiState, actions: ResultsActions, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Scaffold(
        modifier = modifier,
        topBar = {
            SpaTopBar(title = stringResource(R.string.results_title), onAbout = actions.onAbout)
        },
        containerColor = LocalSpaColors.current.ground,
    ) { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = SIDE.dp, vertical = TOP.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
        ) {
            when (state) {
                is PoolUiState.Loading -> LoadingState(state.source)
                is PoolUiState.Loaded -> {
                    // The frame after this state is composed comes once it has been drawn.
                    LaunchedEffect(state) {
                        withFrameNanos {}
                        actions.onLoadedShown(state)
                    }
                    PoolHeaderCard(
                        source = state.source,
                        loaded = state,
                        onNewPool = actions.onNewPool,
                    )
                    val baseline = LocalBaseline.current ?: rememberBundledBaseline()
                    val ui =
                        remember(state, baseline) {
                            baseline?.let { ResultsUi.from(state.result, it) }
                        }
                    if (ui != null) ResultsBody(ui, scroll, actions.onPair)
                }
                else -> Unit
            }
        }
    }
}

/** Everything below the pool header, in the spec's order. */
@Composable
private fun ResultsBody(ui: ResultsUi, scroll: ScrollState, onPair: (ColorPair) -> Unit) {
    val scope = rememberCoroutineScope()
    // Where Pool at a glance starts in the scrolling column, for "Skip to numbers".
    var glanceTop by remember { mutableIntStateOf(0) }
    ProblemsSection(ui, onSkipToNumbers = { scope.launch { scroll.animateScrollTo(glanceTop) } })
    BaselineLegend(ui.kits)
    Column(
        modifier = Modifier.onPlaced { glanceTop = it.positionInParent().y.roundToInt() },
        verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
    ) {
        SectionHeading(stringResource(R.string.results_glance))
        ui.glance.forEach { MetricRow(stringResource(glanceLabel(it.pointer)), it) }
    }
    PairList(ui.pairs, onPair)
    ColorBreakdown(ui.colors)
    FixingSourceList(ui.fixingSources, Modifier.padding(bottom = TOP.dp))
}

/** "Typical: the median of <kits> simulated trk pools, with the middle 80% (...)." */
@Composable
fun BaselineLegend(kits: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.results_legend, kits),
        modifier = modifier.padding(top = ITEM_GAP.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = LocalSpaColors.current.onSurfaceVariant,
    )
}

/** Pool at a glance's row label for [pointer]. */
private fun glanceLabel(pointer: String): Int =
    when (pointer) {
        ResultsUi.TOTAL_CARDS -> R.string.results_total_cards
        ResultsUi.NONLAND_CARDS -> R.string.results_nonland_cards
        ResultsUi.BASIC_LANDS -> R.string.results_basic_lands
        ResultsUi.NONBASIC_LANDS -> R.string.results_nonbasic_lands
        ResultsUi.REMOVAL -> R.string.results_removal
        ResultsUi.INTERACTION -> R.string.results_interaction
        ResultsUi.FIXING_COUNT -> R.string.results_fixing_sources
        else -> error("no label for $pointer")
    }

private const val SIDE = 16
private const val TOP = 12
