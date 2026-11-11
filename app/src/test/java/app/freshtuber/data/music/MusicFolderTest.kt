package app.freshtuber.data.music

import app.freshtuber.domain.model.PlaylistSummary
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class MusicFolderTest {
    @get:Rule val folder = TemporaryFolder()

    private fun box(type: String, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + payload.size).putInt(8 + payload.size).put(type.toByteArray(Charsets.ISO_8859_1)).put(payload).array()

    private val sound = ByteArray(4_000) { (it % 251).toByte() }
    private val index = box("mvhd", ByteArray(100)) + box("trak", ByteArray(300) { 7 })

    private fun file(moovLast: Boolean): File = folder.newFile().apply {
        val parts = if (moovLast) listOf(box("ftyp", ByteArray(16)), box("mdat", sound), box("moov", index))
        else listOf(box("ftyp", ByteArray(16)), box("moov", index), box("mdat", sound))
        writeBytes(parts.reduce(ByteArray::plus))
    }

    private fun boxes(file: File): List<Pair<String, Int>> {
        val bytes = file.readBytes()
        val list = mutableListOf<Pair<String, Int>>()
        var at = 0
        while (at + 8 <= bytes.size) {
            val size = ByteBuffer.wrap(bytes, at, 4).int
            list += String(bytes, at + 4, 4, Charsets.ISO_8859_1) to at
            at += size
        }
        assertEquals("boxes must fill the file exactly", bytes.size, at)
        return list
    }

    private fun tag(file: File, tags: M4aTags.Tags) = RandomAccessFile(file, "rw").use { M4aTags.write(it.channel, tags) }
    private fun videoId(file: File) = RandomAccessFile(file, "r").use { M4aTags.videoId(it.channel) }

    @Test fun tagsGoIntoTheIndexAtTheEndAndTheSoundStaysWhereItWas() {
        val file = file(moovLast = true)
        val soundAt = boxes(file).first { it.first == "mdat" }.second
        tag(file, M4aTags.Tags("Песня", "Артист", cover = byteArrayOf(1, 2, 3), marker = M4aTags.MARKER_PREFIX + "dQw4w9WgXcQ"))
        val after = boxes(file)
        assertEquals(listOf("ftyp", "mdat", "moov"), after.map { it.first })
        assertEquals(soundAt, after.first { it.first == "mdat" }.second)
        assertArrayEquals(sound, file.readBytes().copyOfRange(soundAt + 8, soundAt + 8 + sound.size))
        assertEquals("dQw4w9WgXcQ", videoId(file))
        val text = String(file.readBytes(), Charsets.UTF_8)
        assertTrue(text.contains("Песня") && text.contains("Артист"))
    }

    @Test fun anIndexBeforeTheSoundIsCopiedToTheEndSoNothingMoves() {
        val file = file(moovLast = false)
        val soundAt = boxes(file).first { it.first == "mdat" }.second
        tag(file, M4aTags.Tags("Song", marker = M4aTags.MARKER_PREFIX + "jNQXAC9IVRw"))
        val after = boxes(file)
        assertEquals(listOf("ftyp", "free", "mdat", "moov"), after.map { it.first })
        assertEquals(soundAt, after.first { it.first == "mdat" }.second)
        assertEquals("jNQXAC9IVRw", videoId(file))
    }

    @Test fun taggingTwiceKeepsOneSetOfTags() {
        val file = file(moovLast = true)
        tag(file, M4aTags.Tags("First", marker = M4aTags.MARKER_PREFIX + "dQw4w9WgXcQ"))
        val once = file.length()
        tag(file, M4aTags.Tags("First", marker = M4aTags.MARKER_PREFIX + "jNQXAC9IVRw"))
        assertEquals(once, file.length())
        assertEquals("jNQXAC9IVRw", videoId(file))
    }

    @Test fun aFileWithoutTheMarkIsNotOurs() {
        assertNull(videoId(file(moovLast = true)))
        val other = file(moovLast = true)
        tag(other, M4aTags.Tags("Song", marker = "Lavf60.3.100"))
        assertNull(videoId(other))
        val noise = folder.newFile().apply { writeBytes(ByteArray(64) { 0x7F }) }
        assertNull(videoId(noise))
    }

    private fun youtube(id: String, title: String, artist: String?, ms: Long, favorite: Boolean = false, added: Long = 1) =
        MusicTrack("yt:$id", MusicSource.YouTube, title, artist, durationMs = ms, saved = true, favorite = favorite, addedAt = added)
    private fun phone(id: String, title: String, artist: String?, ms: Long, favorite: Boolean = false) =
        MusicTrack("phone:$id", MusicSource.Phone, title, artist, durationMs = ms, uri = "content://$id", saved = true, favorite = favorite, addedAt = 5)

    @Test fun aPhoneFileOfADownloadedSongIsTheDuplicate() {
        val song = youtube("aaaaaaaaaaa", "Кино - Группа крови (Official Video)", "Кино", 285_000)
        val file = phone("1", "Группа крови", "Кино", 286_500)
        val extras = MusicDuplicates.find(listOf(file, song), downloaded = setOf("aaaaaaaaaaa"))
        assertEquals(listOf(MusicDuplicates.Extra(file, song)), extras)
    }

    @Test fun differentLengthsOrArtistsAreDifferentSongs() {
        val live = youtube("aaaaaaaaaaa", "Song", "Band", 200_000)
        val studio = phone("1", "Song", "Band", 260_000)
        val cover = phone("2", "Song", "Other", 200_000)
        assertTrue(MusicDuplicates.find(listOf(live, studio, cover)).isEmpty())
    }

    @Test fun amongEqualCopiesTheFavouriteStays() {
        val plain = phone("1", "Song", "Band", 200_000)
        val loved = phone("2", "Song", "Band", 200_000, favorite = true)
        assertEquals(listOf(MusicDuplicates.Extra(plain, loved)), MusicDuplicates.find(listOf(plain, loved)))
    }

    @Test fun withoutLengthsOnlyAFullNameMatchCounts() {
        assertEquals(1, MusicDuplicates.find(listOf(phone("1", "Song", "Band", 0), phone("2", "Song", "Band", 200_000))).size)
        assertTrue(MusicDuplicates.find(listOf(phone("1", "Song", null, 0), phone("2", "Song", "Band", 200_000))).isEmpty())
    }

    @Test fun myMixLeadsTheJams() {
        val a = PlaylistSummary("RDabcdefghijk", "Микс: рок")
        val mine = PlaylistSummary("RDMM", "Мой джем")
        assertEquals(listOf(mine, a), MusicJams.order(listOf(a, mine), signedIn = true))
        assertEquals("RDMM", MusicJams.order(listOf(a), signedIn = true).first().id)
        assertEquals(listOf(a), MusicJams.order(listOf(a), signedIn = false))
        assertTrue(MusicJams.isMine(PlaylistSummary("RDxyz", " My Mix ")))
    }

    @Test fun fileNamesHoldNoForbiddenCharacters() {
        assertEquals("AC DC - Back in Black.m4a", MusicFolderNames.fileName("AC/DC", "Back in Black", "m4a"))
        assertEquals("What.m4a", MusicFolderNames.fileName(null, "What?", "m4a"))
        assertEquals("ErgoTube.ogg", MusicFolderNames.fileName(" ", "???", "ogg"))
        assertTrue(MusicFolderNames.fileName("A".repeat(200), "B", "m4a").length <= 124)
    }
}
