package app.freshtuber.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.R
import app.freshtuber.player.NowPlaying
import app.freshtuber.ui.theme.BrandColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Where the floating window rests: one of four corners of the free area. */
object MiniCorners {
    /** Corner index: bit 0 = right side, bit 1 = bottom. */
    fun default(left: Boolean): Int = if (left) 2 else 3

    fun position(corner: Int, area: Offset, window: Offset, margin: Float, bottomMargin: Float): Offset {
        val x = if (corner and 1 == 1) area.x - window.x - margin else margin
        val y = if (corner and 2 == 2) area.y - window.y - bottomMargin else margin
        return Offset(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
    }

    /** The corner nearest to where the window would land after a throw. */
    fun nearest(center: Offset, area: Offset): Int =
        (if (center.x > area.x / 2) 1 else 0) or (if (center.y > area.y / 2) 2 else 0)
}

/** Size limits of the floating window, in dp of its width (height is 16:9). */
object MiniSize {
    const val MIN = 140f
    const val DEFAULT_FRACTION = 0.46f

    fun default(screenWidth: Float): Float = (screenWidth * DEFAULT_FRACTION).coerceIn(150f, 260f)
    fun max(screenWidth: Float): Float = (screenWidth - 24f).coerceAtLeast(MIN)
    fun clamp(width: Float, screenWidth: Float): Float = width.coerceIn(MIN, max(screenWidth))
}

/**
 * The minimized video floats over the app like in YouTube: drag it anywhere, it settles in the
 * nearest corner. Pinch it, or drag the grip on its inner corner, to resize. A tap shows the
 * controls on the picture (open, close, previous, play/pause, next); a double tap opens the video.
 */
@Composable
fun FloatingMiniPlayer(
    player: Player,
    nowPlaying: NowPlaying,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    left: Boolean,
    bottomClearance: Dp,
    corner: Int,
    onCorner: (Int) -> Unit,
    widthDp: Float,
    onWidth: (Float) -> Unit,
    hasNext: Boolean,
    hasPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = rememberProgress(player)
    val openLabel = stringResource(R.string.player_open_video, nowPlaying.title)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidth = maxWidth.value
        val width = (if (widthDp > 0f) MiniSize.clamp(widthDp, screenWidth) else MiniSize.default(screenWidth)).dp
        val height = width * 9f / 16f
        val area = with(density) { Offset(maxWidth.toPx(), maxHeight.toPx()) }
        val window = with(density) { Offset(width.toPx(), height.toPx()) }
        val margin = with(density) { 12.dp.toPx() }
        val bottomMargin = with(density) { (bottomClearance + 12.dp).toPx() }
        val target = MiniCorners.position(corner, area, window, margin, bottomMargin)
        val offset = remember { Animatable(target, Offset.VectorConverter) }
        var dragging by remember { mutableStateOf(false) }
        var resizing by remember { mutableStateOf(false) }
        LaunchedEffect(target) {
            // While resizing the anchored corner stays put; otherwise the window glides there.
            if (resizing) offset.snapTo(target) else if (!dragging) offset.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
        }
        val scope = rememberCoroutineScope()
        var controls by remember { mutableStateOf(false) }
        var controlsPoke by remember { mutableIntStateOf(0) }
        LaunchedEffect(controls, controlsPoke) { if (controls) { delay(3_500); controls = false } }
        val currentWidth by rememberUpdatedState(width.value)
        val resizeBy: (Float) -> Unit = { delta -> onWidth(MiniSize.clamp(currentWidth + delta, screenWidth)) }

        Surface(
            Modifier.offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .size(width, height).testTag("mini")
                .semantics { contentDescription = openLabel; role = Role.Button; onClick { onOpen(); true } },
            shape = RoundedCornerShape(12.dp), color = Color.Black, shadowElevation = 8.dp,
        ) {
            Box(Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { controls = !controls; controlsPoke++ }, onDoubleTap = { onOpen() })
                }
                .pointerInput(screenWidth) {
                    // Two fingers: pinch to resize, like YouTube's floating window.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    resizing = true
                                    onWidth(MiniSize.clamp(currentWidth * zoom, screenWidth))
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                        resizing = false
                    }
                }
                .pointerInput(area, window, margin, bottomMargin) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = { dragging = true; tracker.resetTracking() },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            val thrown = offset.value + Offset(v.x, v.y) * 0.15f + window / 2f
                            val next = MiniCorners.nearest(thrown, area)
                            onCorner(next)
                            dragging = false
                            scope.launch { offset.animateTo(MiniCorners.position(next, area, window, margin, bottomMargin), spring(stiffness = Spring.StiffnessMediumLow)) }
                        },
                        onDragCancel = { dragging = false; scope.launch { offset.animateTo(target) } },
                    ) { change, drag ->
                        if (resizing) return@detectDragGestures
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val next = offset.value + drag
                        scope.launch { offset.snapTo(Offset(next.x.coerceIn(0f, area.x - window.x), next.y.coerceIn(0f, area.y - window.y))) }
                    }
                }) {
                VideoSurface(player, Modifier.fillMaxSize(), texture = true)
                val playing = progress.isPlaying || (progress.buffering && player.playWhenReady)
                val big = width >= 200.dp
                androidx.compose.animation.AnimatedVisibility(controls, Modifier.fillMaxSize(),
                    enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                    Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                        // Close and open on the outer edge (by the screen edge), the grip on the inner corner.
                        val right = corner and 1 == 1
                        MiniButton(Icons.Rounded.Close, stringResource(R.string.player_close), small = !big,
                            modifier = Modifier.align(if (right) Alignment.TopEnd else Alignment.TopStart), onClick = onClose)
                        MiniButton(Icons.Rounded.OpenInFull, stringResource(R.string.player_open), small = !big,
                            modifier = Modifier.align(if (right) Alignment.BottomEnd else Alignment.BottomStart).padding(bottom = 3.dp), onClick = onOpen)
                        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (big) 10.dp else 2.dp)) {
                            MiniButton(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), small = !big, enabled = hasPrevious) {
                                controlsPoke++; onPrevious()
                            }
                            MiniButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                stringResource(if (playing) R.string.player_pause else R.string.player_play), small = !big, large = true) {
                                controlsPoke++; if (player.isPlaying) player.pause() else player.play()
                            }
                            MiniButton(Icons.Rounded.SkipNext, stringResource(R.string.player_next), small = !big, enabled = hasNext) {
                                controlsPoke++; onNext()
                            }
                        }
                        ResizeGrip(corner, Modifier.align(gripAlignment(corner))) { delta, dragEnd ->
                            controlsPoke++
                            resizing = !dragEnd
                            if (!dragEnd) resizeBy(delta / density.density)
                        }
                    }
                }
                if (!controls) {
                    // Without the overlay the essentials stay on the picture: play/pause and close.
                    MiniButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (playing) R.string.player_pause else R.string.player_play), small = true,
                        modifier = Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd)) {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                }
                val fraction = if (progress.durationMs > 0) progress.positionMs.toFloat() / progress.durationMs else 0f
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x40FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(BrandColors.Red))
                }
            }
        }
    }
}

/** The grip sits on the window's inner corner, the one facing the middle of the screen. */
private fun gripAlignment(corner: Int): Alignment = when (corner) {
    0 -> Alignment.BottomEnd   // top-left window
    1 -> Alignment.BottomStart // top-right window
    2 -> Alignment.TopEnd      // bottom-left window
    else -> Alignment.TopStart // bottom-right window
}

/**
 * Drag handle for resizing. Dragging away from the anchored corner grows the window; the
 * reported delta is in pixels of width.
 */
@Composable
private fun ResizeGrip(corner: Int, modifier: Modifier, onResize: (delta: Float, end: Boolean) -> Unit) {
    val label = stringResource(R.string.player_resize)
    // Growth direction: towards the screen middle, i.e. away from the anchored sides.
    val sx = if (corner and 1 == 1) -1f else 1f
    val sy = if (corner and 2 == 2) -1f else 1f
    Box(modifier.size(40.dp).semantics { contentDescription = label }
        .pointerInput(corner) {
            detectDragGestures(onDragEnd = { onResize(0f, true) }, onDragCancel = { onResize(0f, true) }) { change, drag ->
                change.consume()
                // Diagonal drags count by their larger component, measured in width.
                val dx = drag.x * sx
                val dy = drag.y * sy * 16f / 9f
                onResize(if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) dx else dy, false)
            }
        }, contentAlignment = Alignment.Center) {
        // Three short diagonal strokes pointing into the corner, like a window's resize grip.
        androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val stroke = 2.dp.toPx()
            for (i in 1..3) {
                val d = w * i / 3f
                // Drawn for the top-left corner, mirrored for the others.
                val a = Offset(0f, d); val b = Offset(d, 0f)
                fun m(p: Offset) = Offset(if (sx < 0f) p.x else w - p.x, if (sy < 0f) p.y else w - p.y)
                drawLine(Color.White.copy(alpha = 0.9f), m(a), m(b), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun MiniButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier,
    small: Boolean = false, large: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val touch = if (small) 36.dp else 44.dp
    val glyph = when { large && !small -> 34.dp; large -> 28.dp; small -> 24.dp; else -> 30.dp }
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(touch)) {
        Icon(icon, label, Modifier.size(glyph).clip(CircleShape).background(Color(0x99000000)).padding(if (small) 3.dp else 5.dp),
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f))
    }
}
