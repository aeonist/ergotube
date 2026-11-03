package app.freshtuber.background

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import app.freshtuber.MainActivity
import app.freshtuber.R
import app.freshtuber.data.downloads.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*

class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val videoId get() = inputData.getString("videoId").orEmpty()
    private val dependencies get() = EntryPointAccessors.fromApplication(applicationContext, BackgroundDependencies::class.java)
    private val notificationId get() = "progress-$videoId".hashCode()

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(null)

    override suspend fun doWork(): Result {
        if (!DownloadFiles.validId(videoId)) return Result.failure()
        return try {
            setForeground(foreground(null))
            dependencies.downloads().transfer(videoId, id.toString()) { item ->
                if (item.state == DownloadStatus.Complete) completed(item)
                else try { NotificationManagerCompat.from(applicationContext).notify(notificationId, notification(item)) } catch (_: SecurityException) { }
            }
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            dependencies.downloads().failedBeforeTransfer(videoId, id.toString())
            Result.failure()
        }
    }

    private fun foreground(item: Download?) = if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification(item), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification(item))

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(applicationContext, videoId.hashCode(),
        Intent(applicationContext, MainActivity::class.java).putExtra("videoId", videoId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notification(item: Download?): Notification {
        dependencies.notifications().createChannels()
        val pause = PendingIntent.getBroadcast(applicationContext, videoId.hashCode(),
            Intent(applicationContext, DownloadPauseReceiver::class.java).putExtra("videoId", videoId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val percent = if (item != null && item.bytesTotal > 0) (item.bytesDone * 100 / item.bytesTotal).toInt().coerceIn(0, 100) else 0
        return NotificationCompat.Builder(applicationContext, AppNotifications.DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(item?.title ?: applicationContext.getString(R.string.library_downloads))
            .setContentText(applicationContext.getString(R.string.download_downloading))
            .setContentIntent(openIntent()).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, percent, item == null || item.bytesTotal <= 0)
            .addAction(0, applicationContext.getString(R.string.download_pause), pause).build()
    }

    private fun completed(item: Download) {
        if (!dependencies.notifications().allowed()) return
        val notification = NotificationCompat.Builder(applicationContext, AppNotifications.DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(item.title)
            .setContentText(applicationContext.getString(R.string.download_complete)).setContentIntent(openIntent()).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(applicationContext).notify("complete-$videoId".hashCode(), notification) } catch (_: SecurityException) { }
    }
}

/** Explicit, app-only action: pause keeps the partial files for a later resume. */
class DownloadPauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("videoId")?.takeIf(DownloadFiles::validId) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { EntryPointAccessors.fromApplication(context.applicationContext, BackgroundDependencies::class.java).downloads().pause(id) }
            finally { pending.finish() }
        }
    }
}
