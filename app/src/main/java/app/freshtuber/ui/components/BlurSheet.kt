package app.freshtuber.ui.components

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.freshtuber.ui.motion.Motion
import app.freshtuber.ui.motion.blurFade
import kotlinx.coroutines.launch

/**
 * The app's bottom sheet. It looks like the Material one, but it comes into focus out of a blur
 * in its place and blurs away again, instead of sliding up from the edge. A finger can still pull
 * it down (the sheet follows the finger); let go far enough and it blurs out where it is.
 * Tap outside or Back closes it the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlurSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dismiss by rememberUpdatedState(onDismissRequest)
    val shown = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var sheetHeight by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(Motion.ENTER_MS - 60, easing = Motion.Decelerate)) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                shown.animateTo(0f, tween(Motion.EXIT_MS, easing = Motion.Accelerate))
                dismiss()
            }
        }
    }
    val release: (Float) -> Unit = { velocity ->
        if (drag.value > sheetHeight * 0.28f || velocity > 1800f) close()
        else scope.launch { drag.animateTo(0f, Motion.settle()) }
    }
    // Pulling down at the top of a list pulls the sheet; pushing up gives the list back first.
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && drag.value > 0f && source == NestedScrollSource.UserInput) {
                    val used = maxOf(available.y, -drag.value)
                    scope.launch { drag.snapTo(drag.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    scope.launch { drag.snapTo(drag.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag.value > 0f) { release(available.y); return available }
                return Velocity.Zero
            }
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setWindowAnimations(0)
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                @Suppress("DEPRECATION")
                it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown.value * (1f - (drag.value / sheetHeight).coerceIn(0f, 1f) * 0.6f) }
                .background(BottomSheetDefaults.ScrimColor)
                .pointerInput(Unit) { detectTapGestures { close() } })
            Surface(
                modifier = modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(top = 24.dp).widthIn(max = 640.dp).fillMaxWidth()
                    .onSizeChanged { sheetHeight = maxOf(1, it.height) }
                    .graphicsLayer { translationY = drag.value }
                    .blurFade({ shown.value }, max = 20.dp, fromScale = 0.96f)
                    .draggable(rememberDraggableState { delta -> scope.launch { drag.snapTo(maxOf(0f, drag.value + delta)) } },
                        Orientation.Vertical, onDragStopped = { velocity -> release(velocity) })
                    .nestedScroll(nested),
                shape = BottomSheetDefaults.ExpandedShape,
                color = BottomSheetDefaults.ContainerColor,
                tonalElevation = BottomSheetDefaults.Elevation,
            ) {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
                    BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                    content()
                }
            }
        }
    }
}
