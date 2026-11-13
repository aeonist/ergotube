package app.freshtuber

import android.graphics.RuntimeShader
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import app.freshtuber.ui.liquid.LiquidShaders
import org.junit.Test
import org.junit.runner.RunWith

/** The background shaders compile on the device (AGSL errors only show at run time) and take the music pulse. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class LiquidShaderCompileTest {
    @Test
    fun fieldAndDropCompileWithPulseUniforms() {
        for (source in listOf(LiquidShaders.FIELD, LiquidShaders.DROP)) {
            val shader = RuntimeShader(source)
            shader.setFloatUniform("uPulse", 0.7f)
            shader.setFloatUniform("uBeat", 1f)
            shader.setFloatUniform("uMode", 1f)
        }
    }
}
