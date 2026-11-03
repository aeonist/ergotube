package app.freshtuber.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Reads googlevideo streams in bounded chunks. YouTube slows down single requests for large
 * ranges, so a long open-ended read is split into requests of at most [chunkSize] bytes, as
 * yt-dlp does. The full size comes from the URL's `clen` parameter; streams without it are
 * passed through untouched.
 */
@OptIn(UnstableApi::class)
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long = DEFAULT_CHUNK,
) : DataSource {

    private var spec: DataSpec? = null
    private var passthrough = false
    private var position = 0L
    /** Absolute end offset (exclusive) of the requested range. */
    private var end = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val total = ChunkPlan.streamLength(dataSpec.uri.toString())
        if (total == null || dataSpec.position >= total) {
            passthrough = true
            return upstream.open(dataSpec)
        }
        passthrough = false
        spec = dataSpec
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length else total
        openChunk()
        return end - dataSpec.position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (position >= end) return C.RESULT_END_OF_INPUT
        val read = upstream.read(buffer, offset, minOf(length.toLong(), end - position).toInt())
        if (read == C.RESULT_END_OF_INPUT) {
            upstream.close()
            if (position >= end) return C.RESULT_END_OF_INPUT
            openChunk()
            return read(buffer, offset, length)
        }
        position += read
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        upstream.close()
        spec = null
    }

    private fun openChunk() {
        val current = checkNotNull(spec)
        upstream.open(current.subrange(position - current.position, ChunkPlan.nextLength(position, end, chunkSize)))
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource())
    }

    private companion object {
        const val DEFAULT_CHUNK = 10L * 1024 * 1024
    }
}

/** The pure parts of chunking, kept apart for tests. */
object ChunkPlan {
    /** Total size of a googlevideo stream from its `clen` parameter; null for anything else. */
    fun streamLength(url: String): Long? {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (uri.host?.endsWith("googlevideo.com") != true) return null
        return uri.rawQuery?.split('&')
            ?.firstOrNull { it.startsWith("clen=") }
            ?.substringAfter('=')
            ?.toLongOrNull()
    }

    fun nextLength(position: Long, end: Long, chunkSize: Long): Long = minOf(chunkSize, end - position)

    /** Every chunk a read of [start] until [end] makes, as (offset, length). */
    fun plan(start: Long, end: Long, chunkSize: Long): List<Pair<Long, Long>> =
        generateSequence(start) { it + chunkSize }
            .takeWhile { it < end }
            .map { it to nextLength(it, end, chunkSize) }
            .toList()
}
