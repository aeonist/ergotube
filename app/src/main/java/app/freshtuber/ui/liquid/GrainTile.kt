package app.freshtuber.ui.liquid

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.floor

/**
 * The reference shader's static grain, baked once into a repeating tile. Drawing a tiled bitmap
 * costs one texture read per pixel instead of hashing every pixel on every frame.
 * The tile is premultiplied source-over: it adds the grain to a ground of lightness [l].
 */
internal object GrainTile {
    const val SIZE = 512

    fun make(px: Float, dark: Boolean): Bitmap {
        val l = if (dark) 0.07f else 0.84f
        val amp = 0.042f * (0.35f + 0.65f * (1f - abs(2f * l - 1f)))
        val pixels = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val qx = x + 0.5f
            val qy = y + 0.5f
            val g = (hs(qx, qy) + hs(qx + 0.37f, qy + 0.37f) - 1f) * 0.5f +
                (vn(qx / (0.62f * px), qy / (0.62f * px)) - 0.5f) * 0.85f +
                (vn(qx / (1.4f * px) + 19f, qy / (1.4f * px) + 19f) - 0.5f) * 0.35f
            val s7 = 0.7f * px
            val cr = vn(qx / s7 + 41f, qy / s7 + 41f) - 0.5f
            val cgc = vn(qx / s7 + 83f, qy / s7 + 83f) - 0.5f
            val cb = vn(qx / s7 + 127f, qy / s7 + 127f) - 0.5f
            val dl = g * amp
            val a = (abs(dl) / maxOf(if (dl >= 0f) 1f - l else l, 0.05f)).coerceIn(0f, 1f)
            if (a <= 0.001f) continue
            // Premultiplied value l·a + d per channel, stored unpremultiplied for setPixels.
            fun ch(c: Float) = ((l * a + (g + c * 0.25f) * amp).coerceIn(0f, a) / a * 255f + 0.5f).toInt().coerceIn(0, 255)
            pixels[y * SIZE + x] = ((a * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (ch(cr) shl 16) or (ch(cgc) shl 8) or ch(cb)
        }
        return Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    private fun fract(v: Float) = v - floor(v)

    private fun h21(x: Float, y: Float): Float {
        var px = fract(x * 123.34f)
        var py = fract(y * 456.21f)
        val d = px * (px + 45.32f) + py * (py + 45.32f)
        px += d; py += d
        return fract(px * py)
    }

    private fun vn(x: Float, y: Float): Float {
        val ix = floor(x); val iy = floor(y)
        var fx = x - ix; var fy = y - iy
        fx = fx * fx * (3f - 2f * fx); fy = fy * fy * (3f - 2f * fy)
        val a = h21(ix, iy); val b = h21(ix + 1f, iy)
        val c = h21(ix, iy + 1f); val d = h21(ix + 1f, iy + 1f)
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy
    }

    private fun hs(x: Float, y: Float): Float {
        var q0 = fract(x * 0.1031f); var q1 = fract(y * 0.1031f); var q2 = fract(x * 0.1031f)
        val d = q0 * (q1 + 33.33f) + q1 * (q2 + 33.33f) + q2 * (q0 + 33.33f)
        q0 += d; q1 += d; q2 += d
        return fract((q0 + q1) * q2)
    }
}
