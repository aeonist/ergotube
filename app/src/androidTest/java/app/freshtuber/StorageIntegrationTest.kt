package app.freshtuber

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.data.local.*
import app.freshtuber.data.subscriptions.*
import app.freshtuber.data.downloads.*
import app.freshtuber.data.settings.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @get:Rule val migrations = MigrationTestHelper(instrumentation, AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun upgradesAllReleasedSchemasWithoutLosingPositionsOrSubscriptions() {
        for (version in 1..6) {
            val name = "migration-$version"
            context.deleteDatabase(name)
            migrations.createDatabase(name, version).apply {
                execSQL("INSERT INTO playback_positions VALUES ('BaW_jenozKc', 5000, 10000, 123)")
                if (version >= 2) execSQL("INSERT INTO local_channels VALUES ('UCsXVk37bltHxD1rDPwtNM8Q', 'Kurzgesagt', NULL, 1, 123, 1, 123, 0)")
                if (version >= 4) execSQL(
                    "INSERT INTO downloads (videoId, title, status, workId, requestedHeight, height, durationSeconds, bytesDone, bytesTotal, separateAudio, createdAt" +
                        (if (version >= 6) ", kind" else "") + ") " +
                        "VALUES ('BaW_jenozKc', 'Paused video', 'Paused', 'existing-worker', 720, 720, 30, 800, 2000, 1, 123" +
                        (if (version >= 6) ", 'Video'" else "") + ")",
                )
                // Earlier music test builds (5 and 6) already kept songs; they stay, unfavourited.
                if (version >= 5) execSQL("INSERT INTO music_tracks (id, source, title, durationMs, saved, lastPlayedAt) VALUES ('yt:BaW_jenozKc', 'YouTube', 'Song', 1000, 1, 77)")
                close()
            }
            migrations.runMigrationsAndValidate(name, 7, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).apply {
                if (version >= 5) query("SELECT favorite, addedAt, saved FROM music_tracks").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                    assertEquals(77L, it.getLong(1))
                    assertEquals(1, it.getInt(2))
                }
                query("SELECT positionMs FROM playback_positions").use { assertTrue(it.moveToFirst()); assertEquals(5000L, it.getLong(0)) }
                if (version >= 2) query("SELECT notifications FROM local_channels").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
                if (version >= 4) query("SELECT kind, status, workId, bytesDone FROM downloads").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Video", it.getString(0))
                    assertEquals("Paused", it.getString(1))
                    assertEquals("existing-worker", it.getString(2))
                    assertEquals(800, it.getInt(3))
                }
                close()
            }
            context.deleteDatabase(name)
        }
    }

    @Test fun deletingLocalChannelCascadesItsFeedButNotDownloads() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val channel = LocalChannel("UCsXVk37bltHxD1rDPwtNM8Q", "Kurzgesagt", null, addedAt = 123)
            db.localSubscriptions().save(channel)
            db.localSubscriptions().insertVideos(listOf(LocalVideo("BaW_jenozKc", channel.id, channel.name, "Test", null, 123)))
            db.downloads().save(Download("BaW_jenozKc", "Offline test", channelId = channel.id, workId = "test"))
            db.localSubscriptions().remove(channel.id)
            assertTrue(db.localSubscriptions().videos().first().isEmpty())
            assertNotNull(db.downloads().get("BaW_jenozKc"))
        } finally { db.close() }
    }

    @Test fun oldWorkerCannotOverwriteProgressFromNewGeneration() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.downloads()
            dao.save(Download("BaW_jenozKc", "Test", workId = "new", status = DownloadStatus.Downloading.name))
            dao.progress("BaW_jenozKc", "old", 1000, 2000)
            assertEquals(0L, dao.get("BaW_jenozKc")!!.bytesDone)
            dao.progress("BaW_jenozKc", "new", 1000, 2000)
            assertEquals(1000L, dao.get("BaW_jenozKc")!!.bytesDone)
            dao.state("BaW_jenozKc", DownloadStatus.Paused.name)
            dao.progress("BaW_jenozKc", "new", 2000, 2000)
            assertEquals(1000L, dao.get("BaW_jenozKc")!!.bytesDone)
        } finally { db.close() }
    }

    @Test fun audioAndVideoDownloadsHaveIndependentGenerationsAndDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val id = "BaW_jenozKc"
            val dao = db.downloads()
            val video = Download(id, "Video", workId = "video")
            val audio = video.copy(title = "Audio", workId = "audio", kind = DownloadKind.Audio, status = DownloadStatus.Downloading.name)
            dao.save(video)
            dao.save(audio)
            dao.progress(id, "video", 20, 100, DownloadKind.Audio)
            assertEquals(0L, dao.get(id, DownloadKind.Audio)?.bytesDone)
            dao.progress(id, "audio", 30, 100, DownloadKind.Audio)
            assertEquals(30L, dao.get(id, DownloadKind.Audio)?.bytesDone)
            dao.delete(id, DownloadKind.Audio)
            assertNull(dao.get(id, DownloadKind.Audio))
            assertEquals(video, dao.get(id))
        } finally { db.close() }
    }

    @Test fun preferencesPersistIndependentlyAndRestore() = runBlocking {
        val settings = AppSettings(context)
        val original = settings.preferences.first()
        try {
            settings.setTheme(ThemeMode.Amoled)
            settings.setFeature(AppFeature.DownloadsWifi, false)
            settings.setDownloadQuality(480)
            settings.setPlaybackQuality(true, 0)
            settings.setPlaybackQuality(false, 480)
            settings.setDockWidth(25)
            settings.setFeature(AppFeature.MiniPlayerTapOpens, false)
            settings.setFeature(AppFeature.ShortsTab, false)
            val reopened = AppSettings(context).preferences.first()
            assertEquals(ThemeMode.Amoled, reopened.theme)
            assertFalse(reopened.enabled(AppFeature.DownloadsWifi))
            assertEquals(480, reopened.downloadQuality)
            assertEquals(0, reopened.wifiQuality)
            assertEquals(480, reopened.mobileQuality)
            assertEquals(25, reopened.dockWidth)
            assertFalse(reopened.enabled(AppFeature.MiniPlayerTapOpens))
            assertEquals(original.quality, reopened.quality)
            assertEquals(original.enabled(AppFeature.SponsorBlock), reopened.enabled(AppFeature.SponsorBlock))
        } finally {
            settings.setTheme(original.theme)
            settings.setPlaybackQuality(true, original.wifiQuality)
            settings.setPlaybackQuality(false, original.mobileQuality)
            settings.setDockWidth(original.dockWidth)
            settings.setFeature(AppFeature.MiniPlayerTapOpens, original.enabled(AppFeature.MiniPlayerTapOpens))
            settings.setFeature(AppFeature.DownloadsWifi, original.enabled(AppFeature.DownloadsWifi))
            settings.setFeature(AppFeature.ShortsTab, original.enabled(AppFeature.ShortsTab))
            settings.setDownloadQuality(original.downloadQuality)
        }
    }
}
