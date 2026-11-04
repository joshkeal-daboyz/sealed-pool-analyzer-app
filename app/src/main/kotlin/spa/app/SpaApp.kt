package spa.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import spa.app.nav.SpaNavHost
import spa.app.timing.SpaTiming
import spa.app.ui.theme.LocalSpaColors
import spa.app.ui.theme.SpaTheme

/** The root composable: [SpaTheme] around [SpaNavHost]. */
@Composable
fun SpaApp(
    viewModel: PoolViewModel,
    timing: SpaTiming,
    navController: NavHostController = rememberNavController(),
) {
    // The frame after the first composition comes once the first screen has drawn its first frame.
    LaunchedEffect(Unit) {
        withFrameNanos {}
        timing.firstScreenShown()
    }
    SpaTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LocalSpaColors.current.ground,
        ) {
            SpaNavHost(navController, viewModel, timing)
        }
    }
}
