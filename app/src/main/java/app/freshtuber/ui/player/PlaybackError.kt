package app.freshtuber.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.freshtuber.R
import app.freshtuber.ui.feed.LoadError
import app.freshtuber.ui.feed.messageRes

@Composable
internal fun PlaybackError(error: LoadError, onRetry: () -> Unit, onSignIn: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .94f)), contentAlignment = Alignment.Center) {
        Layout(content = {
            Text(stringResource(error.messageRes()), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            if (error == LoadError.SessionExpired && onSignIn != null) {
                TextButton(onClick = onSignIn) { Text(stringResource(R.string.welcome_sign_in)) }
            }
        }, modifier = Modifier.fillMaxSize()) { children, constraints ->
            val available = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = constraints.maxHeight / 2)
            val message = children[0].measure(available)
            val retry = children[1].measure(available)
            val signIn = children.getOrNull(2)?.measure(available)
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val top = (height - retry.height) / 2
            layout(width, height) {
                message.placeRelative((width - message.width) / 2, (top - 12.dp.roundToPx() - message.height).coerceAtLeast(0))
                retry.placeRelative((width - retry.width) / 2, top)
                signIn?.placeRelative((width - signIn.width) / 2, top + retry.height)
            }
        }
    }
}
