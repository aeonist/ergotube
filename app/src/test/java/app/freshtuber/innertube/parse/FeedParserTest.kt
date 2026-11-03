package app.freshtuber.innertube.parse

import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.LikeStatus
import app.freshtuber.innertube.InnerTubeCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream

/** Real anonymous responses captured 2026-11-01, see resources/innertube/README.md. */
internal fun gzFixture(name: String): JsonObject {
    val stream = checkNotNull(FeedParserTest::class.java.getResourceAsStream("/innertube/$name.json.gz")) { name }
    return Json.parseToJsonElement(GZIPInputStream(stream).bufferedReader().readText()) as JsonObject
}

private const val NBSP = " "

class FeedParserTest {

    @Test
    fun `search mixes old video renderers, shorts and a channel`() {
        val page = FeedParser.parse(gzFixture("search"))
        val videos = page.items.filterIsInstance<FeedItem.Video>().map { it.video }

        val first = videos.first { !it.isShort }
        assertEquals("L_CZYpYPozk", first.id)
        assertEquals("БОРЩ #КОНКУРС - Рецепты от Ивлева", first.title)
        assertEquals("IVLEV CHEF", first.channelName)
        assertEquals("UCRxH-Wdf4c_2nDu3_lIQrvg", first.channelId)
        assertEquals("12:49", first.durationText)
        assertEquals("5${NBSP}793${NBSP}279 просмотров • 7 лет назад", first.detailText)
        assertNotNull(first.channelAvatarUrl)
        assertTrue(first.thumbnailUrl!!.startsWith("https://"))

        assertEquals(11, videos.count { !it.isShort })
        assertTrue(videos.count { it.isShort } > 5)
        assertTrue(videos.filter { it.isShort }.all { it.title.isNotBlank() && it.thumbnailUrl != null })
        assertEquals(1, page.items.count { it is FeedItem.Channel })
        assertNotNull(page.continuation)
    }

    @Test
    fun `channel tab uses lockup view models without a channel row`() {
        val page = FeedParser.parse(gzFixture("channel_videos"))
        val videos = page.items.filterIsInstance<FeedItem.Video>().map { it.video }
        assertEquals(30, videos.size)
        val first = videos.first()
        assertEquals("_c2e1NcI3Sc", first.id)
        assertEquals("Победи Холанда, Выиграй \$1,000,000", first.title)
        assertEquals("22:28", first.durationText)
        assertEquals("42${NBSP}млн просмотров • 1 день назад", first.detailText)
        assertNull(first.channelName)
        assertFalse(first.isLive)
        assertNotNull(page.continuation)
    }

    @Test
    fun `playlist lockups carry the channel in the first row`() {
        val page = FeedParser.parse(gzFixture("playlist"))
        val videos = page.items.filterIsInstance<FeedItem.Video>().map { it.video }
        assertEquals(10, videos.size)
        val first = videos.first()
        assertEquals("GvgqDSnpRQM", first.id)
        assertEquals("Google Search Stories", first.channelName)
        assertEquals("UCvceBgMIpKb4zK1ss-Sh90w", first.channelId)
        assertEquals("2,6${NBSP}млн просмотров • 13 лет назад", first.detailText)
        assertNotNull(page.continuation)
    }

    @Test
    fun `signed-out home is just a nudge`() {
        val page = FeedParser.parse(gzFixture("home_anon"))
        assertTrue(page.items.isEmpty())
        assertNull(page.continuation)
    }

    @Test
    fun `filter chips are read with their tokens`() {
        val chips = Json.parseToJsonElement(
            """
            {"header":{"feedFilterChipBarRenderer":{"contents":[
              {"chipCloudChipRenderer":{"text":{"runs":[{"text":"Все"}]},"isSelected":true}},
              {"chipCloudChipRenderer":{"text":{"runs":[{"text":"Музыка"}]},
                "navigationEndpoint":{"continuationCommand":{"token":"music-token"}}}}
            ]}}}
            """.trimIndent(),
        )
        val filters = FeedParser.parse(chips).filters
        assertEquals(listOf("Все", "Музыка"), filters.map { it.title })
        assertTrue(filters[0].selected)
        assertNull(filters[0].token)
        assertEquals("music-token", filters[1].token)
    }
}

class VideoPageParserTest {

    @Test
    fun `watch page gives details, like state, channel and related`() {
        val details = checkNotNull(VideoPageParser.parse("dQw4w9WgXcQ", gzFixture("next")))
        assertEquals("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", details.title)
        assertEquals("1${NBSP}832${NBSP}886${NBSP}980 просмотров", details.viewsText)
        assertEquals("17 лет назад", details.dateText)
        assertTrue(details.description!!.startsWith("The official video for"))
        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", details.channel?.id)
        assertEquals("Rick Astley", details.channel?.name)
        assertEquals("4,55${NBSP}млн подписчиков", details.channel?.detailText)
        assertEquals("19${NBSP}млн", details.likeCountText?.replace(' ', ' '))
        assertEquals(LikeStatus.Indifferent, details.likeStatus)
        assertEquals("like/like", details.likeCommand?.path)
        assertEquals(20, details.related.size)
        assertNotNull(details.commentsContinuation)
    }

    @Test
    fun `a response without the primary info is not a video page`() {
        assertNull(VideoPageParser.parse("x", gzFixture("home_anon")))
    }
}

class CommentsParserTest {

    @Test
    fun `comments are joined with their entities`() {
        val page = CommentsParser.parse(gzFixture("comments"))
        assertEquals(20, page.comments.size)
        assertEquals("2${NBSP}461${NBSP}198${NBSP}комментариев", page.countText)

        val first = page.comments.first()
        assertEquals("UgyEnXfdC-umwvTt8JF4AaABAg", first.id)
        assertEquals("@Oatman69", first.authorName)
        assertEquals("569${NBSP}тыс.", first.likeCountText)
        assertEquals("723", first.replyCountText)
        assertFalse(first.isLiked)
        assertNotNull(first.repliesContinuation)
        assertTrue(first.text.isNotBlank())

        // Signed out: no commands to like, reply or post.
        assertNull(first.likeCommand)
        assertNull(first.replyCommand)
        assertNull(page.createCommand)

        assertEquals(listOf(true, false), page.sortOptions.map { it.selected })
        assertNotNull(page.continuation)
    }
}

class InnerTubeCommandTest {

    private fun json(raw: String) = Json.parseToJsonElement(raw)

    @Test
    fun `like endpoints map to the right path and params`() {
        val like = InnerTubeCommand.fromLike(json("""{"status":"LIKE","target":{"videoId":"v"},"likeParams":"P"}"""))
        assertEquals("like/like", like?.path)
        assertEquals("P", like?.body?.get("params")?.jsonPrimitive?.content)
        assertEquals("v", like?.body?.get("target")?.jsonObject?.get("videoId")?.jsonPrimitive?.content)

        assertEquals("like/dislike", InnerTubeCommand.fromLike(json("""{"status":"DISLIKE","target":{"videoId":"v"}}"""))?.path)
        assertEquals("like/removelike", InnerTubeCommand.fromLike(json("""{"status":"INDIFFERENT","target":{"videoId":"v"}}"""))?.path)
        assertNull(InnerTubeCommand.fromLike(json("""{"status":"LIKE"}""")))
    }

    @Test
    fun `subscribe keeps channel ids and params`() {
        val cmd = InnerTubeCommand.fromSubscribe(json("""{"channelIds":["UC1"],"params":"EgIIAhgA"}"""))
        assertEquals("subscription/subscribe", cmd?.path)
        assertEquals("UC1", cmd?.body?.get("channelIds")?.jsonArray?.single()?.jsonPrimitive?.content)
        assertNull(InnerTubeCommand.fromSubscribe(json("""{"channelIds":[]}""")))
    }

    @Test
    fun `comment commands take text`() {
        val create = InnerTubeCommand.fromCreateComment(json("""{"createCommentParams":"C"}"""))!!.withCommentText("Привет")
        assertEquals("comment/create_comment", create.path)
        assertEquals(JsonPrimitive("Привет"), create.body["commentText"])
        assertEquals(
            "comment/perform_comment_action",
            InnerTubeCommand.fromCommentAction(json("""{"action":"A"}"""))?.path,
        )
    }

    @Test
    fun `playlist picker falls back to building edit commands`() {
        val options = PlaylistPickerParser.parse(
            "vid",
            json(
                """{"contents":[{"addToPlaylistRenderer":{"playlists":[
                  {"playlistAddToOptionRenderer":{"playlistId":"WL","title":{"simpleText":"Смотреть позже"},"containsSelectedVideos":"ALL"}},
                  {"playlistAddToOptionRenderer":{"playlistId":"PL1","title":{"simpleText":"Музыка"},"containsSelectedVideos":"NONE"}}
                ]}}]}""",
            ),
        )
        assertEquals(listOf("WL", "PL1"), options.map { it.id })
        assertTrue(options[0].containsVideo)
        assertEquals("browse/edit_playlist", options[1].addCommand.path)
        assertTrue(options[1].addCommand.body.toString().contains("ACTION_ADD_VIDEO"))
        assertTrue(options[0].removeCommand.body.toString().contains("ACTION_REMOVE_VIDEO_BY_VIDEO_ID"))
    }
}
