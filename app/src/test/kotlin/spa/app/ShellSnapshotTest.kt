package spa.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Snapshot tests. Each compares against its baseline in `src/test/snapshots` when the gate runs
 * `verifyRoborazziDebug`; `./gradlew :app:recordRoborazziDebug` rewrites the baselines.
 */
@RunWith(AndroidJUnit4::class)
class ShellSnapshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun shellPlaceholder() {
        compose.onRoot().captureRoboImage(Snapshots.path("shell_placeholder"), Snapshots.options)
    }
}
