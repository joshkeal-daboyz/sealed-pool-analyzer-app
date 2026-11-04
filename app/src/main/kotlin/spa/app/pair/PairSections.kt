package spa.app.pair

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import spa.app.results.DisplayedValue
import spa.core.metrics.SplashCandidate

@Composable fun UnreadLinesReminder(count: Int, modifier: Modifier = Modifier) {}

@Composable fun LandRecommendationRows(lands: List<LandUi>, modifier: Modifier = Modifier) {}

@Composable fun CurveTable(rows: List<CurveRowUi>, modifier: Modifier = Modifier) {}

@Composable fun CurveRow(bucket: CurveRowUi, modifier: Modifier = Modifier) {}

@Composable
fun SplashCandidateList(
    count: DisplayedValue,
    candidates: List<SplashCandidate>,
    modifier: Modifier = Modifier,
) {}

@Composable fun SplashCandidateItem(candidate: SplashCandidate, modifier: Modifier = Modifier) {}
