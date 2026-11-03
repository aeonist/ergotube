package app.freshtuber.background

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.freshtuber.MainActivity
import app.freshtuber.R
import app.freshtuber.data.subscriptions.LocalVideo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    fun allowed(): Boolean = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(NEW_VIDEOS, context.getString(R.string.notification_new_videos), NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(DOWNLOADS, context.getString(R.string.library_downloads), NotificationManager.IMPORTANCE_LOW))
    }
    fun video(video: LocalVideo) {
        if (!allowed()) return
        createChannels()
        val intent = Intent(context, MainActivity::class.java).putExtra("videoId", video.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(context, video.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, NEW_VIDEOS).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(video.channelName).setContentText(video.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(video.title)).setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(video.id.hashCode(), notification) } catch (_: SecurityException) { }
    }
    companion object { const val NEW_VIDEOS = "new_videos"; const val DOWNLOADS = "downloads" }
}
