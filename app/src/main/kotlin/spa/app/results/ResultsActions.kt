package spa.app.results

import spa.app.PoolUiState
import spa.core.metrics.ColorPair

/** What Results can ask of the app. */
data class ResultsActions(
    val onNewPool: () -> Unit,
    val onAbout: () -> Unit,
    /** A pair row was tapped: open Pair detail for it. */
    val onPair: (ColorPair) -> Unit = {},
    /** The loaded state has drawn (timing). */
    val onLoadedShown: (PoolUiState.Loaded) -> Unit = {},
    /** "Clear pool" was confirmed in the Clear pool dialog. */
    val onClearPool: () -> Unit = {},
)
