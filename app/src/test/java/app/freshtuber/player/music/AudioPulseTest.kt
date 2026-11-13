package app.freshtuber.player.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class AudioPulseTest {
    private val rate = 44_100

    private fun sine(freq: Double, seconds: Double, amplitude: Double = 0.5): ShortArray =
        ShortArray((rate * seconds).toInt() * 2) { i -> (sin(2 * PI * freq * (i / 2) / rate) * amplitude * 32_767).toInt().toShort() }

    private fun buffer(samples: ShortArray): ByteBuffer =
        ByteBuffer.allocate(samples.size * 2).order(ByteOrder.nativeOrder()).apply { asShortBuffer().put(samples) }

    @Test
    fun bassIsMeasuredApartFromHighNotes() {
        val levels = PulseLevels().apply { flush(rate) }
        val analyzer = PulseAnalyzer().apply { configure(rate, 2, float = false) }
        analyzer.measure(buffer(sine(60.0, 0.5)), rate / 2, levels)
        val low = levels.at(levels.generation, rate / 4L)!!
        analyzer.measure(buffer(sine(5_000.0, 0.5)), rate / 2, levels)
        val high = levels.at(levels.generation, rate * 3 / 4L)!!
        assertEquals(0.35f, low[0], 0.03f)
        assertEquals(0.35f, high[0], 0.03f)
        assertTrue("bass ${low[1]}", low[1] > 0.25f)
        assertTrue("treble ${high[1]}", high[1] < 0.03f)
    }

    @Test
    fun levelsAreFoundByStreamPlaceAndForgottenAtFlush() {
        val levels = PulseLevels()
        levels.flush(rate)
        levels.add(1_000, 0.1f, 0.1f)
        levels.add(2_000, 0.2f, 0.2f)
        assertEquals(0.1f, levels.at(levels.generation, 500)!![0])
        assertEquals(0.2f, levels.at(levels.generation, 1_500)!![0])
        assertNull(levels.at(levels.generation, 2_500))
        val old = levels.generation
        levels.flush(rate)
        assertNull(levels.at(old, 500))
        assertNull(levels.at(levels.generation, 500))
    }

    @Test
    fun silenceGivesNoLight() {
        val follower = PulseFollower()
        repeat(120) { follower.step(0f, 0f, 1 / 30f) }
        assertEquals(0f, follower.level)
        assertEquals(0f, follower.beat)
    }

    @Test
    fun kicksFlashAndASteadyHumSettles() {
        val follower = PulseFollower()
        // A steady hum: whatever the volume, the light comes to rest.
        repeat(300) { follower.step(0.2f, 0.15f, 1 / 30f) }
        assertTrue("steady ${follower.level}", follower.level < 0.35f)
        assertEquals(0f, follower.beat)
        // A kick: the bass jumps.
        follower.step(0.5f, 0.6f, 1 / 30f)
        assertTrue("beat ${follower.beat}", follower.beat > 0.5f)
        assertTrue("level ${follower.level}", follower.level > 0.4f)
        // And fades within half a second of the hum.
        repeat(15) { follower.step(0.2f, 0.15f, 1 / 30f) }
        assertTrue("faded ${follower.beat}", follower.beat < 0.1f)
    }

    @Test
    fun readerFollowsThePlayedPlaceNotTheTap() {
        val levels = PulseLevels()
        var position = PulseReader.Position(0, 0, playing = true)
        val reader = PulseReader(levels) { position }
        reader.start()
        assertTrue(levels.wanted)
        levels.flush(rate)
        reader.read(1 / 30f)
        // The tap runs a second ahead: quiet for the first second, loud after it.
        val analyzer = PulseAnalyzer().apply { configure(rate, 1, float = false) }
        repeat(rate) { analyzer.add(0.01f, levels) }
        repeat(rate) { analyzer.add(if (it % 200 < 100) 0.9f else -0.9f, levels) }
        levels.advance(rate * 2)
        // Half a second played: still the quiet part (a tap read without the lag would be loud).
        for (ms in 33L..500L step 33) { position = position.copy(ms = ms); reader.read(1 / 30f) }
        assertTrue("quiet ${reader.level}", reader.level < 0.4f)
        assertEquals(0f, reader.beat)
        // A second and a half: the loud part has come.
        for (ms in 533L..1_500L step 33) { position = position.copy(ms = ms); reader.read(1 / 30f) }
        assertTrue("loud ${reader.level}", reader.level > 0.6f)
        // Paused: the light settles.
        position = position.copy(playing = false)
        repeat(90) { reader.read(1 / 30f) }
        assertEquals(0f, reader.level)
        reader.stop()
        assertTrue(!levels.wanted)
    }

    @Test
    fun readerOpenedMidSongStartsBehindTheTap() {
        val levels = PulseLevels()
        levels.flush(rate)
        val analyzer = PulseAnalyzer().apply { configure(rate, 1, float = false) }
        repeat(rate * 3) { analyzer.add(0.3f, levels) }
        levels.advance(rate * 3)
        val reader = PulseReader(levels) { PulseReader.Position(42_000, 3, playing = true) }
        reader.start()
        reader.read(1 / 30f)
        // Three seconds measured, 0.3 s lag: the clock starts 2.7 s in, where windows exist.
        assertNotNull(levels.at(levels.generation, (2.7 * rate).toLong()))
        assertTrue(reader.active)
    }
}
