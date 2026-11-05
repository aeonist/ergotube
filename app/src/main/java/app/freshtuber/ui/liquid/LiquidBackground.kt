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
            powerSave, preferences.enabled(AppFeature.LiquidPowerSaver))
    }
    if (light.covered) return
    LaunchedEffect(light) { light.run() }
    Box(modifier.fillMaxSize().onSizeChanged { light.viewWidth = it.width.toFloat(); light.viewHeight = it.height.toFloat(); light.poke() }) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ShaderLight(light) else FallbackLight(light)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderLight(light: LiquidLight) {
    val px = LocalDensity.current.density
    val scene = remember { RuntimeShader(LiquidShaders.SCENE) }
    val grain = remember { RuntimeShader(LiquidShaders.GRAIN) }
    val sceneBrush = remember(scene) { ShaderBrush(scene) }
    val grainBrush = remember(grain) { ShaderBrush(grain) }
    val empty = remember {
        BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) },
            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    // Half resolution: the layer is drawn at half size and scaled up by two.
    Canvas(Modifier.halfSize().graphicsLayer {
        scaleX = 2f; scaleY = 2f
        transformOrigin = TransformOrigin(0f, 0f)
        compositingStrategy = CompositingStrategy.Offscreen
    }) {
        light.redraw
        val scale = light.viewWidth / size.width.coerceAtLeast(1f)
        scene.setFloatUniform("uRes", light.viewWidth, light.viewHeight)
        scene.setFloatUniform("uT", light.t)
        scene.setFloatUniform("uFlow", light.flow)
        scene.setFloatUniform("uDark", light.dark)
        scene.setFloatUniform("uMode", if (light.playerMode) 1f else 0f)
        scene.setFloatUniform("uBlur", light.blur)
        scene.setFloatUniform("uSurge", light.surge)
        scene.setFloatUniform("uWob", light.wob)
        scene.setFloatUniform("uPx", px)
        scene.setFloatUniform("uRefr", if (light.refraction) 1f else 0f)
        scene.setFloatUniform("uScale", scale)
        val w = light.slotWeights
        scene.setFloatUniform("uW", w[0], w[1], w[2], w[3])
        scene.setRect("uRect", light.lightRect)
        scene.setRect("uVid", light.videoRect)
        scene.setRect("uP0", light.drops[0])
        scene.setRect("uP1", light.drops[1])
        scene.setFloatUniform("uR0", light.dropRadii[0])
        scene.setFloatUniform("uR1", light.dropRadii[1])
        val sizes = LightFrame.Sizes
        scene.setFloatUniform("uS0", sizes[0].first.toFloat(), sizes[0].second.toFloat())
        scene.setFloatUniform("uS1", sizes[1].first.toFloat(), sizes[1].second.toFloat())
        scene.setFloatUniform("uS2", sizes[2].first.toFloat(), sizes[2].second.toFloat())
        Slots.forEachIndexed { slot, names ->
            val shaders = light.slotFrames[slot]?.shaders()
            names.forEachIndexed { level, name -> scene.setInputShader(name, shaders?.get(level) ?: empty) }
        }
        drawRect(sceneBrush)
    }
    if (light.grain) Canvas(Modifier.fillMaxSize()) {
        light.geometry
        grain.setFloatUniform("uPx", px)
        grain.setFloatUniform("uDark", light.dark)
        grain.setFloatUniform("uMode", if (light.playerMode) 1f else 0f)
        grain.setRect("uVid", light.videoRect)
        grain.setRect("uP0", light.drops[0])
        grain.setRect("uP1", light.drops[1])
        grain.setFloatUniform("uR0", light.dropRadii[0])
        grain.setFloatUniform("uR1", light.dropRadii[1])
        drawRect(grainBrush)
    }
}

private val Slots = arrayOf(arrayOf("a0", "a1", "a2"), arrayOf("b0", "b1", "b2"), arrayOf("c0", "c1", "c2"), arrayOf("d0", "d1", "d2"))

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setRect(name: String, r: Rect) = setFloatUniform(name, r.left, r.top, r.width, r.height)

/** Measures the content at half the size it gets; it is drawn scaled by two. */
private fun Modifier.halfSize() = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 1
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 1
    val placeable = measurable.measure(Constraints.fixed(((width + 1) / 2).coerceAtLeast(1), ((height + 1) / 2).coerceAtLeast(1)))
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
    Canvas(Modifier.fillMaxSize()) {
        light.geometry
        for (i in 0..1) {
            val r = light.drops[i]
            if (r.width < 1f || r.height < 1f) continue
            drawRoundRect(drop.copy(alpha = 0.86f), Offset(r.left, r.top), Size(r.width, r.height), CornerRadius(light.dropRadii[i]))
        }
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
