package spa.app.about

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import spa.app.R
import spa.app.ui.theme.LocalSpaColors

/** Where the word "Scryfall" in the attribution links to. */
const val SCRYFALL_URL = "https://scryfall.com"

/**
 * The README's two Card data sentences as one text, "Scryfall" in the first carrying a link to
 * [SCRYFALL_URL] (B2).
 *
 * The word alone is one line tall, under A1's 48 dp, so the whole text is the target: tapping
 * anywhere in it, or its click action, opens the link too. Its semantics are set once on the whole
 * text, the link annotation included, so the word's own small link node is not a second, undersized
 * click target.
 */
@Composable
fun AttributionText(modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    val uriHandler = LocalUriHandler.current
    val open = { openQuietly(uriHandler) }
    val sentences = stringResource(R.string.about_attribution)
    val word = stringResource(R.string.about_attribution_link)
    val linkStyles =
        TextLinkStyles(
            SpanStyle(color = colors.accentText, textDecoration = TextDecoration.Underline)
        )
    val text =
        remember(sentences, word, linkStyles) { linked(sentences, word, linkStyles) { open() } }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        modifier =
            modifier.clickable(onClick = open).clearAndSetSemantics {
                this.text = text
                onClick {
                    open()
                    true
                }
                getTextLayoutResult { results -> layout?.let { results.add(it) } != null }
            },
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurface,
        onTextLayout = { layout = it },
    )
}

/** [sentences] with the first [word] linked to [SCRYFALL_URL], opened by [onLink]. */
private fun linked(
    sentences: String,
    word: String,
    styles: TextLinkStyles,
    onLink: () -> Unit,
): AnnotatedString {
    val start = sentences.indexOf(word)
    check(start >= 0) { "The attribution has no '$word' to link" }
    return buildAnnotatedString {
        append(sentences.substring(0, start))
        withLink(LinkAnnotation.Url(SCRYFALL_URL, styles) { onLink() }) { append(word) }
        append(sentences.substring(start + word.length))
    }
}

/**
 * Opens [SCRYFALL_URL] in another app. A phone with nothing that opens web links makes the handler
 * throw; the tap then does nothing rather than closing the app.
 */
private fun openQuietly(uriHandler: UriHandler) {
    try {
        uriHandler.openUri(SCRYFALL_URL)
    } catch (_: IllegalArgumentException) {
        // No app handles the link.
    }
}
