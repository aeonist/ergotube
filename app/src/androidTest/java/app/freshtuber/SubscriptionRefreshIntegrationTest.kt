package app.freshtuber

import android.app.NotificationManager
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.background.AppNotifications
import app.freshtuber.data.local.AppDatabase
import app.freshtuber.data.settings.*
import app.freshtuber.data.subscriptions.LocalSubscriptions
import app.freshtuber.domain.model.ChannelSummary
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SubscriptionRefreshIntegrationTest {
    @Test fun rssBaselineDoesNotNotifyAndNewItemsNotifyOnlyOnceWithOptIn() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        Thread.sleep(300)
        val manager = context.getSystemService(NotificationManager::class.java)
        val settings = AppSettings(context)
        val originalNotifications = settings.notificationsEnabled.first()
        val originalShorts = settings.shortsInFeeds.first()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val ids = listOf("rssTest0001", "rssTest0002", "rssTest0003", "rssTest0004")
        var visibleIds = listOf(ids[0])
        var shortId: String? = null
        val published = Instant.now().plusSeconds(2).toString()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("www.youtube.com", chain.request().url.host)
            assertNull(chain.request().header("Cookie"))
            val xml = "<feed xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\">" + visibleIds.joinToString("") { id ->
                "<entry><yt:videoId>$id</yt:videoId><title>RSS $id</title><published>$published</published><link rel=\"alternate\" href=\"https://www.youtube.com/${if (id == shortId) "shorts/$id" else "watch?v=$id"}\" /></entry>"
            } + "</feed>"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(xml.toResponseBody("application/atom+xml".toMediaType())).build()
        }.build()
        val repository = LocalSubscriptions(db, client, settings, AppNotifications(context))
        fun posted(id: String) = manager.activeNotifications.any { it.id == id.hashCode() }
        try {
            ids.forEach { manager.cancel(it.hashCode()) }
            settings.setNotifications(true)
            settings.setFeature(AppFeature.ShortsInFeeds, false)
            val channel = ChannelSummary("UCsXVk37bltHxD1rDPwtNM8Q", "RSS integration test")
            repository.notify(channel, true)
            assertEquals(0, repository.refresh(notify = true))
            assertFalse("The baseline must not flood old-video notifications", posted(ids[0]))
            visibleIds = listOf(ids[1], ids[0])
            assertEquals(0, repository.refresh(notify = true))
            Thread.sleep(300)
            assertTrue("A new video should notify", posted(ids[1]))
            manager.cancel(ids[1].hashCode())
            repository.refresh(notify = true)
            assertFalse("A seen RSS entry must not notify twice", posted(ids[1]))
            shortId = ids[2]
            visibleIds = listOf(ids[2]) + visibleIds
            repository.refresh(notify = true)
            assertFalse("Shorts respect the feed setting", posted(ids[2]))
            repository.notify(channel, false)
            visibleIds = listOf(ids[3]) + visibleIds
            repository.refresh(notify = true)
            assertFalse("The channel's bell is an opt-in", posted(ids[3]))
            assertEquals(4, db.localSubscriptions().videos().first().size)
        } finally {
            ids.forEach { manager.cancel(it.hashCode()) }
            settings.setNotifications(originalNotifications)
            settings.setFeature(AppFeature.ShortsInFeeds, originalShorts)
            db.close()
        }
    }
}
