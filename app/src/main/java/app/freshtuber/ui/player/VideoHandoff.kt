package app.freshtuber.ui.player

import android.graphics.Bitmap
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.ui.PlayerView
import app.freshtuber.ui.motion.Motion
import coil3.compose.AsyncImage
import coil3.toBitmap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Where a flying picture is going. */
enum class FlightTarget { Page, Mini, Short }

/**
 * Carries a video's picture between the places that show it (a feed card, the watch page,
 * the floating window, a Short), so it flies from one to the other instead of popping in.
 * A picture is a decoded frame ([ImageBitmap]) or a thumbnail address ([String]).
 */
@Stable
class VideoHandoff {
    /** One flight at a time; the newest replaces any other. */
    class Flight(val videoId: String, val picture: Any?, val from: Rect, val fromRadius: Float, val target: FlightTarget) {
        var to by mutableStateOf<Rect?>(null)
        var toRadius by mutableStateOf(0f)
        var landed by mutableStateOf(false)
        var arrived by mutableStateOf(false)
    }

    var flight by mutableStateOf<Flight?>(null)
        private set

    /** The last picture of each recent video, shown by a new surface until its own first frame. */
    private val posters = LinkedHashMap<String, Any>()

    /** The video whose watch page is open, so the floating window waits for its picture to arrive. */
    var pageVideoId: String? = null

    /** Set by the app root on every composition: leaving the page now shows the floating window. */
    var miniExpected: Boolean = false

    fun poster(videoId: String?): Any? = videoId?.let(posters::get)

    /** Thumbnail addresses of recently opened videos, for the background light. */
    private val thumbnails = LinkedHashMap<String, String>()

    fun thumbnail(videoId: String?): String? = videoId?.let(thumbnails::get)

    fun setPoster(videoId: String, picture: Any?) {
        if (picture == null) return
        posters.remove(videoId)
        posters[videoId] = picture
        while (posters.size > 4) posters.remove(posters.keys.first())
    }

    fun launch(videoId: String, picture: Any?, from: Rect, fromRadius: Float, target: FlightTarget, thumbnailUrl: String? = null) {
        setPoster(videoId, picture)
        (thumbnailUrl ?: picture as? String)?.let { url ->
            thumbnails.remove(videoId); thumbnails[videoId] = url
            while (thumbnails.size > 8) thumbnails.remove(thumbnails.keys.first())
        }
        flight = Flight(videoId, picture, from, fromRadius, target)
    }

    /** The arriving place reports where the picture ends up. */
    fun land(videoId: String, target: FlightTarget, to: Rect, radius: Float) {
        val f = flight ?: return
        if (f.videoId != videoId || f.target != target || f.landed) return
        if (f.to != to) f.to = to
        f.toRadius = radius
    }

    /** The arriving place shows the same picture now; the flying copy can go. */
    fun arrived(videoId: String, target: FlightTarget) {
        val f = flight ?: return
        if (f.videoId == videoId && f.target == target) f.arrived = true
    }

    /** True while a picture of [videoId] is on its way to [target]. */
    fun inFlight(videoId: String?, target: FlightTarget): Boolean {
        val f = flight ?: return false
        return f.videoId == videoId && f.target == target && !f.arrived
    }

    /**
     * True while the picture of [videoId] is flying toward [target] and has not landed yet:
     * the place itself stays hidden meanwhile, so the picture is never seen twice.
     */
    fun awaiting(videoId: String?, target: FlightTarget): Boolean {
        val f = flight ?: return false
        return f.videoId == videoId && f.target == target && !f.landed
    }

    /** True once the picture of [videoId] has left for [target] (the place it left from hides). */
    fun departed(videoId: String?, target: FlightTarget): Boolean {
        val f = flight ?: return false
        return f.videoId == videoId && f.target == target
    }

    fun end(f: Flight) { if (flight === f) flight = null }
}

val LocalVideoHandoff = staticCompositionLocalOf<VideoHandoff?> { null }

/** Points at a [VideoSurface]'s view so its current picture can be copied. */
class VideoSurfaceHandle {
    internal var view: PlayerView? = null

    /** True once the surface shows its video's first frame (a poster above it may go). */
    var firstFrame by mutableStateOf(false)
        internal set

    /** A copy of what the surface shows now, at half size; null if it shows nothing yet. */
    suspend fun capture(): ImageBitmap? {
        val surface = view?.videoSurfaceView ?: return null
        if (surface.width <= 1 || surface.height <= 1) return null
        val w = (surface.width / 2).coerceAtLeast(1)
        val h = (surface.height / 2).coerceAtLeast(1)
        return when (surface) {
            is TextureView -> if (surface.isAvailable) runCatching { surface.getBitmap(w, h) }.getOrNull()?.asImageBitmap() else null
            is SurfaceView -> {
                if (surface.holder.surface?.isValid != true) return null
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                // A copy can miss while the decoder swaps buffers; one more try a frame later.
                repeat(2) {
                    val result = withTimeoutOrNull(120) {
                        suspendCancellableCoroutine { cont ->
                            runCatching {
                                PixelCopy.request(surface, bitmap, { code -> if (cont.isActive) cont.resumeWith(Result.success(code)) },
                                    android.os.Handler(android.os.Looper.getMainLooper()))
                            }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(-1)) }
                        }
                    }
                    if (result == PixelCopy.SUCCESS) return bitmap.asImageBitmap()
                    if (result == null || result == PixelCopy.ERROR_SOURCE_NO_DATA) return null
                    androidx.compose.runtime.withFrameNanos { }
                }
                null
            }
            else -> null
        }
    }
}

/** The picture a card already shows (decoded, so it flies at once), else its address. */
fun flightPicture(image: coil3.Image?, url: String?): Any? =
    image?.let { runCatching { it.toBitmap().asImageBitmap() }.getOrNull() } ?: url

/** Draws a picture the way every video place does: cropped to fill, black behind. */
@Composable
fun VideoPicture(picture: Any?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    when (picture) {
        is ImageBitmap -> Image(picture, null, modifier, contentScale = contentScale)
        null -> Box(modifier)
        else -> AsyncImage(picture, null, modifier, contentScale = contentScale)
    }
}

/**
 * The flying picture, drawn above everything. It waits for the place it flies to (a short
 * while; with nowhere to go it simply goes), glides there and stays until that place shows
 * the same picture, so the hand-over is invisible.
 */
@Composable
fun VideoFlightOverlay(handoff: VideoHandoff) {
    val f = handoff.flight ?: return
    val density = LocalDensity.current
    val progress = remember(f) { Animatable(0f) }
    LaunchedEffect(f) {
        val to = withTimeoutOrNull(500) { snapshotFlow { f.to }.first { it != null } }
        if (to == null) { handoff.end(f); return@LaunchedEffect }
        // A spring starts from rest, so the picture lifts off gently instead of jumping.
        progress.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 360f, visibilityThreshold = 0.001f))
        f.landed = true
        withTimeoutOrNull(600) { snapshotFlow { f.arrived }.first { it } }
        handoff.end(f)
    }
    val to = f.to ?: f.from
    val p = progress.value.coerceIn(0f, 1f)
    val rect = lerp(f.from, to, p)
    val radius = lerp(f.fromRadius, f.toRadius, p).coerceAtLeast(0f)
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .shadow(lerp(0f, 14f, kotlin.math.sin(p * Math.PI).toFloat()).dp, RoundedCornerShape(with(density) { radius.toDp() }), clip = false)
            .clip(RoundedCornerShape(with(density) { radius.toDp() })),
    ) {
        VideoPicture(f.picture, Modifier.fillMaxSize())
    }
}
