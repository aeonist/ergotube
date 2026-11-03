package app.freshtuber.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.R
import app.freshtuber.ui.screens.player.VideoUiState
import kotlin.math.*

/** Persistent playback controls around the fullscreen button; no menu-opening tap. */
@Composable
fun PortraitControls(player: Player, state: VideoUiState, left: Boolean, pinned: Boolean, hasNext: Boolean,
    onNext: () -> Unit, onFullscreen: () -> Unit, onPin: () -> Unit, onSettings: (Offset) -> Unit,
    onGroup: (VideoActionGroup) -> Unit, onGroupPosition: (VideoActionGroup, Offset) -> Unit) {
    val progress = rememberProgress(player)
    val density = LocalDensity.current
    val playLabel = stringResource(if (progress.isPlaying) R.string.player_pause else R.string.player_play)
    var settingsPosition by remember { mutableStateOf(Offset.Zero) }
    val items = listOf(
        Triple(Icons.Rounded.Replay10, stringResource(R.string.player_back_10), { player.seekBack() }),
        Triple(if (progress.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, playLabel, {
            if (progress.ended) { player.seekTo(0); player.play() } else if (player.isPlaying) player.pause() else player.play()
        }),
        Triple(Icons.Rounded.Forward10, stringResource(R.string.player_forward_10), { player.seekForward() }),
        Triple(Icons.Rounded.SkipNext, stringResource(R.string.player_next), onNext),
        Triple(Icons.Rounded.Settings, stringResource(R.string.player_settings), { onSettings(settingsPosition) }),
    )
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        val anchorX = if (left) 36f else maxWidth.value - 36f
        val anchorY = maxHeight.value - 36f
        items.forEachIndexed { index, (icon, label, action) ->
            val angle = index * PI.toFloat() / 8
            val x = anchorX + (if (left) 1 else -1) * 144f * cos(angle)
            val y = anchorY - 144f * sin(angle)
            IconButton(onClick = { action() }, enabled = (index != 3 || hasNext) && (index !in listOf(0, 2) || !player.isCurrentMediaItemLive),
                modifier = Modifier.offset { IntOffset(with(density) { (x - 24).dp.roundToPx() }, with(density) { (y - 24).dp.roundToPx() }) }
                    .size(48.dp).then(if (index == 4) Modifier.onGloballyPositioned { settingsPosition = it.positionOnScreen() + Offset(it.size.width / 2f, it.size.height / 2f) } else Modifier)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) { Icon(icon, label) }
        }
        Column(Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = if (left) Alignment.Start else Alignment.End) {
            if (state.details != null) VideoActionGroups(state, onGroup, onPosition = onGroupPosition)
            Spacer(Modifier.height(160.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (left) FullscreenAnchor(onFullscreen)
                ActionIcon(if (pinned) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    stringResource(if (pinned) R.string.player_unpin else R.string.player_pin), selected = pinned, onClick = onPin)
                if (!left) FullscreenAnchor(onFullscreen)
            }
        }
    }
}

@Composable
private fun FullscreenAnchor(onClick: () -> Unit) {
    FilledTonalIconButton(onClick, Modifier.size(56.dp)) { Icon(Icons.Rounded.Fullscreen, stringResource(R.string.player_fullscreen)) }
}
