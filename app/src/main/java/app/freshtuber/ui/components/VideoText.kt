package app.freshtuber.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import app.freshtuber.domain.model.TextLink
import app.freshtuber.innertube.parse.TextLinks

/** Native selection and annotated links coexist; timestamps seek without changing play state. */
@Composable
fun VideoText(text: String, links: List<TextLink> = emptyList(), videoId: String? = null, onTimestamp: (Long) -> Unit,
    modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip,
    onOpenUrl: ((String) -> Unit)? = null) {
    val uriHandler = LocalUriHandler.current
    val seek by rememberUpdatedState(onTimestamp)
    val open by rememberUpdatedState(onOpenUrl)
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, links, videoId, linkColor) {
        buildAnnotatedString {
            append(text)
            TextLinks.scan(text, videoId, links).forEach { span ->
                addLink(LinkAnnotation.Clickable("link:${span.start}", TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))) {
                    span.seconds?.let { seek(it * 1000) } ?: span.url?.let { url ->
                        if (open != null) open?.invoke(url) else runCatching { uriHandler.openUri(url) }
                    }
                }, span.start, span.end)
            }
        }
    }
    SelectionContainer { Text(annotated, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = overflow) }
}
