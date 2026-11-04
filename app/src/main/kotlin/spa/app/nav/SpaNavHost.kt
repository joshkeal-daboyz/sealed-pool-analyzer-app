package spa.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import spa.app.PoolUiState
import spa.app.PoolViewModel
import spa.app.about.AboutScreen
import spa.app.pair.PairDetailScreen
import spa.app.pool.PoolEntryScreen
import spa.app.results.ResultsScreen
import spa.app.timing.SpaTiming

/**
 * The app's navigation, by the spec's Navigation flow. With no pool, launch shows Pool. An import
 * replaces the back stack with Results; a failed one replaces it with Pool. "New pool" pushes Pool
 * on top of Results.
 */
@Composable
fun SpaNavHost(
    navController: NavHostController,
    viewModel: PoolViewModel,
    timing: SpaTiming,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toAbout = { navController.navigate(Routes.ABOUT) }
    val replaceStackWith = { route: String ->
        navController.navigate(route) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController, startDestination = Routes.POOL, modifier = modifier) {
        composable(Routes.POOL) {
            val text by viewModel.pastedText.collectAsStateWithLifecycle()
            val onBack: (() -> Unit)? =
                if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else null
            PoolEntryScreen(
                text = text,
                onTextChange = viewModel::onTextChange,
                onAnalyze = {
                    timing.importConfirmed()
                    viewModel.analyze(text, null)
                    replaceStackWith(Routes.RESULTS)
                },
                onAbout = toAbout,
                onBack = onBack,
            )
        }
        composable(Routes.RESULTS) {
            LaunchedEffect(state) {
                if (state is PoolUiState.ImportFailed || state is PoolUiState.DataMissing) {
                    replaceStackWith(Routes.POOL)
                }
            }
            ResultsScreen(
                state = state,
                onNewPool = {
                    viewModel.startNewPool()
                    navController.navigate(Routes.POOL)
                },
                onAbout = toAbout,
                onLoadedShown = timing::resultsShown,
            )
        }
        composable(
            Routes.PAIR,
            arguments = listOf(navArgument(Routes.PAIR_ARG) { type = NavType.StringType }),
        ) { entry ->
            PairDetailScreen(
                pair = entry.arguments?.getString(Routes.PAIR_ARG).orEmpty(),
                onBack = { navController.popBackStack() },
                onAbout = toAbout,
            )
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
    }
}
