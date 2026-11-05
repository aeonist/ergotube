package app.freshtuber.ui.motion

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
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

/** True once a screen has fully arrived and is not leaving. */
val AnimatedVisibilityScope.settled: Boolean
    get() = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible

/** True from the moment a screen starts leaving. */
val AnimatedVisibilityScope.leaving: Boolean
    get() = transition.targetState == EnterExitState.PostExit
