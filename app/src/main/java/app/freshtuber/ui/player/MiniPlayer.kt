package app.freshtuber.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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

/**
 * The minimized video floats over the app like in YouTube: drag it anywhere, it settles in the
 * nearest corner. Tap opens the watch page; play/pause and close sit on the picture.
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
    modifier: Modifier = Modifier,
) {
    val progress = rememberProgress(player)
    val openLabel = stringResource(R.string.player_open_video, nowPlaying.title)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = (maxWidth * 0.46f).coerceIn(150.dp, 260.dp)
        val height = width * 9f / 16f
        val area = with(density) { Offset(maxWidth.toPx(), maxHeight.toPx()) }
        val window = with(density) { Offset(width.toPx(), height.toPx()) }
        val margin = with(density) { 12.dp.toPx() }
        val bottomMargin = with(density) { (bottomClearance + 12.dp).toPx() }
        val target = MiniCorners.position(corner, area, window, margin, bottomMargin)
        val offset = remember { Animatable(target, Offset.VectorConverter) }
        var dragging by remember { mutableStateOf(false) }
        LaunchedEffect(target) { if (!dragging) offset.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
        val scope = rememberCoroutineScope()

        Surface(
            Modifier.offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                .size(width, height).testTag("mini")
                .semantics { contentDescription = openLabel; role = Role.Button; onClick { onOpen(); true } },
            shape = RoundedCornerShape(12.dp), color = Color.Black, shadowElevation = 8.dp,
        ) {
            Box(Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onOpen() }) }
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
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val next = offset.value + drag
                        scope.launch { offset.snapTo(Offset(next.x.coerceIn(0f, area.x - window.x), next.y.coerceIn(0f, area.y - window.y))) }
                    }
                }) {
                VideoSurface(player, Modifier.fillMaxSize(), texture = true)
                val playing = progress.isPlaying || (progress.buffering && player.playWhenReady)
                MiniButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (playing) R.string.player_pause else R.string.player_play),
                    Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd)) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                MiniButton(Icons.Rounded.Close, stringResource(R.string.player_close),
                    Modifier.align(if (left) Alignment.TopEnd else Alignment.TopStart), onClick = onClose)
                val fraction = if (progress.durationMs > 0) progress.positionMs.toFloat() / progress.durationMs else 0f
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x40FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(BrandColors.Red))
                }
            }
        }
    }
}

@Composable
private fun MiniButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(44.dp)) {
        Icon(icon, label, Modifier.size(30.dp).clip(CircleShape).background(Color(0x99000000)).padding(5.dp), tint = Color.White)
    }
}
