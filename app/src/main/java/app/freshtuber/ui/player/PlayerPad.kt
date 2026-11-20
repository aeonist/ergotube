package app.freshtuber.ui.player

import app.freshtuber.ui.motion.blurVisible
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import app.freshtuber.ui.motion.pressBounce
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
import androidx.compose.ui.platform.testTag
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
    var timeline by mutableStateOf(TimelineWindow.Full)
    /** Null when dragging along the track (no fine mode), else whether fine mode is on. */
    var fine by mutableStateOf<Boolean?>(null)
    var bubble by mutableStateOf<String?>(null)
        private set
    var bubbleKeep by mutableStateOf(false)
        private set
    var bubbleId by mutableIntStateOf(0)
        private set
    var boost by mutableStateOf(false)
    var seekTimeVisible by mutableStateOf(false)
    var seekTimeId by mutableIntStateOf(0)
    fun showSeekTime() { seekTimeVisible = true; seekTimeId++ }
    var active by mutableStateOf(false)
    /** Height of the bottom control zone, so the scrub preview floats right above it. */
    var zoneHeight by mutableStateOf(0.dp)
    val burst = SeekBurst()

    fun say(text: String, keep: Boolean = false) { bubble = text; bubbleKeep = keep; bubbleId++ }
    fun hush() { bubble = null }
}

val PlaybackSpeeds = listOf(1f, 1.25f, 1.5f, 2f)

fun speedLabel(speed: Float): String = if (speed == 1f) "1×" else "${speed.toString().trimEnd('0').trimEnd('.').replace('.', ',')}×"

/** Seeks by [seconds] and reports the running total in the bubble ("−20 с → 4:10"). */
fun PadUi.skip(player: Player, seconds: Int, secondsLabel: (String) -> String) {
    showSeekTime()
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
 * Swipe down (from anywhere, buttons too): the page follows the finger like pulling the video. The pad is two rows: time around the track with the speed pill, then
 * quality, transport and full screen in one row under the thumb.
 */
@Composable
fun PlayerPad(
    player: Player,
    ui: PadUi,
    speed: Float,
    chapters: List<Chapter>,
    live: Boolean,
    left: Boolean,
    hasNext: Boolean,
    segments: List<app.freshtuber.data.extras.SkipSegment> = emptyList(),
    onNext: () -> Unit,
    hasPrevious: Boolean,
    onPrevious: () -> Unit,
    onFullscreen: () -> Unit,
    onQuality: () -> Unit,
    onSpeed: () -> Unit,
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
    var playBounds by remember { mutableStateOf(Rect.Zero) }
    fun Modifier.button(key: String) = onGloballyPositioned { buttons[key] = it.boundsInRoot() }
    val durationMs = progress.durationMs
    val shownMs = ui.scrubMs ?: progress.positionMs
    val chapter = Chapter.at(chapters, shownMs / 1000)
    LaunchedEffect(ui.seekTimeId) { if (ui.seekTimeVisible) { delay(900); ui.seekTimeVisible = false } }
    LaunchedEffect(ui.bubbleId) { if (ui.bubble != null && !ui.bubbleKeep) { delay(800); ui.hush() } }

    val toggle = {
        if (progress.ended) { player.seekTo(0); player.play() } else if (player.isPlaying) player.pause() else player.play()
    }
    // While buffering after a seek the video is still meant to play; show that, not ▶.
    val playing = progress.isPlaying || (progress.buffering && player.playWhenReady)
    val playLabel = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    val backLabel = stringResource(R.string.player_back_10)
    val forwardLabel = stringResource(R.string.player_forward_10)

    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    val dropRadius = with(androidx.compose.ui.platform.LocalDensity.current) { 30.dp.toPx() }
    DisposableEffect(light) { onDispose { light?.setDrop(1, null, 0f) } }
    // The remote is the second liquid drop: the background draws it at this rect.
    Box(modifier.fillMaxWidth().padding(horizontal = 14.dp)
        .onGloballyPositioned { light?.setDrop(1, it.boundsInRoot(), dropRadius) }
        .clip(RoundedCornerShape(30.dp))
        .background(if (ui.active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else Color.Transparent)
        .onGloballyPositioned { padCoords = it }
        .pointerInput(player, live, durationMs) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val coords = padCoords ?: return@awaitEachGesture
                val rootDown = coords.localToRoot(down.position)
                // A button keeps its tap. A downward drag (from a button too) is the page's pull
                // (minimizeDrag at the page root), which takes the events and so ends this gesture.
                if (buttons.values.any { it.contains(rootDown) }) return@awaitEachGesture
                val onTrackRow = rootDown.y in (trackBounds.top - 4.dp.toPx())..trackBounds.bottom
                val onTrack = trackBounds.width > 0f && durationMs > 0 && onTrackRow &&
                    rootDown.x in (trackBounds.left - 10.dp.toPx())..(trackBounds.right + 10.dp.toPx())
                val onMain = !onTrackRow && rootDown.y in mainBounds.top..mainBounds.bottom
                val width = size.width.toFloat()
                val start = down.position
                val startMs = player.currentPosition
                var last = start
                var move: PadMove? = null
                var lifted: PointerInputChange? = null
                var abandon = false
                ui.active = true
                // Phase 1: tap, hold or the start of a drag.
                val decided = withTimeoutOrNull(if (onMain && !live) PadGestures.HOLD_MS else Long.MAX_VALUE) {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: run { abandon = true; return@withTimeoutOrNull true }
                        // The page's pull took the finger.
                        if (change.isConsumed) { abandon = true; return@withTimeoutOrNull true }
                        val d = change.position - start
                        if (!change.pressed) { lifted = change; return@withTimeoutOrNull true }
                        move = PadGestures.decide(d.x / density, d.y / density)
                        if (move == PadMove.Seek && live) move = null
                        // Downward is the page's pull, never the pad's.
                        if (move == PadMove.Vertical && d.y > 0f) { abandon = true; return@withTimeoutOrNull true }
                        if (move != null) {
                            if (move == PadMove.Seek) ui.scrubMs = startMs
                            change.consume(); last = change.position; return@withTimeoutOrNull true
                        }
                    }
                }
                if (abandon) { ui.active = false; return@awaitEachGesture }
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
                        onMain -> when (if (live) PadZone.Play else if (playBounds.width > 0f) PadGestures.zone(rootUp.x, playBounds.left, playBounds.right) else PadGestures.zone(up.position.x / width)) {
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
                if (move == PadMove.Seek && onTrack) {
                    ui.timeline = TimelineWindow.zoomAt((rootDown.x - trackBounds.left) / trackBounds.width, durationMs)
                    scrubSeconds = ui.timeline.fractionAt((coords.localToRoot(last).x - trackBounds.left) / trackBounds.width) * durationSeconds
                    ui.scrubMs = (scrubSeconds * 1000).toLong()
                }
                var endY = last.y
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    endY = change.position.y
                    if (move == PadMove.Seek) {
                        if (onTrack) {
                            val position = (coords.localToRoot(change.position).x - trackBounds.left) / trackBounds.width
                            scrubSeconds = ui.timeline.fractionAt(position) * durationSeconds
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
                    ui.showSeekTime()
                    ui.scrubMs = null
                    ui.timeline = TimelineWindow.Full
                    ui.fine = null
                    ui.say("${signedSeek(target / 1000 - startMs / 1000)}  →  ${formatDuration(target / 1000)}")
                } else if (move == PadMove.Vertical) {
                    val dy = (endY - start.y) / density
                    if (dy < -PadGestures.SWIPE_DP) onFullscreen()
                }
            }
        }) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 4.dp)) {
            // Two rows only: time around the track, then every button in one row under the thumb.
            // A folded phone's cover screen gets smaller buttons instead of a clipped clock.
            val side = PadLayout.sideButton(maxWidth.value).dp
            val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = app.freshtuber.ui.theme.TabularNumbers)
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().height(PadLayout.TRACK_ROW_DP.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val chip: @Composable () -> Unit = {
                        if (!live) SpeedChip(if (ui.boost) "2×" else speedLabel(speed), selected = ui.boost || speed != 1f, Modifier.button("speed"), onClick = onSpeed)
                    }
                    if (left) chip()
                    if (live) Text(stringResource(R.string.search_live), color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.background(BrandColors.Red, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    else if (durationMs > 0) Text(formatDuration(shownMs / 1000), style = timeStyle, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
                        modifier = Modifier.testTag("pad:time"))
                    Track(if (durationMs > 0) shownMs.toFloat() / durationMs else 0f, chapters, durationMs, scrubbing = ui.scrubMs != null,
                        live = live, modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp)
                            .testTag("player:track").onGloballyPositioned { trackBounds = it.boundsInRoot() },
                        gap = Color.Transparent, segments = segments, timeline = ui.timeline)
                    if (!live && durationMs > 0) Text(formatDuration(durationMs / 1000), style = timeStyle, maxLines = 1, softWrap = false,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("pad:duration"))
                    if (!left) chip()
                }
                Row(Modifier.fillMaxWidth().height(PadLayout.BUTTON_ROW_DP.dp).onGloballyPositioned { mainBounds = it.boundsInRoot() },
                    verticalAlignment = Alignment.CenterVertically) {
                    val quality: @Composable () -> Unit = {
                        PadButton(Icons.Rounded.HighQuality, stringResource(R.string.player_quality), Modifier.button("quality").qualityAnchor(), size = side, onClick = onQuality)
                    }
                    val full: @Composable () -> Unit = {
                        PadButton(Icons.Rounded.Fullscreen, stringResource(R.string.player_fullscreen), Modifier.button("fs"), size = side, onClick = onFullscreen)
                    }
                    // Full screen sits on the hand side, quality across from it.
                    if (left) full() else quality()
                    PadButton(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), Modifier.button("previous"), enabled = hasPrevious, size = side, onClick = onPrevious)
                    PadZoneIcon(Icons.Rounded.Replay10, backLabel, Modifier.weight(1f), enabled = !live) { ui.skip(player, -10) { secondsFormat.format(it) } }
                    PadZoneIcon(if (progress.ended) Icons.Rounded.Replay else if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        playLabel, Modifier.weight(1.2f).onGloballyPositioned { playBounds = it.boundsInRoot() }, big = true, playing = playing, onClick = toggle)
                    PadZoneIcon(Icons.Rounded.Forward10, forwardLabel, Modifier.weight(1f), enabled = !live) { ui.skip(player, 10) { secondsFormat.format(it) } }
                    PadButton(Icons.Rounded.SkipNext, stringResource(R.string.player_next), Modifier.button("next"), enabled = hasNext, size = side, onClick = onNext)
                    if (left) quality() else full()
                }
            }
        }
    }
}

/** Sizes of the two-row remote, in dp. */
object PadLayout {
    const val TRACK_ROW_DP = 32
    const val BUTTON_ROW_DP = 50
    /** Four side buttons share the row with the three tap zones; narrow pads shrink the buttons. */
    fun sideButton(innerWidthDp: Float): Float = when {
        innerWidthDp < 270f -> 36f
        innerWidthDp < 316f -> 40f
        else -> 48f
    }
    /** Width left for one skip zone (the play zone is 1.2 of it). */
    fun zoneWidth(innerWidthDp: Float): Float = (innerWidthDp - 4 * sideButton(innerWidthDp)) / 3.2f
}

@Composable
fun Bubble(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
        color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun PadButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 48.dp, onClick: () -> Unit) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    IconButton(onClick, modifier.size(size).pressBounce(source), enabled = enabled, interactionSource = source) {
        Icon(icon, label)
    }
}

/** The speed pill beside the track: a slim pill inside a touch target as tall as the row. */
@Composable
private fun SpeedChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val description = stringResource(R.string.player_speed_value, label)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(modifier.fillMaxHeight().widthIn(min = 44.dp)
        .clickable(source, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }.pressBounce(source), contentAlignment = Alignment.Center) {
        Box(Modifier.height(26.dp).widthIn(min = 38.dp)
            .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(13.dp))
            .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A zone of the pad's main area; the pad's gesture handler does the work, semantics expose it. */
@Composable
private fun PadZoneIcon(icon: ImageVector, label: String, modifier: Modifier, big: Boolean = false, enabled: Boolean = true,
    playing: Boolean = false, onClick: () -> Unit) {
    BoxWithConstraints(modifier.fillMaxHeight().semantics { contentDescription = label; role = Role.Button; onClick(label) { if (enabled) onClick(); enabled } },
        contentAlignment = Alignment.Center) {
        if (big) {
            // Play is a circle, pause a rounded square: the shape itself tells the state.
            val corner by animateFloatAsState(if (playing) 34f else 50f, app.freshtuber.ui.motion.Motion.spring(), label = "playShape")
            Box(Modifier.size(minOf(46.dp, maxWidth - 2.dp)).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(corner.toInt())),
                contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedContent(icon, transitionSpec = {
                    (androidx.compose.animation.scaleIn(app.freshtuber.ui.motion.Motion.spring(), initialScale = 0.6f) + androidx.compose.animation.fadeIn(tween(160)))
                        .togetherWith(androidx.compose.animation.scaleOut(tween(140), targetScale = 0.6f) + androidx.compose.animation.fadeOut(tween(120)))
                }, label = "playIcon") { shown -> Icon(shown, null, Modifier.then(blurVisible(6.dp, 160, 140)).size(28.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            }
        }
        else Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

/** Progress with chapter gaps and a dot that grows while scrubbing. */
@Composable
fun Track(fraction: Float, chapters: List<Chapter>, durationMs: Long, scrubbing: Boolean, live: Boolean, modifier: Modifier,
    background: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), gap: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    segments: List<app.freshtuber.data.extras.SkipSegment> = emptyList(), stroke: androidx.compose.ui.unit.Dp = 4.dp,
    timeline: TimelineWindow = TimelineWindow.Full) {
    val spans = remember(segments, durationMs) { SegmentMarks.spans(segments, durationMs) }
    val dot by animateFloatAsState(if (scrubbing) 11f else 7f, label = "dot")
    Canvas(modifier) {
        val y = size.height / 2
        val strokePx = stroke.toPx()
        drawLine(background, Offset(0f, y), Offset(size.width, y), strokePx, StrokeCap.Round)
        if (live) return@Canvas
        val f = timeline.positionOf(fraction).coerceIn(0f, 1f)
        drawLine(BrandColors.Red, Offset(0f, y), Offset(size.width * f, y), strokePx, StrokeCap.Round)
        // Sponsor and intro marks lie over both the played and the unplayed part.
        spans.forEach { (a, b, color) ->
            val start = timeline.positionOf(a).coerceIn(0f, 1f)
            val end = timeline.positionOf(b).coerceIn(0f, 1f)
            if (end > start) drawLine(color, Offset(size.width * start, y), Offset(size.width * end, y), strokePx, StrokeCap.Butt)
        }
        if (durationMs > 0) chapters.drop(1).forEach { c ->
            val position = timeline.positionOf(c.startSeconds * 1000f / durationMs)
            if (position in 0f..1f) {
                val x = size.width * position
                drawLine(gap, Offset(x, y - 4.dp.toPx()), Offset(x, y + 4.dp.toPx()), 3.dp.toPx())
            }
        }
        drawCircle(BrandColors.Red, dot.dp.toPx(), Offset(size.width * f, y))
    }
}
