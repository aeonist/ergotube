package app.freshtuber.ui.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** True while the app is a floating picture-in-picture window. */
val LocalInPictureInPicture = compositionLocalOf { false }

/** The video picture with subtitles; controls are drawn in Compose on top of it. */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier, zoomToFill: Boolean = false) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setKeepContentOnPlayerReset(true)
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.resizeMode = if (zoomToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        onRelease = { view -> view.player = null },
    )
}

data class Progress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
)

/** Player progress, read four times a second while the screen shows it. */
@Composable
fun rememberProgress(player: Player): Progress {
    val progress by produceState(Progress(), player) {
        while (true) {
            value = Progress(
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0),
                bufferedMs = player.bufferedPosition,
                isPlaying = player.isPlaying,
                buffering = player.playbackState == Player.STATE_BUFFERING,
                ended = player.playbackState == Player.STATE_ENDED,
            )
            delay(250)
        }
    }
    return progress
}

/** True for [timeoutMs] after the last [poke], then false; used to auto-hide controls. */
class AutoHide(private val timeoutMs: Long) {
    var visible by mutableStateOf(true)
        private set
    var lastPoke by mutableLongStateOf(System.currentTimeMillis())
        private set

    fun poke() {
        visible = true
        lastPoke = System.currentTimeMillis()
    }

    fun toggle() {
        if (visible) visible = false else poke()
    }

    @Composable
    fun Run(keepVisible: Boolean) {
        LaunchedEffect(lastPoke, visible, keepVisible) {
            if (keepVisible) { visible = true; return@LaunchedEffect }
            if (!visible) return@LaunchedEffect
            delay(timeoutMs)
            visible = false
        }
    }
}

@Composable
fun rememberAutoHide(timeoutMs: Long = 3_000): AutoHide = remember { AutoHide(timeoutMs) }
