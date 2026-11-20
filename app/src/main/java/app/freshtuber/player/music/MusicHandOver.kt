package app.freshtuber.player.music

/**
 * «Как музыку» while the song's sound is on its way: what the tap saw on the video, captured
 * before the watch page goes, and what the listener asked for meanwhile. The video keeps
 * sounding until the song really plays; then it stops.
 */
internal class MusicHandOver(
    val trackId: String,
    val videoId: String,
    /** The video's second at the tap. */
    private val tapPositionMs: Long,
    /** The queue before the tap, put back if the song cannot take over. */
    val previous: MusicQueue? = null,
) {
    /** Play (true) or wait paused once the sound is there; Play and Pause meanwhile change it. */
    var play: Boolean = true
    private var seekMs: Long? = null

    fun seek(ms: Long) { seekMs = ms.coerceAtLeast(0) }

    /** Where the song stands before its sound arrives (the queue's position, so Play resumes there). */
    val positionMs: Long get() = seekMs ?: tapPositionMs

    /**
     * Where the sound starts: a seek made meanwhile; else the video's own second while it still
     * holds the same video (it went on playing); else the second of the tap.
     */
    fun startMs(videoMediaId: String?, videoPositionMs: Long): Long =
        seekMs ?: videoPositionMs.takeIf { videoMediaId == videoId && it > 0 } ?: tapPositionMs
}
