package app.freshtuber.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * OKLab/OKLCH helpers (Björn Ottosson's matrices). The accent hue comes from the frame in
 * focus; lightness and chroma of every role are fixed, so contrast holds for any hue.
 */
object Oklab {
    /** Linear sRGB channel from an 8-bit sRGB value 0..1. */
    fun toLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    private fun toGamma(c: Float): Float = if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f

    /** OKLab (L, a, b) of linear sRGB. */
    fun fromLinear(r: Float, g: Float, b: Float): FloatArray {
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        return floatArrayOf(
            0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
            1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
            0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
        )
    }

    private fun linearOf(lightness: Float, a: Float, b: Float): FloatArray {
        val l = (lightness + 0.3963377774f * a + 0.2158037573f * b).let { it * it * it }
        val m = (lightness - 0.1055613458f * a - 0.0638541728f * b).let { it * it * it }
        val s = (lightness - 0.0894841775f * a - 1.2914855480f * b).let { it * it * it }
        return floatArrayOf(
            4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
            -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
            -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
        )
    }

    /** sRGB color of OKLCH; chroma is reduced until the color fits the sRGB gamut. */
    fun lch(lightness: Float, chroma: Float, hueDegrees: Float, alpha: Float = 1f): Color {
        val h = Math.toRadians(hueDegrees.toDouble())
        var c = chroma
        var rgb = linearOf(lightness, c * cos(h).toFloat(), c * sin(h).toFloat())
        var guard = 0
        while (rgb.any { it < -0.0005f || it > 1.0005f } && guard++ < 24) {
            c *= 0.9f
            rgb = linearOf(lightness, c * cos(h).toFloat(), c * sin(h).toFloat())
        }
        return Color(toGamma(rgb[0].coerceIn(0f, 1f)), toGamma(rgb[1].coerceIn(0f, 1f)), toGamma(rgb[2].coerceIn(0f, 1f)), alpha)
    }

    /**
     * Chroma-weighted average hue of ARGB pixels, in degrees; null for a nearly grey picture.
     * Summing (a, b) weights every pixel's hue direction by its chroma.
     */
    fun dominantHue(pixels: IntArray): Float? {
        var sa = 0.0
        var sb = 0.0
        for (p in pixels) {
            val r = toLinear(((p shr 16) and 0xFF) / 255f)
            val g = toLinear(((p shr 8) and 0xFF) / 255f)
            val b = toLinear((p and 0xFF) / 255f)
            val lab = fromLinear(r, g, b)
            sa += lab[1]
            sb += lab[2]
        }
        if (pixels.isEmpty() || hypot(sa, sb) / pixels.size < 0.008) return null
        return ((Math.toDegrees(atan2(sb, sa)) + 360.0) % 360.0).toFloat()
    }
}

/** Grounds under the light: muted day and deep night. */
object LiquidGround {
    val Light = Color(0xFFE6E1DB)
    val Dark = Color(0xFF070605)
}

/** A role's OKLCH lightness and chroma in the light and the dark theme. */
private class Tone(val lightL: Float, val lightC: Float, val darkL: Float, val darkC: Float) {
    fun at(hue: Float, dark: Float, alpha: Float = 1f) =
        Oklab.lch(lerp(lightL, darkL, dark), lerp(lightC, darkC, dark), hue, alpha)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private val OnSurface = Tone(.21f, .02f, .95f, .015f)
private val OnSurfaceVariant = Tone(.40f, .035f, .78f, .035f)
private val Primary = Tone(.45f, .12f, .84f, .11f)
private val OnPrimary = Tone(1f, 0f, .24f, .07f)
private val SecondaryContainer = Tone(.85f, .065f, .34f, .07f)
private val OnSecondaryContainer = Tone(.24f, .06f, .93f, .04f)
private val PrimaryContainer = Tone(.88f, .05f, .30f, .06f)
private val SurfaceVariant = Tone(.86f, .015f, .24f, .012f)
private val ContainerLowest = Tone(.95f, .01f, .10f, .008f)
private val ContainerLow = Tone(.925f, .012f, .16f, .01f)
private val Container = Tone(.905f, .014f, .20f, .012f)
private val ContainerHigh = Tone(.88f, .016f, .24f, .014f)
private val ContainerHighest = Tone(.85f, .018f, .28f, .016f)
private val Outline = Tone(.60f, .02f, .52f, .02f)
private val OutlineVariant = Tone(.82f, .015f, .30f, .012f)
private val InverseOnSurface = Tone(.95f, .01f, .21f, .02f)

/**
 * The liquid-light scheme for [hue] (degrees). [dark] runs 0..1 so a theme switch can fade
 * between the two; the ground stays plain because the shader draws the light above it.
 */
fun liquidColorScheme(hue: Float, dark: Float, amoled: Boolean = false): ColorScheme {
    val ground = if (amoled && dark > 0.5f) Color.Black else androidx.compose.ui.graphics.lerp(LiquidGround.Light, LiquidGround.Dark, dark)
    val onSurface = OnSurface.at(hue, dark)
    val onVariant = OnSurfaceVariant.at(hue, dark)
    val primary = Primary.at(hue, dark)
    val onPrimary = OnPrimary.at(hue, dark)
    val secondaryContainer = SecondaryContainer.at(hue, dark)
    val onSecondaryContainer = OnSecondaryContainer.at(hue, dark)
    val base = if (dark > 0.5f) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = PrimaryContainer.at(hue, dark), onPrimaryContainer = onSecondaryContainer,
        inversePrimary = Primary.at(hue, 1f - dark),
        secondary = onVariant, onSecondary = onPrimary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = primary, onTertiary = onPrimary,
        tertiaryContainer = secondaryContainer, onTertiaryContainer = onSecondaryContainer,
        background = ground, onBackground = onSurface,
        surface = ground, onSurface = onSurface,
        surfaceVariant = SurfaceVariant.at(hue, dark), onSurfaceVariant = onVariant,
        surfaceTint = primary,
        inverseSurface = onSurface, inverseOnSurface = InverseOnSurface.at(hue, dark),
        surfaceBright = ContainerLowest.at(hue, dark), surfaceDim = ContainerHighest.at(hue, dark),
        surfaceContainerLowest = if (amoled && dark > 0.5f) Color.Black else ContainerLowest.at(hue, dark),
        surfaceContainerLow = ContainerLow.at(hue, dark),
        surfaceContainer = Container.at(hue, dark),
        surfaceContainerHigh = ContainerHigh.at(hue, dark),
        surfaceContainerHighest = ContainerHighest.at(hue, dark),
        outline = Outline.at(hue, dark), outlineVariant = OutlineVariant.at(hue, dark),
        error = if (dark > 0.5f) Color(0xFFF2B8B5) else Color(0xFFB3261E),
        onError = if (dark > 0.5f) Color(0xFF601410) else Color.White,
        scrim = Color.Black,
    )
}
