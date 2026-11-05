package app.freshtuber.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.R
import app.freshtuber.player.PlayerState
import app.freshtuber.ui.feed.messageRes
import app.freshtuber.ui.theme.BrandColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Brief icon in the middle of the video after a tap. */
class VideoFlash {
    var icon by mutableStateOf<ImageVector?>(null)
        private set
    var id by mutableIntStateOf(0)
        private set
    fun show(icon: ImageVector) { this.icon = icon; id++ }
    fun clear() { icon = null }
}

/**
 * Gestures on the video itself, as in YouTube: a tap leaves playback unchanged, double tap on a side skips 10 s and
 * repeated taps add up, swipe down minimizes. Loading and errors are drawn here too.
 */
@Composable
fun PortraitVideoLayer(player: Player, state: PlayerState, onMinimize: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val progress = rememberProgress(player)
    val scope = rememberCoroutineScope()
    val flash = remember { VideoFlash() }
    val burst = remember { SeekBurst(windowMs = 900) }
    var hint by remember { mutableStateOf<Int?>(null) }
    var hintId by remember { mutableIntStateOf(0) }
    LaunchedEffect(flash.id) { if (flash.icon != null) { delay(380); flash.clear() } }
    LaunchedEffect(hintId) { if (hint != null) { delay(800); hint = null } }
    val live = state.nowPlaying?.isLive == true
    Box(modifier.fillMaxSize().pointerInput(player, live) {
        var pending: Job? = null
        var lastSide = 0
        var lastAt = 0L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var end = down
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                // A second pointer belongs to zoom, never to single-tap action or minimize.
                if (change.isConsumed || currentEvent.changes.size > 1) { pending?.cancel(); return@awaitEachGesture }
                end = change
                if (!change.pressed) break
            }
            val dx = (end.position.x - down.position.x) / density
            val dy = (end.position.y - down.position.y) / density
            if (dy > 50 && abs(dy) > abs(dx)) { pending?.cancel(); onMinimize(); return@awaitEachGesture }
            if (abs(dx) >= 10 || abs(dy) >= 10) return@awaitEachGesture
            val fx = end.position.x / size.width
            val side = if (live) 0 else if (fx < 0.33f) -1 else if (fx > 0.67f) 1 else 0
            val now = System.currentTimeMillis()
            if (side != 0 && lastSide == side && now - lastAt < 300) {
                pending?.cancel()
                lastAt = now
                val total = burst.add(10 * side, now)
                val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                player.seekTo((player.currentPosition + 10_000L * side).coerceIn(0, duration))
                hint = total
                hintId++
                return@awaitEachGesture
            }
            pending?.cancel()
            if (side == 0) { lastSide = 0; return@awaitEachGesture }
            lastSide = side
            lastAt = now
            pending = scope.launch { delay(300); lastSide = 0 }
        }
    }) {
        val loading = state.loadingId != null || progress.buffering
        when {
            state.error != null -> Column(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(state.error.messageRes()), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        AnimatedVisibility(flash.icon != null, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            flash.icon?.let { Icon(it, null, Modifier.size(68.dp).background(Color(0x80000000), CircleShape).padding(14.dp), tint = Color.White) }
        }
        hint?.let { total ->
            Text(stringResource(R.string.player_seconds, signedSeek(total.toLong())), color = Color.White, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(if (total > 0) Alignment.CenterEnd else Alignment.CenterStart).padding(24.dp)
                    .background(Color(0x99000000), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (!live && progress.durationMs > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x40FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)).background(BrandColors.Red))
        }
    }
}
