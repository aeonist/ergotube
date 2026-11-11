package app.freshtuber.player

import androidx.media3.common.PlaybackException

/**
 * What to do when playback breaks in the middle: a short network hiccup or an expired stream
 * address must not end in an error screen. Up to [MAX_ATTEMPTS] quiet tries, then the error.
 */
object PlaybackRecovery {
    const val MAX_ATTEMPTS = 3
    /** A failure this long after the last one starts counting again. */
    const val FORGET_MS = 30_000L

    /** [refresh] asks YouTube for new stream addresses; otherwise the same ones are tried again. */
    data class Step(val delayMs: Long, val refresh: Boolean)

    /** The next quiet try after [attempt] earlier ones, or null to show the error. */
    fun next(attempt: Int, errorCode: Int): Step? {
        if (attempt >= MAX_ATTEMPTS) return null
        return when (errorCode) {
            // The connection dropped or timed out: the same address usually works a moment later.
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT ->
                if (attempt == 0) Step(400, refresh = false) else Step(if (attempt == 1) 1_000 else 2_500, refresh = true)
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> Step(0, refresh = false)
            // The address expired or YouTube answered with something that is not the stream.
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                Step(if (attempt == 0) 200 else 1_200L * attempt, refresh = true)
            else -> null
        }
    }
}
