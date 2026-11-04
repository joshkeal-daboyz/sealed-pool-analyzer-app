package spa.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClickableChecksTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun placeholderScreenPasses() {
        compose.setContent { PlaceholderScreen() }
        ClickableChecks.assertClickablesAccessible(compose)
    }

    @Test
    fun smallUnlabelledClickableFailsOnSizeAndLabel() {
        compose.setContent { UnlabelledClickable(30, "small") }
        val failure =
            assertThrows(AssertionError::class.java) {
                ClickableChecks.assertClickablesAccessible(compose)
            }
        val message = failure.message.orEmpty()
        assertTrue(message, message.contains("tag 'small'"))
        assertTrue(
            message,
            message.contains("size 30.0 x 30.0 dp is smaller than 48.0 x 48.0 dp"),
        )
        assertTrue(message, message.contains("has no label"))
    }

    @Test
    fun fullSizeUnlabelledClickableFailsOnLabelOnly() {
        compose.setContent { UnlabelledClickable(48, "unlabelled") }
        val problems = ClickableChecks.problems(compose)
        assertEquals(1, problems.size)
        assertTrue(problems.single(), problems.single().contains("tag 'unlabelled'"))
        assertTrue(problems.single(), problems.single().contains("has no label"))
        assertThrows(AssertionError::class.java) {
            ClickableChecks.assertClickablesAccessible(compose)
        }
    }

    @Test
    fun fullSizeLabelledClickablePasses() {
        compose.setContent { Box(Modifier.size(48.dp).clickable {}) { Text("Open") } }
        assertEquals(emptyList<String>(), ClickableChecks.problems(compose))
    }
}

/** Test-only: a clickable of [sizeDp] square with nothing for a screen reader to say. */
@Composable
private fun UnlabelledClickable(sizeDp: Int, tag: String) {
    Box(Modifier.size(sizeDp.dp).testTag(tag).clickable {})
}
