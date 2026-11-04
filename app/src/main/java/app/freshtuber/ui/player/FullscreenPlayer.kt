package app.freshtuber.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.R
import app.freshtuber.player.Chapter
import app.freshtuber.player.PlayerState
import app.freshtuber.player.Storyboard
import app.freshtuber.ui.feed.messageRes
import app.freshtuber.ui.theme.BrandColors
import app.freshtuber.ui.util.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Shade = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f))

/**
 * Fullscreen: the phone is turned and the controls gather in an arc in the bottom corner under
 * the holding hand. Tap: middle pauses, ends skip 10 s and add up. Hold: 2×. Drag along the
 * outer arc: jump; drag elsewhere: scrub, moving away from the corner scrubs finely. Small chips
 * for speed, captions, exit and next. After a few idle seconds the fan disappears and a plain
 * timeline runs along the bottom edge; a tap brings the fan back. Outside the arc the video keeps YouTube's gestures.
 */
@Composable
fun FullscreenPlayer(
    player: Player,
    state: PlayerState,
    ui: PadUi,
    pages: StoryboardPages,
    left: Boolean,
    gesturesEnabled: Boolean,
    captionsOn: Boolean,
    captionsAvailable: Boolean,
    hasNext: Boolean,
    showRotateHint: Boolean,
    onExit: () -> Unit,
    onNext: () -> Unit,
    onSpeed: () -> Unit,
    onCaptions: () -> Unit,
    onBoost: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    val now = state.nowPlaying
    val chapters = now?.chapters.orEmpty()
    val storyboard: Storyboard? = now?.storyboard
    val live = now?.isLive == true
    val progress = rememberProgress(player)
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val secondsFormat = stringResource(R.string.player_seconds)
    val holdText = stringResource(R.string.player_hold_2x)
    var awake by remember { mutableStateOf(true) }
    var touching by remember { mutableStateOf(false) }
    var wakeId by remember { mutableIntStateOf(0) }
    var level by remember { mutableStateOf<Pair<ImageVector, Float>?>(null) }
    var hint by remember { mutableStateOf(showRotateHint) }
    val buttons = remember { mutableStateMapOf<String, Rect>() }
    fun wake() { awake = true; wakeId++ }
    LaunchedEffect(wakeId, touching, progress.isPlaying) {
        if (touching || !progress.isPlaying) return@LaunchedEffect
        delay(FanGeometry.IDLE_MS)
        awake = false
    }
    LaunchedEffect(ui.bubbleId) { if (ui.bubble != null && !ui.bubbleKeep) { delay(800); ui.hush() } }
    LaunchedEffect(showRotateHint) { if (showRotateHint) { hint = true; delay(2_600); hint = false } }
    val shown by animateFloatAsState(if (awake) 1f else 0f, label = "fan")

    Box(Modifier.fillMaxSize()) {
    // While the video just plays, a plain straight timeline along the very bottom edge.
    if (shown < 1f) {
        val fraction = if (progress.durationMs > 0) (ui.scrubMs ?: progress.positionMs).toFloat() / progress.durationMs else 0f
        Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).alpha(1f - shown)) {
            drawRect(Color.White.copy(alpha = 0.3f))
            if (!live) drawRect(BrandColors.Red, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom)))) {
        val geo = remember(maxWidth, maxHeight, left) { FanGeometry(maxWidth.value, maxHeight.value, left) }
        val boxHeight = maxHeight
        val durationMs = progress.durationMs
        val shownMs = ui.scrubMs ?: progress.positionMs
        val fraction = if (durationMs > 0) shownMs.toFloat() / durationMs else 0f
        val toggle = { if (progress.ended) { player.seekTo(0); player.play() } else if (player.isPlaying) player.pause() else player.play() }

        Box(Modifier.fillMaxSize().pointerInput(geo, live, gesturesEnabled, awake) {
            var pending: Job? = null
            var lastSide = 0
            var lastAt = 0L
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (awake && buttons.values.any { it.contains(down.position) }) return@awaitEachGesture
                val (r0, u0) = geo.polar(down.position.x / density, down.position.y / density)
                if (r0 <= geo.radius) {
                    // The fan.
                    val woke = awake
                    touching = true
                    wake()
                    val onArc = geo.onTrack(r0) && !live
                    var move = false
                    var lifted: PointerInputChange? = null
                    val decided = withTimeoutOrNull(if (!onArc && !live) PadGestures.HOLD_MS else Long.MAX_VALUE) {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                            if (!change.pressed) { lifted = change; return@withTimeoutOrNull true }
                            if (!live && (change.position - down.position).getDistance() > PadGestures.SEEK_SLOP_DP * density) { change.consume(); move = true; return@withTimeoutOrNull true }
                        }
                    }
                    if (decided == null) {
                        ui.boost = true; onBoost(true); ui.say(holdText, keep = true)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                        }
                        ui.boost = false; onBoost(false); ui.hush(); touching = false; wake()
                        return@awaitEachGesture
                    }
                    val up = lifted
                    if (up != null && !move) {
                        up.consume()
                        touching = false
                        if (!woke) return@awaitEachGesture
                        val (_, u) = geo.polar(up.position.x / density, up.position.y / density)
                        if (onArc && durationMs > 0) {
                            val target = (FanGeometry.fromArc(u) * durationMs).toLong()
                            player.seekTo(target)
                            ui.say("→ ${formatDuration(target / 1000)}${Chapter.at(chapters, target / 1000)?.let { " · ${it.title}" }.orEmpty()}")
                        } else when (if (live) PadZone.Play else FanGeometry.zone(u)) {
                            PadZone.Back -> ui.skip(player, -10) { secondsFormat.format(it) }
                            PadZone.Forward -> ui.skip(player, 10) { secondsFormat.format(it) }
                            PadZone.Play -> toggle()
                        }
                        return@awaitEachGesture
                    }
                    // Scrub along the fan.
                    val durationSeconds = durationMs / 1000f
                    var seconds = player.currentPosition / 1000f
                    val startSeconds = seconds
                    var lastU = u0
                    var fine = false
                    ui.hush()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        val (r, u) = geo.polar(change.position.x / density, change.position.y / density)
                        val nowFine = r - r0 > FanGeometry.FINE_DISTANCE
                        if (nowFine != fine) { fine = nowFine; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                        seconds = if (onArc && !fine) FanGeometry.fromArc(u) * durationSeconds
                        else (seconds + (u - lastU) * (if (onArc) durationSeconds else Scrub.spanSeconds(durationSeconds)) * (if (fine) Scrub.FINE_FACTOR else 1f))
                            .coerceIn(0f, durationSeconds)
                        lastU = u
                        ui.fine = if (onArc && !fine) null else fine
                        ui.scrubMs = (seconds * 1000).toLong()
                        if (!change.pressed) break
                    }
                    val target = (seconds * 1000).toLong()
                    player.seekTo(target)
                    ui.scrubMs = null; ui.fine = null
                    ui.say("${signedSeek((seconds - startSeconds).toLong())}  →  ${formatDuration(target / 1000)}")
                    touching = false
                    wake()
                    return@awaitEachGesture
                }
                // The video outside the fan.
                var end = down
                var vertical = false
                val leftHalf = down.position.x < size.width / 2
                var volume = currentVolumeSteps(context)
                var lastY = down.position.y
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    end = change
                    val d = change.position - down.position
                    if (!vertical && gesturesEnabled && abs(d.y) > 16 * density && abs(d.y) > abs(d.x)) vertical = true
                    if (vertical) {
                        change.consume()
                        val delta = -(change.position.y - lastY) / size.height
                        level = if (leftHalf) Icons.Rounded.BrightnessMedium to adjustBrightness(context, delta)
                        else { volume = setVolumeSteps(context, volume + delta * maxVolumeSteps(context)); Icons.AutoMirrored.Rounded.VolumeUp to volume / maxVolumeSteps(context) }
                        lastY = change.position.y
                    }
                    if (!change.pressed) break
                }
                if (vertical) { level = null; return@awaitEachGesture }
                val dx = (end.position.x - down.position.x) / density
                val dy = (end.position.y - down.position.y) / density
                if (dy > 50 && abs(dy) > abs(dx)) { onExit(); return@awaitEachGesture }
                if (abs(dx) >= 10 || abs(dy) >= 10) return@awaitEachGesture
                val fx = end.position.x / size.width
                val side = if (live) 0 else if (fx < 0.33f) -1 else if (fx > 0.67f) 1 else 0
                val t = System.currentTimeMillis()
                if (side != 0 && lastSide == side && t - lastAt < 300) {
                    pending?.cancel(); lastAt = t
                    ui.skip(player, 10 * side) { secondsFormat.format(it) }
                    return@awaitEachGesture
                }
                pending?.cancel()
                lastSide = side; lastAt = t
                pending = scope.launch { delay(if (side != 0) 300L else 0L); lastSide = 0; if (awake) awake = false else wake() }
            }
        }) {
            // Shade under the fan, so white icons read on any picture.
            Canvas(Modifier.fillMaxSize().alpha(shown)) {
                val corner = Offset(if (left) 0f else size.width, size.height)
                val r = geo.radius.dp.toPx()
                drawCircle(Brush.radialGradient(0f to Color.Black.copy(alpha = 0.6f), (r / (r + 80.dp.toPx())) to Color.Black.copy(alpha = 0.45f),
                    1f to Color.Transparent, center = corner, radius = r + 80.dp.toPx()), r + 80.dp.toPx(), corner)
            }
            // The arc: chapters with gaps, progress in red, a dot. Only while the controls are shown.
            if (shown > 0f) Canvas(Modifier.fillMaxSize().alpha(shown)) {
                val corner = Offset(if (left) 0f else size.width, size.height)
                val r = geo.track.dp.toPx()
                val box = Rect(corner - Offset(r, r), Size(2 * r, 2 * r))
                val base = if (left) 270f else 180f
                val stroke = Stroke(5.dp.toPx())
                val cuts = if (live || durationMs <= 0) listOf(0f, 1f) else (chapters.map { it.startSeconds * 1000f / durationMs } + 1f).let { if (it.first() > 0f) listOf(0f) + it else it }
                val gap = if (cuts.size > 2) 0.014f else 0f
                for (i in 0 until cuts.lastIndex) {
                    val a = cuts[i] + if (i > 0) gap / 2 else 0f
                    val b = cuts[i + 1] - if (i < cuts.lastIndex - 1) gap / 2 else 0f
                    if (b <= a) continue
                    val ua = FanGeometry.toArc(a); val ub = FanGeometry.toArc(b)
                    drawArc(Color.White.copy(alpha = 0.4f), base + ua * 90f, (ub - ua) * 90f, false, box.topLeft, box.size, style = stroke)
                    if (!live && fraction > a) {
                        val uf = FanGeometry.toArc(minOf(b, fraction))
                        drawArc(BrandColors.Red, base + ua * 90f, (uf - ua) * 90f, false, box.topLeft, box.size, style = stroke)
                    }
                }
                if (awake && !live) {
                    val p = geo.position(geo.track, FanGeometry.toArc(fraction))
                    drawCircle(BrandColors.Red, (if (ui.scrubMs != null) 11.dp else 8.dp).toPx(), Offset(p.x.dp.toPx(), p.y.dp.toPx()))
                }
            }
            fun Modifier.at(p: Offset, size: Float) = offset { IntOffset(((p.x - size / 2).dp).roundToPx(), ((p.y - size / 2).dp).roundToPx()) }
            if (awake || shown > 0f) Box(Modifier.fillMaxSize().alpha(shown)) {
                val playing = progress.isPlaying || (progress.buffering && player.playWhenReady)
                FanIcon(Icons.Rounded.Replay10, stringResource(R.string.player_back_10), Modifier.at(geo.position(geo.main, 0.16f), 60f), enabled = !live) {
                    ui.skip(player, -10) { secondsFormat.format(it) } }
                val playLabel = stringResource(if (playing) R.string.player_pause else R.string.player_play)
                Box(Modifier.at(geo.position(geo.main, 0.5f), 76f).size(76.dp).background(Color.White.copy(alpha = 0.14f), CircleShape)
                    .semantics { contentDescription = playLabel; role = Role.Button; onClick { toggle(); true } }, contentAlignment = Alignment.Center) {
                    Icon(if (progress.ended) Icons.Rounded.Replay else if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null, Modifier.size(48.dp), tint = Color.White)
                }
                FanIcon(Icons.Rounded.Forward10, stringResource(R.string.player_forward_10), Modifier.at(geo.position(geo.main, 0.84f), 60f), enabled = !live) {
                    ui.skip(player, 10) { secondsFormat.format(it) } }
                val chip: @Composable (String, Offset, Float, @Composable () -> Unit, Boolean, () -> Unit) -> Unit = { key, p, width, content, selected, click ->
                    Surface(onClick = { click(); wake() }, enabled = awake, shape = RoundedCornerShape(22.dp),
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.18f), contentColor = if (selected) Color(0xFF0F0F0F) else Color.White,
                        modifier = Modifier.offset { IntOffset(((p.x - width / 2).dp).roundToPx(), ((p.y - 22).dp).roundToPx()) }
                            .size(width.dp, 44.dp).onGloballyPositioned { buttons[key] = it.boundsInParent() }) {
                        Box(contentAlignment = Alignment.Center) { content() }
                    }
                }
                chip("exit", geo.position(geo.inner, 0.25f), 44f, { Icon(Icons.Rounded.FullscreenExit, stringResource(R.string.player_exit_fullscreen), Modifier.size(22.dp)) }, false, onExit)
                if (hasNext) chip("next", geo.position(geo.inner, 0.75f), 44f, { Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), Modifier.size(22.dp)) }, false, onNext)
                if (!live) chip("speed", geo.position(geo.outer, 0.3f), 56f, {
                    Text(if (ui.boost) "2×" else speedLabel(state.speed), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }, ui.boost || state.speed != 1f, onSpeed)
                if (captionsAvailable) chip("cc", geo.position(geo.outer, 0.7f), 44f, {
                    Icon(if (captionsOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled, stringResource(R.string.player_subtitles), Modifier.size(22.dp))
                }, captionsOn, onCaptions)
                Text(now?.title.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleSmall.merge(Shade), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp, top = 14.dp).fillMaxWidth(0.58f))
                if (!live) Text(buildString {
                    append("${formatDuration(shownMs / 1000)} / ${formatDuration(durationMs / 1000)}")
                    Chapter.at(chapters, shownMs / 1000)?.let { append(" · ${it.title}") }
                }, color = Color(0xFFE6E6E6), style = MaterialTheme.typography.labelLarge.merge(Shade), maxLines = 1,
                    modifier = Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(bottom = 12.dp, start = if (left) (geo.radius + 12).dp else 0.dp, end = if (left) 0.dp else (geo.radius + 12).dp))
            }
            val freeX = geo.freeCenterX.dp
            ui.bubble?.let { Bubble(it, Modifier.offset { IntOffset((freeX - 80.dp).roundToPx(), 58.dp.roundToPx()) }.width(160.dp).wrapContentWidth()) }
            ui.scrubMs?.let { ms ->
                ScrubPreview(storyboard, ms, chapters, ui.fine, fanHint = true, pages = pages,
                    modifier = Modifier.offset { IntOffset((freeX - 76.dp).roundToPx(), (boxHeight * 0.2f).roundToPx()) })
            }
            if (hint) Column(Modifier.offset { IntOffset((freeX - 90.dp).roundToPx(), (boxHeight * 0.28f).roundToPx()) }.width(180.dp)
                .background(Color(0x9E000000), RoundedCornerShape(16.dp)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.ScreenRotation, null, Modifier.size(36.dp), tint = Color.White)
                Text(stringResource(R.string.player_rotate), color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            }
            level?.let { (icon, value) ->
                Row(Modifier.align(Alignment.TopCenter).padding(top = 24.dp).background(Color(0x99000000), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("${(value * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
            when {
                state.error != null -> Column(Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(16.dp)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(state.error.messageRes()), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                state.loadingId != null || progress.buffering -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
        }
    }
    }
}

@Composable
private fun FanIcon(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier.size(60.dp).semantics { contentDescription = label; role = Role.Button; onClick { if (enabled) onClick(); enabled } },
        contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(34.dp), tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

/** Changes this window's brightness by [delta] (−1..1) and returns the new level 0..1. */
private fun adjustBrightness(context: Context, delta: Float): Float {
    val window = (context as? Activity)?.window ?: return 0f
    val attributes = window.attributes
    val current = attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
    attributes.screenBrightness = (current + delta).coerceIn(0.01f, 1f)
    window.attributes = attributes
    return attributes.screenBrightness
}

private fun maxVolumeSteps(context: Context): Float =
    context.getSystemService(AudioManager::class.java)?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 1f

private fun currentVolumeSteps(context: Context): Float =
    context.getSystemService(AudioManager::class.java)?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 0f

/**
 * Sets media volume to [steps] (fractional, so small drags still add up) and returns the
 * clamped value.
 */
private fun setVolumeSteps(context: Context, steps: Float): Float {
    val audio = context.getSystemService(AudioManager::class.java) ?: return steps
    val clamped = steps.coerceIn(0f, maxVolumeSteps(context))
    if (clamped.toInt() != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, clamped.toInt(), 0)
    }
    return clamped
}


