package app.freshtuber.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.R
import app.freshtuber.player.NowPlaying
import app.freshtuber.ui.theme.BrandColors

/** The video keeps playing while the user browses; this bar above the dock brings it back. */
@Composable
fun MiniPlayer(
    player: Player,
    nowPlaying: NowPlaying,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    left: Boolean = false,
) {
    val progress = rememberProgress(player)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxWidth()) {
        val fraction = if (progress.durationMs > 0) progress.positionMs.toFloat() / progress.durationMs else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(BrandColors.Red))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .pointerInput(onOpen) { detectVerticalDragGestures { change, drag -> if (drag < -12f) { change.consume(); onOpen() } } }
                .clickable(onClick = onOpen)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play sits on the working-hand side, close on the far side.
            val parts: List<@Composable RowScope.() -> Unit> = listOf(
                { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.player_close)) } },
                { VideoSurface(player, Modifier.height(43.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))) },
                { Spacer(Modifier.width(10.dp)) },
                {
                    Column(Modifier.weight(1f)) {
                        Text(nowPlaying.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        nowPlaying.channelName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                {
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                        Icon(
                            if (progress.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (progress.isPlaying) R.string.player_pause else R.string.player_play),
                        )
                    }
                },
            )
            (if (left) parts.reversed() else parts).forEach { it() }
        }
    }
}
}
