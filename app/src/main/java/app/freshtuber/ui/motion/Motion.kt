package app.freshtuber.ui.motion

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One motion language for the whole app: soft, quick in, slower to settle. */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** A screen coming in. */
    const val ENTER_MS = 400
    /** A screen going away. */
    const val EXIT_MS = 260
    /** A video picture flying between two places. */
    const val FLIGHT_MS = 440

    /** A light spring for things that answer a finger: quick, with a small overshoot. */
    fun <T> spring(): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 520f)

    /** A softer spring for larger things (sheets, cards) that settle rather than snap. */
    fun <T> settle(): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 380f)
}

/** The thing under the finger gives a little and springs back when released. */
fun Modifier.pressBounce(source: androidx.compose.foundation.interaction.InteractionSource, pressed: Float = 0.9f): Modifier = composed {
    val down by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (down) pressed else 1f,
        if (down) tween(90) else Motion.spring(), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Blur that follows a screen's own enter/exit: sharp while it is settled, softly out of focus
 * while it arrives or leaves. Android 12+; older phones only fade.
 */
@Composable
fun AnimatedVisibilityScope.transitionBlur(max: Dp = 22.dp): Modifier {
    if (Build.VERSION.SDK_INT < 31) return Modifier
    val amount = transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(Motion.ENTER_MS, easing = Motion.Decelerate)
            else tween(Motion.EXIT_MS, easing = Motion.Accelerate)
        },
        label = "screenBlur",
    ) { if (it == EnterExitState.Visible) 0f else 1f }
    return Modifier.graphicsLayer {
        val r = amount.value * max.toPx()
        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
    }
}

/**
 * The app's one way to show and hide anything (sheets, buttons, bars, chips, the ear): it comes
 * into focus out of a blur and goes back into one, the way the watch page leaves. Nothing slides
 * in or out. [p] runs 0 (gone) .. 1 (here); the thing grows from [fromScale] as it sharpens.
 */
fun Modifier.blurFade(p: () -> Float, max: Dp = 16.dp, fromScale: Float = 0.94f): Modifier = graphicsLayer {
    val v = p().coerceIn(0f, 1f)
    alpha = v
    val s = fromScale + (1f - fromScale) * v
    scaleX = s; scaleY = s
    if (Build.VERSION.SDK_INT >= 31) {
        val r = (1f - v) * max.toPx()
        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
    }
}

/** Enter half of the blur appearance (fade and a slight grow; the blur itself comes from [blurVisible]). */
fun blurIn(ms: Int = 300, delay: Int = 0, fromScale: Float = 0.94f): androidx.compose.animation.EnterTransition =
    androidx.compose.animation.fadeIn(tween(ms, delay, Motion.Decelerate)) + androidx.compose.animation.scaleIn(tween(ms, delay, Motion.Decelerate), fromScale)

/** Exit half of the blur appearance. */
fun blurOut(ms: Int = 220, toScale: Float = 0.94f): androidx.compose.animation.ExitTransition =
    androidx.compose.animation.fadeOut(tween(ms, easing = Motion.Accelerate)) + androidx.compose.animation.scaleOut(tween(ms, easing = Motion.Accelerate), toScale)

/**
 * Shows or hides [content] through a blur. With [collapse] the space it takes opens and closes
 * too (for bars inside a column), but the content itself never slides or gets cut.
 */
@Composable
fun BlurVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    collapse: Boolean = false,
    max: Dp = 16.dp,
    enterMs: Int = 300,
    exitMs: Int = 220,
    fromScale: Float = 0.94f,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val enter = blurIn(enterMs, fromScale = fromScale).let {
        if (collapse) it + androidx.compose.animation.expandVertically(tween(enterMs, easing = Motion.Decelerate), expandFrom = androidx.compose.ui.Alignment.Top, clip = false) else it
    }
    val exit = blurOut(exitMs, fromScale).let {
        if (collapse) it + androidx.compose.animation.shrinkVertically(tween(exitMs + 60, easing = Motion.Emphasized), shrinkTowards = androidx.compose.ui.Alignment.Top, clip = false) else it
    }
    androidx.compose.animation.AnimatedVisibility(visible, modifier, enter = enter, exit = exit) {
        androidx.compose.foundation.layout.Box(blurVisible(max, enterMs, exitMs)) { content() }
    }
}

/** Blur for anything inside an AnimatedVisibility / AnimatedContent: sharp when settled. */
@Composable
fun AnimatedVisibilityScope.blurVisible(max: Dp = 16.dp, enterMs: Int = 300, exitMs: Int = 220): Modifier {
    if (Build.VERSION.SDK_INT < 31) return Modifier
    val amount = transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(enterMs, easing = Motion.Decelerate)
            else tween(exitMs, easing = Motion.Accelerate)
        },
        label = "blurVisible",
    ) { if (it == EnterExitState.Visible) 0f else 1f }
    return Modifier.graphicsLayer {
        val r = amount.value * max.toPx()
        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
    }
}

/** A cross-fade through a blur for AnimatedContent: the old one blurs away while the new one sharpens. */
fun blurSwap(enterMs: Int = 300, exitMs: Int = 200, fromScale: Float = 0.94f): androidx.compose.animation.ContentTransform =
    androidx.compose.animation.ContentTransform(blurIn(enterMs, 40, fromScale), blurOut(exitMs, 1.04f))

/** True once a screen has fully arrived and is not leaving. */
val AnimatedVisibilityScope.settled: Boolean
    get() = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible

/** True from the moment a screen starts leaving. */
val AnimatedVisibilityScope.leaving: Boolean
    get() = transition.targetState == EnterExitState.PostExit

/**
 * A card gives slightly under a resting finger (not under a scroll) and springs back. It only
 * watches the touch, so taps, holds and scrolling work as before.
 */
fun Modifier.pressDip(pressed: Float = 0.97f): Modifier = composed {
    var down by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (down) pressed else 1f,
        if (down) tween(160) else Motion.spring(), label = "dip")
    pointerInput(Unit) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            fun still(change: androidx.compose.ui.input.pointer.PointerInputChange?) =
                change != null && change.pressed && (change.position - first.position).getDistance() <= viewConfiguration.touchSlop
            // A scroll moves within the first moments; a finger that rests is a press.
            val left = withTimeoutOrNull(80) {
                while (true) if (!still(awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == first.id })) return@withTimeoutOrNull true
                @Suppress("UNREACHABLE_CODE") true
            }
            if (left == null) {
                down = true
                try { while (still(awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == first.id })) Unit }
                finally { down = false }
            }
        }
    }.graphicsLayer { scaleX = scale; scaleY = scale }
}
