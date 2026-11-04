package spa.app

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spec's A4 checks, run on the Compose semantics tree: every clickable is at least 48 x 48 dp
 * and has a label (text or a content description). ATF does not evaluate Compose content under
 * Robolectric, so the gate runs these instead.
 *
 * Size is measured on the laid-out bounds, not `touchBoundsInRoot`: Compose reports touch bounds
 * widened to 48 dp for every clickable, even when neighbours overlap, so they never fail. The
 * laid-out bounds always lie inside the touch bounds, so this check is the stricter of the two.
 */
object ClickableChecks {
    val MIN_TOUCH_TARGET: Dp = 48.dp

    /** Fails with one line per problem, each naming the node, when any clickable breaks a rule. */
    fun assertClickablesAccessible(rule: ComposeTestRule) {
        val problems = problems(rule)
        if (problems.isNotEmpty()) {
            throw AssertionError(
                "Clickable accessibility checks failed:\n" + problems.joinToString("\n")
            )
        }
    }

    /** One message per broken rule per clickable, in tree order; empty when all pass. */
    fun problems(rule: ComposeTestRule): List<String> {
        rule.waitForIdle()
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        return nodes.flatMap { node -> problemsOf(node, rule) }
    }

    private fun problemsOf(node: SemanticsNode, rule: ComposeTestRule): List<String> {
        val bounds = node.boundsInRoot
        val (width, height) = with(rule.density) { bounds.width.toDp() to bounds.height.toDp() }
        val name = describe(node)
        val small =
            if (width < MIN_TOUCH_TARGET || height < MIN_TOUCH_TARGET) {
                "$name: size ${format(width)} x ${format(height)} dp is smaller than " +
                    "${format(MIN_TOUCH_TARGET)} x ${format(MIN_TOUCH_TARGET)} dp"
            } else null
        val unlabelled =
            if (label(node) == null) "$name: has no label (no text or content description)"
            else null
        return listOfNotNull(small, unlabelled)
    }

    /** The node's text or content description, from the merged tree, or null when it has none. */
    private fun label(node: SemanticsNode): String? {
        val config = node.config
        val text = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ")
        val description =
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        return listOf(text, description).firstOrNull { it.isNotBlank() }
    }

    private fun describe(node: SemanticsNode): String {
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        val identity = tag?.let { "tag '$it'" } ?: label(node)?.let { "'$it'" } ?: "untagged"
        val b = node.boundsInRoot
        return "Clickable node #${node.id} ($identity) at [${b.left.toInt()}, ${b.top.toInt()}, " +
            "${b.right.toInt()}, ${b.bottom.toInt()}] px"
    }

    private fun format(value: Dp): String = "%.1f".format(java.util.Locale.ROOT, value.value)
}
