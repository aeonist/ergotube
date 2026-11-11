package app.freshtuber.data.music

import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.innertube.parse.FeedParser
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.zip.GZIPInputStream

class MusicMixTest {
    @Test fun ordinaryYoutubeRadioCardsPreserveTheirSeedAndMix() {
        val fixture = checkNotNull(javaClass.getResourceAsStream("/innertube/next.json.gz"))
        val response = GZIPInputStream(fixture).bufferedReader().use { Json.parseToJsonElement(it.readText()) }
        val mixes = FeedParser.parseMusicMixes(response)
        val georgeMichael = mixes.single { it.id == "RDizGwDsrQ1eQ" }
        assertEquals("izGwDsrQ1eQ", georgeMichael.seedVideoId)
        assertTrue(georgeMichael.title.contains("Careless Whisper"))
        assertTrue(mixes.size >= 5)
    }

    @Test fun musicDiscoveryIncludesHomeShelvesWithoutChangingTheVideoGrid() {
        val response = Json.parseToJsonElement(
            """
            {"contents":{"richGridRenderer":{"contents":[
              {"richSectionRenderer":{"content":{"shelfRenderer":{"items":[
                {"radioRenderer":{"title":{"simpleText":"Personal mix"},"playlistId":"RDCustom12345",
                  "navigationEndpoint":{"watchEndpoint":{"videoId":"dQw4w9WgXcQ","playlistId":"RDCustom12345","params":"start"}}}}
              ]}}}}
            ]}}}
            """.trimIndent(),
        )
        assertTrue(FeedParser.parseHome(response).items.isEmpty())
        val mix = FeedParser.parseMusicMixes(response).single()
        assertEquals("RDCustom12345", mix.id)
        assertEquals("start", mix.mixParams)
        assertEquals("dQw4w9WgXcQ", mix.seedVideoId)
    }

    @Test fun dailyMixKeepsFamiliarSongsAndInterleavesUniqueSuggestions() {
        val one = song("dQw4w9WgXcQ")
        val two = song("lYBUbBu4W08")
        val three = song("djV11Xbc914")
        val four = song("9jK-NcRmVcw")
        assertEquals(listOf(one, three, two, four), dailyMixOrder(listOf(one, two), listOf(two, three, three, four)))
    }

    private fun song(id: String) = MusicTrack.youtube(VideoSummary(id, id))
}
