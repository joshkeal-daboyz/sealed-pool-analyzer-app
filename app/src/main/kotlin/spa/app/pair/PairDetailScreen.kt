package spa.app.pair

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import spa.app.ui.SpaTopBar
import spa.app.ui.theme.LocalSpaColors

/** Pair detail, a placeholder that shows only its title so the route exists. SPA-019 fills it. */
@Composable
fun PairDetailScreen(
    pair: String,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { SpaTopBar(title = pair, onAbout = onAbout, onBack = onBack) },
        containerColor = LocalSpaColors.current.ground,
    ) { padding ->
        Box(Modifier.padding(padding))
    }
}
