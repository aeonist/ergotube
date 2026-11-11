package app.freshtuber.data.music

import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    @Test fun parsesSyncedLinesWithSeveralStampsInTimeOrder() {
        val lyrics = Lrc.parse("[ar: Someone]\n[00:12.50] First line\n[00:05.1][01:00.00]Chorus\n[00:20.345]  \nno stamp")
        assertTrue(lyrics.synced)
        assertEquals(listOf(5_100L, 12_500L, 20_345L, 60_000L), lyrics.lines.map { it.timeMs })
        assertEquals("Chorus", lyrics.lines.first().text)
        assertEquals("", lyrics.lines[2].text)
    }

    @Test fun wordTimesOfEnhancedLrcAreDropped() {
        val lyrics = Lrc.parse("[00:18.89]<00:18.89>We're <00:19.25>no <00:19.53>strangers")
        assertEquals(LyricLine(18_890, "We're no strangers"), lyrics.lines.single())
    }

    @Test fun findsTheLineSungNow() {
        val lyrics = Lrc.parse("[00:01.00]a\n[00:03.00]b\n[00:05.00]c")
        assertEquals(-1, lyrics.lineAt(500))
        assertEquals(0, lyrics.lineAt(1_000))
        assertEquals(1, lyrics.lineAt(4_999))
        assertEquals(2, lyrics.lineAt(60_000))
    }

    @Test fun plainLyricsHaveNoTimesAndNoHighlight() {
        val lyrics = Lrc.plain("\nOne\nTwo\n\n")
        assertFalse(lyrics.synced)
        assertEquals(listOf("One", "Two"), lyrics.lines.map { it.text })
        assertEquals(-1, lyrics.lineAt(10_000))
    }

    @Test fun youtubeTitlesSplitIntoArtistAndSong() {
        assertEquals("Daft Punk" to "Get Lucky", MusicNames.split("Daft Punk - Get Lucky (Official Video)", "DaftPunkVEVO"))
        assertEquals("Земфира" to "Хочешь?", MusicNames.split("Хочешь? [Клип]", "Земфира - Topic"))
        assertEquals("Queen" to "Bohemian Rhapsody", MusicNames.split("Queen – Bohemian Rhapsody (Remastered 2011)", null))
        assertEquals("Artist", MusicNames.artist("Artist - Topic"))
        assertEquals("Adele", MusicNames.artist("AdeleVEVO"))
    }
}
