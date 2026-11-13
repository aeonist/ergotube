package app.freshtuber.player.music

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import app.freshtuber.ui.liquid.LightPulse
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * How loud the music is, for the light behind the music player. The song's own sound is read on
 * its way to the speaker by [tap], an audio processor in the music player's chain, so no
 * microphone permission is needed (Android's Visualizer would need one). The tap runs ahead of
 * what is heard by the audio buffer, so the levels are stored by their place in the stream and
 * read back at the place that is playing now ([reader]).
 */
@OptIn(UnstableApi::class)
@Singleton
class AudioPulse @Inject constructor() {
    /** The levels measured so far, written on the playback thread and read on the main thread. */
    internal val levels = PulseLevels()
    val tap: AudioProcessor = PulseTap(levels)

    /** Media3's default renderers with [tap] in the audio chain. */
    fun renderers(context: Context): RenderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(tap))
                .build()
    }

    /** A source for the light that follows [player]; at most one music screen reads at a time. */
    fun reader(player: Player): LightPulse = PulseReader(levels) { PulseReader.Position(player.currentPosition, player.currentMediaItemIndex, player.isPlaying) }
}

/**
 * Short windows (about 25 ms) of the stream: where each ends, counted in frames since the last
 * flush, and its loudness overall and in the bass. A ring of about ten seconds; the tap is never
 * more than a second or two ahead of the speaker.
 */
internal class PulseLevels(private val capacity: Int = 512) {
    private val lock = Any()
    private val generations = IntArray(capacity)
    private val ends = LongArray(capacity)
    private val rms = FloatArray(capacity)
    private val bass = FloatArray(capacity)
    private var head = 0
    private var count = 0

    /** Bumped at every flush (seek, new format): frame counts start again from zero. */
    @Volatile var generation = 0
        private set
    @Volatile var sampleRate = 44_100
        private set
    /** Frames the tap has passed on since the last flush: the newest place in the stream. */
    @Volatile var latest = 0L
        private set
    /** True while a screen shows the pulse; otherwise the tap only counts frames. */
    @Volatile var wanted = false
    /**
     * How far the tap runs ahead of the speaker, in seconds: the audio buffer (Media3 keeps
     * 250 to 750 ms of PCM) and the output latency. Measured after every flush a screen saw.
     */
    @Volatile var lagSeconds = 0.3

    fun flush(rate: Int) = synchronized(lock) {
        generation++
        sampleRate = rate.coerceAtLeast(1)
        latest = 0L
        count = 0
    }

    fun advance(frames: Int) { latest += frames }

    fun add(endFrame: Long, level: Float, low: Float) = synchronized(lock) {
        generations[head] = generation
        ends[head] = endFrame
        rms[head] = level
        bass[head] = low
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    /**
     * The window that holds [frame] of [generation], as (loudness, bass), or null when the tap
     * has not measured that far or it fell out of the ring.
     */
    fun at(generation: Int, frame: Long): FloatArray? = synchronized(lock) {
        // Newest first: the window wanted is near the oldest kept only after a long stall.
        var found = -1
        for (i in 1..count) {
            val index = (head - i + capacity) % capacity
            if (generations[index] != generation) break
            if (ends[index] < frame) break
            found = index
        }
        if (found < 0) null else floatArrayOf(rms[found], bass[found])
    }
}

/**
 * Passes the sound through unchanged and measures it. Only 16-bit and float PCM are measured;
 * other encodings switch the tap off ([AudioProcessor.AudioFormat.NOT_SET]) instead of failing.
 */
@OptIn(UnstableApi::class)
internal class PulseTap(private val levels: PulseLevels) : BaseAudioProcessor() {
    private val analyzer = PulseAnalyzer()
    private var bytesPerFrame = 4

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val format = inputAudioFormat
        bytesPerFrame = format.bytesPerFrame.coerceAtLeast(1)
        analyzer.configure(format.sampleRate, format.channelCount, float = format.encoding == C.ENCODING_PCM_FLOAT)
        levels.flush(format.sampleRate)
    }

    override fun onReset() { analyzer.configure(44_100, 2, float = false) }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val frames = size / bytesPerFrame
        if (levels.wanted) analyzer.measure(inputBuffer.duplicate().order(ByteOrder.nativeOrder()), frames, levels)
        else analyzer.skip(frames)
        levels.advance(frames)
        replaceOutputBuffer(size).put(inputBuffer).flip()
    }
}

/**
 * Loudness per window: the root mean square of the mono mix, and of the mix through a one-pole
 * low-pass at about 150 Hz (kick and bass, where the beat lives).
 */
internal class PulseAnalyzer {
    private var channels = 2
    private var float = false
    private var window = 1_100
    private var alpha = 0f
    private var low = 0f
    private var sum = 0.0
    private var lowSum = 0.0
    private var inWindow = 0
    private var frame = 0L

    fun configure(sampleRate: Int, channelCount: Int, float: Boolean) {
        channels = channelCount.coerceAtLeast(1)
        this.float = float
        window = (sampleRate / 40).coerceAtLeast(64)
        alpha = (1.0 - exp(-2.0 * PI * 150.0 / sampleRate.coerceAtLeast(1))).toFloat()
        low = 0f; sum = 0.0; lowSum = 0.0; inWindow = 0; frame = 0L
    }

    /** Frames that pass while nobody looks: only the count goes on, so later windows stay in place. */
    fun skip(frames: Int) {
        frame += frames
        inWindow = 0; sum = 0.0; lowSum = 0.0
    }

    /** Reads [frames] interleaved frames from [buffer] (its position is left where it was). */
    fun measure(buffer: ByteBuffer, frames: Int, levels: PulseLevels) {
        var at = buffer.position()
        val scale = 1f / channels
        for (f in 0 until frames) {
            var mono = 0f
            for (c in 0 until channels) {
                if (float) { mono += buffer.getFloat(at); at += 4 }
                else { mono += buffer.getShort(at) / 32_768f; at += 2 }
            }
            add(mono * scale, levels)
        }
    }

    /** One mono sample; closes a window every [window] samples. */
    fun add(sample: Float, levels: PulseLevels) {
        low += alpha * (sample - low)
        sum += sample * sample
        lowSum += low * low
        inWindow++
        frame++
        if (inWindow >= window) {
            levels.add(frame, sqrt(sum / inWindow).toFloat(), sqrt(lowSum / inWindow).toFloat())
            inWindow = 0; sum = 0.0; lowSum = 0.0
        }
    }
}

/**
 * Turns the measured windows into the light's [level] and [beat]. The clock is the time played
 * since the last flush, the stream place heard now. It is summed from the player's position on
 * the main thread (a new song in a gapless queue starts its position again while the frame count
 * goes on). After a flush the screen saw, it starts at zero and is exact, and it measures how far
 * the tap runs ahead; a screen opened in the middle of a song starts it that far behind the tap.
 */
internal class PulseReader(private val levels: PulseLevels, private val position: () -> Position) : LightPulse {
    data class Position(val ms: Long, val item: Int, val playing: Boolean)

    private val follower = PulseFollower()
    private var generation = -1
    private var playedMs = 0.0
    private var lastMs = 0L
    private var lastItem = -1
    /** True when the clock started at a flush seen here, so it can measure the lag. */
    private var exact = false

    override val level get() = follower.level
    override val beat get() = follower.beat
    override var active = false
        private set

    override fun start() { levels.wanted = true; generation = -1 }
    override fun stop() { levels.wanted = false; active = false }

    override fun read(dt: Float) {
        val now = position()
        val gen = levels.generation
        val rate = levels.sampleRate.toDouble()
        val aheadMs = levels.latest * 1000.0 / rate
        when {
            gen != generation -> {
                // Seen on the first read: the song was already playing, so estimate the place.
                exact = generation >= 0
                generation = gen
                playedMs = if (exact) 0.0 else (aheadMs - levels.lagSeconds * 1000.0).coerceAtLeast(0.0)
            }
            now.item != lastItem -> playedMs += now.ms
            else -> {
                val step = now.ms - lastMs
                // A seek shows here before the flush reaches the tap; the flush resets the clock.
                if (step in 0..1_500) playedMs += step
            }
        }
        lastMs = now.ms
        lastItem = now.item
        val lag = (aheadMs - playedMs) / 1000.0
        if (now.playing && playedMs > 500.0) {
            if (exact && lag in 0.02..2.0) levels.lagSeconds += (lag - levels.lagSeconds) * 0.05
            // A clock that drifted out of the tap's reach (a flush missed while away) starts again from the lag.
            else if (lag !in -0.1..3.0) { exact = false; playedMs = (aheadMs - levels.lagSeconds * 1000.0).coerceAtLeast(0.0) }
        }
        val window = if (now.playing) levels.at(gen, (playedMs * rate / 1000.0).toLong()) else null
        follower.step(window?.get(0) ?: 0f, window?.get(1) ?: 0f, dt)
        active = now.playing || follower.level > 0.003f || follower.beat > 0.003f
    }
}

/**
 * From loudness to light. Music is never silent, so most of the light follows how far the sound
 * rises above its own recent average, against its recent peak: quiet and loud songs pulse alike,
 * and a steady hum comes to rest. The rest follows how loud the passage is against the song's
 * loudest one, so a chorus glows more than a verse. The level rises fast and falls slowly; a
 * sudden rise in the bass is a beat, a short flash.
 */
class PulseFollower {
    var level = 0f; private set
    var beat = 0f; private set
    private var average = 0f
    private var bassAverage = 0f
    private var peak = 0f
    private var loudest = 0f
    private var lastBass = 0f

    fun step(rms: Float, bass: Float, dt: Float) {
        if (dt <= 0f) return
        val raw = bass * 0.65f + rms * 0.35f
        if (raw < 0.0015f) {
            // Silence (or paused): the light settles; the averages wait for the sound to return.
            fall(0f, dt)
            beat = decay(beat, dt, 0.16f)
            lastBass = 0f
            return
        }
        // The first sound sets the averages, so a song does not open with a flash.
        if (average == 0f) { average = raw; bassAverage = bass; peak = raw; loudest = raw }
        average += (raw - average) * (1f - exp(-dt / 1.6f))
        bassAverage += (bass - bassAverage) * (1f - exp(-dt / 0.9f))
        peak = max(raw, average + (peak - average) * exp(-dt / 3.5f))
        loudest = max(average, loudest * exp(-dt / 20f))
        val transient = ((raw - average) / maxOf(peak - average, average * 0.25f, 0.004f)).coerceIn(0f, 1f)
        val energy = (average / loudest.coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
        fall(0.7f * transient + 0.3f * energy * energy, dt)
        beat = decay(beat, dt, 0.16f)
        if (bass > 0.01f && bass > bassAverage * 1.35f && bass - lastBass > bassAverage * 0.12f)
            beat = max(beat, ((bass / bassAverage.coerceAtLeast(1e-4f) - 1.35f) * 1.6f + 0.55f).coerceIn(0f, 1f))
        lastBass = bass
    }

    /** Rises within about 40 ms, falls within about 300 ms. */
    private fun fall(target: Float, dt: Float) {
        val tau = if (target > level) 0.04f else 0.3f
        level += (target - level) * (1f - exp(-dt / tau))
        if (level < 0.002f) level = 0f
    }

    private fun decay(value: Float, dt: Float, tau: Float) = (value * exp(-dt / tau)).let { if (it < 0.003f) 0f else it }
}
