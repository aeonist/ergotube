package app.freshtuber

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.background.AppNotifications
import app.freshtuber.data.subscriptions.LocalVideo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIntegrationTest {
    @Test fun postsChannelNotificationWithAnImmutableOpenVideoIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        Thread.sleep(500)
        val notifications = AppNotifications(context)
        assertTrue(notifications.allowed())
        val video = LocalVideo("testVideo01", "UCsXVk37bltHxD1rDPwtNM8Q", "Notification test", "Local RSS test", null, 123)
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            notifications.video(video)
            Thread.sleep(300)
            val posted = manager.activeNotifications.first { it.id == video.id.hashCode() }.notification
            assertEquals(AppNotifications.NEW_VIDEOS, posted.channelId)
            assertEquals("Notification test", posted.extras.getCharSequence("android.title").toString())
            assertNotNull(posted.contentIntent)
            assertTrue(posted.contentIntent.isImmutable)
        } finally { manager.cancel(video.id.hashCode()) }
    }
}
