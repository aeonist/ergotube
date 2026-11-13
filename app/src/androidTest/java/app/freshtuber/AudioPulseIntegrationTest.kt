package app.freshtuber

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.player.music.AudioPulse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** The pulse tap sits in a real player's audio chain: the song plays to its end and its loudness is measured. */
@RunWith(AndroidJUnit4::class)
class AudioPulseIntegrationTest {
    @Test
    fun songPlaysThroughTheTapAndIsMeasured() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val rate = 44_100
        val seconds = 3
        // Half-second bursts of a 60 Hz hum with silence between them.
        val samples = ShortArray(rate * seconds) { i -> if ((i / (rate / 2)) % 2 == 0) (sin(2 * PI * 60 * i / rate) * 16_000).toInt().toShort() else 0 }
        val file = File(context.cacheDir, "pulse-test.wav")
        file.writeBytes(wav(samples, rate))
        val pulse = AudioPulse()
        var error: PlaybackException? = null
        var player: ExoPlayer? = null
        instrumentation.runOnMainSync {
            pulse.levels.wanted = true
            player = ExoPlayer.Builder(context, pulse.renderers(context)).build().apply {
                addListener(object : Player.Listener { override fun onPlayerError(e: PlaybackException) { error = e } })
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
                play()
            }
        }
        var state = Player.STATE_IDLE
        var position = 0L
        // Measured while it plays: the tap starts again at every flush, the end of the song included.
        var latest = 0L
        var loud: FloatArray? = null
        var quiet: FloatArray? = null
        val levels = pulse.levels
        val until = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < until) {
            instrumentation.runOnMainSync { state = player!!.playbackState; position = player!!.currentPosition }
            if (state == Player.STATE_ENDED || error != null) break
            if (loud == null && position in 1_000..2_500) {
                latest = levels.latest
                loud = levels.at(levels.generation, rate / 4L)
                quiet = levels.at(levels.generation, rate * 3 / 4L)
            }
            Thread.sleep(100)
        }
        instrumentation.runOnMainSync { player!!.release() }
        assertNull(error)
        assertEquals(Player.STATE_ENDED, state)
        assertTrue("generation ${levels.generation}", levels.generation > 0)
        assertTrue("latest $latest", latest > rate)
        assertNotNull(loud); assertNotNull(quiet)
        assertTrue("loud ${loud!![1]}", loud[1] > 0.2f)
        assertTrue("quiet ${quiet!![0]}", quiet[0] < 0.01f)
    }

    private fun wav(samples: ShortArray, rate: Int): ByteArray {
        val data = samples.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        samples.forEach { b.putShort(it) }
        return b.array()
    }
}
