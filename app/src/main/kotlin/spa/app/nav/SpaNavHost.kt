package spa.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import spa.app.pool.PoolEntryActions
import spa.app.pool.PoolEntryModel
import spa.app.pool.PoolEntryScreen
import spa.app.results.ResultsActions
import spa.app.results.ResultsScreen
import spa.app.timing.SpaTiming

/**
 * The app's navigation, by the spec's Navigation flow. With no pool, launch shows Pool. An import,
 * pasted or picked, replaces the back stack with Results. A failed one moves to Pool: on top of
 * Results when there is a current pool, so back returns to it (decision 5), else as the only
 * screen. "New pool" pushes Pool on top of Results.
 */
@Composable
fun SpaNavHost(
    navController: NavHostController,
    viewModel: PoolViewModel,
    timing: SpaTiming,
    modifier: Modifier = Modifier,
) {
    val app =
        remember(navController, viewModel, timing) { AppNav(navController, viewModel, timing) }
    NavHost(navController, startDestination = Routes.POOL, modifier = modifier) {
        composable(Routes.POOL) { PoolRoute(app) }
        composable(Routes.RESULTS) { ResultsRoute(app) }
        composable(
            Routes.PAIR,
            arguments = listOf(navArgument(Routes.PAIR_ARG) { type = NavType.StringType }),
        ) { entry ->
            PairDetailScreen(
                pair = entry.arguments?.getString(Routes.PAIR_ARG).orEmpty(),
                pool = null,
                onBack = { navController.popBackStack() },
                onAbout = app::toAbout,
            )
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
    }
}

/** What every destination shares: the controller, the pool, the timing lines. */
private class AppNav(
    val navController: NavHostController,
    val viewModel: PoolViewModel,
    val timing: SpaTiming,
) {
    fun toAbout() {
        navController.navigate(Routes.ABOUT)
    }

    fun replaceStackWith(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    /** An import is confirmed: logs it, [start]s it, and makes Results the whole back stack. */
    fun confirmImport(start: () -> Unit) {
        timing.importConfirmed()
        start()
        replaceStackWith(Routes.RESULTS)
    }
}

@Composable
private fun PoolRoute(app: AppNav) {
    val vm = app.viewModel
    val state by vm.state.collectAsStateWithLifecycle()
    val text by vm.pastedText.collectAsStateWithLifecycle()
    val format by vm.chosenFormat.collectAsStateWithLifecycle()
    val nav = app.navController
    val onBack: (() -> Unit)? =
        if (nav.previousBackStackEntry != null) {
            { nav.popBackStack() }
        } else null
    PoolEntryScreen(
        model = PoolEntryModel(text, format, state),
        actions =
            PoolEntryActions(
                onTextChange = vm::onTextChange,
                onAnalyze = { app.confirmImport { vm.analyze(text, format) } },
                onAbout = app::toAbout,
                onFormatChoose = vm::onFormatChoose,
                onFilePicked = { uri -> app.confirmImport { vm.analyzeFile(uri) } },
            ),
        onBack = onBack,
    )
}

@Composable
private fun ResultsRoute(app: AppNav) {
    val vm = app.viewModel
    val state by vm.state.collectAsStateWithLifecycle()
    val pool by vm.currentPool.collectAsStateWithLifecycle()
    // Saved with this Results entry: once its failed import has moved to Pool, coming back to it
    // shows the current pool instead of moving to Pool again.
    var failureHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        val failed = state is PoolUiState.ImportFailed || state is PoolUiState.DataMissing
        if (failed && !failureHandled) {
            failureHandled = true
            if (pool != null) app.navController.navigate(Routes.POOL)
            else app.replaceStackWith(Routes.POOL)
        }
    }
    // After a failed import, Results under Pool still shows the current pool.
    val shown =
        if (state is PoolUiState.Loading || state is PoolUiState.Loaded) state else pool ?: state
    ResultsScreen(
        state = shown,
        actions =
            ResultsActions(
                onNewPool = {
                    vm.startNewPool()
                    app.navController.navigate(Routes.POOL)
                },
                onAbout = app::toAbout,
                onPair = { app.navController.navigate(Routes.pair(it.name)) },
                onLoadedShown = app.timing::resultsShown,
            ),
    )
}
