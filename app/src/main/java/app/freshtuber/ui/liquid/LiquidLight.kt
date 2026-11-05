package app.freshtuber.ui.liquid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import app.freshtuber.data.settings.BackgroundMotion
import app.freshtuber.ui.theme.Oklab
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * One picture as the background light sees it: three pre-blurred sizes stand in for mipmap
 * levels, because AGSL has no textureLod. Bilinear sampling between them gives the blur.
 */
class LightFrame(val levels: Array<Bitmap>, val hue: Float?, val average: FloatArray = floatArrayOf(0.5f, 0.5f, 0.5f)) {
    private var shaders: Array<Shader>? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun shaders(): Array<Shader> = shaders ?: levels.map { bitmap ->
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
    }.toTypedArray<Shader>().also { shaders = it }

    companion object {
        /** Level sizes; the shader gets the same numbers. */
        val Sizes = arrayOf(48 to 27, 12 to 7, 3 to 2)

        /** Builds the levels by halving with bilinear filtering, which averages 2×2 blocks. */
        fun of(source: Bitmap): LightFrame {
            val src = if (source.config != Bitmap.Config.ARGB_8888) source.copy(Bitmap.Config.ARGB_8888, false) else source
            val l0 = Bitmap.createScaledBitmap(src, 48, 27, true)
            val l1 = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(l0, 24, 14, true), 12, 7, true)
            val l2 = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(l1, 6, 4, true), 3, 2, true)
            val pixels = IntArray(48 * 27)
            l0.getPixels(pixels, 0, 48, 0, 0, 48, 27)
            return LightFrame(arrayOf(l0, l1, l2), Oklab.dominantHue(pixels), averageLinear(pixels))
        }

        /** Mean color in linear light, for the flat battery-saving background. */
        fun averageLinear(pixels: IntArray): FloatArray {
            var r = 0f; var g = 0f; var b = 0f
            for (p in pixels) {
                r += Oklab.toLinear(((p shr 16) and 0xFF) / 255f)
                g += Oklab.toLinear(((p shr 8) and 0xFF) / 255f)
                b += Oklab.toLinear((p and 0xFF) / 255f)
            }
            val n = pixels.size.coerceAtLeast(1).toFloat()
            return floatArrayOf(r / n, g / n, b / n)
        }
    }
}

/** How much the background still has to animate. */
enum class LightActivity { Static, Idle, Busy }

private class Card(var url: String?, var rect: Rect)

private class Source(val url: String, var frame: LightFrame) {
    var w = 0f
    var v = 0f
    var target = 0f
    var rect = Rect.Zero
}

/**
 * State of the liquid-light background. Feed cards report their thumbnails, the player its
 * frames, the navigation and the remote their drops; [step] runs once per frame and the
 * background composable reads the results.
 */
@Stable
class LiquidLight(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext

    // Effective settings, after battery saver and the system animation switch.
    var motion = BackgroundMotion.Always
        private set
    var refraction by mutableStateOf(true)
        private set
    var grain by mutableStateOf(true)
        private set
    /** Battery saver: the field is rendered even smaller. */
    var lowRes by mutableStateOf(false)
        private set
    /** Energy saving: one flat color averaged from the picture in focus, no shader at all. */
    var flat by mutableStateOf(false)
        private set

    /** Accent hue in degrees, taken from the frame in focus. */
    var hue by mutableFloatStateOf(DefaultHue)
        private set

    /** Bumped when the background should be drawn again. */
    var redraw by mutableIntStateOf(0)
        private set
    /** Bumped when drops, the video or the theme moved (grain only needs these). */
    var geometry by mutableIntStateOf(0)
        private set

    /** True while an opaque screen (Shorts, full screen, picture-in-picture) hides the background. */
    var covered by mutableStateOf(false)
    private var routeCovered = false
    private var playerCovered = false

    var viewWidth = 1f
    var viewHeight = 1f

    private var darkTarget = -1f
    var dark = 0f
        private set

    var t = 0f; private set
    var flow = 0f; private set
    var surge = 0f; private set
    var wob = 0f; private set
    var blur = 0f; private set

    private var playerDepth = 0
    /** Player pages asked for; the light follows at the bottom of a short dip ([dip]). */
    private var requestedDepth = 0
    val playerMode get() = playerDepth > 0
    /**
     * 0..1: how far the light is dimmed while it changes between the feed and the player. The
     * change itself happens at the deepest point, so it never jumps in plain sight.
     */
    var dip = 0f; private set
    var videoRect = Rect(0f, 0f, 1f, 1f); private set
    val drops = arrayOf(Rect.Zero, Rect.Zero)
    val dropRadii = floatArrayOf(0f, 0f)

    /** Up to four frames and their normalized weights, plus the weighted light rect. */
    val slotFrames = arrayOfNulls<LightFrame>(4)
    val slotWeights = FloatArray(4)
    var lightRect = Rect(0f, 0f, 1f, 1f); private set

    private val cards = HashMap<Any, Card>()
    private val cache = LruCache<String, LightFrame>(48)
    private val loading = HashSet<String>()
    private val sources = ArrayList<Source>()
    private var focusKey: Any? = null
    private var calm = 0f
    private var framing = false
    /** Feed hue waiting for the list to come to rest. */
    private var pendingHue: Float? = null
    private var stillFor = 0f
    private var videoHueSet = false
    private var videoHueAt = -100f
    private var focusTop = 0f
    private var videoCurrent: LightFrame? = null
    private var videoPrevious: LightFrame? = null
    private var videoFade = 1f
    private var videoGrabbed = false
    private var animatorsCheckedAt = -10f
    private var animatorsOn = true
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun configure(motion: BackgroundMotion, refraction: Boolean, grain: Boolean, powerSave: Boolean, followPowerSave: Boolean, flat: Boolean = false) {
        val nextFlat = flat || (powerSave && followPowerSave)
        val nextMotion = when {
            !animatorsOn || nextFlat -> BackgroundMotion.Off
            else -> motion
        }
        val nextRefraction = refraction && !nextFlat
        val nextGrain = grain && !nextFlat
        lowRes = false
        if (nextMotion != this.motion || nextRefraction != this.refraction || nextGrain != this.grain || nextFlat != this.flat) {
            this.motion = nextMotion
            this.refraction = nextRefraction
            this.grain = nextGrain
            this.flat = nextFlat
            geometry++
            poke()
        }
    }

    private var themeDark = false
    private var immersive = false

    /** Dark theme target; switching sends a surge through the light. */
    fun setDark(isDark: Boolean) {
        themeDark = isDark
        applyDark()
    }

    /** Shorts are always lit as at night, whatever the theme. */
    fun setImmersive(value: Boolean) {
        if (immersive == value) return
        immersive = value
        applyDark()
    }

    private fun applyDark() {
        val target = if (themeDark || immersive) 1f else 0f
        if (darkTarget < 0f) { darkTarget = target; dark = target; return }
        if (target != darkTarget) {
            darkTarget = target
            if (motion != BackgroundMotion.Off && !immersive) surge = 1f
            poke()
        }
    }

    private val flatRgb = floatArrayOf(-1f, 0f, 0f)

    /**
     * The flat background color: the weighted mean of the pictures in focus, laid over the
     * day or night ground. Returns ARGB.
     */
    fun flatColor(): Int {
        var r = 0f; var g = 0f; var b = 0f; var sum = 0f
        for (i in 0 until 4) {
            val frame = slotFrames[i] ?: continue
            val w = slotWeights[i]
            if (w <= 0f) continue
            r += frame.average[0] * w; g += frame.average[1] * w; b += frame.average[2] * w; sum += w
        }
        if (sum > 0f) { flatRgb[0] = r / sum; flatRgb[1] = g / sum; flatRgb[2] = b / sum }
        else if (flatRgb[0] < 0f) { flatRgb[0] = 0.4f; flatRgb[1] = 0.36f; flatRgb[2] = 0.3f }
        // The mean of a busy picture is greyish: keep its hue, lift the chroma a little and fix
        // the lightness, so the color is calm but clearly the video's.
        val lab = Oklab.fromLinear(flatRgb[0], flatRgb[1], flatRgb[2])
        val chroma = (kotlin.math.hypot(lab[1], lab[2]) * 1.8f).coerceAtMost(0.075f)
        val hueDegrees = Math.toDegrees(kotlin.math.atan2(lab[2], lab[1]).toDouble()).toFloat()
        val day = Oklab.lch(0.88f, chroma, hueDegrees)
        val night = Oklab.lch(0.22f, chroma * 0.9f, hueDegrees)
        return androidx.compose.ui.graphics.lerp(day, night, dark).toArgb()
    }

    fun setRouteCovered(value: Boolean) { routeCovered = value; covered = routeCovered || playerCovered }
    fun setPlayerCovered(value: Boolean) { playerCovered = value; covered = routeCovered || playerCovered }

    // Feed cards.

    fun reportCard(key: Any, url: String?, rect: Rect) {
        val card = cards[key]
        if (card == null) cards[key] = Card(url, rect)
        else if (card.rect == rect && card.url == url) return
        else { card.url = url; card.rect = rect }
        if (!playerMode) poke()
    }

    fun removeCard(key: Any) { cards.remove(key) }

    // Player.

    fun enterPlayer() { requestedDepth++; videoGrabbed = false; if (covered) applyMode(); poke() }
    fun exitPlayer() { requestedDepth = (requestedDepth - 1).coerceAtLeast(0); if (covered) applyMode(); poke() }

    /** Under an opaque screen nobody sees the light, so it changes at once. */
    private fun applyMode() {
        playerDepth = requestedDepth
        if (playerDepth == 0) videoRect = Rect(0f, 0f, 1f, 1f)
        geometry++
    }

    /** Moves the effective player depth toward the requested one through the dip. */
    private fun stepMode(dt: Float): Boolean {
        val wantPlayer = requestedDepth > 0
        if (wantPlayer != playerMode) {
            dip = if (motion == BackgroundMotion.Off) 1f else (dip + dt / 0.16f).coerceAtMost(1f)
            if (dip >= 1f) applyMode()
            return true
        }
        playerDepth = requestedDepth
        if (dip > 0f) {
            dip = if (motion == BackgroundMotion.Off) 0f else (dip - dt / 0.34f).coerceAtLeast(0f)
            return true
        }
        return false
    }

    fun setVideoRect(rect: Rect) {
        if (rect.width < 1f || rect.height < 1f || rect == videoRect) return
        videoRect = rect
        geometry++
        poke()
    }

    /** The video's thumbnail lights the page until the first real frame arrives. */
    fun setVideoThumbnail(url: String?) {
        videoGrabbed = false
        videoHueSet = false
        if (url == null) return
        cache.get(url)?.let { pushVideo(it, grabbed = false); return }
        load(url) { frame -> if (!videoGrabbed) pushVideo(frame, grabbed = false) }
    }

    /** A low-resolution frame grabbed from the playing video. */
    fun pushVideoFrame(bitmap: Bitmap) {
        if (framing) return
        framing = true
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        scope.launch {
            try {
                val frame = withContext(Dispatchers.Default) { LightFrame.of(copy) }
                if (playerMode) pushVideo(frame, grabbed = true)
            } finally { framing = false }
        }
    }

    private fun pushVideo(frame: LightFrame, grabbed: Boolean) {
        if (grabbed) videoGrabbed = true
        videoPrevious = videoCurrent ?: frame
        videoCurrent = frame
        videoFade = 0f
        // Recoloring the app recomposes every screen, so the player changes its accent only for
        // the first real frame of a video and afterwards at most every 10 s for a big change.
        if (playerMode) frame.hue?.let { h ->
            val first = grabbed && !videoHueSet
            if (first || (grabbed && t - videoHueAt > 10f)) {
                if (updateHue(h, threshold = if (first) 3f else 30f)) videoHueAt = t
                if (first) { videoHueSet = true; videoHueAt = t }
            } else if (!grabbed && !videoHueSet) updateHue(h, threshold = 3f)
        }
        poke()
    }

    // Drops: 0 is the navigation bar, 1 the player remote.

    fun setDrop(index: Int, rect: Rect?, radius: Float) {
        val next = rect ?: Rect.Zero
        if (drops[index] == next && dropRadii[index] == radius) return
        drops[index] = next
        dropRadii[index] = radius
        geometry++
        poke()
    }

    fun poke() { wake.trySend(Unit) }

    private fun updateHue(value: Float, threshold: Float): Boolean {
        val diff = abs(((value - hue + 540f) % 360f) - 180f)
        if (diff >= threshold) { hue = value; return true }
        return false
    }

    private fun load(url: String, then: (LightFrame) -> Unit = {}) {
        if (!loading.add(url)) return
        scope.launch {
            try {
                val frame = withContext(Dispatchers.IO) {
                    val request = ImageRequest.Builder(app).data(url).size(96, 54).allowHardware(false).build()
                    val result = SingletonImageLoader.get(app).execute(request) as? SuccessResult ?: return@withContext null
                    LightFrame.of(result.image.toBitmap())
                }
                if (frame != null) { cache.put(url, frame); then(frame); poke() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No light from this picture; the previous one stays.
            } finally {
                loading.remove(url)
            }
        }
    }

    /** Runs the light while the background is on screen. */
    suspend fun run() {
        var last = 0L
        var lastDraw = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
            last = now
            when (step(dt)) {
                // The light is soft: 60 fps is plenty even on 120 Hz screens.
                LightActivity.Busy -> if (now - lastDraw >= 15_000_000L) { redraw++; lastDraw = now }
                // Slow drifting light is drawn at 30 fps.
                LightActivity.Idle -> if (now - lastDraw >= 32_000_000L) { redraw++; lastDraw = now }
                LightActivity.Static -> { redraw++; wake.receive(); last = 0L }
            }
        }
    }

    fun step(dt: Float): LightActivity {
        t += dt
        if (t - animatorsCheckedAt > 1f) {
            animatorsCheckedAt = t
            val on = android.animation.ValueAnimator.areAnimatorsEnabled()
            if (on != animatorsOn) { animatorsOn = on; if (!on) motion = BackgroundMotion.Off }
        }
        val off = motion == BackgroundMotion.Off
        var busy = false
        if (darkTarget >= 0f && dark != darkTarget) {
            dark += (darkTarget - dark) * (1f - exp(-3f * dt))
            if (abs(darkTarget - dark) < 0.003f) dark = darkTarget
            geometry++
            busy = true
        }
        surge = if (off) 0f else (surge * 0.25f.pow(dt)).let { if (it < 0.003f) 0f else it }
        wob = if (off) 0f else (wob * 0.2f.pow(dt)).let { if (it < 0.003f) 0f else it }

        if (stepMode(dt)) busy = true
        val transitioning = if (playerMode) stepPlayer(dt) else stepFeed(dt)
        if (transitioning) busy = true
        val speed = when (motion) {
            BackgroundMotion.Always -> 1f
            BackgroundMotion.OnTransition -> if (transitioning || wob > 0.01f || surge > 0.01f) 2.2f else 0f
            BackgroundMotion.Off -> 0f
        } * (1f + 3f * surge)
        flow += speed * dt
        if (surge > 0f || wob > 0.01f) busy = true
        return when {
            busy -> LightActivity.Busy
            speed > 0f || pendingHue != null -> LightActivity.Idle
            else -> LightActivity.Static
        }
    }

    private fun stepPlayer(dt: Float): Boolean {
        val fading = videoFade < 1f
        videoFade = (videoFade + dt / 0.3f).coerceAtMost(1f)
        slotFrames[0] = videoCurrent
        slotFrames[1] = if (videoFade < 1f) videoPrevious else null
        slotFrames[2] = null
        slotFrames[3] = null
        slotWeights[0] = if (videoCurrent != null) videoFade else 0f
        slotWeights[1] = if (videoFade < 1f && videoPrevious != null) 1f - videoFade else 0f
        slotWeights[2] = 0f
        slotWeights[3] = 0f
        if (slotWeights[0] + slotWeights[1] > 0f) {
            val sum = slotWeights[0] + slotWeights[1]
            slotWeights[0] /= sum
            slotWeights[1] /= sum
        }
        blur = 0f
        return fading
    }

    private fun stepFeed(dt: Float): Boolean {
        val line = viewHeight * FocusLine
        var best: Card? = null
        var bestKey: Any? = null
        var bestDistance = Float.MAX_VALUE
        for ((key, card) in cards) {
            val url = card.url ?: continue
            val r = card.rect
            if (r.width < 1f) continue
            val cached = cache.get(url)
            // Prefetch the cards a screen above and below, so focus never waits for a picture.
            if (cached == null) { if (r.bottom > -viewHeight && r.top < 2f * viewHeight) load(url); continue }
            if (r.bottom < 0f || r.top > viewHeight) continue
            val d = abs(r.center.y - line)
            if (d < bestDistance) { bestDistance = d; best = card; bestKey = key }
        }
        // Screens without thumbnails (settings, account) keep the last light, spread wider.
        val calmTarget = if (best == null) 2.5f else 0f
        val calmBefore = calm
        calm += (calmTarget - calm) * (1f - exp(-4f * dt))
        if (abs(calmTarget - calm) < 0.01f) calm = calmTarget
        var scrolled = calm != calmBefore
        if (best != null) {
            if (bestKey == focusKey) {
                val dy = abs(best.rect.top - focusTop)
                if (dy > 0.5f) {
                    scrolled = true
                    if (motion != BackgroundMotion.Off) wob = (wob + dy * 0.02f).coerceAtMost(1f)
                }
            }
            focusKey = bestKey
            focusTop = best.rect.top
            val url = best.url!!
            if (sources.none { it.url == url }) sources.add(Source(url, cache.get(url)!!))
            for (s in sources) {
                s.target = if (s.url == url) 1f else 0f
                if (s.url == url) s.rect = best.rect
                else cards.values.firstOrNull { it.url == s.url }?.let { s.rect = it.rect }
            }
            cache.get(url)?.hue?.let { h -> pendingHue = if (abs(((h - hue + 540f) % 360f) - 180f) >= 3f) h else null }
        }
        var moving = false
        val om = Omega
        for (s in sources) {
            val a = om * om * (s.target - s.w) - 2f * om * s.v
            s.v += a * dt
            s.w = (s.w + s.v * dt).coerceAtLeast(0f)
            if (abs(s.target - s.w) > 0.003f || abs(s.v) > 0.003f) moving = true
        }
        sources.removeAll { it.target == 0f && it.w < 0.002f && abs(it.v) < 0.002f }
        if (sources.size > 4) {
            sources.sortByDescending { it.w + it.target }
            while (sources.size > 4) sources.removeAt(sources.size - 1)
        }
        val sum = sources.sumOf { it.w.toDouble() }.toFloat()
        var maxW = 0f
        var l = 0f; var tp = 0f; var rt = 0f; var bt = 0f
        for (i in 0 until 4) {
            val s = sources.getOrNull(i)
            val w = if (s != null && sum > 0f) s.w / sum else 0f
            slotFrames[i] = if (w > 0f) s?.frame else null
            slotWeights[i] = w
            if (s != null && w > 0f) {
                maxW = maxOf(maxW, w)
                l += s.rect.left * w; tp += s.rect.top * w; rt += s.rect.right * w; bt += s.rect.bottom * w
            }
        }
        if (maxW > 0f && rt - l >= 1f && bt - tp >= 1f) lightRect = Rect(l, tp, rt, bt)
        blur = (if (maxW > 0f) (minOf(1f, (1f - maxW) * 2f) * 2.6f) else 0f) + calm
        // The accent follows the focused frame only once the list rests: recoloring while
        // scrolling would recompose the whole app on every frame.
        stillFor = if (moving || scrolled) 0f else stillFor + dt
        pendingHue?.let { if (stillFor > 0.35f) { updateHue(it, threshold = 3f); pendingHue = null } }
        return moving || scrolled
    }

    companion object {
        /** Warm amber until a picture gives a hue. */
        const val DefaultHue = 62f
        /** The focused card is the one nearest this fraction of the screen height. */
        const val FocusLine = 0.42f
        /** Critically damped spring, rad/s. */
        const val Omega = 8.5f
    }
}

val LocalLiquidLight = staticCompositionLocalOf<LiquidLight?> { null }
