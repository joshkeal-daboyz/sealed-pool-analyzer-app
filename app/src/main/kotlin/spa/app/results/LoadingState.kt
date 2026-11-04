package spa.app.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import spa.app.R
import spa.app.importing.PoolSource
import spa.app.ui.theme.LocalSpaColors

/**
 * Results while an import runs: the header card with [source] and "Reading your pool…", and a
 * progress indicator described "Reading your pool" (R5). The indicator appears only once loading
 * has lasted [indicatorDelayMillis], to avoid a flash.
 */
@Composable
fun LoadingState(
    source: PoolSource,
    modifier: Modifier = Modifier,
    indicatorDelayMillis: Long = INDICATOR_DELAY_MILLIS,
) {
    val showIndicator by rememberIndicatorDue(indicatorDelayMillis)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(INDICATOR_GAP.dp)) {
        PoolHeaderCard(source = source, loaded = null, onNewPool = null)
        if (showIndicator) {
            val description = stringResource(R.string.results_reading_description)
            Box(Modifier.fillMaxWidth().padding(top = INDICATOR_GAP.dp)) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.align(Alignment.Center).semantics {
                            contentDescription = description
                        },
                    color = LocalSpaColors.current.accent,
                )
            }
        }
    }
}

/**
 * Whether loading has lasted [delayMillis] since this was first composed: false until then, true
 * from then on.
 */
@Composable
fun rememberIndicatorDue(delayMillis: Long = INDICATOR_DELAY_MILLIS): State<Boolean> {
    val due = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        due.value = true
    }
    return due
}

/** The spec's 300 ms: loading shorter than this shows no indicator. */
const val INDICATOR_DELAY_MILLIS = 300L

private const val INDICATOR_GAP = 24
