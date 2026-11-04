package spa.app.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import org.robolectric.RuntimeEnvironment
import spa.app.ClickableChecks

/**
 * The accessibility checks every screen ticket runs on its screen states, on the Compose semantics
 * tree. Built on [ClickableChecks] (SPA-013): ATF does not evaluate Compose content under
 * Robolectric, so the gate runs these instead.
 */
object A11yChecks {
    /** The font scale A5 checks at. */
    const val LARGE_FONT_SCALE = 2f

    /** A1: every node with a click action is at least 48 x 48 dp, on its laid-out bounds. */
    fun assertTouchTargets(rule: ComposeTestRule) =
        fail("Touch targets (A1)", ClickableChecks.sizeProblems(rule))

    /** A4: every node with a click action has text or a content description in the merged tree. */
    fun assertClickablesLabelled(rule: ComposeTestRule) =
        fail("Clickable labels (A4)", ClickableChecks.labelProblems(rule))

    /**
     * A5: sets [content] at font scale 2.0 (the device setting, through Robolectric) and fails when
     * any text has visual overflow (`TextLayoutResult.hasVisualOverflow`). This sets the rule's
     * content, so it is the only `setContent` in its test.
     *
     * Every node with the `GetTextLayoutResult` semantics action is checked. For a plain `Text`
     * that action re-lays the text out without its style (font size comes back unspecified, found
     * 2026-11-03 on Compose 1.11 under Robolectric), so its overflow flag cannot be trusted. For
     * those, the text is measured again with its real style, read from the text modifier, under the
     * constraints the action reports, which are the real ones.
     */
    fun assertNoTextOverflowAtFontScale2(
        rule: ComposeContentTestRule,
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setFontScale(LARGE_FONT_SCALE)
        rule.setContent(content)
        rule.waitForIdle()
        val texts =
            rule
                .onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNodes()
        fail("Text overflow at font scale 2.0 (A5)", texts.mapNotNull(::overflow))
    }

    private fun overflow(node: SemanticsNode): String? {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val layout =
            layouts.firstOrNull()?.let { reported ->
                textStyleOf(node)?.let { remeasure(reported, it) } ?: reported
            }
        if (layout == null || !layout.hasVisualOverflow) return null
        val b = node.boundsInRoot
        return "Text '${layout.layoutInput.text}' (node #${node.id} at [${b.left.toInt()}, " +
            "${b.top.toInt()}, ${b.right.toInt()}, ${b.bottom.toInt()}] px) overflows its box"
    }

    /**
     * The style a plain `Text`'s modifier element holds, or null for other text (fields, rich
     * text). The element lists no inspectable properties, so its `style` field is read directly; if
     * a Compose update renames it, this fails loudly rather than skipping the check.
     */
    private fun textStyleOf(node: SemanticsNode): TextStyle? {
        val element =
            node.layoutInfo
                .getModifierInfo()
                .map { it.modifier }
                .firstOrNull { it.javaClass.name == PLAIN_TEXT_ELEMENT } ?: return null
        val field =
            checkNotNull(element.javaClass.declaredFields.firstOrNull { it.name == "style" }) {
                "$PLAIN_TEXT_ELEMENT has no style field; update A11yChecks for this Compose"
            }
        field.isAccessible = true
        return field.get(element) as TextStyle
    }

    private fun remeasure(reported: TextLayoutResult, style: TextStyle): TextLayoutResult {
        val input = reported.layoutInput
        return TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
            .measure(
                text = input.text,
                style = style,
                overflow = input.overflow,
                softWrap = input.softWrap,
                maxLines = input.maxLines,
                constraints = input.constraints,
            )
    }

    private fun fail(check: String, problems: List<String>) {
        if (problems.isNotEmpty()) {
            throw AssertionError("$check failed:\n" + problems.joinToString("\n"))
        }
    }

    /** The modifier element behind `Text(String)` with no `onTextLayout`. */
    private const val PLAIN_TEXT_ELEMENT =
        "androidx.compose.foundation.text.modifiers.TextStringSimpleElement"
}
