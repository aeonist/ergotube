package app.freshtuber.ui.feed

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freshtuber.R
import app.freshtuber.ui.theme.BrandColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Hold a video and flick. Directions are fixed and the same for both hands, as in the mockup:
 * up = later, right = download, left = not interested, down = share.
 */
enum class FanAction(val icon: ImageVector, @param:StringRes val labelRes: Int, val degrees: Float) {
    WatchLater(Icons.Rounded.Schedule, R.string.mine_later, 90f),
    Download(Icons.Rounded.Download, R.string.feed_download, 0f),
    NotInterested(Icons.Rounded.Block, R.string.feed_not_interested, 180f),
    Share(Icons.Rounded.Share, R.string.video_share, 270f),
}

/** Pure fan geometry, in any one unit (the app passes pixels). */
object FanMath {
    /** Default tolerance: generous, so a flick does not need to be precise (about 43°). */
    const val TOLERANCE = 0.75f

    /**
     * The action the finger points at from the fan [center], or null inside the dead zone or
     * between directions. [dy] grows downward, as on screen.
     */
    fun pick(dx: Float, dy: Float, dead: Float, tolerance: Float = TOLERANCE, available: Set<FanAction> = FanAction.entries.toSet()): FanAction? {
        if (hypot(dx, dy) <= dead) return null
        val angle = atan2(-dy, dx)
        return available.map { it to angleDistance(angle, it.degrees * PI.toFloat() / 180f) }
            .filter { it.second < tolerance }
            .minByOrNull { it.second }?.first
    }

    /** Keeps the whole fan of [radius] on screen, away from the edges and the status bar. */
    fun clampCenter(x: Float, y: Float, width: Float, height: Float, radius: Float, side: Float, top: Float, bottom: Float): Offset {
        val minX = side + radius
        val maxX = width - side - radius
        val minY = top + radius
        val maxY = height - bottom - radius
        return Offset(if (minX <= maxX) x.coerceIn(minX, maxX) else width / 2, if (minY <= maxY) y.coerceIn(minY, maxY) else height / 2)
    }

    /** Where an item sits around the center; y grows downward. */
    fun itemPosition(center: Offset, radius: Float, action: FanAction): Offset {
        val a = action.degrees * PI.toFloat() / 180f
        return Offset(center.x + radius * cos(a), center.y - radius * sin(a))
    }

    private fun angleDistance(a: Float, b: Float): Float {
        var d = abs(a - b) % (2 * PI.toFloat())
        if (d > PI) d = 2 * PI.toFloat() - d
        return d
    }
}

/** What a released hold means. */
sealed interface FanResult {
    data class Picked(val action: FanAction) : FanResult
    /** Held and released without moving: open the normal video menu. */
    data object Still : FanResult
    /** Moved away but not onto an action, or the touch was cancelled. */
    data object None : FanResult
}

/** One hold in progress; drawn by [FanOverlay] above everything else. */
class FanSession(val start: Offset, val center: Offset, val available: Set<FanAction>) {
    var pointer by mutableStateOf(start)
    var moved by mutableStateOf(false)
    var selected by mutableStateOf<FanAction?>(null)
}

/** Root-level owner of the card fan, so it draws over the dock and the whole screen. */
class FanHost {
    var session by mutableStateOf<FanSession?>(null)
        private set
    internal var size by mutableStateOf(Offset.Zero)
    internal var density = 1f

    private val radius get() = RADIUS_DP * density
    private val dead get() = DEAD_DP * density

    fun start(rootPoint: Offset, available: Set<FanAction>) {
        val center = if (size == Offset.Zero) rootPoint else FanMath.clampCenter(rootPoint.x, rootPoint.y, size.x, size.y,
            radius, 36 * density, 96 * density, 36 * density)
        session = FanSession(rootPoint, center, available)
    }

    /** Returns true when the selection changed, for a haptic tick. */
    fun move(rootPoint: Offset): Boolean {
        val s = session ?: return false
        s.pointer = rootPoint
        if ((rootPoint - s.start).getDistance() > dead) s.moved = true
        val pick = if (s.moved) FanMath.pick(rootPoint.x - s.center.x, rootPoint.y - s.center.y, dead, available = s.available) else null
        val changed = pick != s.selected
        s.selected = pick
        return changed && pick != null
    }

    fun finish(cancelled: Boolean): FanResult {
        val s = session ?: return FanResult.None
        session = null
        return when {
            cancelled -> FanResult.None
            s.selected != null -> FanResult.Picked(s.selected!!)
            !s.moved -> FanResult.Still
            else -> FanResult.None
        }
    }

    companion object {
        const val RADIUS_DP = 98f
        const val DEAD_DP = 30f
    }
}

val LocalFanHost = staticCompositionLocalOf<FanHost?> { null }

@Composable
fun FanOverlay(host: FanHost) {
    val density = LocalDensity.current
    host.density = density.density
    Box(Modifier.fillMaxSize().onSizeChanged { host.size = Offset(it.width.toFloat(), it.height.toFloat()) }) {
        val session = host.session ?: return@Box
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(session) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        LaunchedEffect(session.selected) { if (session.selected != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        val radius = FanHost.RADIUS_DP * density.density
        val ringColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)))
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(ringColor, radius, session.center, style = Stroke(width = 76.dp.toPx()))
        }
        val label = session.selected?.let { stringResource(it.labelRes) } ?: stringResource(R.string.fan_hint)
        val labelTop = with(density) { maxOf(64.dp.toPx(), session.center.y - radius - 104.dp.toPx()).toDp() }
        Text(label, Modifier.fillMaxWidth().padding(top = labelTop, start = 12.dp, end = 12.dp), color = Color.White,
            fontSize = 19.sp, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium.copy(
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)))
        FanAction.entries.forEach { action ->
            val p = FanMath.itemPosition(session.center, radius, action)
            val selected = session.selected == action
            val enabled = action in session.available
            val scale by animateFloatAsState(if (selected) 1.14f else 1f, label = "fan-item")
            Column(
                Modifier.offset { IntOffset((p.x - 38.dp.toPx()).toInt(), (p.y - 29.dp.toPx()).toInt()) }
                    .size(76.dp, 58.dp).scale(scale)
                    .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                    .padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                val color = when {
                    selected -> MaterialTheme.colorScheme.surface
                    enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
                Icon(action.icon, null, tint = color)
                Text(stringResource(action.labelRes), color = color, fontSize = 10.5.sp, lineHeight = 12.sp, maxLines = 2,
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
        Box(Modifier.offset { IntOffset((session.center.x - 26.dp.toPx()).toInt(), (session.center.y - 26.dp.toPx()).toInt()) }
            .size(52.dp).background(BrandColors.Red, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SmartDisplay, null, tint = Color.White)
        }
    }
}
