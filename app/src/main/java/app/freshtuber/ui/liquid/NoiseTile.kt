package app.freshtuber.ui.liquid

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import kotlin.random.Random

/** 64×64 random values, repeated: the lattice of the shader's value noise. */
internal object NoiseTile {
    private const val SIZE = 64

    fun shader(): Shader {
        val random = Random(0x11A7)
        val pixels = IntArray(SIZE * SIZE) {
            val v = random.nextInt(256)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val bitmap = Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}
