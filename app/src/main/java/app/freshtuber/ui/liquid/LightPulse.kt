package app.freshtuber.ui.liquid

/**
 * What makes the light pulse: how loud the music is right now. Read on the main thread once per
 * light frame while the music player shows.
 */
interface LightPulse {
    /** 0..1: how far the sound rises above its recent average. */
    val level: Float
    /** 0..1: a short flash on a kick in the bass, fading within a fifth of a second. */
    val beat: Float
    /** False once the music is paused and the light has settled. */
    val active: Boolean
    fun start()
    fun stop()
    fun read(dt: Float)
}
