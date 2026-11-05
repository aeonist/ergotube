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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** True while the app is a floating picture-in-picture window. */
val LocalInPictureInPicture = compositionLocalOf { false }
val LocalVideoBounds = compositionLocalOf<(android.graphics.Rect) -> Unit> { {} }

/**
 * The video picture with subtitles; controls are drawn in Compose on top of it.
 *
 * [poster] (a frame or a thumbnail) covers the surface until it shows the first frame of
 * [mediaId], so a new surface never flashes black. While [active] is false the surface is not
 * there at all and only the poster shows: screens keep it off while they fly in or out, because
 * a surface view ignores fading and blur.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier, zoomToFill: Boolean = false, texture: Boolean = false,
    onFrame: ((android.graphics.Bitmap) -> Unit)? = null, active: Boolean = true, poster: Any? = null, mediaId: String? = null,
    handle: VideoSurfaceHandle? = null, posterScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop) {
    val ownHandle = remember { VideoSurfaceHandle() }
    val surfaceHandle = handle ?: ownHandle
    var firstFrame by remember(player, mediaId) { mutableStateOf(false) }
    val reportBounds = LocalVideoBounds.current
    androidx.compose.foundation.layout.Box(modifier.onGloballyPositioned {
        if (active) {
            val bounds = it.boundsInWindow()
            reportBounds(android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))
        }
    }) {
        if (active) {
            if (onFrame != null) FrameGrabber(player, surfaceHandle, onFrame)
            androidx.compose.runtime.DisposableEffect(player, mediaId) {
                firstFrame = false
                val listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        if (mediaId == null || player.currentMediaItem?.mediaId == mediaId) firstFrame = true
                    }
                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                        if (mediaId != null && mediaItem?.mediaId != mediaId) firstFrame = false
                    }
                }
                player.addListener(listener)
                onDispose { player.removeListener(listener); firstFrame = false }
            }
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { context ->
                    // A texture view can be clipped and dragged around; the default surface view is cheaper.
                    if (texture) (android.view.LayoutInflater.from(context).inflate(app.freshtuber.R.layout.texture_player_view, android.widget.FrameLayout(context), false) as PlayerView).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        this.player = player
                    } else PlayerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setKeepContentOnPlayerReset(true)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        this.player = player
                    }
                },
                update = { view ->
                    surfaceHandle.view = view
                    if (view.player !== player) view.player = player
                    view.resizeMode = if (zoomToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                onRelease = { view -> view.player = null; if (surfaceHandle.view === view) surfaceHandle.view = null },
            )
        }
        androidx.compose.runtime.SideEffect { surfaceHandle.firstFrame = active && firstFrame }
        if (poster != null) {
            val hidden = active && firstFrame
            val alpha = remember { androidx.compose.animation.core.Animatable(1f) }
            LaunchedEffect(hidden) {
                if (hidden) { delay(40); alpha.animateTo(0f, androidx.compose.animation.core.tween(280, easing = app.freshtuber.ui.motion.Motion.Emphasized)) }
                else alpha.snapTo(1f)
            }
            if (alpha.value > 0f) VideoPicture(poster, Modifier.matchParentSize().graphicsLayer { this.alpha = alpha.value },
                contentScale = posterScale)
        }
    }
}

/**
 * Copies a tiny frame (64×36) of the playing video every 300 ms for the background light:
 * PixelCopy from the surface view, or the texture view's bitmap. Paused video is read less often,
 * and the flat battery-saving background reads one frame every 2 s.
 */
@Composable
@OptIn(UnstableApi::class)
private fun FrameGrabber(player: Player, handle: VideoSurfaceHandle, onFrame: (android.graphics.Bitmap) -> Unit) {
    val callback = androidx.compose.runtime.rememberUpdatedState(onFrame)
    val light = app.freshtuber.ui.liquid.LocalLiquidLight.current
    LaunchedEffect(player) {
        val target = android.graphics.Bitmap.createBitmap(64, 36, android.graphics.Bitmap.Config.ARGB_8888)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var lastWasPlaying = true
        while (true) {
            // The flat battery-saving background needs a new color only now and then.
            delay(if (light?.flat == true) 2_000L else if (player.isPlaying || lastWasPlaying) 300L else 1_000L)
            lastWasPlaying = player.isPlaying
            if (player.videoSize.width <= 0) continue
            val ok = when (val surface = handle.view?.videoSurfaceView) {
                is android.view.SurfaceView -> {
                    if (surface.holder.surface?.isValid != true || surface.width <= 0) false
                    else kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                        runCatching {
                            android.view.PixelCopy.request(surface, target, { result -> if (cont.isActive) cont.resumeWith(Result.success(result == android.view.PixelCopy.SUCCESS)) }, handler)
                        }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(false)) }
                    }
                }
                is android.view.TextureView -> surface.isAvailable && runCatching { surface.getBitmap(target) }.getOrNull() != null
                else -> false
            }
            if (ok) callback.value(target)
        }
    }
}

/**
 * Width / height of [player]'s picture, or null while unknown. With [mediaId], only the size of
 * that video counts, so a page never takes the previous video's shape.
 */
@Composable
fun rememberVideoAspect(player: Player, mediaId: String? = null): Float? {
    fun read(): Float? {
        if (mediaId != null && player.currentMediaItem?.mediaId != mediaId) return null
        val size = player.videoSize
        return if (size.width > 0 && size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else null
    }
    var aspect by remember(player, mediaId) { mutableStateOf(read()) }
    androidx.compose.runtime.DisposableEffect(player, mediaId) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { aspect = read() }
        }
        player.addListener(listener)
        aspect = read()
        onDispose { player.removeListener(listener) }
    }
    return aspect
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
