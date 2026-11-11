package app.freshtuber.data.music

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Song tags inside an .m4a file, the way other music players read them (iTunes `ilst` atoms):
 * title, artist, album, cover, and a quiet mark in the «encoded with» field that tells ErgoTube
 * the file is its own download of a given YouTube song.
 *
 * Tags are added without moving the audio: when the index (`moov`) is the last box it is
 * rewritten in place, otherwise a tagged copy is appended and the old one is turned into free
 * space. Sound data keeps its place either way, so its offsets stay valid.
 */
object M4aTags {
    const val MARKER_PREFIX = "ErgoTube yt:"
    private const val MAX_MOOV = 24L * 1024 * 1024

    class Tags(val title: String, val artist: String? = null, val album: String? = null, val cover: ByteArray? = null, val marker: String? = null)

    private class Box(val type: String, val start: Long, val header: Int, val size: Long)

    /** The YouTube id this file was downloaded as, or null when it is not an ErgoTube download. */
    fun videoId(channel: FileChannel): String? = marker(channel)?.takeIf { it.startsWith(MARKER_PREFIX) }?.removePrefix(MARKER_PREFIX)
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }

    fun marker(channel: FileChannel): String? = try {
        val moov = topLevel(channel).firstOrNull { it.type == "moov" }?.takeIf { it.size <= MAX_MOOV }?.let { read(channel, it) }
        val ilst = moov?.child("udta")?.child("meta", skip = 4)?.child("ilst")
        ilst?.child("©too")?.child("data")?.let { if (it.size > 8) String(it, 8, it.size - 8, Charsets.UTF_8) else null }
    } catch (_: IOException) { null } catch (_: RuntimeException) { null }

    @Throws(IOException::class)
    fun write(channel: FileChannel, tags: Tags) {
        val moov = topLevel(channel).firstOrNull { it.type == "moov" } ?: throw IOException("No moov box")
        if (moov.size > MAX_MOOV) throw IOException("moov too large")
        val body = read(channel, moov)
        val kept = ByteArrayOutputStream()
        children(body, 0).forEach { (type, from, to) -> if (type != "udta") kept.write(body, from, to - from) }
        val items = ByteArrayOutputStream()
        fun text(type: String, value: String?) { if (!value.isNullOrBlank()) items.write(box(type, data(1, value.toByteArray(Charsets.UTF_8)))) }
        text("©nam", tags.title)
        text("©ART", tags.artist)
        text("©alb", tags.album)
        text("©too", tags.marker)
        tags.cover?.takeIf { it.isNotEmpty() }?.let { items.write(box("covr", data(13, it))) }
        val handler = box("hdlr", ByteArray(8) + "mdirappl".toByteArray(Charsets.ISO_8859_1) + ByteArray(9))
        val meta = box("meta", ByteArray(4) + handler + box("ilst", items.toByteArray()))
        val tagged = box("moov", kept.toByteArray() + box("udta", meta))
        if (moov.start + moov.size == channel.size()) channel.truncate(moov.start)
        else channel.write(ByteBuffer.wrap("free".toByteArray(Charsets.ISO_8859_1)), moov.start + 4)
        channel.write(ByteBuffer.wrap(tagged), channel.size())
    }

    private fun data(kind: Int, payload: ByteArray) = box("data", byteArrayOf(0, 0, 0, kind.toByte(), 0, 0, 0, 0) + payload)

    private fun box(type: String, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + payload.size).putInt(8 + payload.size).put(type.toByteArray(Charsets.ISO_8859_1)).put(payload).array()

    private fun topLevel(channel: FileChannel): List<Box> {
        val boxes = mutableListOf<Box>()
        val head = ByteBuffer.allocate(16)
        var at = 0L
        val end = channel.size()
        while (at + 8 <= end) {
            head.clear()
            if (channel.read(head, at) < 8) break
            head.flip()
            val small = head.int.toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also { head.get(it) }.toString(Charsets.ISO_8859_1)
            val (header, size) = when (small) {
                0L -> 8 to end - at
                1L -> { if (head.remaining() < 8) break; 16 to head.long }
                else -> 8 to small
            }
            if (size < header || at + size > end) break
            boxes += Box(type, at, header, size)
            at += size
        }
        return boxes
    }

    private fun read(channel: FileChannel, box: Box): ByteArray {
        val buffer = ByteBuffer.allocate((box.size - box.header).toInt())
        var at = box.start + box.header
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, at)
            if (n < 0) throw IOException("Unexpected end of file")
            at += n
        }
        return buffer.array()
    }

    /** Child boxes of [bytes] from [from]: type, start (with header) and end. */
    private fun children(bytes: ByteArray, from: Int): List<Triple<String, Int, Int>> {
        val list = mutableListOf<Triple<String, Int, Int>>()
        var at = from
        while (at + 8 <= bytes.size) {
            val size = ByteBuffer.wrap(bytes, at, 4).int
            if (size < 8 || at + size > bytes.size) break
            list += Triple(String(bytes, at + 4, 4, Charsets.ISO_8859_1), at, at + size)
            at += size
        }
        return list
    }

    /** The payload of the first child box of [type]; [skip] passes a full box's version and flags. */
    private fun ByteArray.child(type: String, skip: Int = 0): ByteArray? =
        children(this, 0).firstOrNull { it.first == type }?.let { (_, from, to) -> copyOfRange(minOf(from + 8 + skip, to), to) }
}
