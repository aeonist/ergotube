package app.freshtuber.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.R
import app.freshtuber.data.extras.SkipSegment
import app.freshtuber.player.PlayerState
import app.freshtuber.ui.feed.messageRes
import app.freshtuber.ui.theme.BrandColors
import app.freshtuber.ui.util.formatDuration

/**
 * YouTube's own controls on the video, for the classic layout: a tap shows them over a dim
 * layer (collapse, captions and settings on top; previous, play/pause and next in the middle;
 * time, full screen and the seek bar at the bottom), they hide after three seconds, and a
 * double tap on a side skips 10 s. Used both on the watch page and in full screen.
 */
@Composable
fun ClassicVideoControls(
    player: Player,
    state: PlayerState,
    fullscreen: Boolean,
    segments: List<SkipSegment>,
    hasPrevious: Boolean,
    hasNext: Boolean,
    captionsOn: Boolean,
    captionsAvailable: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    onCaptions: () -> Unit,
    onMore: () -> Unit,
    onQuality: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    /** Below the fold of a half-open phone the controls stay up: the video is above. */
    alwaysShown: Boolean = false,
) {
    val progress = rememberProgress(player)
    val hide = rememberAutoHide(3_000)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val live = state.nowPlaying?.isLive == true
    hide.Run(keepVisible = alwaysShown || scrub != null || (!progress.isPlaying && !progress.buffering && state.loadingId == null))
    val burst = remember { SeekBurst(windowMs = 900) }
    var hint by remember { mutableStateOf<Int?>(null) }
    var hintId by remember { mutableIntStateOf(0) }
    LaunchedEffect(hintId) { if (hint != null) { kotlinx.coroutines.delay(800); hint = null } }
    val spans = remember(segments, progress.durationMs) { SegmentMarks.spans(segments, progress.durationMs) }
    val togglePlay = {
        if (player.playbackState == Player.STATE_ENDED) { player.seekTo(0); player.play() }
        else if (player.isPlaying) player.pause() else player.play()
        hide.poke()
    }

    Box(modifier.fillMaxSize().pointerInput(player, live) {
        detectTapGestures(
            onTap = { hide.toggle() },
            onDoubleTap = { at ->
                val side = if (live) 0 else if (at.x < size.width * 0.4f) -1 else if (at.x > size.width * 0.6f) 1 else 0
                if (side == 0) { hide.toggle(); return@detectTapGestures }
                val total = burst.add(10 * side, System.currentTimeMillis())
                val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                player.seekTo((player.currentPosition + 10_000L * side).coerceIn(0, duration))
                hint = total; hintId++
            },
        )
    }) {
        val loading = state.loadingId != null || progress.buffering
        AnimatedVisibility(hide.visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(220))) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
                // Top row.
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 12.dp, vertical = 8.dp) else Modifier.padding(4.dp)),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (!fullscreen) OverlayIcon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.player_minimize), onClick = onMinimize)
                    if (fullscreen && title != null) Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    else Spacer(Modifier.weight(1f))
                    if (captionsAvailable) OverlayIcon(if (captionsOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled,
                        stringResource(R.string.player_subtitles)) { onCaptions(); hide.poke() }
                    OverlayIcon(Icons.Rounded.Settings, stringResource(R.string.player_more), onClick = onMore)
                }
                // Middle row.
                if (!loading && state.error == null) Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 56.dp else 36.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OverlayIcon(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), size = 36.dp, enabled = hasPrevious) { onPrevious(); hide.poke() }
                    OverlayIcon(if (progress.ended) Icons.Rounded.Replay else if (progress.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (progress.isPlaying) R.string.player_pause else R.string.player_play), size = 52.dp, tag = "classic:play", onClick = togglePlay)
                    OverlayIcon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), size = 36.dp, enabled = hasNext) { onNext(); hide.poke() }
                }
                // Bottom row: time and full screen.
                val shown = scrub?.let { (it * progress.durationMs).toLong() } ?: progress.positionMs
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars)).padding(start = 16.dp, end = 8.dp, bottom = 30.dp)
                        else Modifier.padding(start = 12.dp, end = 4.dp, bottom = 10.dp)),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (live) stringResource(R.string.search_live) else "${formatDuration(shown / 1000)} / ${formatDuration(progress.durationMs / 1000)}",
                        color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    OverlayIcon(Icons.Rounded.HighQuality, stringResource(R.string.player_quality), tag = "player:quality", onClick = onQuality)
                    OverlayIcon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        stringResource(if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen), tag = "classic:fullscreen", onClick = onFullscreen)
                }
            }
        }
        when {
            state.error != null -> Column(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(state.error.messageRes()), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        hint?.let { total ->
            Text(stringResource(R.string.player_seconds, signedSeek(total.toLong())), color = Color.White, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(if (total > 0) Alignment.CenterEnd else Alignment.CenterStart).padding(32.dp)
                    .background(Color(0x99000000), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        // The seek bar: thin on the bottom edge while the video plays, thicker and draggable with
        // the controls. In full screen it hides with them.
        if (!live && progress.durationMs > 0 && (hide.visible || !fullscreen)) {
            val fraction = scrub ?: (progress.positionMs.toFloat() / progress.durationMs)
            val active = hide.visible
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars)).padding(horizontal = 16.dp, vertical = 8.dp) else Modifier)
                .height(if (active) 24.dp else 3.dp)
                .then(if (active) Modifier.testTag("classic:seek").pointerInput(progress.durationMs) {
                    fun at(x: Float) = (x / size.width).coerceIn(0f, 1f)
                    detectHorizontalDragGestures(
                        onDragStart = { scrub = at(it.x) },
                        onDragEnd = { scrub?.let { player.seekTo((it * player.duration.coerceAtLeast(0)).toLong()) }; scrub = null; hide.poke() },
                        onDragCancel = { scrub = null },
                    ) { change, _ -> scrub = at(change.position.x) }
                }.pointerInput(progress.durationMs) {
                    detectTapGestures { player.seekTo((it.x / size.width * player.duration.coerceAtLeast(0)).toLong()); hide.poke() }
                } else Modifier)) {
                SeekLine(fraction, (progress.bufferedMs.toFloat() / progress.durationMs).coerceIn(0f, 1f), spans, thick = active,
                    dot = active, grown = scrub != null, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** A straight seek line: buffered and played parts, SponsorBlock marks and an optional dot. */
@Composable
fun SeekLine(fraction: Float, buffered: Float, spans: List<Triple<Float, Float, Color>>, thick: Boolean, dot: Boolean, grown: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val h = (if (thick) 3.dp else 3.dp).toPx()
        val y = if (thick) size.height / 2 else size.height - h / 2
        val f = fraction.coerceIn(0f, 1f)
        drawRect(Color.White.copy(alpha = 0.3f), Offset(0f, y - h / 2), Size(size.width, h))
        drawRect(Color.White.copy(alpha = 0.45f), Offset(0f, y - h / 2), Size(size.width * buffered, h))
        drawRect(BrandColors.Red, Offset(0f, y - h / 2), Size(size.width * f, h))
        spans.forEach { (a, b, color) -> drawRect(color, Offset(size.width * a, y - h / 2), Size(size.width * (b - a), h)) }
        if (dot) drawCircle(BrandColors.Red, (if (grown) 9.dp else 6.dp).toPx(), Offset(size.width * f, y))
    }
}

@Composable
private fun OverlayIcon(icon: ImageVector, label: String, size: Dp = 26.dp, enabled: Boolean = true, tag: String? = null, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size + 22.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Icon(icon, label, Modifier.size(size), tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f))
    }
}
