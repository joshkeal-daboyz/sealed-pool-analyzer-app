package spa.app.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import spa.app.PoolUiState
import spa.app.R
import spa.app.ui.SpaTopBar
import spa.app.ui.theme.LocalSpaColors
import spa.core.baseline.Baseline
import spa.core.metrics.ColorPair

/**
 * Results: the top bar "Your pool" with the About icon, then the pool header card, or the Loading
 * state while an import runs. [onLoadedShown] is called when the loaded state draws (timing).
 * SPA-018 adds Problems, the metrics, pairs, colours, and fixing below the header.
 */
@Composable
fun ResultsScreen(
    state: PoolUiState,
    onNewPool: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadedShown: (PoolUiState.Loaded) -> Unit = {},
    onPair: (ColorPair) -> Unit = {},
    baseline: Baseline? = rememberBundledBaseline(),
) {
    Scaffold(
        modifier = modifier,
        topBar = { SpaTopBar(title = stringResource(R.string.results_title), onAbout = onAbout) },
        containerColor = LocalSpaColors.current.ground,
    ) { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SIDE.dp, vertical = TOP.dp),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP.dp),
        ) {
            when (state) {
                is PoolUiState.Loading -> LoadingState(state.source)
                is PoolUiState.Loaded -> {
                    // The frame after this state is composed comes once it has been drawn.
                    LaunchedEffect(state) {
                        withFrameNanos {}
                        onLoadedShown(state)
                    }
                    PoolHeaderCard(source = state.source, loaded = state, onNewPool = onNewPool)
                }
                else -> Unit
            }
        }
    }
}

private const val SIDE = 16
private const val TOP = 12
private const val CARD_GAP = 10
