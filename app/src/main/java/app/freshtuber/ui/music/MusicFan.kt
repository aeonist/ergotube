package app.freshtuber.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.R
import app.freshtuber.data.downloads.Download
import app.freshtuber.data.downloads.DownloadStatus
import app.freshtuber.player.Chapter
import app.freshtuber.player.music.MusicState
import app.freshtuber.player.music.RepeatMode
import app.freshtuber.ui.player.rememberProgress
import app.freshtuber.ui.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The music fan's quarter circle, in dp, anchored in the bottom corner under the thumb (right,
 * or left for the left hand). Same conventions as the fullscreen video fan: [u] runs 0..1 along
 * the quarter, from the bottom edge up on the right, from the side edge across on the left.
 */
internal class MusicFanGeometry(val width: Float, val left: Boolean) {
    val radius = (width * 0.6f).coerceIn(220f, 262f)
    /** The time arc. */
    val track = radius - 22f
    /** Previous, play and next. */
    val main = radius * 0.53f
    /** The named buttons. */
    val outer = radius + 40f
    /** Height of the whole fan, with room for the top button's name. */
    val height = outer + 46f

    fun position(r: Float, u: Float): Offset {
        val a = u * PI.toFloat() / 2
        return if (left) Offset(r * sin(a), height - r * cos(a)) else Offset(width - r * cos(a), height - r * sin(a))
    }

    fun polar(x: Float, y: Float): Pair<Float, Float> {
        val dx = if (left) x else width - x
        val dy = height - y
        val angle = if (left) atan2(dx, dy) else atan2(dy, dx)
        return hypot(dx, dy) to (angle / (PI.toFloat() / 2)).coerceIn(0f, 1f)
    }

    fun onTrack(r: Float): Boolean = abs(r - track) <= TRACK_BAND

    companion object {
        const val U0 = 0.05f
        const val U1 = 0.95f
        const val TRACK_BAND = 30f
        fun toArc(fraction: Float): Float = U0 + fraction.coerceIn(0f, 1f) * (U1 - U0)
        fun fromArc(u: Float): Float = ((u - U0) / (U1 - U0)).coerceIn(0f, 1f)
        /** Evenly spread positions on the outer ring. */
        fun outerSlots(count: Int): List<Float> = if (count <= 1) listOf(0.5f) else List(count) { 0.1f + it * 0.8f / (count - 1) }
    }
}

/**
 * The music remote as a fan in the bottom corner, like the fullscreen video player: everything
 * lies around the thumb at one reach. The time arc runs along the fan (drag or tap on it to
 * seek); inside it previous, play and next; outside it the named buttons: shuffle, repeat,
 * download, lyrics, queue and «Ещё». The time stands big in the free corner across.
 */
@Composable
internal fun MusicFan(
    state: MusicState,
    player: Player,
    left: Boolean,
    download: Download?,
    youTube: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onDownload: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onMore: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit,
    onScan: (Boolean) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    // The fan has no drop of its own; the light stays open around it.
    LaunchedEffect(light) { light?.setDrop(1, null, 0f) }
    val progress = rememberProgress(player)
    val duration = progress.durationMs.takeIf { it > 0 } ?: state.queue.track?.durationMs ?: 0L
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val shownMs = scrubMs ?: progress.positionMs.takeIf { progress.durationMs > 0 } ?: state.queue.positionMs
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val queue = state.queue
    val accent = MaterialTheme.colorScheme.primary
    val rail = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val wash = MaterialTheme.colorScheme.surface

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val geo = remember(maxWidth, left) { MusicFanGeometry(maxWidth.value, left) }
        Box(Modifier.fillMaxWidth().height(geo.height.dp).pointerInput(geo, duration) {
            if (duration <= 0) return@pointerInput
            // Drag or tap on the time arc: the song follows the finger along it.
            awaitEachGesture {
                val down = awaitFirstDown()
                val (r0, u0) = geo.polar(down.position.x / density, down.position.y / density)
                if (!geo.onTrack(r0)) return@awaitEachGesture
                down.consume()
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                var target = (MusicFanGeometry.fromArc(u0) * duration).toLong()
                scrubMs = target
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    val u = geo.polar(change.position.x / density, change.position.y / density).second
                    target = (MusicFanGeometry.fromArc(u) * duration).toLong()
                    scrubMs = target
                }
                onSeekTo(target)
                scrubMs = null
            }
        }) {
            // A soft wash out of the corner, the arc with what has played, and the handle.
            Canvas(Modifier.fillMaxSize()) {
                val corner = Offset(if (left) 0f else size.width, size.height)
                val glow = (geo.outer + 50f).dp.toPx()
                drawCircle(Brush.radialGradient(0f to wash.copy(alpha = 0.5f), 0.75f to wash.copy(alpha = 0.32f), 1f to Color.Transparent,
                    center = corner, radius = glow), glow, corner)
                val r = geo.track.dp.toPx()
                val box = Rect(corner - Offset(r, r), Size(2 * r, 2 * r))
                val base = if (left) 270f else 180f
                val u0 = MusicFanGeometry.U0
                val u1 = MusicFanGeometry.U1
                val width = (if (scrubMs != null) 7.dp else 5.dp).toPx()
                drawArc(rail, base + u0 * 90f, (u1 - u0) * 90f, false, box.topLeft, box.size, style = Stroke(width, cap = StrokeCap.Round))
                val fraction = if (duration > 0) shownMs.toFloat() / duration else 0f
                val uf = MusicFanGeometry.toArc(fraction)
                if (uf > u0) drawArc(accent, base + u0 * 90f, (uf - u0) * 90f, false, box.topLeft, box.size, style = Stroke(width, cap = StrokeCap.Round))
                // Chapter cuts in long mixes.
                if (duration > 0) state.chapters.drop(1).forEach { c ->
                    val p = geo.position(geo.track, MusicFanGeometry.toArc(c.startSeconds * 1000f / duration))
                    drawCircle(wash, 3.dp.toPx(), Offset(p.x.dp.toPx(), p.y.dp.toPx()))
                }
                val p = geo.position(geo.track, uf)
                drawCircle(accent, (if (scrubMs != null) 12.dp else 8.dp).toPx(), Offset(p.x.dp.toPx(), p.y.dp.toPx()))
            }

            fun Modifier.at(p: Offset, w: Float, h: Float = w) = offset { IntOffset(((p.x - w / 2).dp).roundToPx(), ((p.y - h / 2).dp).roundToPx()) }

            // Inside the arc: previous, play, next.
            val nextLabel = stringResource(R.string.music_next)
            val previousLabel = stringResource(R.string.music_previous)
            Box(Modifier.at(geo.position(geo.main, 0.14f), 58f).size(58.dp).clip(CircleShape)
                .semantics { contentDescription = previousLabel }.clickable(onClick = onPrevious), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(36.dp))
            }
            FilledIconButton(onClick = onToggle, modifier = Modifier.at(geo.position(geo.main, 0.5f), 78f).size(78.dp), shape = CircleShape) {
                Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (state.playing) R.string.player_pause else R.string.player_play), Modifier.size(42.dp))
            }
            // Hold Next to scan forward.
            Box(Modifier.at(geo.position(geo.main, 0.86f), 58f).size(58.dp).clip(CircleShape).semantics { contentDescription = nextLabel }.pointerInput(Unit) {
                detectTapGestures(onPress = {
                    var scanning = false
                    val hold = scope.launch { delay(380); scanning = true; onScan(true) }
                    val released = tryAwaitRelease()
                    hold.cancel()
                    if (scanning) onScan(false) else if (released) onNext()
                })
            }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(36.dp)) }

            // Outside the arc: the named buttons, spread evenly along the ring.
            val status = download?.state
            val repeatLabel = stringResource(if (queue.repeat == RepeatMode.One) R.string.music_repeat_one_short else R.string.music_repeat)
            val repeatDescription = stringResource(when (queue.repeat) { RepeatMode.Off -> R.string.music_repeat_off; RepeatMode.All -> R.string.music_repeat_all; RepeatMode.One -> R.string.music_repeat_one })
            val downloadButton: FanButton = when {
                !youTube -> FanButton(Icons.Rounded.PhoneAndroid, stringResource(R.string.music_on_phone_short), enabled = false)
                status == DownloadStatus.Complete -> FanButton(Icons.Rounded.DownloadDone, stringResource(R.string.music_downloaded), selected = true, enabled = false)
                status != null && status != DownloadStatus.Failed -> FanButton(Icons.Rounded.Downloading, stringResource(R.string.music_downloading_short), selected = true, enabled = false)
                else -> FanButton(Icons.Rounded.Download, stringResource(R.string.music_download), onClick = onDownload)
            }
            // Along the ring from its start to its end.
            val ring = listOf(
                FanButton(Icons.Rounded.MoreHoriz, stringResource(R.string.music_more), onClick = onMore),
                downloadButton,
                FanButton(Icons.AutoMirrored.Rounded.Notes, stringResource(R.string.music_lyrics_short), onClick = onLyrics),
                FanButton(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.music_queue), onClick = onQueue),
                FanButton(if (queue.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, repeatLabel,
                    selected = queue.repeat != RepeatMode.Off, description = repeatDescription, onClick = onRepeat),
                FanButton(Icons.Rounded.Shuffle, stringResource(R.string.music_shuffle_short), selected = queue.shuffle, onClick = onShuffle),
            )
            val slots = MusicFanGeometry.outerSlots(ring.size)
            ring.forEachIndexed { i, button ->
                val p = geo.position(geo.outer, slots[i])
                // The icon sits on the ring; its name hangs under it.
                Column(Modifier.offset { IntOffset(((p.x - 34f).dp).roundToPx(), ((p.y - 23f).dp).roundToPx()) }.width(68.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val container = when {
                        button.selected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
                    }
                    val content = when {
                        button.selected -> MaterialTheme.colorScheme.onPrimaryContainer
                        button.enabled -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(onClick = button.onClick, enabled = button.enabled, shape = CircleShape, color = container, contentColor = content,
                        modifier = Modifier.size(46.dp).semantics {
                            selected = button.selected
                            contentDescription = button.description ?: button.label
                        }) {
                        Box(contentAlignment = Alignment.Center) { Icon(button.icon, null, Modifier.size(22.dp)) }
                    }
                    Text(button.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }

            // The time, big, in the free corner across from the fan; it follows the finger while seeking.
            Column(Modifier.align(if (left) Alignment.TopEnd else Alignment.TopStart).padding(horizontal = 22.dp, vertical = 6.dp),
                horizontalAlignment = if (left) Alignment.End else Alignment.Start) {
                val tabular = app.freshtuber.ui.theme.TabularNumbers
                Text(formatDuration(shownMs / 1000), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = tabular),
                    color = if (scrubMs != null) accent else MaterialTheme.colorScheme.onSurface)
                if (duration > 0) Text(formatDuration(duration / 1000), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = tabular),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Chapter.at(state.chapters, shownMs / 1000)?.title?.let {
                    Text(it, Modifier.widthIn(max = 140.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private class FanButton(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val description: String? = null,
    val onClick: () -> Unit = {},
)
