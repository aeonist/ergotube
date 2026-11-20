package app.freshtuber.ui.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.R
import app.freshtuber.player.music.MusicState
import app.freshtuber.ui.player.rememberProgress
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Height of the song line inside the dock drop. */
val MusicLineHeight = 58.dp

/**
 * The playing song inside the dock drop, in music and in video alike. Up opens the player,
 * sideways changes the song, down stops the music (with «Вернуть»). Every gesture also has a
 * button or a TalkBack action.
 */
@Composable
fun MusicLine(state: MusicState, player: Player, offline: Boolean, onOpen: () -> Unit, onToggle: () -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier,
    /** A swipe down on the line; in the dock it stops the music. */
    onSwipeDown: () -> Unit = onStop) {
    val track = state.queue.track ?: return
    val haptics = LocalHapticFeedback.current
    val progress = rememberProgress(player)
    val shift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val openLabel = stringResource(R.string.music_line_open)
    val nextLabel = stringResource(R.string.music_next)
    val previousLabel = stringResource(R.string.music_previous)
    val stopLabel = stringResource(R.string.music_stopped)
    val accent = MaterialTheme.colorScheme.primary
    val rail = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    Box(modifier.fillMaxWidth().height(MusicLineHeight)
        .semantics {
            contentDescription = "${track.title}, ${track.artist.orEmpty()}"
            onClick(openLabel) { onOpen(); true }
            customActions = listOf(CustomAccessibilityAction(nextLabel) { onNext(); true },
                CustomAccessibilityAction(previousLabel) { onPrevious(); true },
                CustomAccessibilityAction(stopLabel) { onStop(); true })
        }
        .pointerInput(track.id) {
            var drag = Offset.Zero
            detectDragGestures(
                onDragStart = { drag = Offset.Zero },
                onDrag = { change, amount -> change.consume(); drag += amount
                    if (abs(drag.x) > abs(drag.y)) scope.launch { shift.snapTo(drag.x * 0.6f) } },
                onDragEnd = {
                    val threshold = 48.dp.toPx()
                    when {
                        abs(drag.y) > abs(drag.x) && drag.y < -threshold / 2 -> { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd); onOpen() }
                        abs(drag.y) > abs(drag.x) && drag.y > threshold / 2 -> { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd); onSwipeDown() }
                        drag.x < -threshold -> { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onNext() }
                        drag.x > threshold -> { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onPrevious() }
                    }
                    scope.launch { shift.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                },
                onDragCancel = { scope.launch { shift.animateTo(0f) } },
            )
        }
        .pointerInput(track.id) { detectTapGestures { onOpen() } }) {
        // Thin progress along the drop's top edge.
        Canvas(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 22.dp)) {
            val y = size.height / 2
            drawLine(rail, Offset(0f, y), Offset(size.width, y), 2.5.dp.toPx(), StrokeCap.Round)
            val f = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
                else if (track.durationMs > 0) (state.queue.positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f
            if (f > 0f) drawLine(accent, Offset(0f, y), Offset(size.width * f, y), 2.5.dp.toPx(), StrokeCap.Round)
        }
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp, top = 4.dp).graphicsLayer { translationX = shift.value; alpha = 1f - (abs(shift.value) / 600f).coerceAtMost(0.5f) },
            verticalAlignment = Alignment.CenterVertically) {
            Cover(track.artwork, Modifier.size(40.dp), corner = 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (offline) stringResource(R.string.music_offline_note) else track.artist.orEmpty(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (state.playing) R.string.player_pause else R.string.player_play))
            }
        }
    }
}
