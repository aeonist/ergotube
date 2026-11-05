package app.freshtuber.ui.liquid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.freshtuber.data.settings.AppFeature
import app.freshtuber.ui.theme.LocalAppPreferences

/**
 * The light behind every list and the portrait player. Android 13+ runs the AGSL scene; older
 * versions stretch the focused picture (blurred on 12) without refraction or grain.
 */
@Composable
fun LiquidBackground(light: LiquidLight, modifier: Modifier = Modifier) {
    val preferences = LocalAppPreferences.current
    val powerSave = rememberPowerSave()
    SideEffect {
        light.configure(preferences.motion, preferences.enabled(AppFeature.LiquidRefraction), preferences.enabled(AppFeature.LiquidGrain),
            powerSave, preferences.enabled(AppFeature.LiquidPowerSaver), preferences.enabled(AppFeature.LiquidFlat))
    }
    if (light.covered) return
    LaunchedEffect(light) { light.run() }
    Box(modifier.fillMaxSize().onSizeChanged { light.viewWidth = it.width.toFloat(); light.viewHeight = it.height.toFloat(); light.poke() }) {
        when {
            light.flat -> FlatLight(light)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> ShaderLight(light)
            else -> FallbackLight(light)
        }
    }
}

/**
 * Energy saving: one color, the mean of the picture in focus, and plain drops. Nothing moves;
 * the color only fades when the picture changes.
 */
@Composable
private fun FlatLight(light: LiquidLight) {
    val dropArgb = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f).toArgb()
    Canvas(Modifier.fillMaxSize()) { drawFlatLight(light, dropArgb) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlatLight(light: LiquidLight, dropArgb: Int) {
    light.redraw
    drawRect(androidx.compose.ui.graphics.Color(light.flatColor()))
    drawFlatDrops(light, dropArgb)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderLight(light: LiquidLight) {
    val px = LocalDensity.current.density
    val field = remember { RuntimeShader(LiquidShaders.FIELD) }
    val drop = remember { RuntimeShader(LiquidShaders.DROP) }
    val fieldBrush = remember(field) { ShaderBrush(field) }
    val dropBrush = remember(drop) { ShaderBrush(drop) }
    val noise = remember { NoiseTile.shader() }
    val empty = remember {
        BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) },
            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    // 1. The field: about a quarter of the screen width (a sixth with battery saver), scaled up.
    val divisor = if (light.lowRes) 6f else 4f
    // Scaled layers are drawn with nearest filtering on some GPUs (Pixel 7: visible pixel
    // steps), so a light blur at full resolution smooths the upscale.
    Box(Modifier.fillMaxSize().blur((divisor * 0.9f).dp, BlurredEdgeTreatment.Rectangle)) {
        Canvas(Modifier.scaledDown(divisor).graphicsLayer {
            scaleX = divisor; scaleY = divisor
            transformOrigin = TransformOrigin(0f, 0f)
            compositingStrategy = CompositingStrategy.Offscreen
        }) {
            light.redraw
            field.bind(light, px, scale = light.viewWidth / size.width.coerceAtLeast(1f), empty, noise)
            drawRect(fieldBrush)
        }
    }
    // 2. Grain: a baked tile, drawn under the drops and the content.
    if (light.grain) GrainLayer(light, px)
    // 3. Drops at half resolution, shaded only inside their rounded rects.
    if (light.drops.any { it.width >= 1f }) Canvas(Modifier.blur(0.8.dp, BlurredEdgeTreatment.Unbounded).scaledDown(2f).graphicsLayer {
        scaleX = 2f; scaleY = 2f
        transformOrigin = TransformOrigin(0f, 0f)
        compositingStrategy = CompositingStrategy.Offscreen
    }) {
        light.redraw
        light.geometry
        val scale = light.viewWidth / size.width.coerceAtLeast(1f)
        drop.bind(light, px, scale, empty, noise)
        for (i in 0..1) {
            val r = light.drops[i]
            if (r.width < 1f || r.height < 1f) continue
            drop.setRect("uD", r)
            drop.setFloatUniform("uRd", light.dropRadii[i])
            drawRoundRect(dropBrush, Offset(r.left / scale, r.top / scale), Size(r.width / scale, r.height / scale),
                CornerRadius(light.dropRadii[i] / scale))
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.bind(light: LiquidLight, px: Float, scale: Float, empty: Shader, noise: Shader) {
    setInputShader("uNoise", noise)
    setFloatUniform("uRes", light.viewWidth, light.viewHeight)
    // Small, wrapped time: large values lose float precision on mobile GPUs.
    setFloatUniform("uT", light.t % 600f)
    setFloatUniform("uFlow", light.flow)
    setFloatUniform("uDark", light.dark)
    setFloatUniform("uMode", if (light.playerMode) 1f else 0f)
    setFloatUniform("uBlur", light.blur)
    setFloatUniform("uSurge", light.surge)
    setFloatUniform("uWob", light.wob)
    setFloatUniform("uPx", px)
    setFloatUniform("uRefr", if (light.refraction) 1f else 0f)
    setFloatUniform("uScale", scale)
    val w = light.slotWeights
    setFloatUniform("uW", w[0], w[1], w[2], w[3])
    setRect("uRect", light.lightRect)
    setRect("uVid", light.videoRect)
    setRect("uP0", light.drops[0])
    setRect("uP1", light.drops[1])
    setFloatUniform("uR0", light.dropRadii[0])
    setFloatUniform("uR1", light.dropRadii[1])
    val sizes = LightFrame.Sizes
    setFloatUniform("uS0", sizes[0].first.toFloat(), sizes[0].second.toFloat())
    setFloatUniform("uS1", sizes[1].first.toFloat(), sizes[1].second.toFloat())
    setFloatUniform("uS2", sizes[2].first.toFloat(), sizes[2].second.toFloat())
    Slots.forEachIndexed { slot, names ->
        val shaders = light.slotFrames[slot]?.shaders()
        names.forEachIndexed { level, name -> setInputShader(name, shaders?.get(level) ?: empty) }
    }
}

/** The two grain tiles (day and night), baked off the main thread, crossfaded with the theme. */
@Composable
private fun GrainLayer(light: LiquidLight, px: Float) {
    val tiles by androidx.compose.runtime.produceState<Pair<Bitmap, Bitmap>?>(null, px) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { GrainTile.make(px, dark = false) to GrainTile.make(px, dark = true) }
    }
    val pair = tiles ?: return
    val brushes = remember(pair) {
        pair.toList().map { ShaderBrush(BitmapShader(it, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)) }
    }
    Canvas(Modifier.fillMaxSize()) {
        light.geometry
        val d = light.dark
        if (d < 1f) drawRect(brushes[0], alpha = 1f - d)
        if (d > 0f) drawRect(brushes[1], alpha = d)
    }
}

private val Slots = arrayOf(arrayOf("a0", "a1", "a2"), arrayOf("b0", "b1", "b2"), arrayOf("c0", "c1", "c2"), arrayOf("d0", "d1", "d2"))

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setRect(name: String, r: Rect) = setFloatUniform(name, r.left, r.top, r.width, r.height)

/** Measures the content at 1/[divisor] of the size it gets; it is drawn scaled back up. */
private fun Modifier.scaledDown(divisor: Float) = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 1
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 1
    val w = kotlin.math.ceil(width / divisor).toInt().coerceAtLeast(1)
    val h = kotlin.math.ceil(height / divisor).toInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(width, height) { placeable.place(0, 0) }
}

/** Below Android 13: the focused picture, small and stretched, so bilinear sampling blurs it. */
@Composable
private fun FallbackLight(light: LiquidLight) {
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(36.dp, BlurredEdgeTreatment.Rectangle) else Modifier
    val drop = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(Modifier.fillMaxSize().then(blurModifier)) {
        light.redraw
        val strength = if (light.dark > 0.5f) 0.26f else 0.34f
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0 else 1
        for (i in 0 until 4) {
            val frame = light.slotFrames[i] ?: continue
            val weight = light.slotWeights[i]
            if (weight <= 0f) continue
            val bitmap = frame.levels[level]
            drawImage(bitmap.asImageBitmap(), srcOffset = IntOffset.Zero, srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                alpha = (weight * strength).coerceIn(0f, 1f), filterQuality = FilterQuality.Low)
        }
    }
    // The color goes in as an Int: R8 once merged a lambda capturing (light, Color) with a
    // Material lambda capturing (Color, State) and emitted code the verifier rejects, which
    // crashed every bottom sheet in release builds. Drawing in a plain function avoids it.
    val dropArgb = drop.copy(alpha = 0.86f).toArgb()
    Canvas(Modifier.fillMaxSize()) { drawFlatDrops(light, dropArgb) }
}

/** The navigation and remote drops as plain rounded rects. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlatDrops(light: LiquidLight, argb: Int) {
    light.geometry
    val color = androidx.compose.ui.graphics.Color(argb)
    for (i in 0..1) {
        val r = light.drops[i]
        if (r.width < 1f || r.height < 1f) continue
        drawRoundRect(color, Offset(r.left, r.top), Size(r.width, r.height), CornerRadius(light.dropRadii[i]))
    }
}

/** System battery saver, followed live. */
@Composable
fun rememberPowerSave(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var saving by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { saving = power?.isPowerSaveMode == true }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return saving
}
