package app.freshtuber.ui.feed

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Headphones
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freshtuber.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Hold a video and flick toward one of six equally spaced actions. Both hands share the order.
 */
enum class FanAction(val icon: ImageVector, @param:StringRes val labelRes: Int, val degrees: Float) {
    WatchLater(Icons.Rounded.Schedule, R.string.mine_later, 90f),
    Download(Icons.Rounded.Download, R.string.feed_download, 30f),
    NotInterested(Icons.Rounded.Block, R.string.feed_not_interested, 210f),
    Share(Icons.Rounded.Share, R.string.video_share, 270f),
    Save(Icons.AutoMirrored.Rounded.PlaylistAdd, R.string.video_save, 330f),
    Channel(Icons.Rounded.AccountCircle, R.string.channel_group, 150f),
    /** On a music video, «Слушать» takes the channel's place: the song plays as music. */
    Listen(Icons.Rounded.Headphones, R.string.fan_listen, 150f),
}

/** Pure fan geometry, in any one unit (the app passes pixels). */
object FanMath {
    /** Leave a narrow neutral gap between the six 60-degree sectors. */
    const val TOLERANCE = 0.49f

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
        val pick = if (s.moved) FanMath.pick(rootPoint.x - s.start.x, rootPoint.y - s.start.y, dead, available = s.available) else null
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
            else -> FanResult.None
        }
    }

    companion object {
        const val RADIUS_DP = 98f
        const val DEAD_DP = 30f
    }
}

val LocalFanHost = staticCompositionLocalOf<FanHost?> { null }

/** Timing of the fan's entrance, as pure numbers. */
object FanMotion {
    /** Each next item starts this fraction of the whole opening later than the one before it. */
    const val STAGGER = 0.11f

    /** How far item [index] of [count] has come out while the whole fan is [open] (0..1). */
    fun itemProgress(open: Float, index: Int, count: Int): Float {
        val span = 1f - STAGGER * (count - 1).coerceAtLeast(0)
        return ((open - STAGGER * index) / span).coerceIn(0f, 1f)
    }

    /** Runs a little past the end and settles back: the items land with a small bounce. */
    fun backOut(x: Float): Float {
        val c = 1.70158f
        val t = x - 1f
        return 1f + (c + 1f) * t * t * t + c * t * t
    }
}

@Composable
fun FanOverlay(host: FanHost) {
    val density = LocalDensity.current
    host.density = density.density
    Box(Modifier.fillMaxSize().onSizeChanged { host.size = Offset(it.width.toFloat(), it.height.toFloat()) }) {
        var lastSession by remember { mutableStateOf<FanSession?>(null) }
        host.session?.let { lastSession = it }
        val visible = host.session != null
        val open = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(host.session) {
            if (host.session != null) { open.snapTo(0f); open.animateTo(1f, androidx.compose.animation.core.tween(340, easing = androidx.compose.animation.core.LinearEasing)) }
            else open.animateTo(0f, androidx.compose.animation.core.tween(200, easing = app.freshtuber.ui.motion.Motion.Accelerate))
        }
        val session = lastSession ?: return@Box
        if (open.value <= 0f && !visible) return@Box
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(session) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        LaunchedEffect(session.selected) { if (session.selected != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        val radius = FanHost.RADIUS_DP * density.density
        val ringColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)
        val glow = MaterialTheme.colorScheme.onSurface
        val scrim = MaterialTheme.colorScheme.scrim
        // Leaving with a choice: the chosen action stays and flashes, the rest fold back.
        val picked = if (visible) null else session.selected
        val whole = if (visible) (open.value * 3f).coerceAtMost(1f) else open.value
        // The finger pulls the middle and a soft thread toward where it points.
        val pull = (session.pointer - session.start).let { d ->
            val length = d.getDistance()
            if (length < 1f) Offset.Zero else d / length * minOf(length, radius - 44 * density.density)
        }
        val reach by animateFloatAsState(if (session.moved && visible) 1f else 0f, app.freshtuber.ui.motion.Motion.spring(), label = "fanReach")
        val sector by animateFloatAsState(if (session.selected != null && visible) 1f else 0f, androidx.compose.animation.core.tween(140), label = "fanSector")
        var sectorAt by remember(session) { mutableFloatStateOf(90f) }
        session.selected?.let { sectorAt = it.degrees }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(scrim, alpha = 0.5f * whole)
            val grow = FanMotion.backOut((open.value * 1.6f).coerceAtMost(1f))
            drawCircle(ringColor, radius * (0.55f + 0.45f * grow), session.center, alpha = whole, style = Stroke(width = 76.dp.toPx()))
            if (sector > 0.01f) drawArc(glow, startAngle = -sectorAt - 27f, sweepAngle = 54f, useCenter = false,
                topLeft = session.center - Offset(radius, radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                alpha = 0.10f * sector * whole, style = Stroke(width = 76.dp.toPx()))
            if (reach > 0.01f) drawLine(glow, session.center, session.center + pull * reach, strokeWidth = 5.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round, alpha = 0.22f * reach * whole)
        }
        val label = session.selected?.let { stringResource(it.labelRes) } ?: stringResource(R.string.fan_hint)
        val labelTop = with(density) { maxOf(64.dp.toPx(), session.center.y - radius - 104.dp.toPx()).toDp() }
        androidx.compose.animation.AnimatedContent(label, Modifier.fillMaxWidth().padding(top = labelTop, start = 12.dp, end = 12.dp).graphicsLayer { alpha = whole },
            transitionSpec = { (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) +
                androidx.compose.animation.slideInVertically(app.freshtuber.ui.motion.Motion.spring()) { it / 3 })
                .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90))) }, label = "fanLabel") { text ->
            Text(text, Modifier.fillMaxWidth(), color = Color.White,
                fontSize = 19.sp, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)))
        }
        // Two actions share a direction (Channel, Listen): the available one is drawn. Items come
        // out clockwise from the top, one after another.
        val shown = FanAction.entries.filter { action -> action in session.available ||
            (action != FanAction.Listen && session.available.none { it.degrees == action.degrees }) }
            .sortedBy { (450f - it.degrees) % 360f }
        shown.forEachIndexed { index, action ->
            val p = FanMath.itemPosition(session.center, radius, action)
            val selected = session.selected == action
            val enabled = action in session.available
            val lift by animateFloatAsState(if (selected && visible) 1f else 0f, app.freshtuber.ui.motion.Motion.spring(), label = "fan-item")
            val isPicked = picked == action
            val out = if (isPicked) 1f else FanMotion.itemProgress(open.value, index, shown.size)
            val eased = if (visible) FanMotion.backOut(out) else out
            Column(
                Modifier.offset {
                    // The chosen item leans toward the finger; the others fly out of the middle.
                    val direction = (p - session.center) / radius
                    val at = session.center + (p - session.center) * eased + direction * (7.dp.toPx() * lift)
                    IntOffset((at.x - 38.dp.toPx()).toInt(), (at.y - 29.dp.toPx()).toInt())
                }
                    .size(76.dp, 58.dp)
                    .graphicsLayer {
                        val flash = if (isPicked) 1f - open.value else 0f
                        val size = (0.45f + 0.55f * eased) * (1f + 0.16f * lift) * (1f + 0.3f * flash)
                        scaleX = size; scaleY = size
                        alpha = if (isPicked) open.value else out.coerceIn(0f, 1f)
                    }
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
        val puck = FanMotion.backOut((open.value * 2f).coerceAtMost(1f))
        Box(Modifier.offset {
            val at = session.center + pull * (0.12f * reach)
            IntOffset((at.x - 26.dp.toPx()).toInt(), (at.y - 26.dp.toPx()).toInt())
        }.size(52.dp).graphicsLayer { scaleX = if (visible) puck else open.value; scaleY = scaleX; alpha = whole }
            .background(Color(0xFF572A35), CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_brand), contentDescription = null, tint = Color(0xFFF3C39E), modifier = Modifier.size(32.dp))
        }
    }
}
