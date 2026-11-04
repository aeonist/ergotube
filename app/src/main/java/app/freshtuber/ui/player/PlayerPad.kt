package app.freshtuber.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.R
import app.freshtuber.player.Chapter
import app.freshtuber.ui.theme.BrandColors
import app.freshtuber.ui.util.formatDuration
import kotlinx.coroutines.delay

/** What the pad shows outside itself: the scrub preview, the bubble and the 2× hold. */
class PadUi {
    /** Position being scrubbed to, or null when not scrubbing. */
    var scrubMs by mutableStateOf<Long?>(null)
    /** Null when dragging along the track (no fine mode), else whether fine mode is on. */
    var fine by mutableStateOf<Boolean?>(null)
    var bubble by mutableStateOf<String?>(null)
        private set
    var bubbleKeep by mutableStateOf(false)
        private set
    var bubbleId by mutableIntStateOf(0)
        private set
    var boost by mutableStateOf(false)
    var active by mutableStateOf(false)
    val burst = SeekBurst()

    fun say(text: String, keep: Boolean = false) { bubble = text; bubbleKeep = keep; bubbleId++ }
    fun hush() { bubble = null }
}

val PlaybackSpeeds = listOf(1f, 1.25f, 1.5f, 2f)

fun speedLabel(speed: Float): String = if (speed == 1f) "1×" else "${speed.toString().trimEnd('0').trimEnd('.').replace('.', ',')}×"

/** Seeks by [seconds] and reports the running total in the bubble ("−20 с → 4:10"). */
fun PadUi.skip(player: Player, seconds: Int, secondsLabel: (String) -> String) {
    val total = burst.add(seconds, System.currentTimeMillis())
    val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
    val target = (player.currentPosition + seconds * 1000L).coerceIn(0, duration)
    player.seekTo(target)
    say("${secondsLabel(signedSeek(total.toLong()))}  →  ${formatDuration(target / 1000)}")
}

/**
 * The video's own controls, moved down under the thumb. Every gesture also has a visible button.
 * Tap: middle pauses, sides skip 10 s and add up. Hold: 2× while held. Drag sideways: scrub,
 * lift the finger higher to scrub finely. Drag along the bar: jump. Swipe up: full screen.
 * Swipe down: minimize.
 */
@Composable
fun PlayerPad(
    player: Player,
    ui: PadUi,
    speed: Float,
    captionsOn: Boolean,
    captionsAvailable: Boolean,
    chapters: List<Chapter>,
    live: Boolean,
    left: Boolean,
    hasNext: Boolean,
    onNext: () -> Unit,
    onMinimize: () -> Unit,
    onFullscreen: () -> Unit,
    onSpeed: () -> Unit,
    onCaptions: () -> Unit,
    onBoost: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = rememberProgress(player)
    val haptics = LocalHapticFeedback.current
    val secondsFormat = stringResource(R.string.player_seconds)
    val holdText = stringResource(R.string.player_hold_2x)
    val buttons = remember { mutableStateMapOf<String, Rect>() }
    var padCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var mainBounds by remember { mutableStateOf(Rect.Zero) }
    var trackBounds by remember { mutableStateOf(Rect.Zero) }
    fun Modifier.button(key: String) = onGloballyPositioned { buttons[key] = it.boundsInRoot() }
    val durationMs = progress.durationMs
    val shownMs = ui.scrubMs ?: progress.positionMs
    val chapter = Chapter.at(chapters, shownMs / 1000)
    LaunchedEffect(ui.bubbleId) { if (ui.bubble != null && !ui.bubbleKeep) { delay(800); ui.hush() } }

    val toggle = {
        if (progress.ended) { player.seekTo(0); player.play() } else if (player.isPlaying) player.pause() else player.play()
    }
    // While buffering after a seek the video is still meant to play; show that, not ▶.
    val playing = progress.isPlaying || (progress.buffering && player.playWhenReady)
    val playLabel = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    val backLabel = stringResource(R.string.player_back_10)
    val forwardLabel = stringResource(R.string.player_forward_10)

    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(22.dp))
        .background(if (ui.active) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh)
        .onGloballyPositioned { padCoords = it }
        .pointerInput(player, live, durationMs) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val coords = padCoords ?: return@awaitEachGesture
                val rootDown = coords.localToRoot(down.position)
                if (buttons.values.any { it.contains(rootDown) }) return@awaitEachGesture
                val onTrack = rootDown.y >= trackBounds.top - 6.dp.toPx()
                val onMain = !onTrack && rootDown.y in mainBounds.top..mainBounds.bottom
                val width = size.width.toFloat()
                val start = down.position
                val startMs = player.currentPosition
                var last = start
                var move: PadMove? = null
                var lifted: PointerInputChange? = null
                ui.active = true
                // Phase 1: tap, hold or the start of a drag.
                val decided = withTimeoutOrNull(if (onMain && !live) PadGestures.HOLD_MS else Long.MAX_VALUE) {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        if (!change.pressed) { lifted = change; return@withTimeoutOrNull true }
                        val d = change.position - start
                        move = PadGestures.decide(d.x / density, d.y / density)
                        if (move == PadMove.Seek && live) move = null
                        if (move != null) { change.consume(); last = change.position; return@withTimeoutOrNull true }
                    }
                }
                if (decided == null) {
                    // Held still: 2× while the finger stays down.
                    ui.boost = true
                    onBoost(true)
                    ui.say(holdText, keep = true)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                    }
                    ui.boost = false
                    onBoost(false)
                    ui.hush()
                    ui.active = false
                    return@awaitEachGesture
                }
                val up = lifted
                if (up != null && move == null) {
                    ui.active = false
                    up.consume()
                    val rootUp = coords.localToRoot(up.position)
                    when {
                        onTrack && !live && durationMs > 0 -> {
                            val fraction = ((rootUp.x - trackBounds.left) / trackBounds.width).coerceIn(0f, 1f)
                            val target = (fraction * durationMs).toLong()
                            player.seekTo(target)
                            ui.say("→ ${formatDuration(target / 1000)}${Chapter.at(chapters, target / 1000)?.let { " · ${it.title}" }.orEmpty()}")
                        }
                        onMain -> when (if (live) PadZone.Play else PadGestures.zone(up.position.x / width)) {
                            PadZone.Back -> ui.skip(player, -10) { secondsFormat.format(it) }
                            PadZone.Forward -> ui.skip(player, 10) { secondsFormat.format(it) }
                            PadZone.Play -> toggle()
                        }
                    }
                    return@awaitEachGesture
                }
                // Phase 2: a drag.
                var fine = false
                var scrubSeconds = startMs / 1000f
                val durationSeconds = durationMs / 1000f
                val liftPx = Scrub.FINE_LIFT_DP * density
                if (move == PadMove.Seek) ui.hush()
                var endY = last.y
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    endY = change.position.y
                    if (move == PadMove.Seek) {
                        if (onTrack) {
                            val rootX = coords.localToRoot(change.position).x
                            scrubSeconds = ((rootX - trackBounds.left) / trackBounds.width).coerceIn(0f, 1f) * durationSeconds
                            ui.fine = null
                        } else {
                            val nowFine = Scrub.isFine(start.y, change.position.y, liftPx)
                            if (nowFine != fine) { fine = nowFine; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                            scrubSeconds = Scrub.step(scrubSeconds, change.position.x - last.x, width, durationSeconds, fine)
                            ui.fine = fine
                        }
                        ui.scrubMs = (scrubSeconds * 1000).toLong()
                    }
                    last = change.position
                    if (!change.pressed) break
                }
                ui.active = false
                if (move == PadMove.Seek) {
                    val target = (scrubSeconds * 1000).toLong()
                    player.seekTo(target)
                    ui.scrubMs = null
                    ui.fine = null
                    ui.say("${signedSeek(target / 1000 - startMs / 1000)}  →  ${formatDuration(target / 1000)}")
                } else if (move == PadMove.Vertical) {
                    val dy = (endY - start.y) / density
                    if (dy < -PadGestures.SWIPE_DP) onFullscreen() else if (dy > PadGestures.SWIPE_DP) onMinimize()
                }
            }
        }) {
        Column(Modifier.fillMaxWidth()) {
            // Top: minimize far from the hand, captions and speed under it.
            Row(Modifier.fillMaxWidth().height(44.dp).padding(start = 6.dp, end = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val parts: List<@Composable RowScope.() -> Unit> = listOf(
                    { PadButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.player_minimize), Modifier.button("min"), onClick = onMinimize) },
                    { Spacer(Modifier.weight(1f)) },
                    { if (captionsAvailable) PadButton(if (captionsOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled,
                        stringResource(R.string.player_subtitles), Modifier.button("cc"), selected = captionsOn, onClick = onCaptions) },
                    { if (!live) SpeedChip(if (ui.boost) "2×" else speedLabel(speed), selected = ui.boost || speed != 1f, Modifier.button("speed"), onClick = onSpeed) },
                )
                (if (left) parts.reversed() else parts).forEach { it() }
            }
            Row(Modifier.fillMaxWidth().height(64.dp).onGloballyPositioned { mainBounds = it.boundsInRoot() }) {
                PadZoneIcon(Icons.Rounded.Replay10, backLabel, Modifier.weight(1f), enabled = !live) { ui.skip(player, -10) { secondsFormat.format(it) } }
                PadZoneIcon(if (progress.ended) Icons.Rounded.Replay else if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    playLabel, Modifier.weight(1.2f), big = true, onClick = toggle)
                PadZoneIcon(Icons.Rounded.Forward10, forwardLabel, Modifier.weight(1f), enabled = !live) { ui.skip(player, 10) { secondsFormat.format(it) } }
            }
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val parts: List<@Composable RowScope.() -> Unit> = listOf(
                    {
                        Row(Modifier.weight(1f).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (live) Text(stringResource(R.string.search_live), color = Color.White, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.background(BrandColors.Red, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                            else Text("${formatDuration(shownMs / 1000)} / ${formatDuration(durationMs / 1000)}", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium, maxLines = 1)
                            chapter?.let { Text(" · ${it.title}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    },
                    { PadButton(Icons.Rounded.SkipNext, stringResource(R.string.player_next), Modifier.button("next"), enabled = hasNext, onClick = onNext) },
                    { PadButton(Icons.Rounded.Fullscreen, stringResource(R.string.player_fullscreen), Modifier.button("fs"), onClick = onFullscreen) },
                )
                (if (left) parts.reversed() else parts).forEach { it() }
            }
            Track(if (durationMs > 0) shownMs.toFloat() / durationMs else 0f, chapters, durationMs, scrubbing = ui.scrubMs != null,
                live = live, modifier = Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 18.dp)
                    .onGloballyPositioned { trackBounds = it.boundsInRoot() })
            Spacer(Modifier.height(4.dp))
        }
        ui.bubble?.let { Bubble(it, Modifier.align(Alignment.TopCenter).padding(top = 6.dp)) }
    }
}

@Composable
fun Bubble(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
        color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun PadButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick, modifier.size(48.dp), enabled = enabled, colors = if (selected) IconButtonDefaults.iconButtonColors(
        containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface) else IconButtonDefaults.iconButtonColors()) {
        Icon(icon, label)
    }
}

@Composable
private fun SpeedChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val description = stringResource(R.string.player_speed_value, label)
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 40.dp).widthIn(min = 56.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface) {
        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** A zone of the pad's main area; the pad's gesture handler does the work, semantics expose it. */
@Composable
private fun PadZoneIcon(icon: ImageVector, label: String, modifier: Modifier, big: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight().semantics { contentDescription = label; role = Role.Button; onClick(label) { if (enabled) onClick(); enabled } },
        contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(if (big) 44.dp else 30.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

/** Progress with chapter gaps and a dot that grows while scrubbing. */
@Composable
fun Track(fraction: Float, chapters: List<Chapter>, durationMs: Long, scrubbing: Boolean, live: Boolean, modifier: Modifier,
    background: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), gap: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    val dot by animateFloatAsState(if (scrubbing) 9.5f else 7f, label = "dot")
    Canvas(modifier) {
        val y = size.height / 2
        val stroke = 4.dp.toPx()
        drawLine(background, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        if (live) return@Canvas
        val f = fraction.coerceIn(0f, 1f)
        drawLine(BrandColors.Red, Offset(0f, y), Offset(size.width * f, y), stroke, StrokeCap.Round)
        if (durationMs > 0) chapters.drop(1).forEach { c ->
            val x = size.width * (c.startSeconds * 1000f / durationMs)
            drawLine(gap, Offset(x, y - 4.dp.toPx()), Offset(x, y + 4.dp.toPx()), 3.dp.toPx())
        }
        drawCircle(BrandColors.Red, dot.dp.toPx(), Offset(size.width * f, y))
    }
}
