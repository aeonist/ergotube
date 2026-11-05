package app.freshtuber.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * How a foldable is held. [tabletop] is a half-open phone with a horizontal fold (a flip phone
 * standing in flex mode, or a book-style one lying in tabletop): the video goes above the fold
 * and the controls below it. [hingeTop]/[hingeBottom] are window pixels.
 */
@Immutable
data class FoldPosture(val tabletop: Boolean = false, val hingeTop: Int = 0, val hingeBottom: Int = 0)

val LocalFoldPosture = staticCompositionLocalOf { FoldPosture() }

/** Follows the window's folding feature; plain phones always report a flat posture. */
@Composable
fun rememberFoldPosture(activity: ComponentActivity): FoldPosture {
    var posture by remember { mutableStateOf(FoldPosture()) }
    LaunchedEffect(activity) {
        runCatching {
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
                val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                posture = if (fold != null && fold.state == FoldingFeature.State.HALF_OPENED && fold.orientation == FoldingFeature.Orientation.HORIZONTAL)
                    FoldPosture(true, fold.bounds.top, fold.bounds.bottom) else FoldPosture()
            }
        }
    }
    return posture
}

/** An unfolded inner screen or a tablet: wide enough for two panes and a grid of cards. */
@Composable
fun isLargeScreen(): Boolean = LocalConfiguration.current.smallestScreenWidthDp >= 600

/** Columns of video cards for the current width. */
@Composable
fun feedColumns(): Int {
    val width = LocalConfiguration.current.screenWidthDp
    return when { width >= 1200 -> 3; width >= 600 -> 2; else -> 1 }
}
